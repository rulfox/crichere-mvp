# Phase 19: Android release readiness

**Status:** R8, logging, version, Crashlytics and signing config done on branch `android-release`. Owner: create the upload
keystore, then App Links SHA and Maps key once the app is in Play Console.
**Date:** 2026-11-07

## 1. Done

- **R8 and resource shrinking** on for `release` and `qa`. APK 31 MB (debug) to 5.7 MB. Rules: `androidApp/proguard-rules.pro`.
  Libraries' own consumer rules were enough, so the file only keeps line numbers for crash traces.
- **`qa` build type:** release's R8 output, debug signed, so it can run with `-Penv=local`, which a release build refuses.
  `./gradlew :androidApp:assembleQa -Penv=local`. Never uploaded. Test it after any dependency or `proguard-rules.pro` change,
  because R8 failures only show at runtime.
- **Logging:** Ktor request logging and Koin's Android logger are debug only. `configureNetworkLogging` defaults to off, so
  iOS logs nothing either.
- **Version:** `1.0.0`, versionCode 1.

- **Crash reporting: Firebase Crashlytics** (owner, 2026-11-07, over Play Console's own reports: better traces and alerts,
  and the Firebase project already exists for push). Plugin 3.0.8, library from BOM 34.19.0. Collection is on only in
  `release` (manifest flag `firebase_crashlytics_collection_enabled`), off in debug and qa. No user ID or custom keys are
  set. A release build uploads its R8 mapping to Firebase; qa doesn't. Privacy Policy (doc and web page) lists it, with
  90 days retention. iOS has no Crashlytics yet.
- **Signing config:** `release` signs with the upload key when `local.properties` has all of `RELEASE_STORE_FILE`,
  `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`; otherwise the APK is unsigned. `*.jks` and
  `*.keystore` are gitignored.

Keep `build/outputs/mapping/release/mapping.txt` for every uploaded build. Crashlytics gets it on each release build, and
Play Console needs it in the bundle for its own reports.

### Signing

Play App Signing: Google holds the app signing key; we hold only the upload key. A lost upload key can be reset through Play
support, which takes days. Keep the keystore and its password in a password manager and one offline backup, never in the repo.

```
keytool -genkeypair -v -keystore crichere-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Crichere, C=IN"
```

Then in `mobile/local.properties` (use forward slashes):

```
RELEASE_STORE_FILE=C:/Users/<you>/keys/crichere-upload.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=...
```

`./gradlew :androidApp:bundleRelease` gives `androidApp/build/outputs/bundle/release/androidApp-release.aab` for Play.

## 2. Checked on the qa build

Pixel_9_Pro, local backend, 2026-11-07. No crash in the app:

- Sign in with JSON auth calls, terms tick, notification prompt, dashboard, My Leagues, My Profile.
- League detail, row menu, report sent (row confirmed in the DB), block and unblock, Blocked people.
- Live auction: Next Player arrived over SSE.
- Process death: app killed in the auction, restored to the same screen (saved navigation keys).
- Remote image through Coil, state picker, Google Map, Places search.
- Terms link in a custom tab.
- Suspended account: `ACCOUNT_SUSPENDED` shows the suspended screen.

Release APK: `usesCleartextTraffic=false`, targetSdk 36, 1.0.0.

Compose UI tests: 44 of 44 passed on Pixel_9_Pro, 2026-11-07. These run the debug build, so they cover the logging change,
not R8.

Not checked on a minified build: photo upload to S3, token refresh, push notification delivery, paid join with payment proof.

Crashlytics: `assembleRelease`, `assembleQa` and `assembleDebug` pass; merged manifests have collection `true` for release,
`false` for qa and debug; the release build uploaded its mapping. Not checked: the qa build with Crashlytics added was not
launched on a device, and no crash has reached the Crashlytics console yet. Check both with the first signed build.

## 3. Open

| Item | Needs |
|---|---|
| Upload keystore | Done 2026-11-07, outside the repo. `bundleRelease` signs with it (checked with `jarsigner -verify`). Upload SHA-256 `40:D6:4F:36:DA:61:2F:BD:19:59:64:8A:04:3C:A0:85:84:11:87:CB:1C:83:E5:2D:37:57:CE:92:98:81:3B:7F`. |
| Play App Signing SHA-256 in `web-viewer/public/.well-known/assetlinks.json` | Lists the debug key only. Without it shared links won't open the app in the Play build. Available once the app is in Play Console. |
| Maps key | `Maps SDK Android (debug)` (Maps SDK and Places) allows the debug SHA-1 and, since 2026-11-07, the upload key SHA-1 `79:60:5F:01:25:A7:60:F1:B6:BC:8C:CB:ED:C3:9B:22:84:95:5D:91`. Add the Play app signing SHA-1 after the first upload, or maps and Places fail in Play installs. `gcloud services api-keys update` replaces the whole list: pass every app and API target. Rename the key then. |
| Data safety form | Add "Crash logs" and "Diagnostics", collected, not shared, for app functionality, not optional. |
| Firebase BOM 35 | 35.x is out (35.1.0 on 2026-11-07). Major jump for auth and messaging; own upgrade with device checks. |
| iOS crash reports | Crashlytics not added to iOS. Needs the Privacy Policy line widened when it is. |

Merging to `master` deploys production through Railway's GitHub trigger, including the web viewer.

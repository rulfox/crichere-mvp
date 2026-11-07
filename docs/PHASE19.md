# Phase 19: Android release readiness

**Status:** R8, logging and version done on branch `android-release`. Signing, App Links, Maps key and crash reporting open.
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

Keep `build/outputs/mapping/release/mapping.txt` for every uploaded build, or upload the bundle with it. Without it crash
traces can't be read.

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

## 3. Open

| Item | Needs |
|---|---|
| Upload keystore | Owner decision. No `signingConfig` on `release` yet, so `assembleRelease` gives an unsigned APK. Keep the keystore and password out of the repo. |
| Play App Signing SHA-256 in `web-viewer/public/.well-known/assetlinks.json` | Lists the debug key only. Without it shared links won't open the app in the Play build. Available once the app is in Play Console. |
| Maps key | `Maps SDK Android (debug)` must allow the release and Play signing SHA-1, or maps and Places fail in the Play build. |
| Crash reporting | Owner decision: Crashlytics (needs Privacy Policy and Data safety updates) or Play Console's reports. |

Merging to `master` deploys production through Railway's GitHub trigger, including the web viewer.

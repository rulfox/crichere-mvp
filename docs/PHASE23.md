# Phase 23: Minimum app version (forced update)

The server can block app builds older than a minimum version and send people to the store to update.
Needed before bid steps (PHASE22) reach production, so every production user can be moved forward.
Design canvas: see "App Update Design" (U1, U2).

## How it works

1. Every request from the app sends `X-App-Platform: android|ios` and `X-App-Version: <versionName>`
   (e.g. `1.1.0`; Android `versionName`, iOS `CFBundleShortVersionString`).
2. The backend compares it with `MIN_ANDROID_VERSION` / `MIN_IOS_VERSION` (Railway env, no deploy to
   change) as major.minor.patch numbers (`1.10.0 > 1.9.0`).
3. Too old → **HTTP 426**, problem code `APP_UPDATE_REQUIRED`, extensions `updateUrl` (the platform's
   store link) and `minVersion` (for the screen's "You have 1.0.0 · Needed 1.1.0" line). The app
   reacts to the code, not the status.
4. The app shows a full-screen, undismissable "Update required" page.

**Never blocks:** a request without both headers (today's 1.0.0 test builds, web viewer, admin site),
an unparseable version, health checks, or a platform whose minimum is empty. Both minimums start empty
(check off).

## Backend

- Servlet filter ahead of the security filters (so sign-in is covered too).
- Config `crichere.app-update.{min-android, min-ios, android-url, ios-url}` from env
  `MIN_ANDROID_VERSION`, `MIN_IOS_VERSION`, `ANDROID_UPDATE_URL`, `IOS_UPDATE_URL` (iOS points at
  TestFlight during testing, the App Store later).
- Tests: version compare (incl. bad input), missing header, older / equal / newer, empty minimum.

## Shared (KMP)

- `expect` platform info: platform name and app version.
- `HttpClientFactory.applyBaseConfig()` adds both headers (main client, sign-in client, SSE stream;
  not the S3 upload client).
- The `HttpResponseValidator` that already catches `ACCOUNT_SUSPENDED` also catches
  `APP_UPDATE_REQUIRED` and sets an app-wide `updateRequired(updateUrl)` state (an `AppUpdateNotices`
  beside `AccountNotices`). The sign-in client gets the same validator.
- Tests: headers sent; 426 sets the state; other errors don't.

## Android

- Play In-App Updates (`com.google.android.play:app-update-ktx`): when blocked, ask Play for an update;
  available → Google's **immediate** update flow; not available (debug / sideloaded) → our blocking
  screen (U1) with a Play Store link.
- Shown at the app root (`AuthNavHost`), above everything, mid-auction too. Back does nothing.

## iOS

- No in-app update API: our blocking screen (U1); **Update** opens `updateUrl` (TestFlight now, App Store
  later). If it can't be opened, U2's fallback text.
- At the root view, beside the suspended screen.

## Release steps

1. Bump: Android `versionCode 2`, `versionName 1.1.0`; iOS `1.1.0` build 2 (`project.yml`, `Info.plist`).
2. Ship to Play closed testing and TestFlight.
3. Set `MIN_ANDROID_VERSION=1.1.0`, `MIN_IOS_VERSION=1.1.0` on Railway; older test builds are blocked.
4. Later releases: raise the minimum only when a release must be forced.

## Verification

- Backend filter tests; `curl` with old / new / missing headers.
- Shared client tests (Ktor mock engine).
- Android and iOS: minimum above the build's version → block on launch and mid-auction; Update opens
  the store; Back can't dismiss.

## Decisions (2026-11-26)

- Compare human versions (`1.1.0`) on both platforms, not build numbers.
- Hard block only for now; a dismissable "update available" nudge can reuse the same headers later.

## Status

- Backend: done (`appupdate/AppUpdate.kt`, registered first in `SecurityConfig`); unit tests pass
  (`AppVersionTest`, `AppUpdateFilterTest`). `ANDROID_UPDATE_URL` defaults to the Play Store listing;
  `IOS_UPDATE_URL` must be set (the TestFlight public link) before `MIN_IOS_VERSION` is.
- Shared: done (`configureAppInfo`, headers and the 426 check on both backend clients,
  `AppUpdateNotices`); `AppUpdateCheckTest` written, not yet run (needs the Android SDK).
- Android (`UpdateRequiredGate` over `AuthNavHost` in `MainActivity`, Play immediate update) and iOS
  (`UpdateRequiredView` overlay on `AppRootView`, version set in `iosAppApp.swift`): written, not yet
  compiled (Android SDK and Xcode not available on this Mac).

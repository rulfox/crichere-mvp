package com.crichere.app.network

/**
 * The backend's base URL, as reached from each platform's app process -- not a single shared
 * hardcoded string, because "localhost" means something different on each side:
 *  - Android emulator: `10.0.2.2` is the special alias the emulator's virtual router maps to the
 *    host machine's `localhost` (see [androidMain]'s `actual`).
 *  - iOS simulator: shares the host's network namespace directly, so `localhost` reaches the
 *    Mac's locally running backend (see [iosMain]'s `actual`).
 *
 * Port 8080 matches the backend's default (`server.port` is unset in `application.yml`, so Spring
 * Boot's default applies) under the `local` profile Task 1 wired up.
 */
internal expect val backendBaseUrl: String

/**
 * The public web viewer's base URL (docs/PHASE6.md) -- unlike [backendBaseUrl], this is a plain
 * `https://` link pasted into a chat app, not a URL the app process itself connects to, so it
 * needs no per-platform `expect`/`actual` (no emulator-alias/localhost distinction applies).
 * Points at the local dev server for now; swap to the deployed Railway URL once one exists (same
 * "authored now, upgrade later" posture as this app's other pre-launch placeholders).
 */
const val webViewerBaseUrl: String = "http://localhost:3000"

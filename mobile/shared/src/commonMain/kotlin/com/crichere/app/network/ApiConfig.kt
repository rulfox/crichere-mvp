package com.crichere.app.network

/**
 * The backend's base URL, as reached from each platform's app process -- not a single shared
 * hardcoded string, for two reasons:
 *  - "localhost" means something different on each side: Android emulator's `10.0.2.2` alias vs
 *    iOS simulator sharing the host's network namespace directly (see each `actual`).
 *  - Debug builds point at that local backend (`./gradlew bootRun`); release builds point at the
 *    deployed Railway backend (`https://backend-production-f74e7.up.railway.app`, docs/OVERVIEW.md
 *    Deployment Strategy) -- each `actual` picks per its own platform's debug/release signal
 *    (`BuildConfig.DEBUG` on Android, `Platform.isDebugBinary` on iOS).
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

package com.crichere.app.network

/**
 * The backend's base URL -- both debug and release builds, both platforms, point at the deployed
 * Railway backend (docs/OVERVIEW.md Deployment Strategy). No per-platform `expect`/`actual` or
 * debug/release branching needed as a result.
 */
internal const val backendBaseUrl: String = "https://backend-production-f74e7.up.railway.app"

/**
 * The public web viewer's base URL (docs/PHASE6.md) -- a plain `https://` link pasted into a chat
 * app, not a URL the app process itself connects to. Points at the local dev server for now;
 * swap to the deployed Railway URL once one exists (same "authored now, upgrade later" posture as
 * this app's other pre-launch placeholders).
 */
const val webViewerBaseUrl: String = "http://localhost:3000"

package com.crichere.app.network

/** The deployed Railway backend (docs/OVERVIEW.md Deployment Strategy). */
const val PRODUCTION_BACKEND_BASE_URL: String = "https://api.crichere.com"

/**
 * The backend's base URL. Production unless the platform entry point calls [configureBackendBaseUrl]
 * before the HTTP clients are built -- the Android app does so from `-Penv=local` (docs/PHASE15.md),
 * pointing a debug build at a backend on the dev machine. iOS never calls it and stays on production.
 */
internal var backendBaseUrl: String = PRODUCTION_BACKEND_BASE_URL
    private set

fun configureBackendBaseUrl(url: String) {
    backendBaseUrl = url
}

/**
 * Whether the HTTP clients log requests. Off unless the platform entry point turns it on before the
 * clients are built, so a release build never writes request URLs to the device log.
 */
internal var networkLoggingEnabled: Boolean = false
    private set

fun configureNetworkLogging(enabled: Boolean) {
    networkLoggingEnabled = enabled
}

/**
 * The public web viewer's base URL (docs/PHASE6.md) -- a plain `https://` link pasted into a chat
 * app, not a URL the app process itself connects to.
 */
const val webViewerBaseUrl: String = "https://crichere.com"

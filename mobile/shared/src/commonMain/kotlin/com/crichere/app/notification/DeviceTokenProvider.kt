package com.crichere.app.notification

/**
 * Platform-agnostic "what's this device's current push-notification token" contract (see
 * docs/PHASE8.md). [FcmDeviceTokenProvider] is the real, per-platform implementation -- same
 * `commonMain interface` + plain per-platform class pattern `LocationProvider`/`DeviceLocationProvider`
 * already use (not an `expect`/`actual class`, since there's no shared constructor signature to
 * keep in sync).
 *
 * Android-only this phase (see docs/PHASE8.md's Decisions Made) -- the iOS implementation always
 * returns `null`, which [com.crichere.app.auth.AuthRepository] treats as "nothing to register,"
 * the same as a real, temporary lookup failure on Android.
 */
interface DeviceTokenProvider {
    /** Best-effort -- `null` if unavailable (no Firebase Messaging on this platform yet, or a real lookup failure). Never throws. */
    suspend fun currentToken(): String?
}

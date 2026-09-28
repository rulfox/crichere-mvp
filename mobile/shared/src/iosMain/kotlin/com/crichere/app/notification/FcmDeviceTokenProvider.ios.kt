package com.crichere.app.notification

/**
 * iOS [DeviceTokenProvider]: always `null` -- FCM delivery to iOS needs an APNs key/cert uploaded
 * via the Apple Developer Portal + Firebase Console (entirely outside this codebase) and no
 * Firebase Messaging SDK is wired into the iOS target this phase (see docs/PHASE8.md's Decisions
 * Made: Android only). `AuthRepository` already treats `null` as "nothing to register," so this
 * is a real, correct implementation of the contract, not a placeholder.
 */
class FcmDeviceTokenProvider : DeviceTokenProvider {
    override suspend fun currentToken(): String? = null
}

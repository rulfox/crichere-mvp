package com.crichere.app.auth

/**
 * What a successful OTP check hands back, which depends on which provider proved the phone:
 *
 *  - [FirebaseIdToken]: Firebase Phone Auth completed client-side. The caller must still
 *    exchange the token for a Crichere session (`AuthRepository.exchangeSession`).
 *  - [BackendSession]: the backend checked the code itself (MSG91, docs/PHASE12.md) and already
 *    issued the session. `AuthRepository` has persisted its tokens by the time this is returned.
 */
sealed interface OtpVerification {
    data class FirebaseIdToken(val idToken: String) : OtpVerification
    data class BackendSession(val session: AuthResult) : OtpVerification
}

/** Which mechanism the backend wants the app to use for phone OTP (`GET /auth/config`). */
enum class OtpProvider { FIREBASE, MSG91 }

/**
 * [PhoneVerificationHandle.resendToken] for the backend-driven flow: marks the handle as
 * belonging to it, so a "Resend" goes to `/auth/otp/resend` for this challenge instead of
 * starting a new send.
 */
data class BackendResendToken(val challengeId: String)

/**
 * A backend-driven OTP request failed. [message] is always one of our own user-safe strings --
 * never raw server or exception text -- so view models may show it directly.
 */
class OtpRequestFailedException(message: String) : Exception(message)

/**
 * The code is no longer usable: expired, already used, or out of attempts (the backend says all
 * three identically). The only way forward is a fresh code from Phone Entry.
 */
class OtpExpiredException(message: String = "This code has expired. Please request a new one.") : Exception(message)

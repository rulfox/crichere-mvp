package com.crichere.app.auth

/**
 * Platform-agnostic phone-OTP verification contract. [FirebasePhoneAuthClient] is the real,
 * per-platform `expect`/`actual` implementation (backed by the real Firebase Phone Auth SDK on
 * each side -- see that file). `AuthRepository` and the two ViewModels depend on this interface
 * rather than the concrete `expect class` directly, so `commonTest` can substitute a fake/test
 * double implementation -- exercising the real OTP state-machine logic (cooldown, resend, wrong-
 * attempt counting) without ever touching a real Firebase project, which this environment has
 * none of connected client-side (see task-6-report.md).
 */
interface PhoneAuthClient {

    /**
     * Starts (or, when [resendToken] is a platform token carried over from a previous
     * [PhoneVerificationHandle], restarts/resends) phone-number verification for [phoneNumber].
     * On success, returns the [PhoneVerificationHandle] needed to complete verification via
     * [verifyCode] and to request a further resend.
     */
    suspend fun sendVerificationCode(
        phoneNumber: String,
        resendToken: Any? = null,
    ): Result<PhoneVerificationHandle>

    /**
     * Completes verification for [verificationId] using the user-entered [code]. Returns a real
     * Firebase ID token (a signed JWT) on success -- this is what gets exchanged for a Crichere
     * session via `POST /api/v1/auth/session`.
     *
     * On failure, implementations MUST fail with [InvalidOtpCodeException] specifically when the
     * SDK's own signal means "the code itself was wrong" (Android: a real
     * `com.google.firebase.auth.FirebaseAuthInvalidCredentialsException`) -- any other failure
     * (network error, expired/unknown verification session, SDK/config error) must fail with a
     * different exception. [OtpVerifyViewModel] relies on this distinction to decide whether a
     * failure burns one of the 5 wrong-code attempts or surfaces as a distinct, retryable error.
     */
    suspend fun verifyCode(verificationId: String, code: String): Result<String>
}

/**
 * Signals that [PhoneAuthClient.verifyCode] failed specifically because the entered code was
 * wrong -- as opposed to a network/SDK/session error. This is the one failure shape that should
 * burn one of the OTP Verify screen's 5 wrong-code attempts; see [PhoneAuthClient.verifyCode]'s
 * doc and `OtpVerifyViewModel.handleVerifyFailure`.
 */
class InvalidOtpCodeException(message: String = "The code you entered is incorrect.") : Exception(message)

/**
 * What a platform SDK hands back after successfully sending (or resending) an OTP.
 *
 * @property verificationId Opaque session id, passed back into [PhoneAuthClient.verifyCode] and
 *   [PhoneAuthClient.sendVerificationCode] (as part of a resend).
 * @property resendToken Platform-specific "force resend" token -- Android:
 *   `com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken`, used so a resend doesn't
 *   wait out the SMS auto-retrieval timeout again; iOS: always `null`, since Firebase's iOS SDK
 *   has no equivalent concept (a resend there is just calling `verifyPhoneNumber` again). Typed
 *   `Any?` rather than a platform type so this class stays representable in `commonMain`; only
 *   the platform `actual` that produced a given instance ever casts it back.
 */
data class PhoneVerificationHandle(
    val verificationId: String,
    val resendToken: Any? = null,
)

package com.crichere.app.auth

import java.util.UUID

/**
 * Debug-only stand-in for the real [FirebasePhoneAuthClient], wired in by `PlatformModule.android.kt`
 * only when `BuildConfig.DEBUG` is true.
 *
 * This environment has no real Firebase project connected client-side -- no `google-services.json`
 * (see `FirebasePhoneAuthClient.android.kt`'s doc), so `FirebaseAuth.getInstance()` would throw
 * before Phone Entry -> OTP Verify could be driven at all. Task 6's brief already anticipated
 * exactly this gap ("a debug-only build variant or dependency-injected fake ... this is a
 * reasonable, real verification within this environment's limits") -- this is that fake, built for
 * Task 8's real on-device manual walkthrough.
 *
 * Accepts any phone number unconditionally -- there is no real per-number Firebase-console test
 * restriction to model here. For the code: [SUCCESS_CODE] always verifies (standing in for a
 * Firebase-console test phone number's fixed test code); any other 6-digit code fails with
 * [InvalidOtpCodeException], the same shape a real wrong SMS code would produce. That distinction
 * (rather than every code succeeding) is deliberate: it is what lets `OtpVerifyScreen`'s real
 * wrong-attempt-counting/cooldown/resend UI actually be driven and observed on-device, not just
 * re-confirmed from `OtpVerifyViewModelTest`'s existing unit coverage.
 *
 * [verifyCode]'s success path returns an ID-token-*shaped* string, not a real signed Firebase JWT
 * -- it does not need to be verifiable, only present, since the real backend has no Firebase Admin
 * credentials configured either (see task-8-brief.md's ruling) and is expected to reject it. That
 * rejection, observed live and surfacing as a clean, non-crashing error, is the actual thing this
 * leg of the task verifies.
 */
class DebugFakePhoneAuthClient : PhoneAuthClient {

    override suspend fun sendVerificationCode(
        phoneNumber: String,
        resendToken: Any?,
    ): Result<PhoneVerificationHandle> =
        Result.success(PhoneVerificationHandle(verificationId = "debug-fake-verification-${UUID.randomUUID()}"))

    override suspend fun verifyCode(verificationId: String, code: String): Result<String> =
        if (code == SUCCESS_CODE) {
            Result.success("debug-fake-id-token.${UUID.randomUUID()}")
        } else {
            Result.failure(InvalidOtpCodeException())
        }

    companion object {
        /** The one code this debug-only fake treats as correct -- enter this on OTP Verify. */
        const val SUCCESS_CODE = "111111"
    }
}

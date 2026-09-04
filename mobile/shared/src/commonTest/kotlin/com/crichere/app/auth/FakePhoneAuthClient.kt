package com.crichere.app.auth

/**
 * Test double for [PhoneAuthClient] -- the environment note in task-6-brief.md is explicit that
 * real on-device Firebase OTP sending can't be verified here (no Firebase project connected
 * client-side). This fake substitutes for the real SDK boundary so [PhoneEntryViewModel],
 * [OtpVerifyViewModel], and [AuthRepository]'s real logic (cooldown/resend/attempt-counting state
 * machine, HTTP request shapes, token persistence) can be genuinely exercised end to end.
 */
class FakePhoneAuthClient : PhoneAuthClient {

    var sendVerificationCodeResult: Result<PhoneVerificationHandle> =
        Result.success(PhoneVerificationHandle(verificationId = "fake-verification-id"))
    var verifyCodeResult: Result<String> = Result.success("fake-firebase-id-token")

    var sendCallCount = 0
        private set
    var verifyCallCount = 0
        private set
    val sentPhoneNumbers = mutableListOf<String>()
    val sentResendTokens = mutableListOf<Any?>()
    val verifiedCodes = mutableListOf<String>()

    override suspend fun sendVerificationCode(phoneNumber: String, resendToken: Any?): Result<PhoneVerificationHandle> {
        sendCallCount++
        sentPhoneNumbers += phoneNumber
        sentResendTokens += resendToken
        return sendVerificationCodeResult
    }

    override suspend fun verifyCode(verificationId: String, code: String): Result<String> {
        verifyCallCount++
        verifiedCodes += code
        return verifyCodeResult
    }
}

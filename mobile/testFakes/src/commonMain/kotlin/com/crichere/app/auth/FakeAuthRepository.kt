package com.crichere.app.auth

/** In-memory [AuthRepository] test double -- no real backend/Firebase to hit in tests. */
class FakeAuthRepository(
    var currentUserId: String? = null,
) : AuthRepository {

    var nextSendOtpResult: Result<PhoneVerificationHandle> = Result.failure(IllegalStateException("nextSendOtpResult not stubbed"))
    var nextVerifyOtpResult: Result<OtpVerification> = Result.failure(IllegalStateException("nextVerifyOtpResult not stubbed"))
    var nextExchangeSessionResult: AuthResult? = null
    var exchangeSessionError: Throwable? = null
    var nextRefreshResult: AuthResult? = null

    val sendOtpCalls = mutableListOf<String>()
    val verifyOtpCalls = mutableListOf<Pair<String, String>>()
    val exchangeSessionCalls = mutableListOf<String>()
    var logoutCallCount = 0
        private set

    override suspend fun sendOtp(phoneNumber: String, resendToken: Any?): Result<PhoneVerificationHandle> {
        sendOtpCalls += phoneNumber
        return nextSendOtpResult
    }

    override suspend fun verifyOtp(verificationId: String, code: String): Result<OtpVerification> {
        verifyOtpCalls += verificationId to code
        return nextVerifyOtpResult
    }

    override suspend fun exchangeSession(idToken: String): AuthResult {
        exchangeSessionCalls += idToken
        exchangeSessionError?.let { throw it }
        val result = nextExchangeSessionResult ?: error("nextExchangeSessionResult not stubbed")
        currentUserId = result.userId
        return result
    }

    override suspend fun refresh(): AuthResult? = nextRefreshResult

    override suspend fun logout() {
        logoutCallCount++
        currentUserId = null
    }

    override suspend fun getCurrentUserId(): String? = currentUserId
}

package com.crichere.app.auth

/**
 * Interface-only stub for Task 6: the real phone-OTP screens will call this to send/verify an
 * OTP code via Firebase Phone Auth and hand the resulting Firebase ID token to the backend's
 * auth endpoints (`/api/v1/auth/...`). Task 5's job is only to prove the `expect`/`actual` shape compiles
 * on both platforms -- neither `actual` below calls a real Firebase SDK yet (that dependency,
 * and the credentials/config it needs, is explicitly deferred to Task 6 per this task's brief).
 */
expect class FirebasePhoneAuthClient {
    /**
     * Starts phone-number verification. Returns an opaque verification-session ID the platform
     * SDK issues, to be passed back into [verifyCode] alongside the user-entered OTP.
     */
    suspend fun sendVerificationCode(phoneNumber: String): Result<String>

    /**
     * Completes verification for a given session, returning a Firebase ID token on success.
     */
    suspend fun verifyCode(verificationId: String, code: String): Result<String>
}

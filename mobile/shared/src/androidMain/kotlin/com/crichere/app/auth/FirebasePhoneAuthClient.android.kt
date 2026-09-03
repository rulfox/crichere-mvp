package com.crichere.app.auth

/**
 * Android stub: no real Firebase SDK call yet. Task 6 replaces this body with
 * `PhoneAuthProvider.verifyPhoneNumber(...)`/`signInWithCredential(...)` calls against
 * `com.google.firebase:firebase-auth-ktx`, which isn't a dependency of this module yet (adding it
 * now would need `google-services.json` credentials this task doesn't have).
 */
actual class FirebasePhoneAuthClient {

    actual suspend fun sendVerificationCode(phoneNumber: String): Result<String> =
        Result.failure(NotImplementedError("Firebase Phone Auth wiring lands in Task 6"))

    actual suspend fun verifyCode(verificationId: String, code: String): Result<String> =
        Result.failure(NotImplementedError("Firebase Phone Auth wiring lands in Task 6"))
}

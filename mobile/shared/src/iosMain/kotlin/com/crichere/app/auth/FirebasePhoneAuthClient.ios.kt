package com.crichere.app.auth

/**
 * iOS stub: no real Firebase SDK call yet. Task 6 replaces this body with calls into Firebase
 * iOS SDK's `PhoneAuthProvider`/`Auth.auth().signIn(with:)`, wired in via CocoaPods/SPM once
 * that task adds the dependency and `GoogleService-Info.plist` credentials this task doesn't have.
 */
actual class FirebasePhoneAuthClient {

    actual suspend fun sendVerificationCode(phoneNumber: String): Result<String> =
        Result.failure(NotImplementedError("Firebase Phone Auth wiring lands in Task 6"))

    actual suspend fun verifyCode(verificationId: String, code: String): Result<String> =
        Result.failure(NotImplementedError("Firebase Phone Auth wiring lands in Task 6"))
}

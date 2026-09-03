package com.crichere.app.auth

/**
 * Real, per-platform Firebase Phone Auth SDK wiring:
 *  - **Android** (`FirebasePhoneAuthClient.android.kt`): real calls against
 *    `com.google.firebase:firebase-auth`'s actual classes (`PhoneAuthOptions`,
 *    `PhoneAuthProvider`, `FirebaseAuth`) -- genuinely compiles against the real SDK; see
 *    task-6-report.md for exactly what could and couldn't be verified without a Firebase project
 *    connected client-side (there is none in this environment).
 *  - **iOS** (`FirebasePhoneAuthClient.ios.kt`): Kotlin/Native has no cinterop binding to the
 *    Firebase iOS SDK available here (no CocoaPods/SPM/Xcode toolchain on this machine), so this
 *    delegates to a Swift-implemented [IosPhoneAuthBridge] that real (but unverified/uncompiled)
 *    Swift code wires up to the actual Firebase iOS Auth SDK -- authored but unverified, same
 *    status as the rest of this repo's Swift files.
 */
expect class FirebasePhoneAuthClient() : PhoneAuthClient {
    override suspend fun sendVerificationCode(phoneNumber: String, resendToken: Any?): Result<PhoneVerificationHandle>
    override suspend fun verifyCode(verificationId: String, code: String): Result<String>
}

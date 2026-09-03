package com.crichere.app.auth

/**
 * Real Firebase iOS Auth calls (`PhoneAuthProvider.provider().verifyPhoneNumber`,
 * `Auth.auth().signIn(with:)`, etc.) cannot be made directly from Kotlin/Native in this
 * environment: there is no CocoaPods/SPM/Xcode toolchain here to generate a cinterop binding for
 * the Firebase iOS SDK (unlike `platform.Security`/`platform.CoreFoundation`, which ship bundled
 * with Kotlin/Native and needed no such step -- see `SecureStorage.ios.kt`). [FirebasePhoneAuthClient]'s
 * iOS `actual` therefore delegates to this Swift-implemented bridge instead, installed once at app
 * startup via [IosPhoneAuthBridgeHolder] by real (but unverified/uncompiled -- no Mac here) Swift
 * code in `iosApp/iosApp/FirebasePhoneAuthBridge.swift`.
 *
 * Plain completion-callback shape rather than `suspend`: a Swift class conforming to a Kotlin
 * interface with `suspend` members needs SKIE/Kotlin-Native interop machinery this environment
 * cannot exercise or verify either way, so a callback is the simplest shape real Swift code can
 * implement directly against the exported `Shared.framework`.
 */
interface IosPhoneAuthBridge {

    fun sendVerificationCode(
        phoneNumber: String,
        resendToken: Any?,
        onResult: (verificationId: String?, resendToken: Any?, error: Throwable?) -> Unit,
    )

    fun verifyCode(
        verificationId: String,
        code: String,
        onResult: (idToken: String?, error: Throwable?) -> Unit,
    )
}

/**
 * Set once from Swift (`iosAppApp.swift`, at app launch, after Firebase itself is configured)
 * before any screen calls into [FirebasePhoneAuthClient]. `null` until then -- the iOS `actual`
 * fails fast with a clear message rather than crashing if something calls in earlier.
 */
object IosPhoneAuthBridgeHolder {
    var bridge: IosPhoneAuthBridge? = null
}

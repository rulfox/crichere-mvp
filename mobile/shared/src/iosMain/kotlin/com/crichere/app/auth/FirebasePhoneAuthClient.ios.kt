package com.crichere.app.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS `actual`: delegates to a Swift-implemented [IosPhoneAuthBridge] rather than calling the
 * Firebase iOS SDK directly from Kotlin/Native -- see [IosPhoneAuthBridge]'s doc for why. This
 * file itself is pure Kotlin (no cinterop against third-party frameworks), so it compiles for
 * real on this machine's Kotlin/Native cross-compiler; the real Firebase SDK usage it delegates
 * to lives entirely in Swift and is unverified (no Mac/Xcode here).
 */
actual class FirebasePhoneAuthClient : PhoneAuthClient {

    actual override suspend fun sendVerificationCode(
        phoneNumber: String,
        resendToken: Any?,
    ): Result<PhoneVerificationHandle> {
        val bridge = IosPhoneAuthBridgeHolder.bridge
            ?: return Result.failure(IllegalStateException("iOS Firebase Auth bridge is not installed"))

        return runCatching {
            suspendCancellableCoroutine { continuation ->
                bridge.sendVerificationCode(phoneNumber, resendToken) { verificationId, newResendToken, error ->
                    if (!continuation.isActive) return@sendVerificationCode
                    when {
                        error != null -> continuation.resumeWithException(error)
                        verificationId != null ->
                            continuation.resume(PhoneVerificationHandle(verificationId, newResendToken))
                        else -> continuation.resumeWithException(
                            IllegalStateException("Bridge returned neither a verification id nor an error"),
                        )
                    }
                }
            }
        }
    }

    actual override suspend fun verifyCode(verificationId: String, code: String): Result<String> {
        val bridge = IosPhoneAuthBridgeHolder.bridge
            ?: return Result.failure(IllegalStateException("iOS Firebase Auth bridge is not installed"))

        return runCatching {
            suspendCancellableCoroutine { continuation ->
                bridge.verifyCode(verificationId, code) { idToken, error ->
                    if (!continuation.isActive) return@verifyCode
                    when {
                        error != null -> continuation.resumeWithException(error)
                        idToken != null -> continuation.resume(idToken)
                        else -> continuation.resumeWithException(
                            IllegalStateException("Bridge returned neither an ID token nor an error"),
                        )
                    }
                }
            }
        }
    }
}

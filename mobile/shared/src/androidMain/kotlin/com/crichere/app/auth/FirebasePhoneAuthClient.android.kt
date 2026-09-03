package com.crichere.app.auth

import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real Android Firebase Phone Auth wiring. This genuinely compiles against
 * `com.google.firebase:firebase-auth`'s real classes (`PhoneAuthOptions`, `PhoneAuthProvider`,
 * `FirebaseAuth`) -- see task-6-report.md for the `:shared:compileDebugKotlinAndroid` run that
 * proves it. What it cannot do in this environment is actually run against a real project: there
 * is no `google-services.json` (see `androidApp/build.gradle.kts`), so `FirebaseAuth.getInstance()`
 * would throw `IllegalStateException("Default FirebaseApp is not initialized")` the first time a
 * real device reached this code. The vertical slice's real-device verification therefore
 * substitutes a fake [PhoneAuthClient] in place of this class rather than exercising this one
 * (see task-6-report.md).
 */
actual class FirebasePhoneAuthClient : PhoneAuthClient {

    actual override suspend fun sendVerificationCode(
        phoneNumber: String,
        resendToken: Any?,
    ): Result<PhoneVerificationHandle> {
        val activity = CurrentActivityTracker.currentActivity()
            ?: return Result.failure(
                IllegalStateException("No foreground activity available to start phone verification"),
            )

        return runCatching {
            suspendCancellableCoroutine { continuation ->
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        // Firebase resolved the code itself (SMS auto-retrieval, or instant
                        // verification when the requesting device owns the number). This
                        // vertical slice's UI is code-entry-first (Phone Entry -> OTP Verify);
                        // wiring this straight into a signed-in credential would need a second
                        // interface shape this task's brief doesn't ask for, so it is surfaced as
                        // a clear, documented failure rather than silently doing nothing.
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException(
                                    "Firebase completed verification automatically; this build requires manual code entry",
                                ),
                            )
                        }
                    }

                    override fun onVerificationFailed(exception: FirebaseException) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }

                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken,
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(PhoneVerificationHandle(verificationId, token))
                        }
                    }
                }

                val optionsBuilder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                    .setPhoneNumber(phoneNumber)
                    .setTimeout(RESEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(callbacks)

                (resendToken as? PhoneAuthProvider.ForceResendingToken)?.let { token ->
                    optionsBuilder.setForceResendingToken(token)
                }

                PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
            }
        }
    }

    actual override suspend fun verifyCode(verificationId: String, code: String): Result<String> = runCatching {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
        authResult.user?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("Firebase sign-in succeeded but returned no ID token")
    }

    private companion object {
        const val RESEND_TIMEOUT_SECONDS = 60L
    }
}

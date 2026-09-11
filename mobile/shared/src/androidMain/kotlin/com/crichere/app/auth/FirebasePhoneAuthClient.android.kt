package com.crichere.app.auth

import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real Android Firebase Phone Auth wiring, backed by `com.google.firebase:firebase-auth`
 * (`PhoneAuthOptions`, `PhoneAuthProvider`, `FirebaseAuth`). Wired unconditionally in
 * `PlatformModule.android.kt` since `google-services.json` was provisioned (2026-09-05) --
 * see docs/PHASE1.md's Open Questions and Gaps. Firebase-console test phone numbers (fixed
 * SMS code, no real SMS sent, no Play Integrity check) are the normal way to exercise this
 * without incurring per-SMS cost; see docs/PHASE1.md for how those are configured.
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

    actual override suspend fun verifyCode(verificationId: String, code: String): Result<String> {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        return try {
            val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
            val idToken = authResult.user?.getIdToken(false)?.await()?.token
                ?: return Result.failure(IllegalStateException("Firebase sign-in succeeded but returned no ID token"))
            Result.success(idToken)
        } catch (wrongCode: FirebaseAuthInvalidCredentialsException) {
            // The real, documented signal Firebase's Android SDK throws specifically for an
            // incorrect SMS code -- translated to the platform-agnostic InvalidOtpCodeException
            // so OtpVerifyViewModel can distinguish "wrong code" (burns an attempt) from every
            // other failure below (network error, expired session, ...) which must not.
            Result.failure(InvalidOtpCodeException())
        } catch (other: Exception) {
            Result.failure(other)
        }
    }

    private companion object {
        const val RESEND_TIMEOUT_SECONDS = 60L
    }
}

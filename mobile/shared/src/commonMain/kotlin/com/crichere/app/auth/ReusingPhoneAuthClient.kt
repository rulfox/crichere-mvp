package com.crichere.app.auth

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Wraps the Android Firebase [PhoneAuthClient] so "Send code" can't hang (docs/PHASE10.md, owner
 * decision 2026-10-02).
 *
 * Firebase's Android SDK fires **no callback at all** for a second `verifyPhoneNumber` on a number
 * whose earlier verification is still inside its timeout window, unless a force-resend token is
 * passed -- so going OTP -> back -> "Send code" again within 60s spun on "Sending code…" forever.
 *
 *  - **Reuse:** a plain send (no [resendToken]) for the number whose code went out less than
 *    [reuseWindow] ago returns that same [PhoneVerificationHandle] without calling Firebase: the
 *    code already sent is still valid, and no extra SMS is billed. The OTP screen's own "Resend"
 *    always passes a token, so it bypasses this and forces a real new SMS.
 *  - **Timeout:** every send is capped at [sendTimeout], so any other silent hang ends in a
 *    retryable error. Long (120s) on purpose: a reCAPTCHA fallback in the browser must have time
 *    to be solved.
 *
 * Android-only (see `PlatformModule.android.kt`): on iOS a "Resend" carries no token, so it would
 * wrongly hit the reuse path.
 */
class ReusingPhoneAuthClient(
    private val delegate: PhoneAuthClient,
    private val reuseWindow: Duration = 60.seconds,
    private val sendTimeout: Duration = 120.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : PhoneAuthClient {

    private class Pending(val phoneNumber: String, val handle: PhoneVerificationHandle, val sentAt: TimeMark)

    private var pending: Pending? = null

    override suspend fun sendVerificationCode(phoneNumber: String, resendToken: Any?): Result<PhoneVerificationHandle> {
        val reusable = pending
        if (resendToken == null && reusable != null && reusable.phoneNumber == phoneNumber &&
            reusable.sentAt.elapsedNow() < reuseWindow
        ) {
            return Result.success(reusable.handle)
        }

        val result = try {
            withTimeout(sendTimeout) { delegate.sendVerificationCode(phoneNumber, resendToken) }
        } catch (timeout: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Couldn't send the code. Please try again."))
        }
        pending = result.getOrNull()?.let { Pending(phoneNumber, it, timeSource.markNow()) }
        return result
    }

    override suspend fun verifyCode(verificationId: String, code: String): Result<String> =
        delegate.verifyCode(verificationId, code).onSuccess {
            // A used verification must never be handed out again (e.g. log out, log back in
            // within the window).
            if (pending?.handle?.verificationId == verificationId) pending = null
        }
}

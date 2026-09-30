package com.crichere.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OtpVerifyState(
    val code: String = "",
    val isVerifying: Boolean = false,
    val isResending: Boolean = false,
    val errorMessage: String? = null,
    val attemptsRemaining: Int = OtpVerifyViewModel.MAX_WRONG_ATTEMPTS,
    val resendsUsed: Int = 0,
    val maxResends: Int = OtpVerifyViewModel.MAX_RESENDS,
    val cooldownSecondsRemaining: Int = OtpVerifyViewModel.RESEND_COOLDOWN_SECONDS,
    val canResend: Boolean = false,
) {
    /**
     * True once the 3rd resend has been used -- no further resend is ever possible again for
     * this verification session, regardless of [canResend]/[cooldownSecondsRemaining] (which
     * both settle to "disabled"/`0` once this is true). The UI must show a real, reachable path
     * back to Phone Entry here ([OtpVerifyViewModel.startOver]) rather than a permanently-disabled
     * resend button with no way forward.
     */
    val resendsExhausted: Boolean get() = resendsUsed >= maxResends
}

/**
 * OTP Verify screen's ViewModel -- the OTP state machine: 60s resend cooldown (with visible
 * countdown), max 3 resends, max 5 wrong-code attempts, per PHASE1.md's exact numbers (non-
 * negotiable, per this task's brief). On a correct code, verifies via [PhoneAuthClient]
 * (through [AuthRepository.verifyOtp]), exchanges the resulting Firebase ID token for a real
 * backend session ([AuthRepository.exchangeSession]), and emits an [AuthNavigationEvent] based on
 * `profileComplete`.
 *
 * Constructed with the [phoneNumber], `verificationId`, and resend token that [PhoneEntryViewModel]
 * obtained -- see `di/AppModule.kt`'s parameterized `factory { }` and `koinViewModel(parameters = ...)`
 * at the Android call site.
 */
class OtpVerifyViewModel(
    private val phoneNumber: String,
    initialVerificationId: String,
    initialResendToken: Any?,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private var verificationId: String = initialVerificationId
    private var resendToken: Any? = initialResendToken
    private var cooldownJob: Job? = null

    private val _state = MutableStateFlow(OtpVerifyState())
    val state: StateFlow<OtpVerifyState> = _state.asStateFlow()

    private val _navigationEvents = Channel<AuthNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<AuthNavigationEvent> = _navigationEvents.receiveAsFlow()

    init {
        startCooldown()
    }

    fun onCodeChanged(code: String) {
        _state.update { it.copy(code = code, errorMessage = null) }
    }

    fun verifyCode() {
        val currentState = _state.value
        if (currentState.isVerifying) return
        if (currentState.code.length != OTP_LENGTH) {
            _state.update { it.copy(errorMessage = "Enter the 6-digit code.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isVerifying = true, errorMessage = null) }

            authRepository.verifyOtp(verificationId, currentState.code)
                .onSuccess { idToken -> completeSignIn(idToken) }
                .onFailure { throwable -> handleVerifyFailure(throwable) }
        }
    }

    /**
     * Distinguishes "the code itself was wrong" ([InvalidOtpCodeException] specifically -- see
     * [PhoneAuthClient.verifyCode]'s contract) from every other `verifyOtp` failure (network
     * error, expired verification session, SDK/config error). Only the former burns one of the
     * 5 wrong-code attempts; the latter surfaces as a distinct, retryable error that leaves
     * [OtpVerifyState.attemptsRemaining] untouched -- a flaky network call must not bounce a user
     * back to Phone Entry the way 5 genuinely wrong digits does.
     */
    private suspend fun handleVerifyFailure(throwable: Throwable) {
        if (throwable is InvalidOtpCodeException) {
            handleWrongCode()
        } else {
            _state.update {
                it.copy(
                    isVerifying = false,
                    errorMessage = throwable.message ?: "Couldn't verify the code right now. Please try again.",
                )
            }
        }
    }

    private suspend fun completeSignIn(idToken: String) {
        val sessionResult = runCatching { authRepository.exchangeSession(idToken) }
        sessionResult
            .onSuccess { authResult ->
                _state.update { it.copy(isVerifying = false, errorMessage = null) }
                _navigationEvents.send(
                    if (authResult.profileComplete) {
                        AuthNavigationEvent.NavigateToOwnProfile
                    } else {
                        AuthNavigationEvent.NavigateToProfileSetup
                    },
                )
            }
            // Never surface the cause: it's a backend status or a local storage/Keystore exception,
            // neither of which the user can act on beyond retrying.
            .onFailure {
                _state.update { it.copy(isVerifying = false, errorMessage = SIGN_IN_FAILED_MESSAGE) }
            }
    }

    private suspend fun handleWrongCode() {
        val attemptsRemaining = (_state.value.attemptsRemaining - 1).coerceAtLeast(0)

        if (attemptsRemaining <= 0) {
            _state.update {
                it.copy(
                    isVerifying = false,
                    attemptsRemaining = 0,
                    errorMessage = "Too many incorrect attempts. Please request a new code.",
                )
            }
            _navigationEvents.send(AuthNavigationEvent.NavigateToPhoneEntry)
        } else {
            _state.update {
                it.copy(
                    isVerifying = false,
                    attemptsRemaining = attemptsRemaining,
                    errorMessage = wrongCodeMessage(attemptsRemaining),
                )
            }
        }
    }

    // The real cause (a Firebase/SDK exception) is intentionally never surfaced here -- a wrong
    // OTP digit isn't a bug the user needs a stack trace for, and it keeps this path consistent
    // with "never display raw SDK/exception text."
    private fun wrongCodeMessage(attemptsRemaining: Int): String {
        val attemptWord = if (attemptsRemaining == 1) "attempt" else "attempts"
        return "Incorrect code. $attemptsRemaining $attemptWord remaining."
    }

    fun resendCode() {
        val currentState = _state.value
        if (currentState.isResending || currentState.isVerifying) return

        // Defense-in-depth: the real UI path once resends are exhausted is `startOver()` (a
        // "Request a new code" affordance that navigates back to Phone Entry -- see
        // OtpVerifyScreen.kt), reached via OtpVerifyState.resendsExhausted, not this method. This
        // guard only protects against `resendCode()` being called directly with stale UI state.
        if (currentState.resendsExhausted) {
            _state.update { it.copy(errorMessage = "No more resends available. Please request a new code.") }
            return
        }
        if (!currentState.canResend) return

        viewModelScope.launch {
            _state.update { it.copy(isResending = true, errorMessage = null) }

            authRepository.sendOtp(phoneNumber, resendToken)
                .onSuccess { handle ->
                    verificationId = handle.verificationId
                    resendToken = handle.resendToken
                    _state.update {
                        it.copy(
                            isResending = false,
                            resendsUsed = it.resendsUsed + 1,
                            code = "",
                            errorMessage = null,
                        )
                    }
                    startCooldown()
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isResending = false,
                            errorMessage = throwable.message ?: "Couldn't resend the code. Please try again.",
                        )
                    }
                }
        }
    }

    /**
     * The user-facing way out once [OtpVerifyState.resendsExhausted] is true: "Request a new
     * code" (or equivalent) on [OtpVerifyScreen] calls this, which -- like the forced 5-wrong-
     * attempts bounce-back -- emits [AuthNavigationEvent.NavigateToPhoneEntry] via the same
     * navigation channel. Unlike the wrong-attempts case this isn't auto-triggered: the brief's
     * "no further resends allowed... user must go back to Phone Entry" only requires that the
     * path be real and reachable, not that the user be yanked away without acting.
     */
    fun startOver() {
        viewModelScope.launch {
            _navigationEvents.send(AuthNavigationEvent.NavigateToPhoneEntry)
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        _state.update { it.copy(cooldownSecondsRemaining = RESEND_COOLDOWN_SECONDS, canResend = false) }
        cooldownJob = viewModelScope.launch {
            var remainingSeconds = RESEND_COOLDOWN_SECONDS
            while (remainingSeconds > 0) {
                delay(1_000)
                remainingSeconds -= 1
                _state.update { it.copy(cooldownSecondsRemaining = remainingSeconds) }
            }
            _state.update { it.copy(canResend = !it.resendsExhausted) }
        }
    }

    override fun onCleared() {
        cooldownJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val OTP_LENGTH = 6
        const val RESEND_COOLDOWN_SECONDS = 60
        const val MAX_RESENDS = 3
        const val MAX_WRONG_ATTEMPTS = 5
        const val SIGN_IN_FAILED_MESSAGE = "Sign-in failed. Please try again."
    }
}

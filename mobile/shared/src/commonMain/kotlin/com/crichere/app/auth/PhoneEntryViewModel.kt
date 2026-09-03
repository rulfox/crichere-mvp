package com.crichere.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhoneEntryState(
    val phoneNumber: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/** One-shot navigation signal -- Task 7's OTP Verify screen is wired to this in this task; see `androidApp`'s nav host. */
sealed interface PhoneEntryNavigationEvent {
    data class NavigateToOtpVerify(
        val phoneNumber: String,
        val verificationId: String,
        val resendToken: Any?,
    ) : PhoneEntryNavigationEvent
}

/**
 * Phone Entry screen's ViewModel: takes a phone number, calls [PhoneAuthClient.sendVerificationCode]
 * (via [AuthRepository.sendOtp]) and, on success, hands the resulting [PhoneVerificationHandle] on
 * to OTP Verify. Failure (invalid number caught client-side, network error, or a real Firebase
 * error) surfaces as [PhoneEntryState.errorMessage] rather than leaving the user stuck with no
 * feedback.
 */
class PhoneEntryViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PhoneEntryState())
    val state: StateFlow<PhoneEntryState> = _state.asStateFlow()

    private val _navigationEvents = Channel<PhoneEntryNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<PhoneEntryNavigationEvent> = _navigationEvents.receiveAsFlow()

    fun onPhoneNumberChanged(value: String) {
        _state.update { it.copy(phoneNumber = value, errorMessage = null) }
    }

    fun requestCode() {
        val currentState = _state.value
        if (currentState.isSubmitting) return

        val phoneNumber = currentState.phoneNumber
        if (!isPlausiblePhoneNumber(phoneNumber)) {
            _state.update {
                it.copy(errorMessage = "Enter a valid phone number with country code, e.g. +919876543210.")
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            authRepository.sendOtp(phoneNumber)
                .onSuccess { handle ->
                    _state.update { it.copy(isSubmitting = false) }
                    _navigationEvents.send(
                        PhoneEntryNavigationEvent.NavigateToOtpVerify(
                            phoneNumber = phoneNumber,
                            verificationId = handle.verificationId,
                            resendToken = handle.resendToken,
                        ),
                    )
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = throwable.message ?: "Couldn't send the code. Please try again.",
                        )
                    }
                }
        }
    }

    /** A cheap, client-side plausibility check (E.164-shaped) so obviously-bad input never reaches the SDK call. */
    private fun isPlausiblePhoneNumber(phoneNumber: String): Boolean {
        if (!phoneNumber.startsWith("+")) return false
        val digits = phoneNumber.drop(1)
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return false
        return phoneNumber.length in MIN_LENGTH..MAX_LENGTH
    }

    private companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 16
    }
}

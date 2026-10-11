package com.crichere.app.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crichere.app.auth.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Delete account screen shows (design D2–D5). */
sealed interface DeleteAccountScreenState {
    /** D2: the eligibility check is running. */
    data object Loading : DeleteAccountScreenState

    /** D3a/D3b: nothing blocks it. [handovers] is empty unless the user organizes leagues that pass to a co-organizer. */
    data class CanDelete(val erasesOn: String, val handovers: List<DeletionHandoverDto>) : DeleteAccountScreenState

    /** D4: at least one league blocks it. No Delete button, only "Check again". */
    data class Blocked(val blockers: List<DeletionBlockerDto>) : DeleteAccountScreenState

    /** D5: the check failed. */
    data object Error : DeleteAccountScreenState
}

/** The typed-DELETE confirm dialog (design D6a–e). `null` in [DeleteAccountState.dialog] means it's closed. */
data class DeleteAccountDialogState(
    val input: String = "",
    val isSubmitting: Boolean = false,
    /** D6e: the request failed; the dialog stays open and Confirm is enabled again. */
    val failed: Boolean = false,
) {
    /** Confirm is enabled only for exactly `DELETE` (case-sensitive, surrounding spaces ignored). */
    val canConfirm: Boolean get() = !isSubmitting && input.trim() == KtorAccountDeletionRepository.CONFIRMATION

    /** D6b: any other non-empty input gets the red border and the "capital letters" helper. */
    val showMismatch: Boolean get() = input.isNotBlank() && input.trim() != KtorAccountDeletionRepository.CONFIRMATION
}

data class DeleteAccountState(
    val screen: DeleteAccountScreenState = DeleteAccountScreenState.Loading,
    val dialog: DeleteAccountDialogState? = null,
)

sealed interface DeleteAccountEvent {
    /** The request was accepted and the local session cleared: go to Sign in with a fresh back stack (D7). */
    data object SignedOut : DeleteAccountEvent
}

/**
 * Delete account (docs/PHASE16.md). The eligibility check runs on every entry ([check]; never in
 * `init`, same as every other ViewModel here). On success the session is cleared locally -- the
 * server already revoked every refresh token -- and [AccountNotices] carries the erase date to
 * Sign in.
 */
class DeleteAccountViewModel(
    private val accountDeletionRepository: AccountDeletionRepository,
    private val authRepository: AuthRepository,
    private val accountNotices: AccountNotices,
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountState())
    val state: StateFlow<DeleteAccountState> = _state.asStateFlow()

    private val _events = Channel<DeleteAccountEvent>(Channel.BUFFERED)
    val events: Flow<DeleteAccountEvent> = _events.receiveAsFlow()

    private var checkJob: Job? = null

    /** D2: on entry, "Check again" (D4) and Retry (D5). */
    fun check() {
        checkJob?.cancel()
        _state.update { DeleteAccountState(screen = DeleteAccountScreenState.Loading) }
        checkJob = viewModelScope.launch {
            val screen = try {
                val status = accountDeletionRepository.status()
                if (status.blockers.isNotEmpty()) {
                    DeleteAccountScreenState.Blocked(status.blockers)
                } else {
                    DeleteAccountScreenState.CanDelete(status.erasesOn, status.handovers)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                DeleteAccountScreenState.Error
            }
            _state.update { it.copy(screen = screen) }
        }
    }

    fun openConfirm() {
        if (_state.value.screen !is DeleteAccountScreenState.CanDelete) return
        _state.update { it.copy(dialog = DeleteAccountDialogState()) }
    }

    /** Ignored while submitting (D6d: the dialog can't be dismissed then). */
    fun dismissConfirm() {
        if (_state.value.dialog?.isSubmitting == true) return
        _state.update { it.copy(dialog = null) }
    }

    fun onInputChange(value: String) {
        val dialog = _state.value.dialog ?: return
        if (dialog.isSubmitting) return
        _state.update { it.copy(dialog = dialog.copy(input = value, failed = false)) }
    }

    fun confirm() {
        val dialog = _state.value.dialog ?: return
        if (!dialog.canConfirm) return
        _state.update { it.copy(dialog = dialog.copy(isSubmitting = true, failed = false)) }
        viewModelScope.launch {
            val result = try {
                accountDeletionRepository.request()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            when (result) {
                null -> _state.update { it.copy(dialog = it.dialog?.copy(isSubmitting = false, failed = true)) }
                is AccountDeletionResult.Blocked ->
                    _state.update { DeleteAccountState(screen = DeleteAccountScreenState.Blocked(result.blockers)) }
                is AccountDeletionResult.Scheduled -> {
                    authRepository.clearLocalSession()
                    accountNotices.deletionScheduled(result.erasesOn)
                    _state.update { DeleteAccountState() }
                    _events.send(DeleteAccountEvent.SignedOut)
                }
            }
        }
    }
}

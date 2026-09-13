package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManageRolesState(
    val isLoading: Boolean = true,
    val league: LeagueDto? = null,
    val phoneNumberInput: String = "",
    val isLookingUp: Boolean = false,
    /** `null` before a lookup, or after one that found nobody -- [lookupAttempted] distinguishes the two so the UI only shows "no user found" once a lookup has actually run. */
    val lookupResult: RoleLookupResultDto? = null,
    val lookupAttempted: Boolean = false,
    val isGranting: Boolean = false,
    val revokingRoleIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
)

/**
 * Manage Co-Organizers screen's ViewModel (see docs/PHASE7.md) -- a distinct sub-flow (phone
 * lookup, then a separate grant confirmation, then a per-row revoke), not folded into
 * [LeagueDetailViewModel], same reasoning [AuctionSettingsViewModel] already documents for why it
 * isn't either. Every mutation reloads from the response it gets back (the updated [LeagueDto],
 * same "the server's return value is the new state" convention every other organizer action in
 * this codebase already follows) rather than a separate re-fetch.
 */
class ManageRolesViewModel(
    private val leagueId: String,
    private val leagueRepository: LeagueRepository,
    private val roleRepository: RoleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageRolesState())
    val state: StateFlow<ManageRolesState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun retry() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            runCatching { leagueRepository.getLeague(leagueId) }
                .onSuccess { league -> _state.update { it.copy(isLoading = false, league = league) } }
                .onFailure { throwable ->
                    _state.update { it.copy(isLoading = false, errorMessage = throwable.message ?: "Couldn't load this league. Please try again.") }
                }
        }
    }

    fun onPhoneNumberChanged(value: String) =
        _state.update { it.copy(phoneNumberInput = value, lookupResult = null, lookupAttempted = false) }

    fun lookup() {
        val phoneNumber = _state.value.phoneNumberInput
        if (phoneNumber.isBlank() || _state.value.isLookingUp) return

        _state.update { it.copy(isLookingUp = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { roleRepository.lookup(leagueId, phoneNumber) }
                .onSuccess { result -> _state.update { it.copy(isLookingUp = false, lookupResult = result, lookupAttempted = true) } }
                .onFailure { throwable ->
                    _state.update { it.copy(isLookingUp = false, errorMessage = throwable.message ?: "That lookup couldn't be completed. Please try again.") }
                }
        }
    }

    fun grant() {
        val targetUserId = _state.value.lookupResult?.userId ?: return
        if (_state.value.isGranting) return

        _state.update { it.copy(isGranting = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { roleRepository.grant(leagueId, targetUserId) }
                .onSuccess { league ->
                    _state.update {
                        it.copy(isGranting = false, league = league, phoneNumberInput = "", lookupResult = null, lookupAttempted = false)
                    }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(isGranting = false, errorMessage = throwable.message ?: "That grant couldn't be completed. Please try again.") }
                }
        }
    }

    fun revoke(roleId: String) {
        if (roleId in _state.value.revokingRoleIds) return

        _state.update { it.copy(revokingRoleIds = it.revokingRoleIds + roleId, errorMessage = null) }
        viewModelScope.launch {
            runCatching { roleRepository.revoke(leagueId, roleId) }
                .onSuccess { league -> _state.update { it.copy(revokingRoleIds = it.revokingRoleIds - roleId, league = league) } }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(revokingRoleIds = it.revokingRoleIds - roleId, errorMessage = throwable.message ?: "That revoke couldn't be completed. Please try again.")
                    }
                }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
    }
}

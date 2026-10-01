package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Design K8's red banner: a bold [title] over a [message]. */
data class RoleNotice(val title: String, val message: String)

data class ManageRolesState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val league: LeagueDto? = null,
    val phoneNumberInput: String = "",
    val isLookingUp: Boolean = false,
    /** The user a lookup found, ready to grant (K2); `null` before a lookup or after one that failed. */
    val lookupResult: RoleLookupResultDto? = null,
    /** Inline under the phone field (K4): nobody found, rate-limited, or the lookup itself failed. */
    val lookupError: String? = null,
    val isGranting: Boolean = false,
    /** K8: under the found card. */
    val grantError: RoleNotice? = null,
    val revokingRoleIds: Set<String> = emptySet(),
    /** Above the co-organizer list when a revoke fails. */
    val revokeError: RoleNotice? = null,
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
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            runCatching { leagueRepository.getLeague(leagueId) }
                .onSuccess { league -> _state.update { it.copy(isLoading = false, league = league) } }
                .onFailure { _state.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }

    fun onPhoneNumberChanged(value: String) =
        _state.update { it.copy(phoneNumberInput = value, lookupResult = null, lookupError = null, grantError = null) }

    fun lookup() {
        val phoneNumber = _state.value.phoneNumberInput.trim()
        if (phoneNumber.isEmpty() || _state.value.isLookingUp) return

        _state.update { it.copy(isLookingUp = true, lookupResult = null, lookupError = null, grantError = null) }
        viewModelScope.launch {
            runCatching { roleRepository.lookup(leagueId, phoneNumber) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(isLookingUp = false, lookupResult = result, lookupError = if (result == null) "No user found with that phone number." else null)
                    }
                }
                .onFailure { throwable -> _state.update { it.copy(isLookingUp = false, lookupError = lookupErrorFor(throwable)) } }
        }
    }

    fun grant() {
        val target = _state.value.lookupResult ?: return
        if (_state.value.isGranting) return

        _state.update { it.copy(isGranting = true, grantError = null) }
        viewModelScope.launch {
            runCatching { roleRepository.grant(leagueId, target.userId) }
                .onSuccess { league ->
                    _state.update { it.copy(isGranting = false, league = league, phoneNumberInput = "", lookupResult = null) }
                }
                .onFailure { throwable -> _state.update { it.copy(isGranting = false, grantError = grantErrorFor(throwable, target.name)) } }
        }
    }

    fun revoke(roleId: String) {
        if (roleId in _state.value.revokingRoleIds) return

        _state.update { it.copy(revokingRoleIds = it.revokingRoleIds + roleId, revokeError = null) }
        viewModelScope.launch {
            runCatching { roleRepository.revoke(leagueId, roleId) }
                .onSuccess { league -> _state.update { it.copy(revokingRoleIds = it.revokingRoleIds - roleId, league = league) } }
                .onFailure { throwable ->
                    if ((throwable as? RoleActionFailedException)?.code == "NOT_FOUND") {
                        // Someone else already revoked it -- the outcome the user wanted; just refresh the list.
                        _state.update { it.copy(revokingRoleIds = it.revokingRoleIds - roleId) }
                        retry()
                    } else {
                        _state.update {
                            it.copy(
                                revokingRoleIds = it.revokingRoleIds - roleId,
                                revokeError = RoleNotice("Couldn't revoke access.", "Check your connection and try again."),
                            )
                        }
                    }
                }
        }
    }

    private fun lookupErrorFor(throwable: Throwable): String = when ((throwable as? RoleActionFailedException)?.code) {
        "RATE_LIMIT_EXCEEDED" -> "Too many lookups. Try again in a few minutes."
        else -> "Couldn't look up that number. Check your connection and try again."
    }

    private fun grantErrorFor(throwable: Throwable, name: String?): RoleNotice = when ((throwable as? RoleActionFailedException)?.code) {
        "ROLE_ALREADY_GRANTED" -> RoleNotice("Already a co-organizer.", "${name ?: "This user"} already has access to this league.")
        "CANNOT_GRANT_ROLE_TO_ORGANIZER" -> RoleNotice("That's the league's organizer.", "They already have full access.")
        else -> RoleNotice("Couldn't grant access.", "Check your connection and try again.")
    }

    override fun onCleared() {
        loadJob?.cancel()
    }
}

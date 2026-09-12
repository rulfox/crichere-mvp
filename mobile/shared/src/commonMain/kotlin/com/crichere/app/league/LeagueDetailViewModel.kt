package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crichere.app.auth.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeagueDetailState(
    val isLoading: Boolean = true,
    val league: LeagueDto? = null,
    val currentUserId: String? = null,
    val isOrganizer: Boolean = false,
    val isCompleting: Boolean = false,
    val isJoining: Boolean = false,
    val isClaiming: Boolean = false,
    val isTogglingFollow: Boolean = false,
    val isLeaveRequesting: Boolean = false,
    /** Row ids (player or franchise) with a Remove in flight -- lets one row's action disable only that row, not the whole screen. */
    val removingIds: Set<String> = emptySet(),
    /** Row ids with an approve/dismiss in flight. */
    val respondingToLeaveRequestIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
)

/**
 * League Detail's ViewModel: loads the full league (ground, awards, players, franchises
 * included) and gates the organizer-only Edit/Mark-completed/Remove/approve-leave actions on
 * comparing the loaded `organizerUserId` against the signed-in user's own id
 * ([AuthRepository.getCurrentUserId]) -- same self-vs-subject shape
 * [com.crichere.app.profile.OwnProfileViewModel] uses, just cross-entity here since the viewer
 * isn't always the subject. "Already joined"/"already claimed" are derived the same way, client-
 * side, from the embedded `players`/`franchises` lists -- see docs/PHASE3.md's implementation
 * plan, decision 3. Edit itself has no dedicated navigation event: `LeagueDetailRoute`
 * re-enters `LeagueCreationScreen` with this league's id directly (see `AuthNavHost.kt`'s
 * `onEditLeague`), the same "edit mode" pattern `ProfileSetupViewModel(isEditMode)` established.
 *
 * Every mutating action here follows [markCompleted]'s original template: guard on current
 * state, set a loading flag, `runCatching { repo call }`, and on success **reload via [load]**
 * rather than hand-patch a single field -- so the refetched `players`/`franchises`/`isFollowing`
 * become the new source of truth. This mirrors the explicit-retry-on-entry convention this
 * ViewModel already established (never an `init` block) -- a real bug from forgetting this
 * exact thing was found and fixed on-device during Phase 2 QA (commit `ae56a1a`).
 */
class LeagueDetailViewModel(
    private val leagueId: String,
    private val leagueRepository: LeagueRepository,
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository,
    private val franchiseRepository: FranchiseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LeagueDetailState())
    val state: StateFlow<LeagueDetailState> = _state.asStateFlow()

    // Deliberately not calling load() here -- Koin's koinViewModel(key = "league-detail:$leagueId")
    // returns this same instance every time this league's detail screen is re-entered with the
    // same id (e.g. returning from Edit having just changed its awards), so a one-time init load
    // would keep showing what was true the first time this screen was visited. LeagueDetailRoute
    // calls retry() itself on every entry instead -- see that composable.

    // Since this ViewModel instance outlives any single visit to this screen (see above), a visit
    // under one signed-in user followed by another visit under a different one (log out, sign in
    // as someone else, come back) can leave two `load()` coroutines in flight at once, both still
    // running in this same `viewModelScope`. Without cancelling the older one, its response can
    // arrive *after* the newer visit's and silently overwrite the correct, fresh `isOrganizer` with
    // stale data -- the organizer-only actions this state gates then look tappable but quietly do
    // nothing (their own guards read `_state.value.isOrganizer`, now wrong). Real bug found this
    // way during on-device Phase 3/4 QA, reproduced by switching accounts without restarting the
    // app; a full process restart "fixed" it only because it also dropped the stale coroutine.
    private var loadJob: Job? = null

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            runCatching {
                val league = leagueRepository.getLeague(leagueId)
                val currentUserId = authRepository.getCurrentUserId()
                Triple(league, currentUserId, currentUserId != null && currentUserId == league.organizerUserId)
            }.onSuccess { (league, currentUserId, isOrganizer) ->
                _state.update {
                    it.copy(isLoading = false, league = league, currentUserId = currentUserId, isOrganizer = isOrganizer)
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(isLoading = false, errorMessage = throwable.message ?: "Couldn't load this league. Please try again.")
                }
            }
        }
    }

    fun markCompleted() {
        val league = _state.value.league ?: return
        if (!_state.value.isOrganizer || _state.value.isCompleting) return

        _state.update { it.copy(isCompleting = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { leagueRepository.completeLeague(league.id) }
                .onSuccess { updated -> _state.update { it.copy(isCompleting = false, league = updated) } }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isCompleting = false, errorMessage = throwable.message ?: "Couldn't mark this league completed. Please try again.")
                    }
                }
        }
    }

    fun toggleFollow() {
        val league = _state.value.league ?: return
        if (_state.value.isTogglingFollow) return

        _state.update { it.copy(isTogglingFollow = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                if (league.isFollowing) leagueRepository.unfollow(league.id) else leagueRepository.follow(league.id)
            }
                .onSuccess { _state.update { it.copy(isTogglingFollow = false) }; load() }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isTogglingFollow = false, errorMessage = throwable.message ?: "Couldn't update follow status. Please try again.")
                    }
                }
        }
    }

    fun removePlayer(playerId: String) = runOrganizerRowAction(playerId, removing = true) {
        playerRepository.remove(leagueId, playerId)
    }

    fun removeFranchise(franchiseId: String) = runOrganizerRowAction(franchiseId, removing = true) {
        franchiseRepository.remove(leagueId, franchiseId)
    }

    fun approvePlayerLeave(playerId: String) = runOrganizerRowAction(playerId, removing = false) {
        playerRepository.approveLeave(leagueId, playerId)
    }

    fun dismissPlayerLeave(playerId: String) = runOrganizerRowAction(playerId, removing = false) {
        playerRepository.dismissLeave(leagueId, playerId)
    }

    fun approveFranchiseLeave(franchiseId: String) = runOrganizerRowAction(franchiseId, removing = false) {
        franchiseRepository.approveLeave(leagueId, franchiseId)
    }

    fun dismissFranchiseLeave(franchiseId: String) = runOrganizerRowAction(franchiseId, removing = false) {
        franchiseRepository.dismissLeave(leagueId, franchiseId)
    }

    fun requestLeaveAsPlayer(playerId: String) {
        if (_state.value.isLeaveRequesting) return
        _state.update { it.copy(isLeaveRequesting = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { playerRepository.requestLeave(leagueId, playerId) }
                .onSuccess { _state.update { it.copy(isLeaveRequesting = false) }; load() }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLeaveRequesting = false, errorMessage = throwable.message ?: "Couldn't request to leave. Please try again.")
                    }
                }
        }
    }

    fun requestLeaveAsFranchise(franchiseId: String) {
        if (_state.value.isLeaveRequesting) return
        _state.update { it.copy(isLeaveRequesting = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { franchiseRepository.requestLeave(leagueId, franchiseId) }
                .onSuccess { _state.update { it.copy(isLeaveRequesting = false) }; load() }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLeaveRequesting = false, errorMessage = throwable.message ?: "Couldn't request to leave. Please try again.")
                    }
                }
        }
    }

    /** Shared template for organizer-only per-row actions (Remove, approve/dismiss leave) -- [removing] only controls which loading-id set the row belongs to. */
    private fun runOrganizerRowAction(rowId: String, removing: Boolean, action: suspend () -> Unit) {
        if (!_state.value.isOrganizer) return
        val alreadyInFlight = if (removing) rowId in _state.value.removingIds else rowId in _state.value.respondingToLeaveRequestIds
        if (alreadyInFlight) return

        _state.update {
            if (removing) it.copy(removingIds = it.removingIds + rowId, errorMessage = null)
            else it.copy(respondingToLeaveRequestIds = it.respondingToLeaveRequestIds + rowId, errorMessage = null)
        }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess {
                    _state.update {
                        if (removing) it.copy(removingIds = it.removingIds - rowId)
                        else it.copy(respondingToLeaveRequestIds = it.respondingToLeaveRequestIds - rowId)
                    }
                    load()
                }
                .onFailure { throwable ->
                    _state.update {
                        val cleared = if (removing) it.copy(removingIds = it.removingIds - rowId) else it.copy(respondingToLeaveRequestIds = it.respondingToLeaveRequestIds - rowId)
                        cleared.copy(errorMessage = throwable.message ?: "That action couldn't be completed. Please try again.")
                    }
                }
        }
    }
}

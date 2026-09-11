package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crichere.app.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeagueDetailState(
    val isLoading: Boolean = true,
    val league: LeagueDto? = null,
    val isOrganizer: Boolean = false,
    val isCompleting: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * League Detail's ViewModel: loads the full league (ground, awards included) and gates the
 * organizer-only Edit/Mark-completed actions on comparing the loaded `organizerUserId` against
 * the signed-in user's own id ([AuthRepository.getCurrentUserId]) -- same self-vs-subject shape
 * [com.crichere.app.profile.OwnProfileViewModel] uses, just cross-entity here since the viewer
 * isn't always the subject. Edit itself has no dedicated navigation event: `LeagueDetailRoute`
 * re-enters `LeagueCreationScreen` with this league's id directly (see `AuthNavHost.kt`'s
 * `onEditLeague`), the same "edit mode" pattern `ProfileSetupViewModel(isEditMode)` established.
 */
class LeagueDetailViewModel(
    private val leagueId: String,
    private val leagueRepository: LeagueRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LeagueDetailState())
    val state: StateFlow<LeagueDetailState> = _state.asStateFlow()

    // Deliberately not calling load() here -- Koin's koinViewModel(key = "league-detail:$leagueId")
    // returns this same instance every time this league's detail screen is re-entered with the
    // same id (e.g. returning from Edit having just changed its awards), so a one-time init load
    // would keep showing what was true the first time this screen was visited. LeagueDetailRoute
    // calls retry() itself on every entry instead -- see that composable.

    fun retry() = load()

    private fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val league = leagueRepository.getLeague(leagueId)
                val currentUserId = authRepository.getCurrentUserId()
                league to (currentUserId != null && currentUserId == league.organizerUserId)
            }.onSuccess { (league, isOrganizer) ->
                _state.update { it.copy(isLoading = false, league = league, isOrganizer = isOrganizer) }
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
}

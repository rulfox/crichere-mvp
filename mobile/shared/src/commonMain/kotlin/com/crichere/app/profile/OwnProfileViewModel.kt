package com.crichere.app.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crichere.app.auth.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OwnProfileState(
    val isLoading: Boolean = true,
    val name: String? = null,
    val photoUrl: String? = null,
    val country: String? = null,
    val state: String? = null,
    val district: String? = null,
    val city: String? = null,
    val playingRole: PlayingRole? = null,
    val battingStyle: BattingStyle? = null,
    val bowlingStyle: BowlingStyle? = null,
    val errorMessage: String? = null,
)

sealed interface OwnProfileNavigationEvent {
    /** "Edit" action -- reached with [com.crichere.app.profile.ProfileSetupViewModel]'s `isEditMode = true`. */
    data object NavigateToEditProfile : OwnProfileNavigationEvent

    /** Logout action, after [AuthRepository.logout] has run. */
    data object NavigateToPhoneEntry : OwnProfileNavigationEvent
}

/**
 * Own Profile View's ViewModel: loads and displays the signed-in user's complete profile, and
 * exposes the two actions this screen offers -- edit (re-enters Profile Setup in edit mode) and
 * logout (real [AuthRepository.logout], then back to Phone Entry).
 */
class OwnProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OwnProfileState())
    val state: StateFlow<OwnProfileState> = _state.asStateFlow()

    private val _navigationEvents = Channel<OwnProfileNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<OwnProfileNavigationEvent> = _navigationEvents.receiveAsFlow()

    // Deliberately not loading in an `init` block -- Koin's koinViewModel() (no key, no
    // parameters) returns this same instance for the whole process lifetime, same caveat as
    // ProfileSetupViewModel/LeagueDetailViewModel. Reproduced on-device: view this tab as user A,
    // log out, sign in as user B, and this tab would keep showing user A's profile instead of
    // refetching. OwnProfileRoute calls retry() itself on every entry instead -- see that
    // composable.
    private var loadJob: Job? = null

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        _state.update { OwnProfileState(isLoading = true) }
        loadJob = viewModelScope.launch {
            runCatching { profileRepository.getProfile() }
                .onSuccess { profile ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            name = profile.name,
                            photoUrl = profile.photoUrl,
                            country = profile.country,
                            state = profile.state,
                            district = profile.district,
                            city = profile.city,
                            playingRole = profile.playingRole,
                            battingStyle = profile.battingStyle,
                            bowlingStyle = profile.bowlingStyle,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Couldn't load your profile. Please try again.",
                        )
                    }
                }
        }
    }

    fun editProfile() {
        viewModelScope.launch {
            _navigationEvents.send(OwnProfileNavigationEvent.NavigateToEditProfile)
        }
    }

    fun logout() {
        viewModelScope.launch {
            // AuthRepository.logout() clears SecureStore regardless of whether the backend
            // revoke call itself succeeds -- see its own doc.
            authRepository.logout()
            _navigationEvents.send(OwnProfileNavigationEvent.NavigateToPhoneEntry)
        }
    }
}

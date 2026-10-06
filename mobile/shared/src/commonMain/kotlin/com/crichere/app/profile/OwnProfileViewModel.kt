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
    val playingRole: PlayingRole? = null,
    val battingStyle: BattingStyle? = null,
    val bowlingStyle: BowlingStyle? = null,
    /** First load failed with nothing to show (design N5). A failed refresh keeps the last profile. */
    val loadFailed: Boolean = false,
    /** Design N2 "Choose new photo": upload, then the profile save that points at it. */
    val isUploadingPhoto: Boolean = false,
    val photoUploadProgress: Float = 0f,
    val photoError: String? = null,
) {
    val hasProfile: Boolean get() = name != null
}

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

    /** The profile as last loaded / saved -- the base for the full-snapshot save a photo change needs. */
    private var loaded: ProfileDto? = null

    private fun load() {
        loadJob?.cancel()
        // Keep showing the last profile while refreshing (same user -- logout clears it, see logout()).
        _state.update { if (loaded != null) it.copy(isLoading = true, loadFailed = false) else OwnProfileState(isLoading = true) }
        loadJob = viewModelScope.launch {
            runCatching { profileRepository.getProfile() }
                .onSuccess { profile -> show(profile) }
                .onFailure { _state.update { it.copy(isLoading = false, loadFailed = loaded == null) } }
        }
    }

    private fun show(profile: ProfileDto) {
        loaded = profile
        _state.update {
            it.copy(
                isLoading = false,
                loadFailed = false,
                name = profile.name,
                photoUrl = profile.photoUrl,
                country = profile.country,
                state = profile.state,
                district = profile.district,
                playingRole = profile.playingRole,
                battingStyle = profile.battingStyle,
                bowlingStyle = profile.bowlingStyle,
            )
        }
    }

    /**
     * Uploads [bytes] as the new profile photo, then saves the full profile pointing at it (the
     * `PUT` takes a complete snapshot, see [ProfileRepository.saveProfile]). The old photo stays
     * on screen until both succeed.
     */
    fun changePhoto(bytes: ByteArray, contentType: String) {
        val base = loaded ?: return
        if (_state.value.isUploadingPhoto) return
        _state.update { it.copy(isUploadingPhoto = true, photoUploadProgress = 0f, photoError = null) }
        viewModelScope.launch {
            runCatching {
                val uploadInfo = profileRepository.requestPhotoUploadUrl()
                val photoUrl = profileRepository.uploadPhoto(uploadInfo, bytes, contentType) { fraction ->
                    _state.update { it.copy(photoUploadProgress = fraction) }
                }
                profileRepository.saveProfile(
                    ProfileUpdateRequestDto(
                        name = base.name,
                        photoUrl = photoUrl,
                        state = base.state,
                        district = base.district,
                        playingRole = base.playingRole,
                        battingStyle = base.battingStyle,
                        bowlingStyle = base.bowlingStyle,
                    ),
                )
            }
                .onSuccess { saved ->
                    _state.update { it.copy(isUploadingPhoto = false, photoUploadProgress = 1f) }
                    show(saved)
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isUploadingPhoto = false,
                            photoError = if (throwable is PhotoUploadUnavailableException) "Photo upload isn't available right now. Try again later."
                            else "Couldn't change your photo. Check your connection and try again.",
                        )
                    }
                }
        }
    }

    fun dismissPhotoError() = _state.update { it.copy(photoError = null) }

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
            loaded = null
            _state.update { OwnProfileState() }
            _navigationEvents.send(OwnProfileNavigationEvent.NavigateToPhoneEntry)
        }
    }
}

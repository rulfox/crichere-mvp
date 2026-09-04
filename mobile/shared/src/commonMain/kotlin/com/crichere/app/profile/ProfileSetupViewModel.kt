package com.crichere.app.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crichere.app.location.LocationProvider
import com.crichere.app.reference.CityDto
import com.crichere.app.reference.ReferenceRepository
import com.crichere.app.reference.StateDto
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The exact resumability field order this task's brief locks down (non-negotiable): name -> photo
 * -> state -> city -> role -> batting -> bowling-if-applicable. Also doubles as the set of fields
 * [ProfileSetupState.isSaveEnabled] checks.
 */
enum class ProfileField {
    NAME, PHOTO, STATE, CITY, ROLE, BATTING, BOWLING
}

data class ProfileSetupState(
    val isLoading: Boolean = true,
    val name: String = "",
    val photoUrl: String? = null,
    val state: String? = null,
    val city: String? = null,
    val playingRole: PlayingRole? = null,
    val battingStyle: BattingStyle? = null,
    val bowlingStyle: BowlingStyle? = null,
    val states: List<StateDto> = emptyList(),
    val cities: List<CityDto> = emptyList(),
    val isUploadingPhoto: Boolean = false,
    val isLocating: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val photoUploadErrorMessage: String? = null,
    /**
     * The field the screen should focus first: computed once, on load, from resumability
     * ([ProfileSetupViewModel]'s `isEditMode`/first-missing-field logic) -- purely advisory for
     * the UI (e.g. auto-scroll), never re-computed as the user edits the form.
     */
    val initialFocusField: ProfileField = ProfileField.NAME,
) {
    /** Whether bowling style applies to the currently-selected role -- mirrors the backend's rule. */
    val isBowlingStyleApplicable: Boolean
        get() = playingRole == PlayingRole.BOWLER || playingRole == PlayingRole.ALL_ROUNDER

    /**
     * Mirrors [com.crichere.backend.profile.ProfileCompletionService]'s "is this profile
     * complete?" rule client-side, for the save button -- the server remains the actual
     * enforcement point (see this task's brief), this is UX-only.
     */
    val isSaveEnabled: Boolean
        get() {
            if (isSaving) return false
            if (name.isBlank()) return false
            if (photoUrl.isNullOrBlank()) return false
            if (state.isNullOrBlank()) return false
            if (city.isNullOrBlank()) return false
            if (playingRole == null) return false
            if (battingStyle == null) return false
            if (isBowlingStyleApplicable && bowlingStyle == null) return false
            // Mirrors the backend's other direction of the same rule (BowlingStyleNotAllowedException):
            // unreachable via this ViewModel's own actions (onRoleSelected always clears
            // bowlingStyle the moment it stops applying), kept here so the state's own invariant
            // holds regardless of how it was constructed.
            if (!isBowlingStyleApplicable && bowlingStyle != null) return false
            return true
        }
}

sealed interface ProfileSetupNavigationEvent {
    /** Fired once a save leaves the profile fully complete -- this task owns both ends of this signal. */
    data object NavigateToOwnProfile : ProfileSetupNavigationEvent
}

/**
 * Profile Setup screen's ViewModel: resumable onboarding (pre-filled from `GET /profiles/me`,
 * first-missing-field initial focus) plus the standalone "edit" entry point from Own Profile View.
 *
 * @param isEditMode `true` when reached via "edit" from a complete profile -- skips the
 *   first-missing-field calculation (there's nothing missing to find) and starts focus at the
 *   top ([ProfileField.NAME]) instead. `false` for the real onboarding path reached from
 *   [com.crichere.app.auth.AuthNavigationEvent.NavigateToProfileSetup]. Same parameterized-
 *   ViewModel-via-Koin-factory pattern `OtpVerifyViewModel` already uses.
 */
class ProfileSetupViewModel(
    private val isEditMode: Boolean,
    private val profileRepository: ProfileRepository,
    private val referenceRepository: ReferenceRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileSetupState())
    val state: StateFlow<ProfileSetupState> = _state.asStateFlow()

    private val _navigationEvents = Channel<ProfileSetupNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<ProfileSetupNavigationEvent> = _navigationEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val profile = runCatching { profileRepository.getProfile() }.getOrNull()
            val initialFocus = if (isEditMode || profile == null) {
                ProfileField.NAME
            } else {
                firstMissingField(profile) ?: ProfileField.NAME
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    name = profile?.name.orEmpty(),
                    photoUrl = profile?.photoUrl,
                    state = profile?.state,
                    city = profile?.city,
                    playingRole = profile?.playingRole,
                    battingStyle = profile?.battingStyle,
                    bowlingStyle = profile?.bowlingStyle,
                    initialFocusField = initialFocus,
                )
            }
            loadStatesAndPreselectedCities()
        }
    }

    fun onNameChanged(value: String) {
        _state.update { it.copy(name = value, errorMessage = null) }
    }

    /** [stateDto] comes from [ProfileSetupState.states] -- selecting a state clears the city and re-fetches its cities. */
    fun onStateSelected(stateDto: StateDto) {
        _state.update { it.copy(state = stateDto.name, city = null, cities = emptyList(), errorMessage = null) }
        viewModelScope.launch { loadCities(stateDto.code) }
    }

    fun onCitySelected(cityDto: CityDto) {
        _state.update { it.copy(city = cityDto.name, errorMessage = null) }
    }

    fun onRoleSelected(role: PlayingRole) {
        _state.update { current ->
            val stillNeedsBowlingStyle = role == PlayingRole.BOWLER || role == PlayingRole.ALL_ROUNDER
            current.copy(
                playingRole = role,
                // Clear any previously-selected bowling style the moment the role no longer needs one.
                bowlingStyle = if (stillNeedsBowlingStyle) current.bowlingStyle else null,
                errorMessage = null,
            )
        }
    }

    fun onBattingStyleSelected(style: BattingStyle) {
        _state.update { it.copy(battingStyle = style, errorMessage = null) }
    }

    fun onBowlingStyleSelected(style: BowlingStyle) {
        _state.update { it.copy(bowlingStyle = style, errorMessage = null) }
    }

    /**
     * Photo picker -> raw bytes -> presigned URL -> S3 upload -> local form state. A `503`
     * ([PhotoUploadUnavailableException], the real response this environment's unconfigured S3
     * setup returns) surfaces as a clear, non-crashing [ProfileSetupState.photoUploadErrorMessage]
     * rather than a silent failure, per this task's environment note.
     */
    fun uploadPhoto(bytes: ByteArray, contentType: String) {
        if (_state.value.isUploadingPhoto) return
        viewModelScope.launch {
            _state.update { it.copy(isUploadingPhoto = true, photoUploadErrorMessage = null) }
            try {
                val uploadInfo = profileRepository.requestPhotoUploadUrl()
                val photoUrl = profileRepository.uploadPhoto(uploadInfo, bytes, contentType)
                _state.update { it.copy(isUploadingPhoto = false, photoUrl = photoUrl) }
            } catch (unavailable: PhotoUploadUnavailableException) {
                _state.update { it.copy(isUploadingPhoto = false, photoUploadErrorMessage = unavailable.message) }
            } catch (other: Exception) {
                _state.update {
                    it.copy(
                        isUploadingPhoto = false,
                        photoUploadErrorMessage = other.message ?: "Couldn't upload photo. Please try again.",
                    )
                }
            }
        }
    }

    /**
     * User-initiated "use my location" -- never auto-triggered on screen load (that would fire an
     * unprompted permission dialog). Best-effort per this task's ruling: any failure at any step
     * (permission denied, no fix, geocoder miss, no matching seeded state/city) is a silent no-op,
     * never a blocking error -- the selectors remain hand-editable regardless.
     */
    fun useMyLocation() {
        if (_state.value.isLocating) return
        viewModelScope.launch {
            _state.update { it.copy(isLocating = true, errorMessage = null) }

            val point = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
            val geocoded = point?.let { runCatching { locationProvider.reverseGeocode(it) }.getOrNull() }

            val states = _state.value.states
            val matchedState = geocoded?.administrativeArea?.let { area ->
                states.firstOrNull { it.name.equals(area, ignoreCase = true) }
            }

            if (matchedState == null) {
                _state.update { it.copy(isLocating = false) }
                return@launch
            }

            val cities = runCatching { referenceRepository.getCitiesForState(matchedState.code) }.getOrDefault(emptyList())
            val matchedCity = geocoded.locality?.let { locality ->
                cities.firstOrNull { it.name.equals(locality, ignoreCase = true) }
            }

            _state.update {
                it.copy(
                    isLocating = false,
                    state = matchedState.name,
                    city = matchedCity?.name,
                    cities = cities,
                )
            }
        }
    }

    fun save() {
        val current = _state.value
        if (!current.isSaveEnabled) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val snapshot = ProfileUpdateRequestDto(
                name = current.name,
                photoUrl = current.photoUrl,
                state = current.state,
                city = current.city,
                playingRole = current.playingRole,
                battingStyle = current.battingStyle,
                bowlingStyle = if (current.isBowlingStyleApplicable) current.bowlingStyle else null,
            )
            runCatching { profileRepository.saveProfile(snapshot) }
                .onSuccess { result ->
                    _state.update { it.copy(isSaving = false) }
                    if (result.profileComplete) {
                        _navigationEvents.send(ProfileSetupNavigationEvent.NavigateToOwnProfile)
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = throwable.message ?: "Couldn't save your profile. Please try again.",
                        )
                    }
                }
        }
    }

    private suspend fun loadStatesAndPreselectedCities() {
        val states = runCatching { referenceRepository.getStates() }.getOrDefault(emptyList())
        _state.update { it.copy(states = states) }

        // If the loaded profile already has a state saved (resumed onboarding, or edit mode),
        // pre-load that state's cities too, so the city selector isn't empty on redisplay.
        val savedStateName = _state.value.state ?: return
        val matchedState = states.firstOrNull { it.name.equals(savedStateName, ignoreCase = true) } ?: return
        loadCities(matchedState.code)
    }

    private suspend fun loadCities(stateCode: String) {
        val cities = runCatching { referenceRepository.getCitiesForState(stateCode) }.getOrDefault(emptyList())
        _state.update { it.copy(cities = cities) }
    }

    /**
     * The resumability field order, exact: name -> photo -> state -> city -> role -> batting ->
     * bowling-if-applicable. `null` means every field this profile needs is already filled.
     */
    private fun firstMissingField(profile: ProfileDto): ProfileField? {
        if (profile.name.isNullOrBlank()) return ProfileField.NAME
        if (profile.photoUrl.isNullOrBlank()) return ProfileField.PHOTO
        if (profile.state.isNullOrBlank()) return ProfileField.STATE
        if (profile.city.isNullOrBlank()) return ProfileField.CITY
        val role = profile.playingRole ?: return ProfileField.ROLE
        if (profile.battingStyle == null) return ProfileField.BATTING
        val requiresBowlingStyle = role == PlayingRole.BOWLER || role == PlayingRole.ALL_ROUNDER
        if (requiresBowlingStyle && profile.bowlingStyle == null) return ProfileField.BOWLING
        return null
    }
}

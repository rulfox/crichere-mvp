package com.crichere.app.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crichere.app.location.LocationProvider
import com.crichere.app.reference.CityDto
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.ReferenceRepository
import com.crichere.app.reference.StateDto
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

/**
 * The exact resumability field order this task's brief locks down (non-negotiable): name -> photo
 * -> state -> district -> city -> role -> batting -> bowling-if-applicable (District retrofit
 * inserted between state and city). Also doubles as the set of fields
 * [ProfileSetupState.isSaveEnabled] checks.
 */
enum class ProfileField {
    NAME, PHOTO, STATE, DISTRICT, CITY, ROLE, BATTING, BOWLING
}

data class ProfileSetupState(
    val isLoading: Boolean = true,
    val name: String = "",
    val photoUrl: String? = null,
    val state: String? = null,
    val district: String? = null,
    val city: String? = null,
    val playingRole: PlayingRole? = null,
    val battingStyle: BattingStyle? = null,
    val bowlingStyle: BowlingStyle? = null,
    val states: List<StateDto> = emptyList(),
    val districts: List<DistrictDto> = emptyList(),
    val cities: List<CityDto> = emptyList(),
    val isUploadingPhoto: Boolean = false,
    /** Fraction of the photo body sent, 0..1 -- only meaningful while [isUploadingPhoto]. */
    val photoUploadProgress: Float = 0f,
    /** Name/size of the photo being uploaded (or that just failed), for the upload row's caption. */
    val uploadingPhotoName: String? = null,
    val uploadingPhotoSizeBytes: Long? = null,
    val isLocating: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    /** Under the name field after Save with fewer than 2 letters (design update #4, C11); cleared on the next keystroke. */
    val nameError: String? = null,
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
            if (district.isNullOrBlank()) return false
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

    // Deliberately not loading in an `init` block -- Koin's koinViewModel(key = "profile-setup:
    // $isEditMode") returns this same instance every time this screen is entered with the same
    // isEditMode value, for the life of the process (see LeagueDetailViewModel's doc for the same
    // caveat). A one-time init load would keep showing whichever user's profile was loaded the
    // first time this screen was ever reached -- reproduced on-device: sign in as user A, load
    // this screen, log out, sign in as a brand-new user B (no profile row at all), and this screen
    // would show user A's name/location/photo instead of a blank form. ProfileSetupRoute calls
    // retry() itself on every entry instead -- see that composable, same convention
    // LeagueDetailViewModel/AuctionSettingsViewModel already use.
    private var loadJob: Job? = null
    private var uploadJob: Job? = null
    private var lastPhoto: PendingPhoto? = null

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        _state.update { ProfileSetupState(isLoading = true) }
        loadJob = viewModelScope.launch {
            val profile = runCatching { profileRepository.getProfile() }.getOrNull()
            val initialFocus = if (isEditMode || profile == null) {
                ProfileField.NAME
            } else {
                firstMissingField(profile) ?: ProfileField.NAME
            }
            _state.update {
                ProfileSetupState(
                    isLoading = false,
                    name = profile?.name.orEmpty(),
                    photoUrl = profile?.photoUrl,
                    state = profile?.state,
                    district = profile?.district,
                    city = profile?.city,
                    playingRole = profile?.playingRole,
                    battingStyle = profile?.battingStyle,
                    bowlingStyle = profile?.bowlingStyle,
                    initialFocusField = initialFocus,
                )
            }
            loadStatesAndPreselectedDistrictsAndCities()
        }
    }

    fun onNameChanged(value: String) {
        _state.update { it.copy(name = value, errorMessage = null, nameError = null) }
    }

    /** [stateDto] comes from [ProfileSetupState.states] -- selecting a state clears the district/city and re-fetches its districts. */
    fun onStateSelected(stateDto: StateDto) {
        _state.update {
            it.copy(
                state = stateDto.name,
                district = null,
                districts = emptyList(),
                city = null,
                cities = emptyList(),
                errorMessage = null,
            )
        }
        viewModelScope.launch { loadDistricts(stateDto.code) }
    }

    /** [districtDto] comes from [ProfileSetupState.districts] -- selecting a district clears the city and re-fetches its cities. */
    fun onDistrictSelected(districtDto: DistrictDto) {
        _state.update { it.copy(district = districtDto.name, city = null, cities = emptyList(), errorMessage = null) }
        viewModelScope.launch { loadCities(districtDto.id) }
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
    fun uploadPhoto(bytes: ByteArray, contentType: String) = uploadPhoto(bytes, contentType, fileName = null)

    fun uploadPhoto(bytes: ByteArray, contentType: String, fileName: String?) {
        if (_state.value.isUploadingPhoto) return
        lastPhoto = PendingPhoto(bytes, contentType, fileName)
        uploadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isUploadingPhoto = true,
                    photoUploadProgress = 0f,
                    uploadingPhotoName = fileName,
                    uploadingPhotoSizeBytes = bytes.size.toLong(),
                    photoUploadErrorMessage = null,
                )
            }
            try {
                val uploadInfo = profileRepository.requestPhotoUploadUrl()
                val photoUrl = profileRepository.uploadPhoto(uploadInfo, bytes, contentType) { fraction ->
                    _state.update { it.copy(photoUploadProgress = fraction) }
                }
                lastPhoto = null
                _state.update { it.copy(isUploadingPhoto = false, photoUploadProgress = 1f, photoUrl = photoUrl) }
            } catch (cancelled: CancellationException) {
                throw cancelled
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

    /** Re-uploads the photo whose upload last failed; no-op if there's nothing to retry. */
    fun retryPhotoUpload() {
        val photo = lastPhoto ?: return
        uploadPhoto(photo.bytes, photo.contentType, photo.fileName)
    }

    /** Abandons an in-flight upload; any previously uploaded [ProfileSetupState.photoUrl] is kept. */
    fun cancelPhotoUpload() {
        uploadJob?.cancel()
        uploadJob = null
        lastPhoto = null
        _state.update {
            it.copy(
                isUploadingPhoto = false,
                photoUploadProgress = 0f,
                uploadingPhotoName = null,
                uploadingPhotoSizeBytes = null,
                photoUploadErrorMessage = null,
            )
        }
    }

    private class PendingPhoto(val bytes: ByteArray, val contentType: String, val fileName: String?)

    /**
     * User-initiated "use my location" -- never auto-triggered on screen load (that would fire an
     * unprompted permission dialog). Best-effort per this task's ruling: any failure at any step
     * (permission denied, no fix, geocoder miss, no matching seeded state/district/city) is a
     * silent no-op, never a blocking error -- the selectors remain hand-editable regardless.
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

            val districts = runCatching { referenceRepository.getDistrictsForState(matchedState.code) }.getOrDefault(emptyList())
            val matchedDistrict = geocoded.subAdministrativeArea?.let { area ->
                districts.firstOrNull { it.name.equals(area, ignoreCase = true) }
            }

            if (matchedDistrict == null) {
                _state.update { it.copy(isLocating = false, state = matchedState.name, districts = districts) }
                return@launch
            }

            val cities = runCatching { referenceRepository.getCitiesForDistrict(matchedDistrict.id) }.getOrDefault(emptyList())
            val matchedCity = geocoded.locality?.let { locality ->
                cities.firstOrNull { it.name.equals(locality, ignoreCase = true) }
            }

            _state.update {
                it.copy(
                    isLocating = false,
                    state = matchedState.name,
                    districts = districts,
                    district = matchedDistrict.name,
                    city = matchedCity?.name,
                    cities = cities,
                )
            }
        }
    }

    fun save() {
        val current = _state.value
        if (!current.isSaveEnabled) return
        if (current.name.trim().length < MIN_NAME_LENGTH) {
            _state.update { it.copy(nameError = NAME_TOO_SHORT_MESSAGE) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val snapshot = ProfileUpdateRequestDto(
                name = current.name,
                photoUrl = current.photoUrl,
                state = current.state,
                district = current.district,
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
                // The cause is a backend status or transport error -- nothing the user can act on beyond retrying.
                .onFailure {
                    _state.update { it.copy(isSaving = false, errorMessage = SAVE_FAILED_MESSAGE) }
                }
        }
    }

    private suspend fun loadStatesAndPreselectedDistrictsAndCities() {
        val states = runCatching { referenceRepository.getStates() }.getOrDefault(emptyList())
        _state.update { it.copy(states = states) }

        // If the loaded profile already has a state saved (resumed onboarding, or edit mode),
        // pre-load that state's districts too, so the district selector isn't empty on redisplay.
        val savedStateName = _state.value.state ?: return
        val matchedState = states.firstOrNull { it.name.equals(savedStateName, ignoreCase = true) } ?: return
        loadDistricts(matchedState.code)

        // Same for a saved district's cities.
        val savedDistrictName = _state.value.district ?: return
        val matchedDistrict = _state.value.districts.firstOrNull { it.name.equals(savedDistrictName, ignoreCase = true) } ?: return
        loadCities(matchedDistrict.id)
    }

    private suspend fun loadDistricts(stateCode: String) {
        val districts = runCatching { referenceRepository.getDistrictsForState(stateCode) }.getOrDefault(emptyList())
        _state.update { it.copy(districts = districts) }
    }

    private suspend fun loadCities(districtId: String) {
        val cities = runCatching { referenceRepository.getCitiesForDistrict(districtId) }.getOrDefault(emptyList())
        _state.update { it.copy(cities = cities) }
    }

    companion object {
        const val SAVE_FAILED_MESSAGE = "Couldn't save your profile. Please try again."
        const val NAME_TOO_SHORT_MESSAGE = "Enter your full name."
        const val MIN_NAME_LENGTH = 2
    }

    /**
     * The resumability field order, exact: name -> photo -> state -> district -> city -> role ->
     * batting -> bowling-if-applicable. `null` means every field this profile needs is already
     * filled.
     */
    private fun firstMissingField(profile: ProfileDto): ProfileField? {
        if (profile.name.isNullOrBlank()) return ProfileField.NAME
        if (profile.photoUrl.isNullOrBlank()) return ProfileField.PHOTO
        if (profile.state.isNullOrBlank()) return ProfileField.STATE
        if (profile.district.isNullOrBlank()) return ProfileField.DISTRICT
        if (profile.city.isNullOrBlank()) return ProfileField.CITY
        val role = profile.playingRole ?: return ProfileField.ROLE
        if (profile.battingStyle == null) return ProfileField.BATTING
        val requiresBowlingStyle = role == PlayingRole.BOWLER || role == PlayingRole.ALL_ROUNDER
        if (requiresBowlingStyle && profile.bowlingStyle == null) return ProfileField.BOWLING
        return null
    }
}

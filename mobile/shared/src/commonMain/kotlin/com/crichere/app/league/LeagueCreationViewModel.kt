package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crichere.app.ground.GroundCreateRequestDto
import com.crichere.app.ground.GroundDto
import com.crichere.app.ground.GroundRepository
import com.crichere.app.location.LocationProvider
import com.crichere.app.reference.CityDto
import com.crichere.app.reference.DistrictDto
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
 * One award row in the Awards section. [id] is `null` for a not-yet-persisted draft (every award
 * starts this way in create mode; the three pre-suggested rows are drafts too) and non-null once
 * it's a real, already-saved award (always true in edit mode, for awards loaded from the
 * existing league) -- [LeagueCreationViewModel.save] uses this distinction to reconcile drafts
 * against real awards via their own endpoints rather than bundling them into the league save.
 */
data class AwardDraft(
    val id: String? = null,
    val name: String = "",
    val cashAmount: String = "",
    val hasTrophy: Boolean = false,
)

data class LeagueCreationState(
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    // Basics
    val name: String = "",
    val description: String = "",
    val logoUrl: String? = null,
    val bannerUrl: String? = null,
    val isUploadingLogo: Boolean = false,
    val isUploadingBanner: Boolean = false,
    // Location
    val state: String? = null,
    val district: String? = null,
    val city: String? = null,
    val states: List<StateDto> = emptyList(),
    val districts: List<DistrictDto> = emptyList(),
    val cities: List<CityDto> = emptyList(),
    val isLocating: Boolean = false,
    // Ground
    val groundId: String? = null,
    val groundDisplayName: String? = null,
    val groundSearchQuery: String = "",
    val groundSearchResults: List<GroundDto> = emptyList(),
    val isSearchingGrounds: Boolean = false,
    val isRegisteringNewGround: Boolean = false,
    val newGroundName: String = "",
    val newGroundLatitude: Double? = null,
    val newGroundLongitude: Double? = null,
    val isRegisteringGround: Boolean = false,
    // Schedule
    val startsOn: String? = null,
    // Format
    val format: String = "",
    // Capacity (text-field-backed; parsed to Int on save)
    val franchisesRequired: String = "",
    val playersRequired: String = "",
    // Fees (text-field-backed; parsed to Double on save)
    val franchiseFee: String = "",
    val playerFee: String = "",
    // Awards
    val awards: List<AwardDraft> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val isSaveEnabled: Boolean
        get() = !isSaving && name.isNotBlank() && state != null && district != null && city != null && startsOn != null
}

sealed interface LeagueCreationNavigationEvent {
    data class Saved(val leagueId: String) : LeagueCreationNavigationEvent
}

/**
 * League Creation's ViewModel: one continuous form, single Save action -- mirrors
 * `ProfileSetupViewModel`'s "one flat state class" pattern exactly (see docs/PHASE2.md's
 * Decisions Made on why this isn't a multi-screen wizard).
 *
 * Reused for both create (`editingLeagueId == null`) and edit
 * (`editingLeagueId != null`, pre-filled from the real league). Awards are the one section that
 * behaves differently underneath depending on mode, even though the UI is identical: in create
 * mode every award is a local, unpersisted draft bundled into the create call; in edit mode
 * [save] reconciles the current draft list against what was actually loaded, calling the awards
 * endpoints directly (add/update/delete) -- see [save]'s own doc.
 *
 * Logo/banner: the presign endpoints need a real league id, which doesn't exist yet in create
 * mode when the user picks an image. Picked bytes are cached in-memory
 * ([pendingLogoBytes]/[pendingBannerBytes], not part of the exposed [LeagueCreationState] --
 * there's nothing for the UI to render differently once the local file is picked, only after
 * upload completes and a URL comes back) and the actual upload happens as part of [save], after
 * the league itself exists.
 */
class LeagueCreationViewModel(
    private val editingLeagueId: String?,
    private val leagueRepository: LeagueRepository,
    private val groundRepository: GroundRepository,
    private val referenceRepository: ReferenceRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(LeagueCreationState(isLoading = editingLeagueId != null, isEditMode = editingLeagueId != null))
    val state: StateFlow<LeagueCreationState> = _state.asStateFlow()

    private val _navigationEvents = Channel<LeagueCreationNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<LeagueCreationNavigationEvent> = _navigationEvents.receiveAsFlow()

    /** The awards actually loaded from the server in edit mode -- [save]'s reconciliation baseline. Empty (nothing to reconcile) in create mode. */
    private var originalAwards: List<AwardDraft> = emptyList()

    private var pendingLogoBytes: ByteArray? = null
    private var pendingLogoContentType: String? = null
    private var pendingBannerBytes: ByteArray? = null
    private var pendingBannerContentType: String? = null

    init {
        // States load first and loadExistingLeague runs only after -- sequential, not two
        // independent launches -- because loadExistingLeague matches the league's state/district
        // names against _state.value.states synchronously; running them in parallel raced on
        // which finished first, silently dropping the district/city preselect in edit mode.
        viewModelScope.launch {
            val states = runCatching { referenceRepository.getStates() }.getOrDefault(emptyList())
            _state.update { it.copy(states = states) }

            if (editingLeagueId != null) {
                loadExistingLeague(editingLeagueId)
            } else {
                // Pre-suggest the three starter awards -- editable/removable, not mandatory to fill (see docs/PHASE2.md's Decisions Made).
                _state.update {
                    it.copy(
                        awards = listOf(
                            AwardDraft(name = "First Prize"),
                            AwardDraft(name = "Second Prize"),
                            AwardDraft(name = "Third Prize"),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun loadExistingLeague(leagueId: String) {
        runCatching { leagueRepository.getLeague(leagueId) }
            .onSuccess { league ->
                val awardDrafts = league.awards.sortedBy { it.displayOrder }.map {
                    AwardDraft(id = it.id, name = it.name, cashAmount = it.cashAmount?.toString().orEmpty(), hasTrophy = it.hasTrophy)
                }
                originalAwards = awardDrafts
                _state.update {
                    it.copy(
                        isLoading = false,
                        name = league.name,
                        description = league.description.orEmpty(),
                        logoUrl = league.logoUrl,
                        bannerUrl = league.bannerUrl,
                        state = league.state,
                        district = league.district,
                        city = league.city,
                        groundId = league.groundId,
                        startsOn = league.startsOn,
                        format = league.format.orEmpty(),
                        franchisesRequired = league.franchisesRequired?.toString().orEmpty(),
                        playersRequired = league.playersRequired?.toString().orEmpty(),
                        franchiseFee = league.franchiseFee?.toString().orEmpty(),
                        playerFee = league.playerFee?.toString().orEmpty(),
                        awards = awardDrafts,
                    )
                }
                league.state.let { stateName ->
                    val matchedState = _state.value.states.firstOrNull { it.name.equals(stateName, ignoreCase = true) }
                    if (matchedState != null) {
                        loadDistricts(matchedState.code)
                        val matchedDistrict = _state.value.districts.firstOrNull { it.name.equals(league.district, ignoreCase = true) }
                        if (matchedDistrict != null) loadCities(matchedDistrict.id)
                    }
                }
            }
            .onFailure { throwable ->
                _state.update { it.copy(isLoading = false, errorMessage = throwable.message ?: "Couldn't load this league.") }
            }
    }

    // ---- Basics ----

    fun onNameChanged(value: String) = _state.update { it.copy(name = value, errorMessage = null) }
    fun onDescriptionChanged(value: String) = _state.update { it.copy(description = value) }

    fun onLogoPicked(bytes: ByteArray, contentType: String) {
        pendingLogoBytes = bytes
        pendingLogoContentType = contentType
    }

    fun onBannerPicked(bytes: ByteArray, contentType: String) {
        pendingBannerBytes = bytes
        pendingBannerContentType = contentType
    }

    // ---- Location ----

    fun onStateSelected(stateDto: StateDto) {
        _state.update { it.copy(state = stateDto.name, district = null, districts = emptyList(), city = null, cities = emptyList(), errorMessage = null) }
        viewModelScope.launch { loadDistricts(stateDto.code) }
    }

    fun onDistrictSelected(districtDto: DistrictDto) {
        _state.update { it.copy(district = districtDto.name, city = null, cities = emptyList(), errorMessage = null) }
        viewModelScope.launch { loadCities(districtDto.id) }
    }

    fun onCitySelected(cityDto: CityDto) = _state.update { it.copy(city = cityDto.name, errorMessage = null) }

    /** User-initiated -- never auto-triggered (that would fire an unprompted permission dialog). Best-effort, silent no-op on any failure. */
    fun useMyLocation() {
        if (_state.value.isLocating) return
        viewModelScope.launch {
            _state.update { it.copy(isLocating = true) }
            val point = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
            val geocoded = point?.let { runCatching { locationProvider.reverseGeocode(it) }.getOrNull() }
            val states = _state.value.states
            val matchedState = geocoded?.administrativeArea?.let { area -> states.firstOrNull { it.name.equals(area, ignoreCase = true) } }
            if (matchedState == null) {
                _state.update { it.copy(isLocating = false) }
                return@launch
            }
            val districts = runCatching { referenceRepository.getDistrictsForState(matchedState.code) }.getOrDefault(emptyList())
            val matchedDistrict = geocoded.subAdministrativeArea?.let { area -> districts.firstOrNull { it.name.equals(area, ignoreCase = true) } }
            if (matchedDistrict == null) {
                _state.update { it.copy(isLocating = false, state = matchedState.name, districts = districts) }
                return@launch
            }
            val cities = runCatching { referenceRepository.getCitiesForDistrict(matchedDistrict.id) }.getOrDefault(emptyList())
            val matchedCity = geocoded.locality?.let { locality -> cities.firstOrNull { it.name.equals(locality, ignoreCase = true) } }
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

    private suspend fun loadDistricts(stateCode: String) {
        val districts = runCatching { referenceRepository.getDistrictsForState(stateCode) }.getOrDefault(emptyList())
        _state.update { it.copy(districts = districts) }
    }

    private suspend fun loadCities(districtId: String) {
        val cities = runCatching { referenceRepository.getCitiesForDistrict(districtId) }.getOrDefault(emptyList())
        _state.update { it.copy(cities = cities) }
    }

    // ---- Ground ----

    fun onGroundSearchQueryChanged(query: String) {
        _state.update { it.copy(groundSearchQuery = query) }
        viewModelScope.launch {
            _state.update { it.copy(isSearchingGrounds = true) }
            val results = runCatching { groundRepository.search(search = query) }.getOrDefault(emptyList())
            _state.update { it.copy(isSearchingGrounds = false, groundSearchResults = results) }
        }
    }

    fun onGroundSelected(ground: GroundDto) {
        _state.update { it.copy(groundId = ground.id, groundDisplayName = ground.name, isRegisteringNewGround = false, groundSearchResults = emptyList()) }
    }

    fun onClearGround() = _state.update { it.copy(groundId = null, groundDisplayName = null) }

    fun onStartRegisteringNewGround() = _state.update { it.copy(isRegisteringNewGround = true, groundSearchResults = emptyList()) }
    fun onCancelRegisteringNewGround() = _state.update { it.copy(isRegisteringNewGround = false) }
    fun onNewGroundNameChanged(value: String) = _state.update { it.copy(newGroundName = value) }
    fun onNewGroundPositionChanged(latitude: Double, longitude: Double) = _state.update { it.copy(newGroundLatitude = latitude, newGroundLongitude = longitude) }

    fun registerNewGround() {
        val current = _state.value
        val state = current.state
        val district = current.district
        val city = current.city
        val latitude = current.newGroundLatitude
        val longitude = current.newGroundLongitude
        if (current.newGroundName.isBlank() || state == null || district == null || city == null || latitude == null || longitude == null) return

        viewModelScope.launch {
            _state.update { it.copy(isRegisteringGround = true, errorMessage = null) }
            runCatching {
                groundRepository.registerGround(
                    GroundCreateRequestDto(
                        name = current.newGroundName, state = state, district = district, city = city, latitude = latitude, longitude = longitude,
                    ),
                )
            }
                .onSuccess { ground ->
                    _state.update {
                        it.copy(
                            isRegisteringGround = false,
                            isRegisteringNewGround = false,
                            groundId = ground.id,
                            groundDisplayName = ground.name,
                            newGroundName = "",
                            newGroundLatitude = null,
                            newGroundLongitude = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(isRegisteringGround = false, errorMessage = throwable.message ?: "Couldn't register this ground. Please try again.") }
                }
        }
    }

    // ---- Schedule / Format / Capacity / Fees ----

    fun onStartsOnChanged(isoDate: String) = _state.update { it.copy(startsOn = isoDate, errorMessage = null) }
    fun onFormatChanged(value: String) = _state.update { it.copy(format = value) }
    fun onFranchisesRequiredChanged(value: String) = _state.update { it.copy(franchisesRequired = value) }
    fun onPlayersRequiredChanged(value: String) = _state.update { it.copy(playersRequired = value) }
    fun onFranchiseFeeChanged(value: String) = _state.update { it.copy(franchiseFee = value) }
    fun onPlayerFeeChanged(value: String) = _state.update { it.copy(playerFee = value) }

    // ---- Awards ----

    fun onAddAward() = _state.update { it.copy(awards = it.awards + AwardDraft()) }
    fun onRemoveAward(index: Int) = _state.update { it.copy(awards = it.awards.filterIndexed { i, _ -> i != index }) }
    fun onAwardNameChanged(index: Int, value: String) = updateAward(index) { it.copy(name = value) }
    fun onAwardCashAmountChanged(index: Int, value: String) = updateAward(index) { it.copy(cashAmount = value) }
    fun onAwardTrophyToggled(index: Int) = updateAward(index) { it.copy(hasTrophy = !it.hasTrophy) }

    private fun updateAward(index: Int, transform: (AwardDraft) -> AwardDraft) {
        _state.update { current ->
            current.copy(awards = current.awards.mapIndexed { i, award -> if (i == index) transform(award) else award })
        }
    }

    // ---- Save ----

    /**
     * Create mode: creates the league (bundling every award draft into the same call), then --
     * only if the user picked a logo/banner -- uploads it and patches the resulting URL in with
     * a follow-up edit, all under one Save press from the user's point of view.
     *
     * Edit mode: full-replace edits the league's own fields (awards excluded, per the backend's
     * own contract), then reconciles the awards list against [originalAwards]: a draft with no
     * `id` is added, an original award missing from the current list is deleted, one present in
     * both with changed fields is updated.
     */
    fun save() {
        val current = _state.value
        if (!current.isSaveEnabled) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                val leagueId = if (editingLeagueId != null) {
                    leagueRepository.updateLeague(editingLeagueId, buildSaveRequest(current, includeAwards = false))
                    reconcileAwards(editingLeagueId, current.awards)
                    editingLeagueId
                } else {
                    val created = leagueRepository.createLeague(buildSaveRequest(current, includeAwards = true))
                    created.id
                }
                uploadPendingImagesIfAny(leagueId, current)
                leagueId
            }
                .onSuccess { leagueId ->
                    _state.update { it.copy(isSaving = false) }
                    _navigationEvents.send(LeagueCreationNavigationEvent.Saved(leagueId))
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            // A failure partway through uploadPendingImagesIfAny() (e.g. the
                            // photo-upload 503 this environment returns by design) would otherwise
                            // leave whichever upload flag it set stuck true forever, since the only
                            // place that clears it is that same function's success path.
                            isUploadingLogo = false,
                            isUploadingBanner = false,
                            errorMessage = throwable.message ?: "Couldn't save this league. Please try again.",
                        )
                    }
                }
        }
    }

    private suspend fun reconcileAwards(leagueId: String, current: List<AwardDraft>) {
        val originalById = originalAwards.filter { it.id != null }.associateBy { it.id }
        val currentIds = current.mapNotNull { it.id }.toSet()

        for (award in current) {
            if (award.id == null) {
                if (award.name.isNotBlank()) leagueRepository.addAward(leagueId, award.toRequest())
            } else if (award.name.isBlank()) {
                // Blanking an existing award's name reads as "remove this," same as the create
                // path's blank-name-is-dropped rule -- the backend rejects a blank name outright,
                // so silently forwarding it as an update would fail past already-committed edits.
                leagueRepository.deleteAward(leagueId, award.id)
            } else {
                val original = originalById[award.id]
                if (original != null && original != award) leagueRepository.updateAward(leagueId, award.id, award.toRequest())
            }
        }
        for (original in originalAwards) {
            if (original.id != null && original.id !in currentIds) leagueRepository.deleteAward(leagueId, original.id)
        }
    }

    private suspend fun uploadPendingImagesIfAny(leagueId: String, current: LeagueCreationState) {
        var newLogoUrl: String? = null
        var newBannerUrl: String? = null

        pendingLogoBytes?.let { bytes ->
            _state.update { it.copy(isUploadingLogo = true) }
            val uploadInfo = leagueRepository.requestLogoUploadUrl(leagueId)
            newLogoUrl = leagueRepository.uploadPhoto(uploadInfo, bytes, pendingLogoContentType ?: "image/jpeg", "logo.jpg")
            _state.update { it.copy(isUploadingLogo = false, logoUrl = newLogoUrl) }
        }
        pendingBannerBytes?.let { bytes ->
            _state.update { it.copy(isUploadingBanner = true) }
            val uploadInfo = leagueRepository.requestBannerUploadUrl(leagueId)
            newBannerUrl = leagueRepository.uploadPhoto(uploadInfo, bytes, pendingBannerContentType ?: "image/jpeg", "banner.jpg")
            _state.update { it.copy(isUploadingBanner = false, bannerUrl = newBannerUrl) }
        }

        if (newLogoUrl != null || newBannerUrl != null) {
            val latest = _state.value
            leagueRepository.updateLeague(leagueId, buildSaveRequest(latest, includeAwards = false))
        }
    }

    private fun buildSaveRequest(current: LeagueCreationState, includeAwards: Boolean) = LeagueSaveRequestDto(
        name = current.name,
        description = current.description.ifBlank { null },
        logoUrl = current.logoUrl,
        bannerUrl = current.bannerUrl,
        state = requireNotNull(current.state),
        district = requireNotNull(current.district),
        city = requireNotNull(current.city),
        groundId = current.groundId,
        startsOn = requireNotNull(current.startsOn),
        format = current.format.ifBlank { null },
        franchisesRequired = current.franchisesRequired.toIntOrNull(),
        playersRequired = current.playersRequired.toIntOrNull(),
        franchiseFee = current.franchiseFee.toDoubleOrNull(),
        playerFee = current.playerFee.toDoubleOrNull(),
        awards = if (includeAwards) current.awards.filter { it.name.isNotBlank() }.map { it.toRequest() } else null,
    )

    private fun AwardDraft.toRequest() = LeagueAwardSaveRequestDto(
        name = name,
        cashAmount = cashAmount.toDoubleOrNull(),
        hasTrophy = hasTrophy,
    )
}

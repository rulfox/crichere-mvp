package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crichere.app.ground.GroundCreateRequestDto
import com.crichere.app.ground.GroundDto
import com.crichere.app.ground.GroundRepository
import com.crichere.app.location.LocationProvider
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

/** The required League Creation fields, in form order -- the order the first error is scrolled to. */
enum class LeagueField { Name, State, District, Ground, StartsOn, UpiId }

data class LeagueCreationState(
    val isLoading: Boolean = false,
    /** Edit mode only: the league couldn't be loaded; [LeagueCreationViewModel.retryLoad] tries again. */
    val loadFailed: Boolean = false,
    val isEditMode: Boolean = false,
    /** True once anything differs from the empty (create) or loaded (edit) form -- gates Save and the discard prompt. */
    val isDirty: Boolean = false,
    /** Set by a Save tap with missing fields; from then on [fieldErrors] reports them. */
    val showValidation: Boolean = false,
    /** Bumped on each Save tap that found missing fields, so the UI can scroll to the first one again. */
    val validationAttempt: Int = 0,
    // Basics
    val name: String = "",
    val description: String = "",
    val logoUrl: String? = null,
    val bannerUrl: String? = null,
    val isUploadingLogo: Boolean = false,
    val isUploadingBanner: Boolean = false,
    // True the instant a logo/banner is picked, before there's a leagueId to upload against --
    // see onLogoPicked/onBannerPicked's doc. Lets the button reflect the pick immediately instead
    // of staying on "Choose a logo" until Save (the actual upload's only trigger point).
    val hasPendingLogo: Boolean = false,
    val hasPendingBanner: Boolean = false,
    /** 0..1 while the logo/banner uploads during Save, else `null`. */
    val logoUploadProgress: Float? = null,
    val bannerUploadProgress: Float? = null,
    // Location
    val state: String? = null,
    val district: String? = null,
    val states: List<StateDto> = emptyList(),
    val districts: List<DistrictDto> = emptyList(),
    val isLocating: Boolean = false,
    // Ground
    val groundId: String? = null,
    val groundDisplayName: String? = null,
    /** Edit mode: "Change" reopened the search while the current ground stays selected (design update #6, I10). */
    val isChangingGround: Boolean = false,
    val groundSearchQuery: String = "",
    val groundSearchResults: List<GroundDto> = emptyList(),
    val isSearchingGrounds: Boolean = false,
    val isRegisteringNewGround: Boolean = false,
    val newGroundName: String = "",
    val newGroundLatitude: Double? = null,
    val newGroundLongitude: Double? = null,
    val isRegisteringGround: Boolean = false,
    /** Register-ground tapped with no name (design I11). */
    val newGroundNameError: Boolean = false,
    /** Title + message for the register-ground sheet's error banner (design I11), or `null`. */
    val groundErrorTitle: String? = null,
    val groundErrorMessage: String? = null,
    // Schedule
    val startsOn: String? = null,
    // Format: one of [LeagueCreationViewModel.FORMAT_OPTIONS], or free text when [isFormatOther]
    val format: String = "",
    val isFormatOther: Boolean = false,
    // Capacity (text-field-backed; parsed to Int on save)
    val franchisesRequired: String = "",
    val playersRequired: String = "",
    // Fees (text-field-backed; parsed to Double on save)
    val franchiseFee: String = "",
    val playerFee: String = "",
    /** Required by the backend (`OrganizerUpiRequiredException`, 400) the moment either fee above is non-blank -- see docs/PHASE3.md's League Creation screen spec. */
    val organizerUpiId: String = "",
    // Awards
    val awards: List<AwardDraft> = emptyList(),
    val isSaving: Boolean = false,
    /** A failed Save (shown with Retry), or -- edit mode -- a failed load. */
    val errorMessage: String? = null,
) {
    /** Enabled once the form is edited; a tap with missing fields shows them instead of saving. */
    val isSaveEnabled: Boolean
        get() = !isSaving && !isLoading && !loadFailed && isDirty

    /** Every required field that's still missing, with its inline message, in form order. */
    val missingFields: Map<LeagueField, String>
        get() = buildMap {
            if (name.isBlank()) put(LeagueField.Name, "Enter a league name")
            if (state == null) put(LeagueField.State, "Select a state")
            if (district == null) put(LeagueField.District, "Select a district")
            if (groundId == null) put(LeagueField.Ground, LeagueCreationViewModel.GROUND_REQUIRED_MESSAGE)
            if (startsOn == null) put(LeagueField.StartsOn, "Pick a start date")
            val feeSet = franchiseFee.isNotBlank() || playerFee.isNotBlank()
            if (feeSet && organizerUpiId.isBlank()) put(LeagueField.UpiId, "Required when a fee is set")
        }

    /** Registering a ground needs the league's own State + District (design update #6, I16). */
    val canRegisterGround: Boolean
        get() = state != null && district != null

    /** [missingFields], but only once a Save tap has asked for them (design I7/I9). */
    val fieldErrors: Map<LeagueField, String>
        get() = if (showValidation) missingFields else emptyMap()
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

    /** Create mode: the league once the create call succeeded, so a retried Save updates it instead of creating another. */
    private var createdLeagueId: String? = null

    /** The form as first shown (empty, or as loaded) -- [LeagueCreationState.isDirty] compares against it. */
    private var baseline: FormSnapshot? = null

    init {
        // States load first and loadExistingLeague runs only after -- sequential, not two
        // independent launches -- because loadExistingLeague matches the league's state/district
        // names against _state.value.states synchronously; running them in parallel raced on
        // which finished first, silently dropping the district preselect in edit mode.
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
                baseline = _state.value.snapshot()
            }
        }
    }

    /** Edit mode, after a failed load. */
    fun retryLoad() {
        val leagueId = editingLeagueId ?: return
        _state.update { it.copy(isLoading = true, loadFailed = false, errorMessage = null) }
        viewModelScope.launch { loadExistingLeague(leagueId) }
    }

    private suspend fun loadExistingLeague(leagueId: String) {
        runCatching { leagueRepository.getLeague(leagueId) }
            .onSuccess { league ->
                val awardDrafts = league.awards.sortedBy { it.displayOrder }.map {
                    AwardDraft(id = it.id, name = it.name, cashAmount = it.cashAmount?.let(::plainNumber).orEmpty(), hasTrophy = it.hasTrophy)
                }
                originalAwards = awardDrafts
                val format = league.format.orEmpty()
                _state.update {
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                        name = league.name,
                        description = league.description.orEmpty(),
                        logoUrl = league.logoUrl,
                        bannerUrl = league.bannerUrl,
                        state = league.state,
                        district = league.district,
                        groundId = league.groundId,
                        groundDisplayName = league.groundName,
                        startsOn = league.startsOn,
                        format = format,
                        isFormatOther = format.isNotBlank() && format !in FORMAT_OPTIONS,
                        franchisesRequired = league.franchisesRequired?.toString().orEmpty(),
                        playersRequired = league.playersRequired?.toString().orEmpty(),
                        franchiseFee = league.franchiseFee?.let(::plainNumber).orEmpty(),
                        playerFee = league.playerFee?.let(::plainNumber).orEmpty(),
                        organizerUpiId = league.organizerUpiId.orEmpty(),
                        awards = awardDrafts,
                    )
                }
                league.state.let { stateName ->
                    val matchedState = _state.value.states.firstOrNull { it.name.equals(stateName, ignoreCase = true) }
                    if (matchedState != null) loadDistricts(matchedState.code)
                }
                baseline = _state.value.snapshot()
            }
            .onFailure {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
            }
    }

    /** Applies a form edit, recomputes [LeagueCreationState.isDirty] and clears a stale save failure. */
    private fun edit(transform: (LeagueCreationState) -> LeagueCreationState) {
        _state.update { current ->
            val next = transform(current)
            next.copy(isDirty = baseline != null && next.snapshot() != baseline, errorMessage = null)
        }
    }

    // ---- Basics ----

    fun onNameChanged(value: String) = edit { it.copy(name = value) }
    fun onDescriptionChanged(value: String) = edit { it.copy(description = value) }

    /**
     * Unlike Profile Setup's photo (uploaded immediately -- the caller's own `userId` is already
     * known), a league logo/banner can't be uploaded yet: the presign endpoint needs a real
     * `leagueId`, which doesn't exist until the league itself is created. The bytes are staged
     * here and actually uploaded by [uploadPendingImagesIfAny], called from [save]. [hasPendingLogo]
     * only exists so the button can say "Logo selected" right away instead of looking like the tap
     * did nothing until Save.
     */
    fun onLogoPicked(bytes: ByteArray, contentType: String) {
        pendingLogoBytes = bytes
        pendingLogoContentType = contentType
        edit { it.copy(hasPendingLogo = true) }
    }

    fun onBannerPicked(bytes: ByteArray, contentType: String) {
        pendingBannerBytes = bytes
        pendingBannerContentType = contentType
        edit { it.copy(hasPendingBanner = true) }
    }

    /** The picked-but-not-yet-uploaded logo/banner bytes, for the preview card -- `null` once uploaded or removed. */
    fun pendingLogoBytes(): ByteArray? = pendingLogoBytes
    fun pendingBannerBytes(): ByteArray? = pendingBannerBytes

    /** Drops a picked-but-unsaved logo and, in edit mode, the league's current one (applied on Save). */
    fun removeLogo() {
        pendingLogoBytes = null
        pendingLogoContentType = null
        edit { it.copy(hasPendingLogo = false, logoUrl = null) }
    }

    fun removeBanner() {
        pendingBannerBytes = null
        pendingBannerContentType = null
        edit { it.copy(hasPendingBanner = false, bannerUrl = null) }
    }

    // ---- Location ----

    fun onStateSelected(stateDto: StateDto) {
        edit { it.copy(state = stateDto.name, district = null, districts = emptyList()) }
        viewModelScope.launch { loadDistricts(stateDto.code) }
    }

    fun onDistrictSelected(districtDto: DistrictDto) = edit { it.copy(district = districtDto.name) }

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
                edit { it.copy(isLocating = false, state = matchedState.name, districts = districts, district = null) }
                return@launch
            }
            edit { it.copy(isLocating = false, state = matchedState.name, districts = districts, district = matchedDistrict.name) }
        }
    }

    private suspend fun loadDistricts(stateCode: String) {
        val districts = runCatching { referenceRepository.getDistrictsForState(stateCode) }.getOrDefault(emptyList())
        _state.update { it.copy(districts = districts) }
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
        edit {
            it.copy(
                groundId = ground.id,
                groundDisplayName = ground.name,
                isRegisteringNewGround = false,
                isChangingGround = false,
                groundSearchQuery = "",
                groundSearchResults = emptyList(),
            )
        }
    }

    /** Create mode only: drops the selection. Edit mode never clears a ground (every league has one), see [onChangeGround]. */
    fun onClearGround() = edit { it.copy(groundId = null, groundDisplayName = null) }

    /** Edit mode (I10): search for a replacement; the current ground stays until another is picked or registered. */
    fun onChangeGround() = _state.update { it.copy(isChangingGround = true) }

    fun onStartRegisteringNewGround() = _state.update {
        it.copy(isRegisteringNewGround = true, groundSearchResults = emptyList(), newGroundNameError = false, groundErrorTitle = null, groundErrorMessage = null)
    }
    fun onCancelRegisteringNewGround() = _state.update {
        it.copy(isRegisteringNewGround = false, newGroundNameError = false, groundErrorTitle = null, groundErrorMessage = null)
    }
    fun onNewGroundNameChanged(value: String) = _state.update { it.copy(newGroundName = value, newGroundNameError = value.isBlank() && it.newGroundNameError) }
    fun onNewGroundPositionChanged(latitude: Double, longitude: Double) = _state.update { it.copy(newGroundLatitude = latitude, newGroundLongitude = longitude) }

    fun registerNewGround() {
        val current = _state.value
        val state = current.state
        val district = current.district
        val latitude = current.newGroundLatitude
        val longitude = current.newGroundLongitude
        if (current.isRegisteringGround) return
        if (current.newGroundName.isBlank()) {
            _state.update { it.copy(newGroundNameError = true) }
            return
        }

        // Each of these can be missing on its own -- surfacing which one beats a silent no-op,
        // which is indistinguishable from the tap simply not registering at all.
        if (state == null || district == null) {
            _state.update { it.copy(groundErrorTitle = GROUND_LOCATION_MISSING_MESSAGE, groundErrorMessage = null) }
            return
        }
        if (latitude == null || longitude == null) {
            _state.update { it.copy(groundErrorTitle = "Place the pin first.", groundErrorMessage = "Move the map until the pin sits on the ground.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isRegisteringGround = true, groundErrorTitle = null, groundErrorMessage = null) }
            runCatching {
                groundRepository.registerGround(
                    GroundCreateRequestDto(
                        name = current.newGroundName, state = state, district = district, latitude = latitude, longitude = longitude,
                    ),
                )
            }
                .onSuccess { ground ->
                    edit {
                        it.copy(
                            isRegisteringGround = false,
                            isRegisteringNewGround = false,
                            isChangingGround = false,
                            groundId = ground.id,
                            groundDisplayName = ground.name,
                            newGroundName = "",
                            newGroundLatitude = null,
                            newGroundLongitude = null,
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(isRegisteringGround = false, groundErrorTitle = "Couldn't register this ground.", groundErrorMessage = "Check your connection and try again.")
                    }
                }
        }
    }

    // ---- Schedule / Format / Capacity / Fees ----

    fun onStartsOnChanged(isoDate: String) = edit { it.copy(startsOn = isoDate) }

    /** Free-text format -- the "Other" field, or iOS's plain text field. */
    fun onFormatChanged(value: String) = edit { it.copy(format = value) }

    /** A [FORMAT_OPTIONS] entry, or `null` for Other (keeps any text already typed there). */
    fun onFormatOptionSelected(option: String?) = edit {
        if (option == null) {
            it.copy(isFormatOther = true, format = if (it.isFormatOther) it.format else "")
        } else {
            it.copy(isFormatOther = false, format = option)
        }
    }

    fun onFranchisesRequiredChanged(value: String) = edit { it.copy(franchisesRequired = value) }
    fun onPlayersRequiredChanged(value: String) = edit { it.copy(playersRequired = value) }
    fun onFranchiseFeeChanged(value: String) = edit { it.copy(franchiseFee = value) }
    fun onPlayerFeeChanged(value: String) = edit { it.copy(playerFee = value) }
    fun onOrganizerUpiIdChanged(value: String) = edit { it.copy(organizerUpiId = value) }

    // ---- Awards ----

    fun onAddAward() = edit { it.copy(awards = it.awards + AwardDraft()) }
    fun onRemoveAward(index: Int) = edit { it.copy(awards = it.awards.filterIndexed { i, _ -> i != index }) }
    fun onAwardNameChanged(index: Int, value: String) = updateAward(index) { it.copy(name = value) }
    fun onAwardCashAmountChanged(index: Int, value: String) = updateAward(index) { it.copy(cashAmount = value) }
    fun onAwardTrophyToggled(index: Int) = updateAward(index) { it.copy(hasTrophy = !it.hasTrophy) }

    private fun updateAward(index: Int, transform: (AwardDraft) -> AwardDraft) {
        edit { current ->
            current.copy(awards = current.awards.mapIndexed { i, award -> if (i == index) transform(award) else award })
        }
    }

    /** Dismisses the save-failure message. */
    fun dismissError() = _state.update { it.copy(errorMessage = null) }

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
        if (current.missingFields.isNotEmpty()) {
            _state.update { it.copy(showValidation = true, validationAttempt = it.validationAttempt + 1) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                val createdId = createdLeagueId
                val leagueId = when {
                    editingLeagueId != null -> {
                        leagueRepository.updateLeague(editingLeagueId, buildSaveRequest(current, includeAwards = false))
                        reconcileAwards(editingLeagueId, current.awards)
                        editingLeagueId
                    }
                    // A retry after the league was created but a later step (an image upload)
                    // failed: update that league rather than creating a second one. Its awards
                    // went in with the create call.
                    createdId != null -> {
                        leagueRepository.updateLeague(createdId, buildSaveRequest(current, includeAwards = false))
                        createdId
                    }
                    else -> leagueRepository.createLeague(buildSaveRequest(current, includeAwards = true)).id.also { createdLeagueId = it }
                }
                uploadPendingImagesIfAny(leagueId)
                leagueId
            }
                .onSuccess { leagueId ->
                    _state.update { it.copy(isSaving = false) }
                    _navigationEvents.send(LeagueCreationNavigationEvent.Saved(leagueId))
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            // A failure partway through uploadPendingImagesIfAny() would otherwise
                            // leave whichever upload flag it set stuck true forever, since the only
                            // place that clears it is that same function's success path.
                            isUploadingLogo = false,
                            isUploadingBanner = false,
                            logoUploadProgress = null,
                            bannerUploadProgress = null,
                            errorMessage = "Couldn't save the league. Check your connection and try again.",
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

    /** Uploads a picked logo/banner with progress; each one's bytes are dropped once it's up, so a retry only sends what's left. */
    private suspend fun uploadPendingImagesIfAny(leagueId: String) {
        var newLogoUrl: String? = null
        var newBannerUrl: String? = null

        pendingLogoBytes?.let { bytes ->
            _state.update { it.copy(isUploadingLogo = true, logoUploadProgress = 0f) }
            val uploadInfo = leagueRepository.requestLogoUploadUrl(leagueId)
            newLogoUrl = leagueRepository.uploadPhoto(uploadInfo, bytes, pendingLogoContentType ?: "image/jpeg", "logo.jpg") { progress ->
                _state.update { it.copy(logoUploadProgress = progress) }
            }
            pendingLogoBytes = null
            _state.update { it.copy(isUploadingLogo = false, logoUploadProgress = null, logoUrl = newLogoUrl, hasPendingLogo = false) }
        }
        pendingBannerBytes?.let { bytes ->
            _state.update { it.copy(isUploadingBanner = true, bannerUploadProgress = 0f) }
            val uploadInfo = leagueRepository.requestBannerUploadUrl(leagueId)
            newBannerUrl = leagueRepository.uploadPhoto(uploadInfo, bytes, pendingBannerContentType ?: "image/jpeg", "banner.jpg") { progress ->
                _state.update { it.copy(bannerUploadProgress = progress) }
            }
            pendingBannerBytes = null
            _state.update { it.copy(isUploadingBanner = false, bannerUploadProgress = null, bannerUrl = newBannerUrl, hasPendingBanner = false) }
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
        groundId = requireNotNull(current.groundId),
        startsOn = requireNotNull(current.startsOn),
        format = current.format.ifBlank { null },
        franchisesRequired = current.franchisesRequired.toIntOrNull(),
        playersRequired = current.playersRequired.toIntOrNull(),
        franchiseFee = current.franchiseFee.toDoubleOrNull(),
        playerFee = current.playerFee.toDoubleOrNull(),
        organizerUpiId = current.organizerUpiId.ifBlank { null },
        awards = if (includeAwards) current.awards.filter { it.name.isNotBlank() }.map { it.toRequest() } else null,
    )

    private fun AwardDraft.toRequest() = LeagueAwardSaveRequestDto(
        name = name,
        cashAmount = cashAmount.toDoubleOrNull(),
        hasTrophy = hasTrophy,
    )

    companion object {
        /** The Format dropdown's fixed choices; anything else is "Other" with free text. */
        val FORMAT_OPTIONS = listOf("T10", "T20", "50 overs", "Test")

        const val GROUND_REQUIRED_MESSAGE = "Select a ground or register a new one"
        const val GROUND_LOCATION_MISSING_MESSAGE = "Set the league's State and District before registering a ground"
    }
}

/** The user-editable part of [LeagueCreationState] -- what "edited" means for [LeagueCreationState.isDirty]. */
private data class FormSnapshot(
    val name: String,
    val description: String,
    val logoUrl: String?,
    val bannerUrl: String?,
    val hasPendingLogo: Boolean,
    val hasPendingBanner: Boolean,
    val state: String?,
    val district: String?,
    val groundId: String?,
    val startsOn: String?,
    val format: String,
    val isFormatOther: Boolean,
    val franchisesRequired: String,
    val playersRequired: String,
    val franchiseFee: String,
    val playerFee: String,
    val organizerUpiId: String,
    val awards: List<AwardDraft>,
)

private fun LeagueCreationState.snapshot() = FormSnapshot(
    name, description, logoUrl, bannerUrl, hasPendingLogo, hasPendingBanner, state, district, groundId, startsOn,
    format, isFormatOther, franchisesRequired, playersRequired, franchiseFee, playerFee, organizerUpiId, awards,
)

/** `5000.0` -> `"5000"`, `12.5` -> `"12.5"`: how a stored amount reads back in a text field. */
internal fun plainNumber(value: Double): String =
    if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()

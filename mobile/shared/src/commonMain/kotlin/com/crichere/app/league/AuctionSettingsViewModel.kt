package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuctionField { BasePrice, Purse, SquadMin, SquadMax, BidIncrement }

/** Design J2's amber note: squad max × franchises required is more than players required. */
data class SquadWarning(val squadMax: Int, val franchises: Int, val playersRequired: Int) {
    val total: Int get() = squadMax * franchises
}

/** Design J5: the save-failure bar. [canRetry] is false when retrying can't help (the auction has started). */
data class AuctionSaveError(val message: String, val canRetry: Boolean)

data class AuctionSettingsState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val league: LeagueDto? = null,
    val basePrice: String = "",
    val purse: String = "",
    val squadMin: String = "",
    val squadMax: String = "",
    val bidIncrement: String = "",
    /** Fields the user has changed -- their errors show as they type (owner decision 2026-10-02). */
    val touched: Set<AuctionField> = emptySet(),
    /** Save was tapped with errors -- every field's error shows from then on. */
    val submitAttempted: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: AuctionSaveError? = null,
    /** Design J4: "Auction settings saved", until the screen reports it shown. */
    val showSavedNotice: Boolean = false,
) {
    /** Every rule that currently fails, shown or not. */
    val allErrors: Map<AuctionField, String> get() = validate(this)

    /** The errors to draw: a field's own error once it's been edited, or all of them after a Save tap. */
    val fieldErrors: Map<AuctionField, String>
        get() = allErrors.filterKeys { submitAttempted || it in touched }

    /** [fieldErrors] for one field -- a plain lookup for Swift, where a Kotlin enum-keyed map bridges awkwardly. */
    fun errorFor(field: AuctionField): String? = fieldErrors[field]

    /** Design J2: Save greys out while any error is showing. */
    val canSave: Boolean get() = !isSaving && fieldErrors.isEmpty()

    /** Live from the typed squad max -- the league's own server flag only reflects the last save. */
    val squadWarning: SquadWarning?
        get() {
            val max = squadMax.toIntOrNull()?.takeIf { it > 0 } ?: return null
            val franchises = league?.franchisesRequired ?: return null
            val players = league.playersRequired ?: return null
            return SquadWarning(max, franchises, players).takeIf { it.total > players }
        }
}

/**
 * The client-side copy of the server's rules (`AuctionSettingsSaveRequest` + `SquadSizeInvalidException`),
 * mirrored so mistakes show before a round trip (owner decision 2026-10-02). The server stays the
 * authority -- anything it still rejects comes back through [AuctionSettingsState.saveError].
 */
internal fun validate(state: AuctionSettingsState): Map<AuctionField, String> = buildMap {
    fun amount(field: AuctionField, text: String) {
        val value = text.trim()
        when {
            value.isEmpty() -> put(field, "Required")
            value.toDoubleOrNull() == null -> put(field, "Enter a number")
            value.toDouble() <= 0.0 -> put(field, "Must be more than 0")
        }
    }
    fun count(field: AuctionField, text: String) {
        val value = text.trim()
        when {
            value.isEmpty() -> put(field, "Required")
            value.toIntOrNull() == null -> put(field, if (value.toDoubleOrNull() != null) "Enter a whole number" else "Enter a number")
            value.toInt() <= 0 -> put(field, "Must be more than 0")
        }
    }
    amount(AuctionField.BasePrice, state.basePrice)
    amount(AuctionField.Purse, state.purse)
    count(AuctionField.SquadMin, state.squadMin)
    count(AuctionField.SquadMax, state.squadMax)
    amount(AuctionField.BidIncrement, state.bidIncrement)
    val min = state.squadMin.trim().toIntOrNull()
    val max = state.squadMax.trim().toIntOrNull()
    if (AuctionField.SquadMax !in this && AuctionField.SquadMin !in this && min != null && max != null && max < min) {
        put(AuctionField.SquadMax, "Can't be less than the min")
    }
}

/** `500.0` -> `"500"`, `12.5` -> `"12.5"` -- the board shows whole amounts without a decimal. */
fun amountText(value: Double?): String = when {
    value == null -> ""
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> value.toString()
}

/**
 * Auction Settings screen's ViewModel (see docs/PHASE4.md) -- loads the league (pre-filling the
 * five editable fields from whatever's already configured) and lets the organizer save all five
 * together. The pool/purse read view renders directly from the same loaded [LeagueDto.players]/
 * [LeagueDto.franchises] -- no second fetch, no separate repository call.
 *
 * Same explicit-retry-on-entry convention every other ViewModel in this codebase uses (never an
 * `init` block) -- `AuctionSettingsRoute` calls [retry] in `LaunchedEffect(Unit)`.
 */
class AuctionSettingsViewModel(
    private val leagueId: String,
    private val leagueRepository: LeagueRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuctionSettingsState())
    val state: StateFlow<AuctionSettingsState> = _state.asStateFlow()

    // See LeagueDetailViewModel's doc: this instance outlives any single visit (keyed only by
    // leagueId, not by user), so an older visit's load() can finish after a newer one's and
    // overwrite fresher data -- cancel any prior in-flight load before starting a new one.
    private var loadJob: Job? = null

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            runCatching { leagueRepository.getLeague(leagueId) }
                .onSuccess { league -> _state.update { it.withLeague(league).copy(isLoading = false) } }
                .onFailure { _state.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }

    private fun AuctionSettingsState.withLeague(league: LeagueDto) = copy(
        league = league,
        basePrice = amountText(league.auctionBasePrice),
        purse = amountText(league.auctionPurse),
        squadMin = league.auctionSquadMin?.toString() ?: "",
        squadMax = league.auctionSquadMax?.toString() ?: "",
        bidIncrement = amountText(league.auctionBidIncrement),
        touched = emptySet(),
        submitAttempted = false,
    )

    fun onBasePriceChanged(value: String) = edit(AuctionField.BasePrice) { it.copy(basePrice = value) }
    fun onPurseChanged(value: String) = edit(AuctionField.Purse) { it.copy(purse = value) }
    fun onSquadMinChanged(value: String) = edit(AuctionField.SquadMin) { it.copy(squadMin = value) }
    fun onSquadMaxChanged(value: String) = edit(AuctionField.SquadMax) { it.copy(squadMax = value) }
    fun onBidIncrementChanged(value: String) = edit(AuctionField.BidIncrement) { it.copy(bidIncrement = value) }

    private fun edit(field: AuctionField, change: (AuctionSettingsState) -> AuctionSettingsState) =
        _state.update { change(it).copy(touched = it.touched + field, saveError = null) }

    fun onSavedNoticeShown() = _state.update { it.copy(showSavedNotice = false) }

    fun dismissSaveError() = _state.update { it.copy(saveError = null) }

    fun submit() {
        val current = _state.value
        if (current.isSaving) return
        if (current.allErrors.isNotEmpty()) {
            _state.update { it.copy(submitAttempted = true) }
            return
        }

        val request = AuctionSettingsSaveRequestDto(
            basePrice = current.basePrice.trim().toDouble(),
            purse = current.purse.trim().toDouble(),
            squadMin = current.squadMin.trim().toInt(),
            squadMax = current.squadMax.trim().toInt(),
            bidIncrement = current.bidIncrement.trim().toDouble(),
        )
        _state.update { it.copy(isSaving = true, saveError = null, showSavedNotice = false) }
        viewModelScope.launch {
            runCatching { leagueRepository.updateAuctionSettings(leagueId, request) }
                .onSuccess { league -> _state.update { it.withLeague(league).copy(isSaving = false, showSavedNotice = true) } }
                .onFailure { throwable -> _state.update { it.copy(isSaving = false, saveError = saveErrorFor(throwable)) } }
        }
    }

    private fun saveErrorFor(throwable: Throwable): AuctionSaveError = when {
        throwable is LeagueSaveFailedException && throwable.code == "AUCTION_ALREADY_STARTED" ->
            AuctionSaveError("The auction has started, so these settings can't change now.", canRetry = false)
        throwable is LeagueSaveFailedException ->
            AuctionSaveError("Couldn't save settings. Please try again.", canRetry = true)
        else -> AuctionSaveError("Couldn't save settings. Check your connection and try again.", canRetry = true)
    }
}

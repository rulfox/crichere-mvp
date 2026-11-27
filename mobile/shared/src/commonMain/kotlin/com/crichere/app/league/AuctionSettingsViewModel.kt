package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

enum class AuctionField { BasePrice, Purse, SquadMin, SquadMax, BidIncrement }

/** Design J2's amber note: squad max × franchises required is more than players required. */
data class SquadWarning(val squadMax: Int, val franchises: Int, val playersRequired: Int) {
    val total: Int get() = squadMax * franchises

    /** The amber note's words (Auction Settings redesign), the same on both apps. */
    val message: String
        get() = "$franchises ${plural(franchises, "franchise")} with up to $squadMax ${plural(squadMax, "player")} each need " +
            "$total players, but the league is set for $playersRequired. Some squads may not fill."
}

/** How bids go up (Auction Settings redesign): one amount for every bid, or amounts that change with price. */
enum class BidMode { SameAlways, ChangesWithPrice }

private fun plural(count: Int, word: String) = if (count == 1) word else word + "s"

/** One bid-step row as typed (design S1): bids up to, not including, [upTo] go up by [step]. */
data class BidRangeRow(val upTo: String = "", val step: String = "")

/** Which box in a bid-step row an error belongs to. */
enum class RangeField { UpTo, Step }

/**
 * The one error a price-range row shows, on the box named by [field]; [suggestions] are amounts that
 * would work, shown as tap-to-fill chips ("Try ₹400 ₹500").
 */
data class RangeRowError(val field: RangeField, val message: String, val suggestions: List<Long> = emptyList())

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
    /** The step above the last bid-step range -- with no ranges, the one flat step (docs/PHASE22.md). */
    val bidIncrement: String = "",
    /** "Same amount always" or "Changes with price"; the ranges only count in the second. */
    val bidMode: BidMode = BidMode.SameAlways,
    /** Optional price ranges, at most [MAX_BID_RANGES]; always empty in [BidMode.SameAlways]. */
    val ranges: List<BidRangeRow> = emptyList(),
    /** Ranges set aside on switching to "Same amount always", brought back on switching back (not saved). */
    val stashedRanges: List<BidRangeRow> = emptyList(),
    /** Range rows the user has edited, by index -- their errors show as they type, like [touched]. */
    val touchedRanges: Set<Int> = emptySet(),
    /** Optional "bidding opens at" time as an ISO-8601 instant (docs/PHASE11.md D3), `null` = not set. */
    val scheduledAt: String? = null,
    /** A newly picked time that isn't in the future (design update #4, J12): blocks Save until changed or cleared. */
    val scheduledAtError: String? = null,
    /** The saved time has since passed (J9): a quiet warning, Save still allowed. */
    val scheduledAtPassed: Boolean = false,
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

    /** Every bid-step row rule that currently fails, by row index, shown or not. */
    val allRangeErrors: Map<Int, RangeRowError> get() = validateRanges(this)

    /** The row errors to draw: an edited row's, or all of them after a Save tap. */
    val rangeErrors: Map<Int, RangeRowError>
        get() = allRangeErrors.filterKeys { submitAttempted || it in touchedRanges }

    /** [rangeErrors] for one row -- a plain lookup for Swift. */
    fun rangeErrorFor(index: Int): RangeRowError? = rangeErrors[index]

    /** Design J2: Save greys out while any error is showing. */
    val canSave: Boolean get() = !isLocked && !isSaving && fieldErrors.isEmpty() && rangeErrors.isEmpty() && scheduledAtError == null

    /** Design S4: once the auction has started nothing here can change -- the screen shows a read-only summary. */
    val isLocked: Boolean get() = league?.auctionStatus.let { it != null && it != AuctionStatus.NOT_STARTED }

    /** "+ Add another price range" hides at the limit. */
    val canAddRange: Boolean get() = !isLocked && bidMode == BidMode.ChangesWithPrice && ranges.size < MAX_BID_RANGES

    /** The "Same amount always" card's example, from the typed starting price and amount: `e.g. 250 → 500 …` (two values, so both cards stay two lines). */
    val sameExample: String
        get() {
            val base = wholeRupees(basePrice) ?: 250
            val step = wholeRupees(bidIncrement) ?: 250
            return "e.g. " + List(2) { groupedRupees(base + it * step) }.joinToString(" → ") + " …"
        }

    /** The dashed box shown instead of "Bids will be" while the preview can't be drawn. */
    val previewPlaceholder: String
        get() = if (basePrice.isBlank() || bidIncrement.isBlank()) "Enter a starting price and an amount to see how bids will go."
        else "Fix the highlighted rows to see how bids will go."

    /** The one-line summary at the top: "4 players and 2 franchises have joined". */
    val joinedSummary: String?
        get() {
            val league = league ?: return null
            val players = league.players.size
            val franchises = league.franchises.size
            if (players == 0 && franchises == 0) return "No players or franchises have joined yet"
            return "$players ${plural(players, "player")} and $franchises ${plural(franchises, "franchise")} have joined"
        }

    /** Where row [index] starts -- the base price, then the row above's "Up to" -- as "₹1,000"; `null` while that isn't a valid number. */
    fun rangeStartText(index: Int): String? = rangeStart(this, index)?.let { "₹" + groupedRupees(it) }

    /** The caption over the "and above" step: where the last range ends, or the base price. */
    val aboveStartText: String? get() = rangeStartText(ranges.size)

    /** Design S1's "Bids will go" preview; `null` while any bid-step value is missing or wrong (S3). */
    val ladderPreview: List<LadderPreviewLine>?
        get() {
            if (AuctionField.BasePrice in allErrors || AuctionField.BidIncrement in allErrors || allRangeErrors.isNotEmpty()) return null
            val ladder = typedLadder(this) ?: return null
            return ladder.previewLines()
        }

    /** The read-only view's price ranges: "₹250 to ₹1,000" with "add ₹250", ending with "Above ₹3,000". */
    val lockedSteps: List<LadderPreviewLine>
        get() {
            val ladder = BidLadder.of(league) ?: return emptyList()
            return ladder.segments.map { segment ->
                val range = segment.to?.let { "₹${groupedRupees(segment.from)} to ₹${groupedRupees(it)}" } ?: "Above ₹${groupedRupees(segment.from)}"
                LadderPreviewLine(step = "add ₹" + groupedRupees(segment.step), values = range)
            }
        }

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
const val MAX_BID_RANGES = 10

/** A typed whole-rupee amount above zero, else `null`. */
private fun wholeRupees(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it > 0 }

/** Where bid-step row [index] starts (row [ranges.size] = the "and above" step); `null` if anything before it isn't a valid amount. */
internal fun rangeStart(state: AuctionSettingsState, index: Int): Long? {
    var from = wholeRupees(state.basePrice) ?: return null
    for (i in 0 until index) from = wholeRupees(state.ranges.getOrNull(i)?.upTo ?: return null) ?: return null
    return from
}

private fun typedLadder(state: AuctionSettingsState): BidLadder? {
    val base = wholeRupees(state.basePrice) ?: return null
    val above = wholeRupees(state.bidIncrement) ?: return null
    val ranges = state.ranges.map { row -> BidRangeDto((wholeRupees(row.upTo) ?: return null).toDouble(), (wholeRupees(row.step) ?: return null).toDouble()) }
    return BidLadder(base, ranges, above)
}

private fun wholeAmountError(text: String): String? {
    val value = text.trim()
    return when {
        value.isEmpty() -> "Required"
        value.toLongOrNull() == null -> if (value.toDoubleOrNull() != null) "Use whole rupees" else "Enter a number"
        value.toLong() <= 0 -> "Must be more than 0"
        else -> null
    }
}

/** The two amounts nearest [typed] that split [width] evenly and are bigger than [above] -- "Try ₹400 ₹500". */
internal fun stepSuggestions(width: Long, typed: Long, above: Long?): List<Long> {
    val divisors = mutableSetOf<Long>()
    var d = 1L
    while (d * d <= width) {
        if (width % d == 0L) { divisors += d; divisors += width / d }
        d++
    }
    return divisors.filter { it > (above ?: 0L) && it < width }
        .sortedWith(compareBy({ kotlin.math.abs(it - typed) }, { it }))
        .take(2)
        .sorted()
}

/**
 * The bid-step rules (docs/PHASE22.md), mirroring the backend's `BidRangesValidator` so each row shows
 * its own error. A row's start comes from the row above; while that isn't a valid number the checks
 * that need it wait.
 */
internal fun validateRanges(state: AuctionSettingsState): Map<Int, RangeRowError> = buildMap {
    var previousStep: Long? = null
    state.ranges.forEachIndexed { index, row ->
        val from = rangeStart(state, index)
        val upTo = wholeRupees(row.upTo)
        val step = wholeRupees(row.step)
        val upToError = wholeAmountError(row.upTo)
        val stepError = wholeAmountError(row.step)
        val error = when {
            upToError != null -> RangeRowError(RangeField.UpTo, upToError)
            from != null && upTo != null && upTo <= from -> RangeRowError(RangeField.UpTo, "End price must be more than ₹${groupedRupees(from)}.")
            stepError != null -> RangeRowError(RangeField.Step, stepError)
            previousStep != null && step != null && step <= previousStep ->
                RangeRowError(RangeField.Step, mustAddMore(previousStep))
            from != null && upTo != null && step != null && (upTo - from) % step != 0L -> RangeRowError(
                RangeField.Step,
                "Adding ₹${groupedRupees(step)} each time won’t land exactly on ₹${groupedRupees(upTo)}.",
                stepSuggestions(upTo - from, step, previousStep),
            )
            else -> null
        }
        if (error != null) put(index, error)
        previousStep = step ?: previousStep
    }
}

/** A range (or the "Above" row) whose amount isn't bigger than the one before it. */
private fun mustAddMore(previous: Long) = "Higher prices should add more. Make this more than ₹${groupedRupees(previous)}."

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
    // The base price and steps make up the bid ladder, which is whole rupees (docs/PHASE22.md).
    wholeAmountError(state.basePrice)?.let { put(AuctionField.BasePrice, it) }
    amount(AuctionField.Purse, state.purse)
    count(AuctionField.SquadMin, state.squadMin)
    count(AuctionField.SquadMax, state.squadMax)
    wholeAmountError(state.bidIncrement)?.let { put(AuctionField.BidIncrement, it) }
    val lastRangeStep = state.ranges.lastOrNull()?.let { wholeRupees(it.step) }
    val above = wholeRupees(state.bidIncrement)
    if (AuctionField.BidIncrement !in this && lastRangeStep != null && above != null && above <= lastRangeStep) {
        put(AuctionField.BidIncrement, mustAddMore(lastRangeStep))
    }
    val min = state.squadMin.trim().toIntOrNull()
    val max = state.squadMax.trim().toIntOrNull()
    if (AuctionField.SquadMax !in this && AuctionField.SquadMin !in this && min != null && max != null && max < min) {
        put(AuctionField.SquadMax, "Can't be less than the min")
    }
}

/** A typed amount for the read-only view: `100000` -> `₹1,00,000`, blank -> `Not set`. */
fun rupeesText(text: String): String {
    val value = text.trim()
    if (value.isEmpty()) return "Not set"
    return "₹" + (value.toLongOrNull()?.let { groupedRupees(it) } ?: value)
}

/** `500.0` -> `"500"`, `12.5` -> `"12.5"` -- the board shows whole amounts without a decimal. */
fun amountText(value: Double?): String = when {
    value == null -> ""
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> value.toString()
}

/**
 * Auction Settings screen's ViewModel (see docs/PHASE4.md) -- loads the league (pre-filling the
 * five required fields, plus the optional scheduled time from docs/PHASE11.md, from whatever's already configured) and lets the organizer save all five
 * together. The pool/purse read view renders directly from the same loaded [LeagueDto.players]/
 * [LeagueDto.franchises] -- no second fetch, no separate repository call.
 *
 * Same explicit-retry-on-entry convention every other ViewModel in this codebase uses (never an
 * `init` block) -- `AuctionSettingsRoute` calls [retry] in `LaunchedEffect(Unit)`.
 */
class AuctionSettingsViewModel(
    private val leagueId: String,
    private val leagueRepository: LeagueRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
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
        ranges = league.auctionBidRanges.map { BidRangeRow(amountText(it.upTo), amountText(it.step)) },
        bidMode = if (league.auctionBidRanges.isEmpty()) BidMode.SameAlways else BidMode.ChangesWithPrice,
        stashedRanges = emptyList(),
        touchedRanges = emptySet(),
        scheduledAt = league.auctionScheduledAt,
        scheduledAtError = null,
        scheduledAtPassed = isPast(league.auctionScheduledAt),
        touched = emptySet(),
        submitAttempted = false,
    )

    private fun isPast(instant: String?): Boolean =
        instant?.let { runCatching { Instant.parse(it).toEpochMilliseconds() <= nowMillis() }.getOrNull() } == true

    fun onBasePriceChanged(value: String) = edit(AuctionField.BasePrice) { it.copy(basePrice = value) }
    fun onPurseChanged(value: String) = edit(AuctionField.Purse) { it.copy(purse = value) }
    fun onSquadMinChanged(value: String) = edit(AuctionField.SquadMin) { it.copy(squadMin = value) }
    fun onSquadMaxChanged(value: String) = edit(AuctionField.SquadMax) { it.copy(squadMax = value) }
    fun onBidIncrementChanged(value: String) = edit(AuctionField.BidIncrement) { it.copy(bidIncrement = value) }

    /**
     * The "How bids go up" cards. "Same amount always" sets the ranges aside (they come back if the
     * organizer switches back before leaving); "Changes with price" brings them back, or starts one blank row.
     */
    fun onBidModeChanged(mode: BidMode) = _state.update { current ->
        if (current.isLocked || mode == current.bidMode) return@update current
        when (mode) {
            BidMode.SameAlways -> current.copy(bidMode = mode, stashedRanges = current.ranges, ranges = emptyList(), touchedRanges = emptySet(), saveError = null)
            BidMode.ChangesWithPrice -> current.copy(
                bidMode = mode,
                ranges = current.stashedRanges.ifEmpty { listOf(BidRangeRow()) },
                stashedRanges = emptyList(),
                touchedRanges = emptySet(),
                saveError = null,
            )
        }
    }

    /** "+ Add another price range": a blank row above the "Above" row. */
    fun onAddRange() = _state.update {
        if (!it.canAddRange) it else it.copy(ranges = it.ranges + BidRangeRow(), saveError = null)
    }

    /** Removes row [index]; the rows below keep their edited state. Removing the last one means one amount again. */
    fun onRemoveRange(index: Int) = _state.update { current ->
        if (index !in current.ranges.indices) return@update current
        val ranges = current.ranges.filterIndexed { i, _ -> i != index }
        current.copy(
            ranges = ranges,
            bidMode = if (ranges.isEmpty()) BidMode.SameAlways else current.bidMode,
            touchedRanges = current.touchedRanges.filter { it != index }.map { if (it > index) it - 1 else it }.toSet(),
            saveError = null,
        )
    }

    /** A tapped suggestion chip fills that row's amount. */
    fun onRangeSuggestionPicked(index: Int, amount: Long) = editRange(index) { it.copy(step = amount.toString()) }

    fun onRangeUpToChanged(index: Int, value: String) = editRange(index) { it.copy(upTo = value) }
    fun onRangeStepChanged(index: Int, value: String) = editRange(index) { it.copy(step = value) }

    private fun editRange(index: Int, change: (BidRangeRow) -> BidRangeRow) = _state.update { current ->
        if (index !in current.ranges.indices) return@update current
        current.copy(
            ranges = current.ranges.mapIndexed { i, row -> if (i == index) change(row) else row },
            touchedRanges = current.touchedRanges + index,
            saveError = null,
        )
    }

    /**
     * The picked date-time as an ISO-8601 instant, or `null` to clear it. A newly picked time must be in the
     * future (J12); putting back the saved one (Undo after a clear) only warns if it has passed (J9).
     */
    fun onScheduledAtChanged(value: String?) = _state.update {
        val saved = it.league?.auctionScheduledAt
        val past = isPast(value)
        it.copy(
            scheduledAt = value,
            scheduledAtError = if (past && value != saved) SCHEDULED_TIME_PAST else null,
            scheduledAtPassed = past && value == saved,
            saveError = null,
        )
    }

    private fun edit(field: AuctionField, change: (AuctionSettingsState) -> AuctionSettingsState) =
        _state.update { change(it).copy(touched = it.touched + field, saveError = null) }

    fun onSavedNoticeShown() = _state.update { it.copy(showSavedNotice = false) }

    fun dismissSaveError() = _state.update { it.copy(saveError = null) }

    fun submit() {
        val current = _state.value
        if (current.isSaving || current.isLocked) return
        if (current.allErrors.isNotEmpty() || current.allRangeErrors.isNotEmpty()) {
            _state.update { it.copy(submitAttempted = true) }
            return
        }
        if (current.scheduledAtError != null) return

        val request = AuctionSettingsSaveRequestDto(
            basePrice = current.basePrice.trim().toDouble(),
            purse = current.purse.trim().toDouble(),
            squadMin = current.squadMin.trim().toInt(),
            squadMax = current.squadMax.trim().toInt(),
            bidIncrement = current.bidIncrement.trim().toDouble(),
            scheduledAt = current.scheduledAt,
            bidRanges = current.ranges.map { BidRangeDto(it.upTo.trim().toDouble(), it.step.trim().toDouble()) },
        )
        _state.update { it.copy(isSaving = true, saveError = null, showSavedNotice = false) }
        viewModelScope.launch {
            runCatching { leagueRepository.updateAuctionSettings(leagueId, request) }
                .onSuccess { league -> _state.update { it.withLeague(league).copy(isSaving = false, showSavedNotice = true) } }
                .onFailure { throwable -> _state.update { it.copy(isSaving = false, saveError = saveErrorFor(throwable)) } }
        }
    }

    companion object {
        const val SCHEDULED_TIME_PAST = "Pick a time later than now."
    }

    private fun saveErrorFor(throwable: Throwable): AuctionSaveError = when {
        throwable is LeagueSaveFailedException && throwable.code == "AUCTION_ALREADY_STARTED" ->
            AuctionSaveError("The auction has started, so these settings can't change now.", canRetry = false)
        throwable is LeagueSaveFailedException && throwable.code == "BID_RANGES_INVALID" ->
            AuctionSaveError("Some price ranges aren't valid. Check them and try again.", canRetry = false)
        throwable is LeagueSaveFailedException ->
            AuctionSaveError("Couldn't save settings. Please try again.", canRetry = true)
        else -> AuctionSaveError("Couldn't save settings. Check your connection and try again.", canRetry = true)
    }
}

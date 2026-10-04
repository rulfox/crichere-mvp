package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crichere.app.auth.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Design L1's "Bidding as Satara Titans · ₹42,000 left · 11/12 players". */
data class BiddingContext(
    val franchiseId: String,
    val franchiseName: String,
    val purseLeft: Double?,
    val playersWon: Int,
    val squadMax: Int?,
)

data class AuctionState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** The live stream dropped after a successful load -- the last known state stays on screen. */
    val connectionLost: Boolean = false,
    /** The connection pill (design update #4, A4), driven by timers that start when [connectionLost] turns on. */
    val connectionPhase: ConnectionPhase = ConnectionPhase.ONLINE,
    /** When the last stream event arrived (epoch ms, heartbeats included) -- "updated 34s ago" on the Connection lost line. */
    val lastEventAtMillis: Long? = null,
    val league: LeagueDto? = null,
    val isOrganizer: Boolean = false,
    /** The first franchise this signed-in user owns in this league, if any -- who they bid as. A user owning more than one franchise here (see docs/PHASE3.md's "dual roles allowed freely") always bids as the first one; picking among several is a future refinement, not needed for this MVP screen. */
    val myFranchiseId: String? = null,
    val auction: AuctionStateDto? = null,
    /** Per-franchise spend and squads. Loaded on entry and after every sale (it feeds [biddingContext]), and shown as Results once the auction ends. */
    val results: AuctionResultsDto? = null,
    val bidAmountInput: String = "",
    val isActing: Boolean = false,
    val isBidding: Boolean = false,
    /** Under the Amount field (design L6). */
    val bidError: String? = null,
    /** In the organizer controls dock -- a Sold / Next Player / … the server refused. */
    val actionError: String? = null,
    /** End Auction asks first (design update #5, L19); [isEnding] locks the dialog (L20). */
    val isEndConfirmOpen: Boolean = false,
    val isEnding: Boolean = false,
    /** L21's snackbar; Retry reopens the dialog rather than resending. */
    val endFailure: EndAuctionFailure? = null,
) {
    /** The dialog's body for the current pool. */
    val endAuctionBody: EndAuctionBody get() = endAuctionBody(auction?.playersPending ?: 0, isDeadEnd)

    /** Current bid + the league's increment, or the base price before the first bid (the server's own rule). */
    val minimumNextBid: Double?
        get() {
            val auctionState = auction ?: return null
            if (auctionState.currentPlayerId == null) return null
            val current = auctionState.currentBidAmount
            return if (current != null) league?.auctionBidIncrement?.let { current + it } else league?.auctionBasePrice
        }

    val bidIncrement: Double? get() = league?.auctionBidIncrement

    /** Live, nobody on the block, and no franchise can open a bid (design update #4, A2). */
    val isDeadEnd: Boolean
        get() {
            val auctionState = auction ?: return false
            return auctionState.auctionStatus == AuctionStatus.IN_PROGRESS && auctionState.currentPlayerId == null && !auctionState.canAnyoneBid
        }

    /**
     * What the signed-in franchise owner's dock shows in place of the Amount field (design update #4, A3).
     * Between players the purse is checked against the base price, so a dead end shows the owner's own reason.
     */
    val dockMode: DockMode
        get() {
            val context = biddingContext ?: return DockMode.Bid
            val auctionState = auction ?: return DockMode.Bid
            val squadMax = context.squadMax
            val threshold = minimumNextBid ?: league?.auctionBasePrice
            val leadingBid = auctionState.currentBidAmount
            return when {
                squadMax != null && context.playersWon >= squadMax -> DockMode.SquadFull(context.playersWon, squadMax)
                leadingBid != null && auctionState.currentLeadingFranchiseId == context.franchiseId -> DockMode.Leading(leadingBid)
                !auctionState.allowExceedPurse && threshold != null && context.purseLeft != null && context.purseLeft < threshold ->
                    DockMode.PurseShort(threshold)
                else -> DockMode.Bid
            }
        }

    val biddingContext: BiddingContext?
        get() {
            val franchiseId = myFranchiseId ?: return null
            val franchise = league?.franchises?.firstOrNull { it.id == franchiseId } ?: return null
            val result = results?.franchises?.firstOrNull { it.franchiseId == franchiseId }
            return BiddingContext(
                franchiseId = franchiseId,
                franchiseName = franchise.name,
                purseLeft = result?.purseRemaining ?: league.auctionPurse,
                playersWon = result?.playersWon?.size ?: 0,
                squadMax = league.auctionSquadMax,
            )
        }
}

/**
 * The live auction screen's ViewModel (see docs/PHASE5.md). Unlike every other ViewModel in this
 * codebase, mutating actions do **not** reload via a fresh `GET` on success -- the whole point of
 * the SSE stream is that it *is* the reload, pushed continuously and shared by every connected
 * client (organizer, every franchise owner, any spectator). Each action does apply its own HTTP
 * response immediately for the actor's own snappier feedback, but the stream stays the ongoing
 * source of truth after that.
 *
 * Same explicit-retry-on-entry convention every other ViewModel here uses (never `init`).
 */
class AuctionViewModel(
    private val leagueId: String,
    private val leagueRepository: LeagueRepository,
    private val auctionRepository: AuctionRepository,
    private val authRepository: AuthRepository,
    /**
     * Waits before each reconnect attempt after the live stream ends (the last value repeats). Empty --
     * the default -- never reconnects: the screen shows [AuctionState.connectionLost] and the user
     * taps retry. The app passes [STREAM_RECONNECT_DELAYS_MS]; tests that don't care keep the default
     * so a stream that completes or throws can't loop under `advanceUntilIdle`.
     */
    private val reconnectDelaysMs: List<Long> = emptyList(),
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private val _state = MutableStateFlow(AuctionState())
    val state: StateFlow<AuctionState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var streamJob: Job? = null
    private var connectionJob: Job? = null
    private var backOnlineJob: Job? = null

    fun retry() {
        loadJob?.cancel()
        streamJob?.cancel()
        // Retry on the "Connection lost" pill goes straight back to Reconnecting and restarts the 30 s clock.
        _state.update {
            it.copy(
                isLoading = it.league == null,
                loadFailed = false,
                connectionLost = false,
                connectionPhase = if (it.connectionPhase == ConnectionPhase.LOST) ConnectionPhase.RECONNECTING else it.connectionPhase,
            )
        }
        connectionJob?.cancel()

        loadJob = viewModelScope.launch {
            runCatching {
                val league = leagueRepository.getLeague(leagueId)
                val currentUserId = authRepository.getCurrentUserId()
                val isOrganizer = league.isOrganizerOrCoOrganizer(currentUserId)
                val myFranchiseId = currentUserId?.let { uid -> league.franchises.firstOrNull { it.ownerUserId == uid }?.id }
                Triple(league, isOrganizer, myFranchiseId)
            }.onSuccess { (league, isOrganizer, myFranchiseId) ->
                _state.update { it.copy(league = league, isOrganizer = isOrganizer, myFranchiseId = myFranchiseId) }
                startStream()
            }.onFailure {
                _state.update { it.copy(isLoading = false, loadFailed = it.league == null, connectionLost = it.league != null) }
                if (_state.value.connectionLost) onConnectionDropped()
            }
        }
    }

    private fun startStream() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            var failures = 0
            while (true) {
                var received = false
                val error = try {
                    auctionRepository.streamAuctionState(leagueId).collect { newState ->
                        received = true
                        _state.update { it.copy(isLoading = false, connectionLost = false, lastEventAtMillis = nowMillis()) }
                        onFeedBack()
                        applyAuctionState(newState)
                    }
                    null
                } catch (e: Exception) {
                    // A timeout also arrives as a CancellationException, so the type can't tell "the screen
                    // closed" from "the stream went quiet": only our own scope being cancelled should stop us.
                    currentCoroutineContext().ensureActive()
                    e
                }
                if (received) failures = 0
                // A stream is meant to run forever, so a clean end is a drop too -- but only once
                // reconnecting is on; without it a completing stream stays the no-op it always was.
                if (error != null || reconnectDelaysMs.isNotEmpty()) {
                    _state.update { it.copy(isLoading = false, loadFailed = it.auction == null, connectionLost = it.auction != null) }
                    if (_state.value.connectionLost) onConnectionDropped()
                }
                // Nothing on screen yet: the load-failed state's own Retry is the way back.
                if (reconnectDelaysMs.isEmpty() || _state.value.auction == null) return@launch
                delay(reconnectDelaysMs[minOf(failures, reconnectDelaysMs.lastIndex)])
                failures++
            }
        }
    }

    /**
     * The pill's timers for a drop: nothing for the first second (a blip that recovers stays invisible),
     * then Reconnecting, then Connection lost 30 s after the drop. A drop while the timers already run
     * (a reconnect attempt failing again) doesn't restart them; a stream event or Retry cancels them.
     */
    private fun onConnectionDropped() {
        backOnlineJob?.cancel()
        if (connectionJob?.isActive == true) return
        connectionJob = viewModelScope.launch {
            delay(CONNECTION_GRACE_MS)
            _state.update { if (it.connectionPhase == ConnectionPhase.LOST) it else it.copy(connectionPhase = ConnectionPhase.RECONNECTING) }
            delay(CONNECTION_LOST_AFTER_MS - CONNECTION_GRACE_MS)
            _state.update { it.copy(connectionPhase = ConnectionPhase.LOST) }
        }
    }

    /** "Back online" for 1.5 s -- only when a pill was showing. */
    private fun onFeedBack() {
        connectionJob?.cancel()
        val phase = _state.value.connectionPhase
        if (phase != ConnectionPhase.RECONNECTING && phase != ConnectionPhase.LOST) return
        _state.update { it.copy(connectionPhase = ConnectionPhase.BACK_ONLINE) }
        backOnlineJob?.cancel()
        backOnlineJob = viewModelScope.launch {
            delay(BACK_ONLINE_MS)
            _state.update { if (it.connectionPhase == ConnectionPhase.BACK_ONLINE) it.copy(connectionPhase = ConnectionPhase.ONLINE) else it }
        }
    }

    /**
     * Shared by the SSE path and every direct action response ([runOrganizerAction], [placeBid])
     * so whichever of them observes a change first applies it. Reloads [AuctionState.results] when
     * a sale changes the squads (or the auction starts / ends), and keeps the Amount field at
     * least the new minimum bid (design L1: pre-filled with the minimum).
     */
    private fun applyAuctionState(newState: AuctionStateDto) {
        val previous = _state.value.auction
        _state.update { current ->
            val updated = current.copy(auction = newState)
            val minimum = updated.minimumNextBid
            val typed = current.bidAmountInput.toDoubleOrNull()
            val playerChanged = previous?.currentPlayerId != newState.currentPlayerId
            updated.copy(
                bidAmountInput = when {
                    minimum == null -> ""
                    playerChanged || typed == null || typed < minimum -> amountText(minimum)
                    else -> current.bidAmountInput
                },
                bidError = if (playerChanged || minimum != current.minimumNextBid) null else current.bidError,
            )
        }
        val squadsChanged = previous == null || previous.playersSold != newState.playersSold || previous.auctionStatus != newState.auctionStatus
        if (newState.auctionStatus != AuctionStatus.NOT_STARTED && squadsChanged) loadResults()
    }

    fun loadResults() {
        viewModelScope.launch {
            runCatching { auctionRepository.getResults(leagueId) }
                .onSuccess { results -> _state.update { it.copy(results = results) } }
                .onFailure { /* Non-fatal -- the live state is still correct without it. */ }
        }
    }

    fun onBidAmountChanged(value: String) = _state.update { it.copy(bidAmountInput = value, bidError = null) }

    /** Design L1's "+₹500": the typed amount (or the minimum) plus one bid increment. */
    fun onStepBid() {
        val current = _state.value
        val increment = current.bidIncrement ?: return
        val base = current.bidAmountInput.toDoubleOrNull() ?: current.minimumNextBid ?: return
        _state.update { it.copy(bidAmountInput = amountText(base + increment), bidError = null) }
    }

    fun start() = runOrganizerAction { auctionRepository.start(leagueId) }
    fun nextPlayer() = runOrganizerAction { auctionRepository.nextPlayer(leagueId) }
    fun sold() = runOrganizerAction { auctionRepository.sold(leagueId) }
    fun unsold() = runOrganizerAction { auctionRepository.unsold(leagueId) }
    fun undo() = runOrganizerAction { auctionRepository.undo(leagueId) }

    /** End Auction (both entry points) opens the confirmation instead of acting (design update #5, L19). */
    fun requestEnd() {
        if (!_state.value.isOrganizer || _state.value.isActing || _state.value.isEnding) return
        _state.update { it.copy(isEndConfirmOpen = true, endFailure = null) }
    }

    /** Cancel, scrim or back -- ignored while ending (L20 locks the dialog). */
    fun dismissEnd() = _state.update { if (it.isEnding) it else it.copy(isEndConfirmOpen = false) }

    fun clearEndFailure() = _state.update { it.copy(endFailure = null) }

    /**
     * Confirm: success closes the dialog and the Ended state is the confirmation (no snackbar). A refusal
     * because the auction is no longer running (a co-organizer ended it) shows nothing -- the stream brings
     * the Ended state. Anything else closes the dialog and shows L21's snackbar.
     */
    fun confirmEnd() {
        if (!_state.value.isOrganizer || _state.value.isEnding) return
        _state.update { it.copy(isEnding = true, endFailure = null) }
        viewModelScope.launch {
            runCatching { auctionRepository.end(leagueId) }
                .onSuccess { newState ->
                    _state.update { it.copy(isEnding = false, isEndConfirmOpen = false) }
                    applyAuctionState(newState)
                }
                .onFailure { throwable ->
                    val failure = when {
                        throwable !is AuctionActionFailedException -> EndAuctionFailure.NETWORK
                        throwable.code == "AUCTION_NOT_IN_PROGRESS" -> null
                        else -> EndAuctionFailure.REFUSED
                    }
                    _state.update { it.copy(isEnding = false, isEndConfirmOpen = false, endFailure = failure) }
                }
        }
    }
    fun toggleExceedPurse(allow: Boolean) = runOrganizerAction { auctionRepository.toggleExceedPurse(leagueId, allow) }

    fun placeBid() {
        val current = _state.value
        val franchiseId = current.myFranchiseId ?: return
        if (current.isBidding) return
        val amount = current.bidAmountInput.trim().toDoubleOrNull()
        val minimum = current.minimumNextBid
        when {
            current.auction?.currentLeadingFranchiseId == franchiseId -> {
                _state.update { it.copy(bidError = "You already have the leading bid.") }
                return
            }
            amount == null -> {
                _state.update { it.copy(bidError = "Enter a bid amount.") }
                return
            }
            minimum != null && amount < minimum -> {
                _state.update { it.copy(bidError = "Bid must be at least ${rupeeText(minimum)}.") }
                return
            }
        }

        _state.update { it.copy(isBidding = true, bidError = null) }
        viewModelScope.launch {
            runCatching { auctionRepository.placeBid(leagueId, PlaceBidRequestDto(franchiseId, amount!!)) }
                .onSuccess { newState ->
                    _state.update { it.copy(isBidding = false) }
                    applyAuctionState(newState)
                }
                .onFailure { throwable -> _state.update { it.copy(isBidding = false, bidError = bidErrorFor(throwable, it)) } }
        }
    }

    private fun bidErrorFor(throwable: Throwable, state: AuctionState): String {
        val context = state.biddingContext
        return when ((throwable as? AuctionActionFailedException)?.code) {
            // Someone outbid in the meantime -- the stream will bring the new minimum; say what it was when we knew.
            "BID_TOO_LOW" -> state.minimumNextBid?.let { "Bid must be at least ${rupeeText(it)}." } ?: "Someone bid higher. Try again."
            "ALREADY_LEADING" -> "You already have the leading bid."
            "SQUAD_FULL" -> context?.squadMax?.let { "Your squad is full ($it/$it)." } ?: "Your squad is full."
            "PURSE_EXCEEDED" -> context?.purseLeft?.let { "This bid is more than your remaining purse (${rupeeText(it)})." }
                ?: "This bid is more than your remaining purse."
            "RATE_LIMIT_EXCEEDED" -> "Too many bids too fast. Wait a moment and try again."
            "AUCTION_NO_PLAYER_OPEN", "AUCTION_NOT_IN_PROGRESS" -> "Bidding on this player has closed."
            else -> "Couldn't place that bid. Check your connection and try again."
        }
    }

    private fun runOrganizerAction(action: suspend () -> AuctionStateDto) {
        if (!_state.value.isOrganizer || _state.value.isActing) return
        _state.update { it.copy(isActing = true, actionError = null) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { newState ->
                    _state.update { it.copy(isActing = false) }
                    applyAuctionState(newState)
                }
                .onFailure { throwable -> _state.update { it.copy(isActing = false, actionError = actionErrorFor(throwable)) } }
        }
    }

    private fun actionErrorFor(throwable: Throwable): String = when ((throwable as? AuctionActionFailedException)?.code) {
        "AUCTION_NOT_READY" -> "The auction isn't ready to start. Check Auction settings and the player pool."
        "NO_BIDS_TO_SELL" -> "No bids yet. Mark the player Unsold instead."
        "NOTHING_TO_UNDO" -> "Nothing to undo."
        "AUCTION_PLAYER_ALREADY_OPEN" -> "Sell or mark the current player unsold first."
        "AUCTION_NOT_IN_PROGRESS", "AUCTION_ALREADY_STARTED" -> "The auction has moved on. The screen shows its latest state."
        else -> "That didn't go through. Check your connection and try again."
    }

    override fun onCleared() {
        loadJob?.cancel()
        streamJob?.cancel()
        connectionJob?.cancel()
        backOnlineJob?.cancel()
    }

    companion object {
        const val CONNECTION_GRACE_MS = 1_000L
        const val CONNECTION_LOST_AFTER_MS = 30_000L
        const val BACK_ONLINE_MS = 1_500L

        /** Same backoff as the web viewer's `useAuctionStream` (2/4/8/15s, then 15s). */
        val STREAM_RECONNECT_DELAYS_MS: List<Long> = listOf(2_000L, 4_000L, 8_000L, 15_000L)
    }
}

/** `15500.0` -> `"₹15,500"` (Indian grouping) -- for messages; the screen formats its own amounts. */
internal fun rupeeText(amount: Double): String {
    val whole = amountText(amount)
    val (intPart, fraction) = whole.split('.').let { it[0] to it.getOrNull(1) }
    val grouped = if (intPart.length <= 3) intPart else {
        val head = intPart.dropLast(3)
        head.reversed().chunked(2).joinToString(",").reversed() + "," + intPart.takeLast(3)
    }
    return "₹" + grouped + (fraction?.let { ".$it" } ?: "")
}

package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crichere.app.auth.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) {
    /** Current bid + the league's increment, or the base price before the first bid (the server's own rule). */
    val minimumNextBid: Double?
        get() {
            val auctionState = auction ?: return null
            if (auctionState.currentPlayerId == null) return null
            val current = auctionState.currentBidAmount
            return if (current != null) league?.auctionBidIncrement?.let { current + it } else league?.auctionBasePrice
        }

    val bidIncrement: Double? get() = league?.auctionBidIncrement

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
) : ViewModel() {

    private val _state = MutableStateFlow(AuctionState())
    val state: StateFlow<AuctionState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var streamJob: Job? = null

    fun retry() {
        loadJob?.cancel()
        streamJob?.cancel()
        _state.update { it.copy(isLoading = it.league == null, loadFailed = false, connectionLost = false) }

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
            }
        }
    }

    private fun startStream() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            auctionRepository.streamAuctionState(leagueId)
                .catch { _state.update { it.copy(isLoading = false, loadFailed = it.auction == null, connectionLost = it.auction != null) } }
                .collect { newState ->
                    _state.update { it.copy(isLoading = false, connectionLost = false) }
                    applyAuctionState(newState)
                }
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
    fun end() = runOrganizerAction { auctionRepository.end(leagueId) }
    fun toggleExceedPurse(allow: Boolean) = runOrganizerAction { auctionRepository.toggleExceedPurse(leagueId, allow) }

    fun placeBid() {
        val current = _state.value
        val franchiseId = current.myFranchiseId ?: return
        if (current.isBidding) return
        val amount = current.bidAmountInput.trim().toDoubleOrNull()
        val minimum = current.minimumNextBid
        when {
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

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

data class AuctionState(
    val isLoading: Boolean = true,
    val league: LeagueDto? = null,
    val isOrganizer: Boolean = false,
    /** The first franchise this signed-in user owns in this league, if any -- who they bid as. A user owning more than one franchise here (see docs/PHASE3.md's "dual roles allowed freely") always bids as the first one; picking among several is a future refinement, not needed for this MVP screen. */
    val myFranchiseId: String? = null,
    val auction: AuctionStateDto? = null,
    val results: AuctionResultsDto? = null,
    val bidAmountInput: String = "",
    val isActing: Boolean = false,
    val isBidding: Boolean = false,
    val errorMessage: String? = null,
)

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
        _state.update { it.copy(isLoading = true, errorMessage = null) }

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
            }.onFailure { throwable ->
                _state.update {
                    it.copy(isLoading = false, errorMessage = throwable.message ?: "Couldn't load this league. Please try again.")
                }
            }
        }
    }

    private fun startStream() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            auctionRepository.streamAuctionState(leagueId)
                .catch { throwable ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = throwable.message ?: "Lost connection to the live auction. Please retry.")
                    }
                }
                .collect { newState -> onAuctionState(newState) }
        }
    }

    private fun onAuctionState(newState: AuctionStateDto) {
        _state.update { it.copy(isLoading = false) }
        applyAuctionState(newState)
    }

    /**
     * Shared by the SSE path ([onAuctionState]) and every direct action response ([runOrganizerAction],
     * [placeBid]) so the auto-load-results-on-completion behavior fires no matter which of them
     * observes the COMPLETED transition first -- without this, the organizer whose own `sold`/`end`
     * call is what completes the auction would never see it (their own response bypasses the SSE
     * stream entirely), while every other connected client would.
     */
    private fun applyAuctionState(newState: AuctionStateDto) {
        _state.update { it.copy(auction = newState) }
        if (newState.auctionStatus == AuctionStatus.COMPLETED && _state.value.results == null) loadResults()
    }

    fun loadResults() {
        viewModelScope.launch {
            runCatching { auctionRepository.getResults(leagueId) }
                .onSuccess { results -> _state.update { it.copy(results = results) } }
                .onFailure { /* Non-fatal -- the live state above is still correct without it. */ }
        }
    }

    fun onBidAmountChanged(value: String) = _state.update { it.copy(bidAmountInput = value) }

    fun start() = runOrganizerAction { auctionRepository.start(leagueId) }
    fun nextPlayer() = runOrganizerAction { auctionRepository.nextPlayer(leagueId) }
    fun sold() = runOrganizerAction { auctionRepository.sold(leagueId) }
    fun unsold() = runOrganizerAction { auctionRepository.unsold(leagueId) }
    fun undo() = runOrganizerAction { auctionRepository.undo(leagueId) }
    fun end() = runOrganizerAction { auctionRepository.end(leagueId) }
    fun toggleExceedPurse(allow: Boolean) = runOrganizerAction { auctionRepository.toggleExceedPurse(leagueId, allow) }

    fun placeBid() {
        val franchiseId = _state.value.myFranchiseId ?: return
        if (_state.value.isBidding) return
        val amount = _state.value.bidAmountInput.toDoubleOrNull()
        if (amount == null) {
            _state.update { it.copy(errorMessage = "Enter a valid bid amount.") }
            return
        }

        _state.update { it.copy(isBidding = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { auctionRepository.placeBid(leagueId, PlaceBidRequestDto(franchiseId, amount)) }
                .onSuccess { newState ->
                    _state.update { it.copy(isBidding = false, bidAmountInput = "") }
                    applyAuctionState(newState)
                }
                .onFailure { throwable ->
                    _state.update { it.copy(isBidding = false, errorMessage = throwable.message ?: "That bid was rejected. Please try again.") }
                }
        }
    }

    private fun runOrganizerAction(action: suspend () -> AuctionStateDto) {
        if (!_state.value.isOrganizer || _state.value.isActing) return
        _state.update { it.copy(isActing = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { newState ->
                    _state.update { it.copy(isActing = false) }
                    applyAuctionState(newState)
                }
                .onFailure { throwable ->
                    _state.update { it.copy(isActing = false, errorMessage = throwable.message ?: "That action couldn't be completed. Please try again.") }
                }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        streamJob?.cancel()
    }
}

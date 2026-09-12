package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuctionSettingsState(
    val isLoading: Boolean = true,
    val league: LeagueDto? = null,
    val basePrice: String = "",
    val purse: String = "",
    val squadMin: String = "",
    val squadMax: String = "",
    val bidIncrement: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Auction Settings screen's ViewModel (see docs/PHASE4.md) -- loads the league (pre-filling the
 * five editable fields from whatever's already configured) and lets the organizer save all five
 * together. The pool/purse read view renders directly from the same loaded [LeagueDto.players]/
 * [LeagueDto.franchises]/`auctionPurse` -- no second fetch, no separate repository call.
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

    fun retry() = load()

    private fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { leagueRepository.getLeague(leagueId) }
                .onSuccess { league ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            league = league,
                            basePrice = league.auctionBasePrice?.toString() ?: "",
                            purse = league.auctionPurse?.toString() ?: "",
                            squadMin = league.auctionSquadMin?.toString() ?: "",
                            squadMax = league.auctionSquadMax?.toString() ?: "",
                            bidIncrement = league.auctionBidIncrement?.toString() ?: "",
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = throwable.message ?: "Couldn't load this league. Please try again.")
                    }
                }
        }
    }

    fun onBasePriceChanged(value: String) = _state.update { it.copy(basePrice = value) }
    fun onPurseChanged(value: String) = _state.update { it.copy(purse = value) }
    fun onSquadMinChanged(value: String) = _state.update { it.copy(squadMin = value) }
    fun onSquadMaxChanged(value: String) = _state.update { it.copy(squadMax = value) }
    fun onBidIncrementChanged(value: String) = _state.update { it.copy(bidIncrement = value) }

    /**
     * No client-side validation duplicated here (min<=max, positive numbers) -- same posture as
     * every other form in this app: the server is the single source of truth for validation, the
     * client just surfaces whatever error message comes back.
     */
    fun submit() {
        if (_state.value.isSaving) return
        val basePrice = _state.value.basePrice.toDoubleOrNull()
        val purse = _state.value.purse.toDoubleOrNull()
        val squadMin = _state.value.squadMin.toIntOrNull()
        val squadMax = _state.value.squadMax.toIntOrNull()
        val bidIncrement = _state.value.bidIncrement.toDoubleOrNull()
        if (basePrice == null || purse == null || squadMin == null || squadMax == null || bidIncrement == null) {
            _state.update { it.copy(errorMessage = "All five fields are required.") }
            return
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                leagueRepository.updateAuctionSettings(
                    leagueId,
                    AuctionSettingsSaveRequestDto(
                        basePrice = basePrice,
                        purse = purse,
                        squadMin = squadMin,
                        squadMax = squadMax,
                        bidIncrement = bidIncrement,
                    ),
                )
            }
                .onSuccess { _state.update { it.copy(isSaving = false) }; load() }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isSaving = false, errorMessage = throwable.message ?: "Couldn't save auction settings. Please try again.")
                    }
                }
        }
    }
}

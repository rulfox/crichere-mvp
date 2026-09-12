package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinLeagueState(
    val isLoading: Boolean = true,
    val league: LeagueDto? = null,
    /** Set once a screenshot has been uploaded -- present iff a proof screenshot has been attached this session. */
    val screenshotUrl: String? = null,
    val isUploadingScreenshot: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    /** Drives navigation back to League Detail on success. */
    val joined: Boolean = false,
)

/**
 * Join-as-player flow's ViewModel (see docs/PHASE3.md's Screens section). Loads the league to
 * read `playerFee`/`organizerUpiId`; a payment screenshot is required to submit only when
 * `playerFee` is set. Screenshot-only proof, no UPI auto-capture (see docs/PHASE3.md's Decisions
 * Made) -- the "Pay via UPI" action is pure platform chrome (launches a UPI app), not modeled
 * here at all.
 */
class JoinLeagueViewModel(
    private val leagueId: String,
    private val leagueRepository: LeagueRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinLeagueState())
    val state: StateFlow<JoinLeagueState> = _state.asStateFlow()

    fun retry() = load()

    private fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { leagueRepository.getLeague(leagueId) }
                .onSuccess { league -> _state.update { it.copy(isLoading = false, league = league) } }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = throwable.message ?: "Couldn't load this league. Please try again.")
                    }
                }
        }
    }

    fun uploadScreenshot(bytes: ByteArray, contentType: String, filename: String) {
        if (_state.value.isUploadingScreenshot) return
        _state.update { it.copy(isUploadingScreenshot = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val uploadInfo = leagueRepository.requestPaymentScreenshotUploadUrl(leagueId)
                leagueRepository.uploadPhoto(uploadInfo, bytes, contentType, filename)
            }
                .onSuccess { url -> _state.update { it.copy(isUploadingScreenshot = false, screenshotUrl = url) } }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isUploadingScreenshot = false, errorMessage = throwable.message ?: "Couldn't upload the screenshot. Please try again.")
                    }
                }
        }
    }

    fun submit() {
        val league = _state.value.league ?: return
        if (_state.value.isSubmitting) return
        if (league.playerFee != null && _state.value.screenshotUrl.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Attach a payment screenshot to join.") }
            return
        }

        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                playerRepository.join(leagueId, LeaguePlayerJoinRequestDto(paymentScreenshotUrl = _state.value.screenshotUrl))
            }
                .onSuccess { _state.update { it.copy(isSubmitting = false, joined = true) } }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isSubmitting = false, errorMessage = throwable.message ?: "Couldn't join this league. Please try again.")
                    }
                }
        }
    }
}

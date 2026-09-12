package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClaimFranchiseState(
    val isLoading: Boolean = true,
    val league: LeagueDto? = null,
    val name: String = "",
    val logoUrl: String? = null,
    val isUploadingLogo: Boolean = false,
    val screenshotUrl: String? = null,
    val isUploadingScreenshot: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    /** Drives navigation back to League Detail on success. */
    val claimed: Boolean = false,
)

/**
 * Claim-a-franchise flow's ViewModel -- same shape as [JoinLeagueViewModel], plus the franchise's
 * own [ClaimFranchiseState.name] (required) and optional logo. A payment screenshot is required
 * to submit only when the league's `franchiseFee` is set.
 */
class ClaimFranchiseViewModel(
    private val leagueId: String,
    private val leagueRepository: LeagueRepository,
    private val franchiseRepository: FranchiseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ClaimFranchiseState())
    val state: StateFlow<ClaimFranchiseState> = _state.asStateFlow()

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

    fun onNameChanged(name: String) {
        _state.update { it.copy(name = name) }
    }

    /**
     * The franchise doesn't exist yet at pick time, so there's no franchiseId to presign
     * against -- uploads against a self-scoped, randomly-keyed pending slot instead
     * ([LeagueRepository.requestPendingFranchiseLogoUploadUrl]) so the resulting URL can be
     * included directly in [submit]'s claim request body.
     */
    fun uploadLogo(bytes: ByteArray, contentType: String, filename: String) {
        if (_state.value.isUploadingLogo) return
        _state.update { it.copy(isUploadingLogo = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val uploadInfo = leagueRepository.requestPendingFranchiseLogoUploadUrl(leagueId)
                leagueRepository.uploadPhoto(uploadInfo, bytes, contentType, filename)
            }
                .onSuccess { url -> _state.update { it.copy(isUploadingLogo = false, logoUrl = url) } }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isUploadingLogo = false, errorMessage = throwable.message ?: "Couldn't upload the logo. Please try again.")
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
        if (_state.value.name.isBlank()) {
            _state.update { it.copy(errorMessage = "Franchise name is required.") }
            return
        }
        if (league.franchiseFee != null && _state.value.screenshotUrl.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Attach a payment screenshot to claim a franchise.") }
            return
        }

        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                franchiseRepository.claim(
                    leagueId,
                    LeagueFranchiseClaimRequestDto(
                        name = _state.value.name,
                        logoUrl = _state.value.logoUrl,
                        paymentScreenshotUrl = _state.value.screenshotUrl,
                    ),
                )
            }
                .onSuccess { _state.update { it.copy(isSubmitting = false, claimed = true) } }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isSubmitting = false, errorMessage = throwable.message ?: "Couldn't claim a franchise. Please try again.")
                    }
                }
        }
    }
}

package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClaimFranchiseState(
    val isLoading: Boolean = true,
    val league: LeagueDto? = null,
    /** True when the league itself couldn't be loaded (design G7) -- distinct from a failed claim. */
    val loadFailed: Boolean = false,
    val name: String = "",
    /** The name was cleared after typing, or a claim was attempted without one (design G1). */
    val nameError: Boolean = false,
    val logoUrl: String? = null,
    val isUploadingLogo: Boolean = false,
    /** Set once a screenshot has been uploaded -- present iff a proof screenshot has been attached this session. */
    val screenshotUrl: String? = null,
    val isUploadingScreenshot: Boolean = false,
    /** Fraction of the screenshot sent, 0..1 -- only meaningful while [isUploadingScreenshot]. */
    val uploadProgress: Float = 0f,
    val uploadFileName: String? = null,
    val uploadSizeBytes: Long? = null,
    /** The last screenshot upload failed and can be retried. */
    val uploadFailed: Boolean = false,
    val isSubmitting: Boolean = false,
    /** Short headline for a failed claim (design G6), e.g. "All franchise slots are taken."; [errorMessage] is the detail. */
    val errorTitle: String? = null,
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

    private var uploadJob: Job? = null
    private var pendingUpload: PendingUpload? = null
    private var logoJob: Job? = null

    fun retry() = load()

    private fun load() {
        _state.update { it.copy(isLoading = true, loadFailed = false, errorTitle = null, errorMessage = null) }
        viewModelScope.launch {
            runCatching { leagueRepository.getLeague(leagueId) }
                .onSuccess { league -> _state.update { it.copy(isLoading = false, league = league) } }
                .onFailure { _state.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }

    fun onNameChanged(name: String) {
        _state.update { it.copy(name = name, nameError = name.isBlank()) }
    }

    /**
     * The franchise doesn't exist yet at pick time, so there's no franchiseId to presign
     * against -- uploads against a self-scoped, randomly-keyed pending slot instead
     * ([LeagueRepository.requestPendingFranchiseLogoUploadUrl]) so the resulting URL can be
     * included directly in [submit]'s claim request body.
     */
    fun uploadLogo(bytes: ByteArray, contentType: String, filename: String) {
        logoJob?.cancel()
        _state.update { it.copy(isUploadingLogo = true, errorTitle = null, errorMessage = null) }
        logoJob = viewModelScope.launch {
            try {
                val uploadInfo = leagueRepository.requestPendingFranchiseLogoUploadUrl(leagueId)
                val url = leagueRepository.uploadPhoto(uploadInfo, bytes, contentType, filename)
                _state.update { it.copy(isUploadingLogo = false, logoUrl = url) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _state.update {
                    it.copy(isUploadingLogo = false, logoUrl = null, errorTitle = LOGO_FAILED_TITLE, errorMessage = CONNECTION_MESSAGE)
                }
            }
        }
    }

    /** Drops the logo (uploaded or in flight); the franchise falls back to its initials tile. */
    fun removeLogo() {
        logoJob?.cancel()
        logoJob = null
        _state.update { it.copy(isUploadingLogo = false, logoUrl = null) }
    }

    fun uploadScreenshot(bytes: ByteArray, contentType: String, filename: String) {
        if (_state.value.isUploadingScreenshot) return
        pendingUpload = PendingUpload(bytes, contentType, filename)
        _state.update {
            it.copy(
                isUploadingScreenshot = true,
                uploadProgress = 0f,
                uploadFileName = filename,
                uploadSizeBytes = bytes.size.toLong(),
                uploadFailed = false,
                errorTitle = null,
                errorMessage = null,
            )
        }
        uploadJob = viewModelScope.launch {
            try {
                val uploadInfo = leagueRepository.requestPaymentScreenshotUploadUrl(leagueId)
                val url = leagueRepository.uploadPhoto(uploadInfo, bytes, contentType, filename) { fraction ->
                    _state.update { it.copy(uploadProgress = fraction) }
                }
                pendingUpload = null
                _state.update { it.copy(isUploadingScreenshot = false, uploadProgress = 1f, screenshotUrl = url) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _state.update { it.copy(isUploadingScreenshot = false, uploadFailed = true) }
            }
        }
    }

    /** Re-sends the screenshot whose upload failed. */
    fun retryUpload() {
        val upload = pendingUpload ?: return
        uploadScreenshot(upload.bytes, upload.contentType, upload.filename)
    }

    /** Abandons an in-flight upload. */
    fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
        pendingUpload = null
        _state.update { it.copy(isUploadingScreenshot = false, uploadProgress = 0f, uploadFailed = false, uploadFileName = null, uploadSizeBytes = null) }
    }

    /** Detaches the screenshot (attached or failed), back to the empty picker. */
    fun removeScreenshot() {
        cancelUpload()
        _state.update { it.copy(screenshotUrl = null) }
    }

    fun submit() {
        val league = _state.value.league ?: return
        if (_state.value.isSubmitting || _state.value.isUploadingLogo) return
        if (_state.value.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        if (league.franchiseFee != null && _state.value.screenshotUrl.isNullOrBlank()) {
            _state.update { it.copy(errorTitle = null, errorMessage = "Attach a payment screenshot to claim a franchise.") }
            return
        }

        _state.update { it.copy(isSubmitting = true, errorTitle = null, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                franchiseRepository.claim(
                    leagueId,
                    LeagueFranchiseClaimRequestDto(
                        name = _state.value.name.trim(),
                        logoUrl = _state.value.logoUrl,
                        paymentScreenshotUrl = _state.value.screenshotUrl,
                    ),
                )
            }
                .onSuccess { _state.update { it.copy(isSubmitting = false, claimed = true) } }
                .onFailure { throwable ->
                    val (title, detail) = claimFailureMessage((throwable as? LeagueFranchiseActionFailedException)?.code)
                    _state.update { it.copy(isSubmitting = false, errorTitle = title, errorMessage = detail) }
                }
        }
    }

    private class PendingUpload(val bytes: ByteArray, val contentType: String, val filename: String)

    private companion object {
        const val LOGO_FAILED_TITLE = "Couldn't upload the logo."
        const val CONNECTION_MESSAGE = "Check your connection and try again."
    }
}

/** Friendly copy for a failed claim, keyed by the backend's problem `code`. */
internal fun claimFailureMessage(code: String?): Pair<String, String> = when (code) {
    "CAPACITY_FULL" -> "All franchise slots are taken." to "This league has no franchises left to claim."
    "LEAGUE_COMPLETED" -> "This league has ended." to "It isn't taking new franchises."
    "RATE_LIMIT_EXCEEDED" -> "Too many attempts." to "Wait a few minutes, then try again."
    else -> "Couldn't claim a franchise." to "Check your connection and try again."
}

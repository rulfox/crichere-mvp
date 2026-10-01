package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crichere.app.profile.BattingStyle
import com.crichere.app.profile.PlayingRole
import com.crichere.app.profile.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinLeagueState(
    val isLoading: Boolean = true,
    val league: LeagueDto? = null,
    /** True when the league itself couldn't be loaded (design F8) -- distinct from a failed join. */
    val loadFailed: Boolean = false,
    /** Set once a screenshot has been uploaded -- present iff a proof screenshot has been attached this session. */
    val screenshotUrl: String? = null,
    val isUploadingScreenshot: Boolean = false,
    /** Fraction of the screenshot sent, 0..1 -- only meaningful while [isUploadingScreenshot]. */
    val uploadProgress: Float = 0f,
    val uploadFileName: String? = null,
    val uploadSizeBytes: Long? = null,
    /** The last screenshot upload failed and can be retried (design F5). */
    val uploadFailed: Boolean = false,
    val isSubmitting: Boolean = false,
    /** Short headline for a failed join (design F7), e.g. "Registration is full."; [errorMessage] is the detail. */
    val errorTitle: String? = null,
    val errorMessage: String? = null,
    /** Drives navigation back to League Detail on success. */
    val joined: Boolean = false,
    /** Who's joining, for the free-league summary (design F6). `null` until the profile loads. */
    val viewerName: String? = null,
    val viewerRole: PlayingRole? = null,
    val viewerBatting: BattingStyle? = null,
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
    /** Optional: only feeds the "Joining as" summary on free leagues. */
    private val profileRepository: ProfileRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinLeagueState())
    val state: StateFlow<JoinLeagueState> = _state.asStateFlow()

    private var uploadJob: Job? = null
    private var pendingUpload: PendingUpload? = null

    fun retry() = load()

    private fun load() {
        _state.update { it.copy(isLoading = true, loadFailed = false, errorTitle = null, errorMessage = null) }
        viewModelScope.launch {
            runCatching { leagueRepository.getLeague(leagueId) }
                .onSuccess { league -> _state.update { it.copy(isLoading = false, league = league) } }
                .onFailure { _state.update { it.copy(isLoading = false, loadFailed = true) } }
        }
        profileRepository?.let { repository ->
            viewModelScope.launch {
                runCatching { repository.getProfile() }.getOrNull()?.let { profile ->
                    _state.update {
                        it.copy(viewerName = profile.name, viewerRole = profile.playingRole, viewerBatting = profile.battingStyle)
                    }
                }
            }
        }
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
        if (_state.value.isSubmitting) return
        if (league.playerFee != null && _state.value.screenshotUrl.isNullOrBlank()) {
            _state.update { it.copy(errorTitle = null, errorMessage = "Attach a payment screenshot to join.") }
            return
        }

        _state.update { it.copy(isSubmitting = true, errorTitle = null, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                playerRepository.join(leagueId, LeaguePlayerJoinRequestDto(paymentScreenshotUrl = _state.value.screenshotUrl))
            }
                .onSuccess { _state.update { it.copy(isSubmitting = false, joined = true) } }
                .onFailure { throwable ->
                    val (title, detail) = joinFailureMessage((throwable as? LeaguePlayerActionFailedException)?.code)
                    _state.update { it.copy(isSubmitting = false, errorTitle = title, errorMessage = detail) }
                }
        }
    }

    private class PendingUpload(val bytes: ByteArray, val contentType: String, val filename: String)
}

/** Friendly copy for a failed join/claim, keyed by the backend's problem `code`. */
internal fun joinFailureMessage(code: String?): Pair<String, String> = when (code) {
    "CAPACITY_FULL" -> "Registration is full." to "This league isn't taking more players."
    "LEAGUE_COMPLETED" -> "This league has ended." to "It isn't taking new players."
    "ALREADY_JOINED" -> "You've already joined." to "You're on this league's player list."
    "RATE_LIMIT_EXCEEDED" -> "Too many attempts." to "Wait a few minutes, then try again."
    else -> "Couldn't join." to "Check your connection and try again."
}

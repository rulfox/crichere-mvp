package com.crichere.app.parity

import androidx.compose.runtime.Composable
import com.crichere.app.league.ClaimFranchiseState
import com.crichere.app.league.ClaimFranchiseViewModel
import com.crichere.app.league.FakeFranchiseRepository
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.league.LeagueDto
import com.crichere.app.ui.ClaimFranchiseScreen

internal fun claimState(
    league: LeagueDto? = ParityFixtures.league(),
    name: String = "",
    nameError: Boolean = false,
    logo: Boolean = false,
    uploadingLogo: Boolean = false,
    uploading: Float? = null,
    attached: Boolean = false,
    failed: Boolean = false,
    submitting: Boolean = false,
    errorTitle: String? = null,
    error: String? = null,
    loadFailed: Boolean = false,
) = ClaimFranchiseState(
    isLoading = false, league = league, loadFailed = loadFailed, name = name, nameError = nameError,
    logoUrl = if (logo) SamplePhoto.url else null, isUploadingLogo = uploadingLogo,
    screenshotUrl = if (attached) SamplePhoto.url else null, isUploadingScreenshot = uploading != null,
    uploadProgress = uploading ?: 0f, uploadFileName = "payment-proof.jpg", uploadSizeBytes = 397_000,
    uploadFailed = failed, isSubmitting = submitting, errorTitle = errorTitle, errorMessage = error,
)

internal fun claim(state: ClaimFranchiseState): @Composable () -> Unit = {
    val viewModel = ClaimFranchiseViewModel("league-kpl", FakeLeagueRepository(), FakeFranchiseRepository())
    ClaimFranchiseScreen(state = state, viewModel = viewModel, onCancel = {})
}

fun claimBoards(): Map<String, @Composable () -> Unit> = mapOf(
    "G1" to claim(claimState()),
    "G1-error" to claim(claimState(nameError = true)),
    "G1-initials" to claim(claimState(name = "Kolhapur Kings")),
    "G3" to claim(claimState(name = "Kolhapur Kings", logo = true, attached = true)),
    "G3-uploading" to claim(claimState(name = "Kolhapur Kings", logo = true, uploadingLogo = true, uploading = 0.42f)),
    "G3-failed" to claim(claimState(name = "Kolhapur Kings", failed = true)),
    "G4" to claim(claimState(ParityFixtures.league(franchiseFee = null), name = "Kolhapur Kings")),
    "G5" to claim(claimState(name = "Kolhapur Kings", logo = true, attached = true, submitting = true)),
    "G6" to claim(claimState(name = "Kolhapur Kings", attached = true, errorTitle = "All franchise slots are taken.",
        error = "Another team claimed the last spot. Ask the organizer if more franchises open.")),
    "G7" to claim(claimState(null, loadFailed = true)),
    "G9" to claim(claimState(ParityFixtures.league(upiId = null), name = "Kolhapur Kings")),
)

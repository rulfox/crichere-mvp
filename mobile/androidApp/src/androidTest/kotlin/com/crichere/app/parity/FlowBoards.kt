package com.crichere.app.parity

import androidx.compose.runtime.Composable
import com.crichere.app.account.DeleteAccountDialogState
import com.crichere.app.account.DeleteAccountScreenState
import com.crichere.app.account.DeletionBlockReason
import com.crichere.app.account.DeletionBlockerDto
import com.crichere.app.account.DeletionHandoverDto
import com.crichere.app.league.AuctionField
import com.crichere.app.league.AuctionSaveError
import com.crichere.app.league.AuctionSettingsState
import com.crichere.app.league.AuctionSettingsViewModel
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.league.FakePlayerRepository
import com.crichere.app.league.FakeRoleRepository
import com.crichere.app.league.JoinLeagueState
import com.crichere.app.league.JoinLeagueViewModel
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueRoleDto
import com.crichere.app.league.ManageRolesState
import com.crichere.app.league.ManageRolesViewModel
import com.crichere.app.league.RoleLookupResultDto
import com.crichere.app.league.RoleNotice
import com.crichere.app.profile.BattingStyle
import com.crichere.app.profile.PlayingRole
import com.crichere.app.ui.AuctionSettingsScreen
import com.crichere.app.ui.DeleteAccountConfirmDialog
import com.crichere.app.ui.DeleteAccountScreen
import com.crichere.app.ui.JoinLeagueScreen
import com.crichere.app.ui.ManageRolesScreen

// F Join as player

internal fun joinState(league: LeagueDto? = ParityFixtures.league(), uploading: Float? = null, attached: Boolean = false, failed: Boolean = false,
                      errorTitle: String? = null, error: String? = null, loadFailed: Boolean = false) = JoinLeagueState(
    isLoading = false, league = league, loadFailed = loadFailed, screenshotUrl = if (attached) SamplePhoto.url else null,
    isUploadingScreenshot = uploading != null, uploadProgress = uploading ?: 0f, uploadFileName = "payment-proof.jpg", uploadSizeBytes = 421_000,
    uploadFailed = failed, isSubmitting = false, errorTitle = errorTitle, errorMessage = error, joined = false,
    viewerName = "Aarav Pawar", viewerRole = PlayingRole.ALL_ROUNDER, viewerBatting = BattingStyle.RIGHT_HAND,
)

internal fun join(state: JoinLeagueState): @Composable () -> Unit = {
    JoinLeagueScreen(state = state, viewModel = JoinLeagueViewModel("league-kpl", FakeLeagueRepository(), FakePlayerRepository()), onCancel = {})
}

fun joinBoards(): Map<String, @Composable () -> Unit> = mapOf(
    "F1" to join(joinState()),
    "F5" to join(joinState(uploading = 0.42f)),
    "F5-attached" to join(joinState(attached = true)),
    "F5-failed" to join(joinState(failed = true)),
    "F6" to join(joinState(ParityFixtures.league(playerFee = null))),
    "F7" to join(joinState(attached = true, errorTitle = "Couldn't join", error = "Registration just filled up. Ask the organizer if a spot opens.")),
    "F9" to join(joinState(ParityFixtures.league(upiId = null))),
    "F-error" to join(joinState(null, loadFailed = true)),
)

// J Auction settings

internal fun auctionState(squadMin: String = "8", squadMax: String = "12", scheduledAt: String? = "2026-10-11T13:30:00Z", scheduleError: String? = null,
                         passed: Boolean = false, touched: Set<AuctionField> = emptySet(), saving: Boolean = false, saveError: AuctionSaveError? = null,
                         saved: Boolean = false, loadFailed: Boolean = false) = AuctionSettingsState(
    isLoading = false, loadFailed = loadFailed, league = if (loadFailed) null else ParityFixtures.league(), basePrice = "1000", purse = "100000",
    squadMin = squadMin, squadMax = squadMax, bidIncrement = "500", scheduledAt = scheduledAt, scheduledAtError = scheduleError,
    scheduledAtPassed = passed, touched = touched, isSaving = saving, saveError = saveError, showSavedNotice = saved,
)

internal fun auction(state: AuctionSettingsState): @Composable () -> Unit = {
    AuctionSettingsScreen(state = state, viewModel = AuctionSettingsViewModel("league-kpl", FakeLeagueRepository()), onBack = {})
}

fun auctionSettingsBoards(): Map<String, @Composable () -> Unit> = mapOf(
    "J1" to auction(auctionState(scheduledAt = null)),
    "J1-scheduled" to auction(auctionState()),
    "J2" to auction(auctionState(squadMin = "14", squadMax = "25", touched = setOf(AuctionField.SquadMin, AuctionField.SquadMax))),
    "J3" to auction(auctionState(saving = true)),
    "J4" to auction(auctionState(saved = true)),
    "J5" to auction(auctionState(saveError = AuctionSaveError("Couldn't save auction settings. Check your connection.", canRetry = true))),
    "J9" to auction(auctionState(scheduledAt = "2026-09-01T13:30:00Z", passed = true)),
    "J12" to auction(auctionState(scheduleError = "Pick a time after the league's registrations close.")),
    "J-error" to auction(auctionState(loadFailed = true)),
)

// K Co-organizers

private val coOrganizers = listOf(
    LeagueRoleDto(id = "r1", userId = "u-me", name = "Nikhil Desai", grantedAt = "2026-09-20T10:00:00Z"),
    LeagueRoleDto(id = "r2", userId = "u-2", name = "Pranav More", grantedAt = "2026-09-21T10:00:00Z"),
)

internal fun rolesState(phone: String = "", lookingUp: Boolean = false, found: Boolean = false, granting: Boolean = false, lookupError: String? = null,
                       grantError: RoleNotice? = null, revoking: Set<String> = emptySet(), roles: List<LeagueRoleDto> = coOrganizers,
                       loadFailed: Boolean = false) = ManageRolesState(
    isLoading = false, loadFailed = loadFailed, league = if (loadFailed) null else ParityFixtures.league(coOrganizers = roles),
    phoneNumberInput = phone, isLookingUp = lookingUp, lookupResult = if (found) RoleLookupResultDto("u-9", "Omkar Shinde") else null,
    lookupError = lookupError, isGranting = granting, grantError = grantError, revokingRoleIds = revoking, currentUserId = "u-me",
)

internal fun roles(state: ManageRolesState): @Composable () -> Unit = {
    ManageRolesScreen(state = state, viewModel = ManageRolesViewModel("league-kpl", FakeLeagueRepository(), FakeRoleRepository()), onBack = {})
}

fun rolesBoards(): Map<String, @Composable () -> Unit> = mapOf(
    "K1" to roles(rolesState(roles = emptyList())),
    "K2" to roles(rolesState(phone = "9876543210", lookingUp = true)),
    "K4" to roles(rolesState(phone = "9876543210", found = true)),
    "K4-granting" to roles(rolesState(phone = "9876543210", found = true, granting = true)),
    "K5" to roles(rolesState(revoking = setOf("r2"))),
    "K6" to roles(rolesState(phone = "9876543210", lookupError = "No Crichere account uses this number.")),
    "K8" to roles(rolesState(phone = "9876543210", found = true, grantError = RoleNotice("Couldn't grant access", "Check your connection and try again."))),
    "K-error" to roles(rolesState(loadFailed = true)),
)

// AD Account deletion (D2-D6; AD because D* is the Dashboard)

private val canDelete = DeleteAccountScreenState.CanDelete(
    erasesOn = "2026-12-06T10:00:00Z",
    handovers = listOf(DeletionHandoverDto("league-kpl", "Kolhapur Premier League", null, "u2", "Nikhil Desai")),
)

private fun deletion(screen: DeleteAccountScreenState): @Composable () -> Unit = {
    DeleteAccountScreen(screen = screen, onBack = {}, onOpenLeague = {}, onRetry = {}, onDelete = {})
}

private fun confirm(input: String, submitting: Boolean = false, failed: Boolean = false): @Composable () -> Unit = {
    DeleteAccountScreen(screen = canDelete, onBack = {}, onOpenLeague = {}, onRetry = {}, onDelete = {})
    DeleteAccountConfirmDialog(DeleteAccountDialogState(input, submitting, failed), "6 Dec 2026", {}, {}, {})
}

fun deletionBoards(): Map<String, @Composable () -> Unit> = mapOf(
    "AD2" to deletion(DeleteAccountScreenState.Loading),
    "AD3" to deletion(DeleteAccountScreenState.CanDelete("2026-12-06T10:00:00Z", emptyList())),
    "AD3b" to deletion(canDelete),
    "AD4" to deletion(DeleteAccountScreenState.Blocked(listOf(
        DeletionBlockerDto("league-kpl", "Kolhapur Premier League", DeletionBlockReason.AUCTION_LIVE),
        DeletionBlockerDto("league-scl", "Sangli Champions League", DeletionBlockReason.NO_CO_ORGANIZER),
    ))),
    "AD5" to deletion(DeleteAccountScreenState.Error),
    "AD6a" to confirm(""),
    "AD6b" to confirm("delete"),
    "AD6c" to confirm("DELETE"),
    "AD6d" to confirm("DELETE", submitting = true),
    "AD6e" to confirm("DELETE", failed = true),
)

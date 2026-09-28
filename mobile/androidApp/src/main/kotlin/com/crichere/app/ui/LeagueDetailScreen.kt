package com.crichere.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.league.LeagueAwardDto
import com.crichere.app.league.LeagueDetailState
import com.crichere.app.league.LeagueDetailViewModel
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueFranchiseDto
import com.crichere.app.league.LeaguePlayerDto
import com.crichere.app.league.LeagueStatus
import com.crichere.app.network.webViewerBaseUrl
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Resolves [LeagueDetailViewModel] via Koin, parameterized on [leagueId] -- see `AuthNavHost`'s `MainDestination.LeagueDetail`. */
@Composable
internal fun LeagueDetailRoute(
    leagueId: String,
    onBack: () -> Unit,
    onEditLeague: (String) -> Unit,
    onJoinLeague: (String) -> Unit,
    onClaimFranchise: (String) -> Unit,
    onViewScreenshot: (String) -> Unit,
    onAuctionSettings: (String) -> Unit,
    onAuctionLive: (String) -> Unit,
    onManageRoles: (String) -> Unit,
    viewModel: LeagueDetailViewModel = koinViewModel(key = "league-detail:$leagueId") { parametersOf(leagueId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The ViewModel survives leaving and re-entering this screen for the same leagueId (see its
    // doc), so a fresh fetch on every visit is what makes an edit just saved (e.g. this session's
    // real bug: a removed/renamed award still showing here) actually show up.
    LaunchedEffect(Unit) { viewModel.retry() }

    LeagueDetailScreen(
        state = state,
        onBack = onBack,
        onEditLeague = { onEditLeague(leagueId) },
        onMarkCompleted = viewModel::markCompleted,
        onRetry = viewModel::retry,
        onAuctionSettings = { onAuctionSettings(leagueId) },
        onAuctionLive = { onAuctionLive(leagueId) },
        onManageRoles = { onManageRoles(leagueId) },
        onJoinLeague = { onJoinLeague(leagueId) },
        onClaimFranchise = { onClaimFranchise(leagueId) },
        onToggleFollow = viewModel::toggleFollow,
        onViewScreenshot = onViewScreenshot,
        onRequestLeaveAsPlayer = viewModel::requestLeaveAsPlayer,
        onRequestLeaveAsFranchise = viewModel::requestLeaveAsFranchise,
        onRemovePlayer = viewModel::removePlayer,
        onRemoveFranchise = viewModel::removeFranchise,
        onApprovePlayerLeave = viewModel::approvePlayerLeave,
        onDismissPlayerLeave = viewModel::dismissPlayerLeave,
        onApproveFranchiseLeave = viewModel::approveFranchiseLeave,
        onDismissFranchiseLeave = viewModel::dismissFranchiseLeave,
    )
}

/**
 * League Detail: read view of a single league (ground/schedule/format/capacity/fees/awards),
 * organizer-only Edit/Mark-completed actions, and (Phase 3) Join/Claim/Follow/Share actions plus
 * the Players/Franchises rosters with organizer-only Remove and leave-request approve/dismiss --
 * see docs/PHASE3.md. Content-only, same as [LeagueDashboardScreen] -- rendered above
 * `MainRoute`'s tab `Scaffold`, not inside it.
 */
@Composable
private fun LeagueDetailScreen(
    state: LeagueDetailState,
    onBack: () -> Unit,
    onEditLeague: () -> Unit,
    onMarkCompleted: () -> Unit,
    onRetry: () -> Unit,
    onAuctionSettings: () -> Unit,
    onAuctionLive: () -> Unit,
    onManageRoles: () -> Unit,
    onJoinLeague: () -> Unit,
    onClaimFranchise: () -> Unit,
    onToggleFollow: () -> Unit,
    onViewScreenshot: (String) -> Unit,
    onRequestLeaveAsPlayer: (String) -> Unit,
    onRequestLeaveAsFranchise: (String) -> Unit,
    onRemovePlayer: (String) -> Unit,
    onRemoveFranchise: (String) -> Unit,
    onApprovePlayerLeave: (String) -> Unit,
    onDismissPlayerLeave: (String) -> Unit,
    onApproveFranchiseLeave: (String) -> Unit,
    onDismissFranchiseLeave: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("Back") }

        val league = state.league
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            league == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = state.errorMessage ?: "Couldn't load this league.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
            }

            else -> LeagueDetailContent(
                league = league,
                state = state,
                onEditLeague = onEditLeague,
                onMarkCompleted = onMarkCompleted,
                onAuctionSettings = onAuctionSettings,
                onAuctionLive = onAuctionLive,
                onManageRoles = onManageRoles,
                onJoinLeague = onJoinLeague,
                onClaimFranchise = onClaimFranchise,
                onToggleFollow = onToggleFollow,
                onViewScreenshot = onViewScreenshot,
                onRequestLeaveAsPlayer = onRequestLeaveAsPlayer,
                onRequestLeaveAsFranchise = onRequestLeaveAsFranchise,
                onRemovePlayer = onRemovePlayer,
                onRemoveFranchise = onRemoveFranchise,
                onApprovePlayerLeave = onApprovePlayerLeave,
                onDismissPlayerLeave = onDismissPlayerLeave,
                onApproveFranchiseLeave = onApproveFranchiseLeave,
                onDismissFranchiseLeave = onDismissFranchiseLeave,
            )
        }
    }
}

@Composable
private fun LeagueDetailContent(
    league: LeagueDto,
    state: LeagueDetailState,
    onEditLeague: () -> Unit,
    onMarkCompleted: () -> Unit,
    onAuctionSettings: () -> Unit,
    onAuctionLive: () -> Unit,
    onManageRoles: () -> Unit,
    onJoinLeague: () -> Unit,
    onClaimFranchise: () -> Unit,
    onToggleFollow: () -> Unit,
    onViewScreenshot: (String) -> Unit,
    onRequestLeaveAsPlayer: (String) -> Unit,
    onRequestLeaveAsFranchise: (String) -> Unit,
    onRemovePlayer: (String) -> Unit,
    onRemoveFranchise: (String) -> Unit,
    onApprovePlayerLeave: (String) -> Unit,
    onDismissPlayerLeave: (String) -> Unit,
    onApproveFranchiseLeave: (String) -> Unit,
    onDismissFranchiseLeave: (String) -> Unit,
) {
    val context = LocalContext.current
    val isOrganizer = state.isOrganizer
    val currentUserId = state.currentUserId
    val myPlayerRow = league.players.firstOrNull { it.userId == currentUserId }
    val playersRequired = league.playersRequired
    val isFull = playersRequired != null && league.players.size >= playersRequired

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = league.name, style = MaterialTheme.typography.headlineSmall)
                Text(text = "${league.city}, ${league.district}, ${league.state}")
                Text(text = "Starts ${league.startsOn}" + (league.format?.let { " -- $it" } ?: ""))
                Text(text = if (league.status == LeagueStatus.COMPLETED) "Completed" else "Announced")
                league.groundName?.let { Text("Ground: $it") }
                league.description?.let { Text(it) }
            }
        }

        if (league.franchisesRequired != null || league.playersRequired != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "Capacity", style = MaterialTheme.typography.titleMedium)
                    league.franchisesRequired?.let { Text("Franchises: $it") }
                    league.playersRequired?.let { Text("Players: $it") }
                }
            }
        }

        if (league.franchiseFee != null || league.playerFee != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "Fees", style = MaterialTheme.typography.titleMedium)
                    league.franchiseFee?.let { Text("Franchise fee: $it") }
                    league.playerFee?.let { Text("Player fee: $it") }
                }
            }
        }

        // Join / Claim / Follow / Share -- shown to everyone (organizer included, minus the
        // Join/Claim actions themselves, which are for non-organizers to participate in someone
        // else's league). See docs/PHASE3.md's Screens section.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isOrganizer) {
                    when {
                        league.status == LeagueStatus.COMPLETED -> Text("League completed", style = MaterialTheme.typography.labelLarge)
                        myPlayerRow != null -> OutlinedButton(
                            onClick = { onRequestLeaveAsPlayer(myPlayerRow.id) },
                            enabled = !state.isLeaveRequesting && myPlayerRow.leaveRequestedAt == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (myPlayerRow.leaveRequestedAt != null) "Leave requested" else "Request to leave") }
                        isFull -> Text("Registration full", style = MaterialTheme.typography.labelLarge)
                        else -> Button(onClick = onJoinLeague, modifier = Modifier.fillMaxWidth()) { Text("Join as Player") }
                    }

                    if (league.status != LeagueStatus.COMPLETED) {
                        val franchisesRequired = league.franchisesRequired
                        val franchisesFull = franchisesRequired != null && league.franchises.size >= franchisesRequired
                        if (franchisesFull) {
                            Text("Registration full", style = MaterialTheme.typography.labelLarge)
                        } else {
                            OutlinedButton(onClick = onClaimFranchise, modifier = Modifier.fillMaxWidth()) { Text("Claim a Franchise") }
                        }
                    }
                }

                OutlinedButton(onClick = onToggleFollow, enabled = !state.isTogglingFollow, modifier = Modifier.fillMaxWidth()) {
                    Text(if (league.isFollowing) "Following" else "Follow")
                }

                OutlinedButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Join my league on Crichere: crichere://leagues/${league.id}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share league"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Share") }

                // Additive to the crichere:// share above, not a replacement (docs/PHASE6.md) --
                // for the audience that link can't reach: someone without the app installed. Copies
                // to the clipboard rather than opening a second share sheet, matching the actual use
                // case (pasting into a WhatsApp group).
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("Watch link", "$webViewerBaseUrl/leagues/${league.id}"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Copy watch link") }

                // Visible to everyone, not just the organizer -- the organizer sees Start/manage
                // controls inside this screen once opened, everyone else can watch/bid once an
                // auction is running (see docs/PHASE5.md's Decisions Made: the live auction is
                // public like the rest of the league).
                OutlinedButton(onClick = onAuctionLive, modifier = Modifier.fillMaxWidth()) {
                    Text("Live Auction")
                }
            }
        }

        if (league.players.isNotEmpty()) {
            item { Text(text = "Players", style = MaterialTheme.typography.titleMedium) }
            items(league.players, key = { "player-${it.id}" }) { player ->
                PlayerRow(
                    player = player,
                    isOrganizer = isOrganizer,
                    isRemoving = player.id in state.removingIds,
                    isRespondingToLeaveRequest = player.id in state.respondingToLeaveRequestIds,
                    onViewScreenshot = onViewScreenshot,
                    onRemove = { onRemovePlayer(player.id) },
                    onApproveLeave = { onApprovePlayerLeave(player.id) },
                    onDismissLeave = { onDismissPlayerLeave(player.id) },
                )
            }
        }

        if (league.franchises.isNotEmpty()) {
            item { Text(text = "Franchises", style = MaterialTheme.typography.titleMedium) }
            items(league.franchises, key = { "franchise-${it.id}" }) { franchise ->
                FranchiseRow(
                    franchise = franchise,
                    isOrganizer = isOrganizer,
                    isOwnFranchise = !isOrganizer && franchise.ownerUserId == currentUserId,
                    isRemoving = franchise.id in state.removingIds,
                    isRespondingToLeaveRequest = franchise.id in state.respondingToLeaveRequestIds,
                    isLeaveRequesting = state.isLeaveRequesting,
                    onViewScreenshot = onViewScreenshot,
                    onRemove = { onRemoveFranchise(franchise.id) },
                    onApproveLeave = { onApproveFranchiseLeave(franchise.id) },
                    onDismissLeave = { onDismissFranchiseLeave(franchise.id) },
                    onRequestLeave = { onRequestLeaveAsFranchise(franchise.id) },
                )
            }
        }

        if (league.awards.isNotEmpty()) {
            item { Text(text = "Awards", style = MaterialTheme.typography.titleMedium) }
            items(league.awards, key = LeagueAwardDto::id) { award ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = award.name, style = MaterialTheme.typography.titleSmall)
                        award.cashAmount?.let { Text("Cash: $it") }
                        if (award.hasTrophy) Text("Trophy")
                    }
                }
            }
        }

        if (isOrganizer) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val errorMessage = state.errorMessage
                    if (errorMessage != null) {
                        Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = onEditLeague, modifier = Modifier.fillMaxWidth()) {
                        Text("Edit league")
                    }
                    OutlinedButton(onClick = onAuctionSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Auction settings")
                    }
                    OutlinedButton(onClick = onManageRoles, modifier = Modifier.fillMaxWidth()) {
                        Text("Manage co-organizers")
                    }
                    if (league.status != LeagueStatus.COMPLETED) {
                        OutlinedButton(onClick = onMarkCompleted, enabled = !state.isCompleting, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.isCompleting) "Marking completed..." else "Mark completed")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(
    player: LeaguePlayerDto,
    isOrganizer: Boolean,
    isRemoving: Boolean,
    isRespondingToLeaveRequest: Boolean,
    onViewScreenshot: (String) -> Unit,
    onRemove: () -> Unit,
    onApproveLeave: () -> Unit,
    onDismissLeave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = player.name ?: player.userId, style = MaterialTheme.typography.titleSmall)
            player.paymentScreenshotUrl?.let { url ->
                TextButton(onClick = { onViewScreenshot(url) }) { Text("View payment screenshot") }
            }
            if (isOrganizer) {
                if (player.leaveRequestedAt != null) {
                    Text("Requested to leave", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onApproveLeave, enabled = !isRespondingToLeaveRequest) { Text("Approve leave") }
                        OutlinedButton(onClick = onDismissLeave, enabled = !isRespondingToLeaveRequest) { Text("Dismiss") }
                    }
                }
                OutlinedButton(onClick = onRemove, enabled = !isRemoving, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isRemoving) "Removing..." else "Remove")
                }
            }
        }
    }
}

@Composable
private fun FranchiseRow(
    franchise: LeagueFranchiseDto,
    isOrganizer: Boolean,
    isOwnFranchise: Boolean,
    isRemoving: Boolean,
    isRespondingToLeaveRequest: Boolean,
    isLeaveRequesting: Boolean,
    onViewScreenshot: (String) -> Unit,
    onRemove: () -> Unit,
    onApproveLeave: () -> Unit,
    onDismissLeave: () -> Unit,
    onRequestLeave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = franchise.name, style = MaterialTheme.typography.titleSmall)
            Text(text = "Owner: ${franchise.ownerName ?: franchise.ownerUserId}")
            franchise.paymentScreenshotUrl?.let { url ->
                TextButton(onClick = { onViewScreenshot(url) }) { Text("View payment screenshot") }
            }
            if (isOrganizer) {
                if (franchise.leaveRequestedAt != null) {
                    Text("Requested to leave", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onApproveLeave, enabled = !isRespondingToLeaveRequest) { Text("Approve leave") }
                        OutlinedButton(onClick = onDismissLeave, enabled = !isRespondingToLeaveRequest) { Text("Dismiss") }
                    }
                }
                OutlinedButton(onClick = onRemove, enabled = !isRemoving, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isRemoving) "Removing..." else "Remove")
                }
            } else if (isOwnFranchise) {
                OutlinedButton(
                    onClick = onRequestLeave,
                    enabled = !isLeaveRequesting && franchise.leaveRequestedAt == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (franchise.leaveRequestedAt != null) "Leave requested" else "Request to leave") }
            }
        }
    }
}

package com.crichere.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.crichere.app.R
import com.crichere.app.league.LeagueAwardDto
import com.crichere.app.league.LeagueDetailState
import com.crichere.app.league.LeagueDetailViewModel
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueFranchiseDto
import com.crichere.app.league.LeaguePlayerDto
import com.crichere.app.league.LeagueStatus
import com.crichere.app.network.webViewerBaseUrl
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereDisabledContainer
import com.crichere.app.ui.theme.CrichereErrorStrong
import com.crichere.app.ui.theme.CrichereInkDisabled
import com.crichere.app.ui.theme.CrichereInkSubtle
import com.crichere.app.ui.theme.InstrumentSansFamily
import com.crichere.app.ui.theme.JetBrainsMonoFamily
import com.crichere.app.ui.theme.LocalCrichereExtraColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
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
    // doc), so a fresh fetch on every visit is what makes an edit just saved actually show up.
    LaunchedEffect(Unit) { viewModel.retry() }

    LeagueDetailScreen(
        state = state,
        actions = LeagueDetailActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onEditLeague = { onEditLeague(leagueId) },
            onMarkCompleted = viewModel::markCompleted,
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
        ),
    )
}

private class LeagueDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onEditLeague: () -> Unit,
    val onMarkCompleted: () -> Unit,
    val onAuctionSettings: () -> Unit,
    val onAuctionLive: () -> Unit,
    val onManageRoles: () -> Unit,
    val onJoinLeague: () -> Unit,
    val onClaimFranchise: () -> Unit,
    val onToggleFollow: () -> Unit,
    val onViewScreenshot: (String) -> Unit,
    val onRequestLeaveAsPlayer: (String) -> Unit,
    val onRequestLeaveAsFranchise: (String) -> Unit,
    val onRemovePlayer: (String) -> Unit,
    val onRemoveFranchise: (String) -> Unit,
    val onApprovePlayerLeave: (String) -> Unit,
    val onDismissPlayerLeave: (String) -> Unit,
    val onApproveFranchiseLeave: (String) -> Unit,
    val onDismissFranchiseLeave: (String) -> Unit,
)

/** What a "Request to leave" confirmation would send, once the user confirms the E2 dialog. */
private sealed interface LeaveTarget {
    data class Player(val id: String) : LeaveTarget
    data class Franchise(val id: String) : LeaveTarget
}

/**
 * League Detail (design board screen E). Players see the league page (E1/E3/E5/E6); the organizer
 * (or a co-organizer) gets the management view (E4): menu, rosters with Remove and leave-request
 * Approve/Dismiss. Rendered above `MainRoute`'s tab `Scaffold`, so it owns its own insets.
 */
@Composable
private fun LeagueDetailScreen(state: LeagueDetailState, actions: LeagueDetailActions) {
    val context = LocalContext.current
    var leaveTarget by remember { mutableStateOf<LeaveTarget?>(null) }
    var snackbarTick by remember { mutableIntStateOf(0) }
    var showCopied by remember { mutableStateOf(false) }
    LaunchedEffect(snackbarTick) {
        if (snackbarTick > 0) {
            showCopied = true
            delay(3_000)
            showCopied = false
        }
    }

    val share: (LeagueDto) -> Unit = { league ->
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Join my league on Crichere: crichere://leagues/${league.id}")
        }
        context.startActivity(Intent.createChooser(intent, "Share league"))
    }
    // Additive to the crichere:// share (docs/PHASE6.md): for people without the app installed.
    val copyWatchLink: (LeagueDto) -> Unit = { league ->
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Watch link", "$webViewerBaseUrl/leagues/${league.id}"))
        snackbarTick++
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val league = state.league
        when {
            state.isLoading && league == null -> {
                CompactHeader(title = null, onBack = actions.onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            league == null -> Column(Modifier.fillMaxSize()) {
                CompactHeader(title = null, onBack = actions.onBack)
                LoadError(onRetry = actions.onRetry)
            }
            else -> LeagueView(
                league = league,
                state = state,
                actions = actions,
                onShare = { share(league) },
                onCopyLink = { copyWatchLink(league) },
                onRequestLeave = { leaveTarget = it },
            )
        }

        AnimatedVisibility(
            visible = showCopied,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 13.dp, vertical = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Watch link copied", style = body(14.sp, FontWeight.Medium), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    leaveTarget?.let { target ->
        LeaveDialog(
            onDismiss = { leaveTarget = null },
            onConfirm = {
                leaveTarget = null
                when (target) {
                    is LeaveTarget.Player -> actions.onRequestLeaveAsPlayer(target.id)
                    is LeaveTarget.Franchise -> actions.onRequestLeaveAsFranchise(target.id)
                }
            },
        )
    }
}

// ---------------------------------------------------------------- league view (E1-E6)

/**
 * One page for everyone. Players/visitors get the join/claim/follow block (E1), a "You're
 * registered" card with Request to leave once joined (E2), or the leave-requested notice (E3).
 * Organizers and co-organizers see the same league info plus the E4 management block (menu,
 * rosters with Remove and leave approve/dismiss) -- the board's E4 shows only the management part,
 * but organizers keep the league info and Share/Copy (decision 2026-10-01, see DESIGN-REVIEW.md).
 */
@Composable
private fun LeagueView(
    league: LeagueDto,
    state: LeagueDetailState,
    actions: LeagueDetailActions,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onRequestLeave: (LeaveTarget) -> Unit,
) {
    val me = state.currentUserId
    val organizer = state.isOrganizer
    val myPlayer = league.players.firstOrNull { it.userId == me }.takeUnless { organizer }
    val myFranchise = league.franchises.firstOrNull { it.ownerUserId == me }.takeUnless { organizer }
    val completed = league.status == LeagueStatus.COMPLETED
    val playersFull = league.playersRequired?.let { league.players.size >= it } == true
    val franchisesFull = league.franchisesRequired?.let { league.franchises.size >= it } == true
    val leaveRequested = myPlayer?.leaveRequestedAt != null || myFranchise?.leaveRequestedAt != null

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            if (league.bannerUrl != null) BannerHeader(league, onBack = actions.onBack) else CompactHeader(title = null, onBack = actions.onBack)
        }
        item {
            Column(Modifier.padding(horizontal = 19.dp)) {
                Spacer(Modifier.height(if (league.bannerUrl != null) 8.dp else 2.dp))
                Text(
                    league.name,
                    style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 23.1.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                LeagueFacts(league)
            }
        }
        if (leaveRequested) {
            item {
                NoticeCard(
                    icon = R.drawable.ic_hourglass_top,
                    title = "Requested to leave",
                    body = "You stay listed until the organizer approves or dismisses it.",
                    containerColor = LocalCrichereExtraColors.current.warningContainer,
                    titleColor = LocalCrichereExtraColors.current.onWarning,
                    bodyColor = LocalCrichereExtraColors.current.onWarning,
                    modifier = Modifier.padding(start = 19.dp, end = 19.dp, top = 12.dp),
                )
            }
        } else if (!completed && (myPlayer != null || myFranchise != null)) {
            item {
                Column(Modifier.padding(start = 19.dp, end = 19.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    if (myPlayer != null) {
                        RegisteredCard("You're registered", registrationLine(myPlayer.paymentScreenshotUrl, league.playerFee, myPlayer.joinedAt))
                    } else if (myFranchise != null) {
                        RegisteredCard("You own ${myFranchise.name}", registrationLine(myFranchise.paymentScreenshotUrl, league.franchiseFee, myFranchise.joinedAt))
                    }
                    val target = myPlayer?.let { LeaveTarget.Player(it.id) } ?: myFranchise?.let { LeaveTarget.Franchise(it.id) }
                    if (target != null) {
                        Pill("Request to leave", R.drawable.ic_logout, PillStyle.Danger, enabled = !state.isLeaveRequesting) { onRequestLeave(target) }
                    }
                }
            }
        }
        if (league.franchisesRequired != null || league.playersRequired != null || league.franchiseFee != null || league.playerFee != null) {
            item { CapacityFeesCard(league, Modifier.padding(start = 19.dp, end = 19.dp, top = 10.dp)) }
        }
        item {
            Column(Modifier.padding(start = 19.dp, end = 19.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (completed) {
                    Text("League completed. Rosters and awards stay visible.", style = body(13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (organizer) {
                    OrganizerMenu(league, state, actions)
                    state.errorMessage?.let { Text(it, style = body(12.5.sp, FontWeight.Medium), color = MaterialTheme.colorScheme.error) }
                } else if (!completed && myPlayer == null && myFranchise == null) {
                    if (playersFull) {
                        Pill("Registration full", R.drawable.ic_person_off, PillStyle.Disabled, height = 50.dp) {}
                    } else {
                        Pill("Join as Player", R.drawable.ic_person_add, PillStyle.Primary, onClick = actions.onJoinLeague)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!franchisesFull) {
                            Pill("Claim a Franchise", R.drawable.ic_shield, PillStyle.Outlined, Modifier.weight(1f), onClick = actions.onClaimFranchise)
                        }
                        FollowPill(league, state, playersFull && franchisesFull, Modifier.weight(1f), actions.onToggleFollow)
                    }
                } else if (!completed) {
                    FollowPill(league, state, registrationClosed = false, Modifier, actions.onToggleFollow)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Share", R.drawable.ic_share, PillStyle.Outlined, Modifier.weight(1f), onClick = onShare)
                    Pill("Copy watch link", R.drawable.ic_link, PillStyle.Outlined, Modifier.weight(1f), onClick = onCopyLink)
                }
                // Public like the rest of the league (docs/PHASE5.md): everyone can watch/bid.
                Pill("Live Auction", R.drawable.ic_gavel, PillStyle.Auction, onClick = actions.onAuctionLive)
            }
        }
        awardsSection(league.awards, horizontal = 19)
        if (organizer) {
            sectionHeader("Players · ${league.players.size}", horizontal = 19)
            item {
                Box(Modifier.padding(horizontal = 19.dp)) {
                    if (league.players.isEmpty()) {
                        EmptyRosterText("No players yet.")
                    } else {
                        RosterCard {
                            league.players.forEachIndexed { i, p ->
                                if (i > 0) RowDivider()
                                PlayerRow(
                                    p, league, isMe = false,
                                    organizer = OrganizerRowActions(
                                        isRemoving = p.id in state.removingIds,
                                        isResponding = p.id in state.respondingToLeaveRequestIds,
                                        onViewScreenshot = actions.onViewScreenshot,
                                        onRemove = { actions.onRemovePlayer(p.id) },
                                        onApproveLeave = { actions.onApprovePlayerLeave(p.id) },
                                        onDismissLeave = { actions.onDismissPlayerLeave(p.id) },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            sectionHeader("Franchises · ${league.franchises.size}", horizontal = 19)
            item {
                Box(Modifier.padding(horizontal = 19.dp)) {
                    if (league.franchises.isEmpty()) {
                        EmptyRosterText("No franchises yet.")
                    } else {
                        RosterCard {
                            league.franchises.forEachIndexed { i, f ->
                                if (i > 0) RowDivider()
                                FranchiseRow(
                                    f, league, isMe = false,
                                    organizer = OrganizerRowActions(
                                        isRemoving = f.id in state.removingIds,
                                        isResponding = f.id in state.respondingToLeaveRequestIds,
                                        onViewScreenshot = actions.onViewScreenshot,
                                        onRemove = { actions.onRemoveFranchise(f.id) },
                                        onApproveLeave = { actions.onApproveFranchiseLeave(f.id) },
                                        onDismissLeave = { actions.onDismissFranchiseLeave(f.id) },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            if (league.franchises.isNotEmpty()) {
                sectionHeader("Franchises", horizontal = 19)
                item {
                    RosterCard(Modifier.padding(horizontal = 19.dp)) {
                        league.franchises.forEachIndexed { i, f ->
                            if (i > 0) RowDivider()
                            FranchiseRow(f, league, isMe = f.ownerUserId == me, organizer = null)
                        }
                    }
                }
            }
            if (league.players.isNotEmpty()) {
                sectionHeader("Players", horizontal = 19)
                item {
                    RosterCard(Modifier.padding(horizontal = 19.dp)) {
                        league.players.forEachIndexed { i, p ->
                            if (i > 0) RowDivider()
                            PlayerRow(p, league, isMe = p.userId == me, organizer = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegisteredCard(title: String, line: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_check_circle_filled), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = body(14.5.sp, FontWeight.Bold, 15.95.sp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(3.dp))
            Text(line, style = body(12.5.sp, lineHeight = 16.25.sp), color = Color(0xFF2E4A31))
        }
    }
}

/** "Paid ₹500 · proof sent 26 Sep" when a screenshot was attached, else "Joined 26 Sep". */
private fun registrationLine(screenshotUrl: String?, fee: Double?, joinedAt: String): String {
    val date = runCatching { Instant.parse(joinedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
        .map { shortDate(it) }
        .getOrDefault("")
    return if (screenshotUrl != null && fee != null) "Paid ${rupees(fee)} · proof sent $date" else "Joined $date"
}

@Composable
private fun OrganizerMenu(league: LeagueDto, state: LeagueDetailState, actions: LeagueDetailActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(5.dp),
    ) {
        MenuRow(R.drawable.ic_edit, "Edit league", onClick = actions.onEditLeague)
        MenuRow(R.drawable.ic_tune, "Auction settings", onClick = actions.onAuctionSettings)
        MenuRow(R.drawable.ic_admin_panel_settings, "Manage co-organizers", onClick = actions.onManageRoles)
        if (league.status != LeagueStatus.COMPLETED) {
            MenuRow(
                R.drawable.ic_task_alt,
                if (state.isCompleting) "Marking completed…" else "Mark completed",
                enabled = !state.isCompleting,
                onClick = actions.onMarkCompleted,
            )
        }
    }
}

@Composable
private fun FollowPill(league: LeagueDto, state: LeagueDetailState, registrationClosed: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val label = when {
        league.isFollowing -> "Following"
        registrationClosed -> "Follow for updates"
        else -> "Follow"
    }
    Pill(
        label,
        if (league.isFollowing) R.drawable.ic_notifications_active else R.drawable.ic_notifications,
        PillStyle.Outlined,
        modifier,
        enabled = !state.isTogglingFollow,
        onClick = onToggle,
    )
}

@Composable
private fun BannerHeader(league: LeagueDto, onBack: () -> Unit) {
    Box {
        Column {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)) {
                AsyncImage(
                    model = league.bannerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Column {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Spacer(Modifier.height(118.dp))
                }
            }
            Spacer(Modifier.height(28.dp)) // the part of the logo tile that hangs below the banner
        }
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 11.dp, top = 9.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(21.dp))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 19.dp)
                .size(64.dp)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tileColor(league.id)),
            contentAlignment = Alignment.Center,
        ) {
            Text(shortCode(league.name), style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 17.9.sp), color = Color.White)
            if (league.logoUrl != null) {
                AsyncImage(model = league.logoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun CompactHeader(title: String?, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 5.dp, top = 3.dp, end = 19.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
        }
        if (title != null) {
            Spacer(Modifier.width(6.dp))
            Text(title, style = body(16.sp, FontWeight.SemiBold), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LeagueFacts(league: LeagueDto) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = body(12.5.sp, lineHeight = 16.875.sp)
    Text(
        "${league.city}, ${league.district}, ${league.state}" + (league.groundName?.let { " · Ground: $it" } ?: ""),
        style = style,
        color = muted,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        buildAnnotatedString {
            append("Starts ${longDate(league.startsOn)}")
            league.format?.takeIf { it.isNotBlank() }?.let { append(" -- $it") }
            append(" · ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)) { append(statusLabel(league)) }
        },
        style = style,
        color = muted,
    )
    league.description?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = body(13.sp, lineHeight = 18.sp), color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun CapacityFeesCard(league: LeagueDto, modifier: Modifier = Modifier) {
    val capacity = listOfNotNull(
        league.franchisesRequired?.let { "Franchises: $it" },
        league.playersRequired?.let { "Players: $it" },
    ).joinToString(" · ")
    val fees = listOfNotNull(
        league.franchiseFee?.let { "Franchise ${rupees(it)}" },
        league.playerFee?.let { "Player ${rupees(it)}" },
    ).joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(11.dp),
        horizontalArrangement = Arrangement.spacedBy(21.dp),
    ) {
        FactColumn("Capacity", capacity.ifEmpty { "Open" }, Modifier.weight(1f))
        FactColumn("Fees", fees.ifEmpty { "Free" }, Modifier.weight(1f))
    }
}

@Composable
private fun FactColumn(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = body(10.5.sp, FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(value, style = body(12.5.sp, FontWeight.SemiBold, 16.25.sp), color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun MenuRow(@DrawableRes icon: Int, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = body(13.5.sp, FontWeight.Medium), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = CrichereInkDisabled, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------- rosters

private class OrganizerRowActions(
    val isRemoving: Boolean,
    val isResponding: Boolean,
    val onViewScreenshot: (String) -> Unit,
    val onRemove: () -> Unit,
    val onApproveLeave: () -> Unit,
    val onDismissLeave: () -> Unit,
)

@Composable
private fun RosterCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
    ) { content() }
}

@Composable
private fun RowDivider() = HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)

@Composable
private fun EmptyRosterText(text: String) {
    Text(text, style = body(13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PlayerRow(player: LeaguePlayerDto, league: LeagueDto, isMe: Boolean, organizer: OrganizerRowActions?) {
    val paid = if (player.paymentScreenshotUrl != null) league.playerFee?.let { "paid ${rupees(it)}" } else null
    RosterRow(
        avatarText = initials(player.name ?: "?"),
        avatarUrl = null,
        title = (player.name ?: "Player") + if (isMe) " (you)" else "",
        subtitle = listOfNotNull(player.playingRole?.label(), paid).joinToString(" · ").ifEmpty { null },
        leaveRequested = player.leaveRequestedAt != null,
        screenshotUrl = player.paymentScreenshotUrl,
        organizer = organizer,
    )
}

@Composable
private fun FranchiseRow(franchise: LeagueFranchiseDto, league: LeagueDto, isMe: Boolean, organizer: OrganizerRowActions?) {
    val paid = if (franchise.paymentScreenshotUrl != null) league.franchiseFee?.let { "paid ${rupees(it)}" } else null
    val owner = "Owner: ${franchise.ownerName ?: "Unknown"}" + if (isMe) " (you)" else ""
    RosterRow(
        avatarText = initials(franchise.name),
        avatarUrl = franchise.logoUrl,
        title = franchise.name,
        subtitle = listOfNotNull(owner, paid).joinToString(" · "),
        leaveRequested = franchise.leaveRequestedAt != null,
        screenshotUrl = franchise.paymentScreenshotUrl,
        organizer = organizer,
    )
}

@Composable
private fun RosterRow(
    avatarText: String,
    avatarUrl: String?,
    title: String,
    subtitle: String?,
    leaveRequested: Boolean,
    screenshotUrl: String?,
    organizer: OrganizerRowActions?,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFE3E8DD)), contentAlignment = Alignment.Center) {
            Text(avatarText, style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 11.5.sp), color = colors.primary)
            if (avatarUrl != null) {
                AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = body(13.5.sp, FontWeight.SemiBold, 14.85.sp), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = body(11.5.sp, lineHeight = 13.8.sp), color = colors.onSurfaceVariant)
            }
            if (organizer == null) {
                if (leaveRequested) {
                    Spacer(Modifier.height(8.dp))
                    Text("Requested to leave", style = body(12.sp, FontWeight.SemiBold), color = colors.error)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (leaveRequested) {
                        Text("Requested to leave", style = body(12.sp, FontWeight.SemiBold, 14.4.sp), color = colors.error, modifier = Modifier.weight(1f))
                        SmallPill("Approve leave", filled = true, enabled = !organizer.isResponding, onClick = organizer.onApproveLeave)
                        SmallPill("Dismiss", filled = false, enabled = !organizer.isResponding, onClick = organizer.onDismissLeave)
                    } else {
                        Box(Modifier.weight(1f)) {
                            if (screenshotUrl != null) {
                                Row(
                                    Modifier.clickable { organizer.onViewScreenshot(screenshotUrl) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(painterResource(R.drawable.ic_photo_library), contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("View payment screenshot", style = body(12.sp, FontWeight.SemiBold), color = colors.primary)
                                }
                            }
                        }
                        SmallPill(
                            if (organizer.isRemoving) "Removing…" else "Remove",
                            filled = false,
                            danger = true,
                            enabled = !organizer.isRemoving,
                            onClick = organizer.onRemove,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallPill(text: String, filled: Boolean, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(15.dp)
    val border = when {
        filled -> null
        danger -> BorderStroke(1.dp, Color(0xFFE4B4AC))
        else -> BorderStroke(1.dp, colors.outline)
    }
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .background(if (filled) colors.primary else Color.Transparent)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = body(12.sp, FontWeight.SemiBold),
            color = when {
                filled -> colors.onPrimary
                danger -> CrichereErrorStrong
                else -> colors.primary
            },
        )
    }
}

// ---------------------------------------------------------------- shared bits

private fun LazyListScope.sectionHeader(text: String, horizontal: Int) {
    item {
        Text(
            text,
            style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 14.5.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = horizontal.dp, end = horizontal.dp, top = 14.dp, bottom = 9.dp),
        )
    }
}

private fun LazyListScope.awardsSection(awards: List<LeagueAwardDto>, horizontal: Int) {
    if (awards.isEmpty()) return
    sectionHeader("Awards", horizontal)
    item {
        Column(Modifier.padding(horizontal = horizontal.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            awards.sortedBy { it.displayOrder }.withIndex().chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (index, award) -> AwardCard(award, index, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AwardCard(award: LeagueAwardDto, index: Int, modifier: Modifier) {
    val (icon, tint) = when (index) {
        0 -> R.drawable.ic_trophy to Color(0xFFB8860B)
        1 -> R.drawable.ic_military_tech to CrichereInkSubtle
        else -> R.drawable.ic_star_filled to MaterialTheme.colorScheme.primary
    }
    Column(
        modifier
            .height(108.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(11.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(8.dp))
        Text(award.name, style = body(13.sp, FontWeight.SemiBold, 14.3.sp), color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        award.cashAmount?.let {
            Spacer(Modifier.height(4.dp))
            Text("Cash ${rupees(it)}", style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 11.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (award.hasTrophy) {
            Spacer(Modifier.height(4.dp))
            Text("Trophy", style = body(11.5.sp, FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class PillStyle { Primary, Outlined, Auction, Danger, Disabled }

@Composable
private fun Pill(
    text: String,
    @DrawableRes icon: Int,
    style: PillStyle,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 44.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(height / 2)
    val (container, content, border) = when (style) {
        PillStyle.Primary -> Triple(colors.primary, colors.onPrimary, null)
        PillStyle.Outlined -> Triple(Color.Transparent, colors.primary, colors.outline)
        PillStyle.Auction -> Triple(Color(0xFF0E1A11), LocalCrichereExtraColors.current.auctionGold, null)
        PillStyle.Danger -> Triple(Color.Transparent, CrichereErrorStrong, colors.outline)
        PillStyle.Disabled -> Triple(CrichereDisabledContainer, Color(0xFF6B756D), null)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(container)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(enabled = enabled && style != PillStyle.Disabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = body(if (style == PillStyle.Disabled) 14.5.sp else 13.5.sp, FontWeight.SemiBold), color = content, maxLines = 1)
    }
}

@Composable
private fun LoadError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_cloud_off), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text("Couldn't load this league", style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp), color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(10.dp))
        Text("Check your connection and try again.", style = body(13.sp, lineHeight = 18.85.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Retry", style = body(13.5.sp, FontWeight.SemiBold), color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Design E2: confirm before asking the organizer to let you leave. */
@Composable
private fun LeaveDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF1F4EE),
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null, tint = CrichereErrorStrong, modifier = Modifier.size(26.dp)) },
        title = { Text("Request to leave?", style = body(19.sp, FontWeight.SemiBold, 22.8.sp), color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Text(
                "The organizer can approve or dismiss it. Any refund is between you and the organizer; Crichere doesn't handle payments.",
                style = body(13.5.sp, lineHeight = 19.575.sp),
                color = Color(0xFF3E4A41),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Send request", style = body(14.sp, FontWeight.SemiBold), color = CrichereErrorStrong) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = body(14.sp, FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary) }
        },
    )
}

private fun statusLabel(league: LeagueDto) = if (league.status == LeagueStatus.COMPLETED) "Completed" else "Announced"

private fun body(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight = FontWeight.Normal, lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified) =
    TextStyle(fontFamily = InstrumentSansFamily, fontWeight = weight, fontSize = size, lineHeight = lineHeight)

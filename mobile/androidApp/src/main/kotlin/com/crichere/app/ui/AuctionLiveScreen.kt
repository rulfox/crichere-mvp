package com.crichere.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import com.crichere.app.league.ConnectionPhase
import com.crichere.app.league.DeadEndKind
import com.crichere.app.league.DockMode
import com.crichere.app.league.EndAuctionFailure
import com.crichere.app.league.deadEndKind
import com.crichere.app.league.overPurseAmount
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.crichere.app.league.groupIndianAmount
import com.crichere.app.R
import com.crichere.app.league.AuctionBidTickerDto
import com.crichere.app.league.AuctionState
import com.crichere.app.league.AuctionStateDto
import com.crichere.app.league.AuctionStatus
import com.crichere.app.league.AuctionViewModel
import com.crichere.app.league.FranchiseAuctionResultDto
import com.crichere.app.league.LeagueFranchiseDto
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereAuctionBg
import com.crichere.app.ui.theme.CrichereAuctionGold
import com.crichere.app.ui.theme.CrichereAuctionMuted
import com.crichere.app.ui.theme.CrichereAuctionSurface
import com.crichere.app.ui.theme.InstrumentSansFamily
import com.crichere.app.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Duration
import java.time.Instant

// Board L1-L10 (dark auction theme).
private val Dock = Color(0xFF101B14)
private val TextSoft = Color(0xFFD5DDD6)
private val TextDim = Color(0xFFB7C2B9)
private val Alert = Color(0xFFFF8B70)
private val Hairline = Color.White.copy(alpha = 0.08f)
private val GhostButton = Color.White.copy(alpha = 0.08f)
private val PhotoPlaceholder = Color(0xFF25362B)
private val LiveFeed = Color(0xFF7BC47F)

/** Resolves [AuctionViewModel] via Koin, parameterized on [leagueId] -- see `AppRoute.AuctionLive` (ui/navigation). */
@Composable
internal fun AuctionLiveRoute(
    leagueId: String,
    onBack: () -> Unit,
    viewModel: AuctionViewModel = koinViewModel(key = "auction-live:$leagueId") { parametersOf(leagueId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.retry() }

    AuctionLiveScreen(state = state, viewModel = viewModel, onBack = onBack)
}

/**
 * The live auction (see docs/PHASE5.md), board L1-L10: status chip and progress, the player on
 * the block (photo, role, current bid, minimum next bid), recent bids, then a docked panel --
 * the bid form for a franchise owner, organizer controls, or both (dual roles are allowed, see
 * docs/PHASE3.md). Results replace the block once the auction ends. Kept live by the
 * ViewModel's SSE subscription; there is no manual refresh.
 */
@Composable
private fun AuctionLiveScreen(state: AuctionState, viewModel: AuctionViewModel, onBack: () -> Unit) {
    LightStatusBarIcons(enabled = true, navigationBar = true)
    Column(Modifier.fillMaxSize().background(CrichereAuctionBg).imePadding()) {
        TopBar(onBack)
        val auction = state.auction
        when {
            auction == null && state.loadFailed -> LoadFailed(onRetry = viewModel::retry)
            auction == null -> Loading()
            else -> LoadedAuction(state, auction, viewModel)
        }
    }
}

@Composable
private fun LoadedAuction(state: AuctionState, auction: AuctionStateDto, viewModel: AuctionViewModel) {
    val league = state.league
    val phase = state.connectionPhase
    val offline = phase == ConnectionPhase.RECONNECTING || phase == ConnectionPhase.LOST
    // U4 A4: content stays visible at 50% while the feed is down, back to 100% over 200 ms.
    val contentAlpha by animateFloatAsState(if (offline) 0.5f else 1f, tween(200), label = "contentAlpha")
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    auction.auctionStatus,
                    pulsing = !offline && !state.isDeadEnd,
                    modifier = Modifier.alpha(if (phase == ConnectionPhase.LOST) 0.5f else 1f),
                )
                ConnectionPill(phase, onRetry = viewModel::retry)
            }
            if (auction.auctionStatus == AuctionStatus.IN_PROGRESS && auction.playersTotal > 0) {
                Spacer(Modifier.height(6.dp))
                val progress = if (auction.currentPlayerId != null) "Player ${auction.playersSold + 1} of ${auction.playersTotal}"
                else "${auction.playersSold} of ${auction.playersTotal} done"
                Text(
                    if (phase == ConnectionPhase.LOST) "$progress · updated ${secondsSince(state.lastEventAtMillis)}s ago" else progress,
                    style = text(11.5.sp, FontWeight.Medium, 11.5.sp),
                    color = CrichereAuctionMuted,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
            Column(Modifier.alpha(contentAlpha)) {
                when (auction.auctionStatus) {
                    AuctionStatus.NOT_STARTED -> {
                        Spacer(Modifier.height(12.dp))
                        NoticeCardDark(
                            title = if (state.isOrganizer) "Ready when you are" else "The auction hasn't started",
                            body = if (state.isOrganizer) "Current bid: none yet. Start the auction, then bring up the first player."
                            else "It starts when the organizer opens it. This screen updates on its own.",
                        )
                    }
                    AuctionStatus.IN_PROGRESS -> {
                        Spacer(Modifier.height(12.dp))
                        when {
                            auction.currentPlayerId != null -> {
                                BlockCard(auction, state.minimumNextBid, league?.franchises.orEmpty())
                                Spacer(Modifier.height(15.dp))
                                RecentBids(auction.recentBids, league?.franchises.orEmpty())
                            }
                            state.isDeadEnd -> DeadEndCard(auction, league?.auctionBasePrice, state.isOrganizer)
                            else -> EmptyBlockCard(auction, state.isOrganizer)
                        }
                    }
                    AuctionStatus.COMPLETED -> {
                        Spacer(Modifier.height(12.dp))
                        Text("This auction has ended.", style = text(13.5.sp, FontWeight.Medium, 17.55.sp), color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Results(state.results?.franchises.orEmpty(), league?.franchises.orEmpty(), league?.auctionPurse)
                    }
                }
            }
        }
        // U5 L21: inverse I12 bar 12 dp above the dock. Retry reopens the dialog rather than resending.
        val endSnack = state.endFailure?.let { failure ->
            Snack(
                message = when (failure) {
                    EndAuctionFailure.NETWORK -> "Couldn't end the auction. Check your connection and try again."
                    EndAuctionFailure.REFUSED -> "Couldn't end the auction right now. Try again in a moment."
                },
                actionLabel = "Retry",
                onAction = viewModel::requestEnd,
                durationMs = null,
            )
        }
        SnackHost(endSnack, onDismiss = viewModel::clearEndFailure, inverse = true, placement = SnackPlacement.ABOVE_DOCK)
        Dock(state, auction, viewModel, offline)
    }
    if (state.isEndConfirmOpen) {
        EndAuctionDialog(state, onDismiss = viewModel::dismissEnd, onConfirm = viewModel::confirmEnd)
    }
}

/**
 * U5 L19a/b and L20: the N4/K3 dialog shell in the auction's dark tokens -- scrim .6, surface #13211A with
 * a hairline, coral confirm. Both End Auction buttons open it; the dead end only shortens the body. While
 * ending, the confirm shows a spinner and "Ending…", Cancel dims to 38%, and scrim/back do nothing.
 */
@Composable
private fun EndAuctionDialog(state: AuctionState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val ending = state.isEnding
    val body = state.endAuctionBody
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !ending, dismissOnClickOutside = !ending),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0.6f)
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .background(CrichereAuctionSurface, RoundedCornerShape(28.dp))
                .border(1.dp, Hairline, RoundedCornerShape(28.dp))
                .padding(25.dp),
        ) {
            Text("End the auction?", style = tight(InstrumentSansFamily, 19.sp, FontWeight.SemiBold, 22.8.sp), color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                buildAnnotatedString {
                    append(body.prefix)
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) { append(body.emphasis) }
                    append(body.suffix)
                },
                style = tight(InstrumentSansFamily, 13.5.sp, FontWeight.Normal, 19.6.sp),
                color = TextDim,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancel",
                    style = tight(InstrumentSansFamily, 14.sp, FontWeight.SemiBold, 20.sp),
                    color = TextSoft,
                    modifier = Modifier
                        .alpha(if (ending) 0.38f else 1f)
                        .clickable(enabled = !ending, onClick = onDismiss),
                )
                Spacer(Modifier.width(22.dp))
                Row(
                    Modifier.clickable(enabled = !ending, onClick = onConfirm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ending) {
                        CircularProgressIndicator(color = Alert, trackColor = Alert.copy(alpha = 0.25f), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (ending) "Ending…" else "End auction", style = tight(InstrumentSansFamily, 14.sp, FontWeight.SemiBold, 20.sp), color = Alert)
                }
            }
        }
    }
}

/** Whole seconds since [millis], ticking every second while shown -- "updated 34s ago" (U4 L16). */
@Composable
private fun secondsSince(millis: Long?): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return if (millis == null) 0 else ((now - millis) / 1_000).coerceAtLeast(0)
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 5.dp, top = 3.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text("Live Auction", style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 16.sp), color = Color.White)
    }
}

/** "● Live" (pulsing), "Not started", "Ended". The dot holds still while the feed is down or nothing can sell (U4 A2/A4). */
@Composable
private fun StatusChip(status: AuctionStatus, pulsing: Boolean, modifier: Modifier = Modifier) {
    val live = status == AuctionStatus.IN_PROGRESS
    Row(
        modifier
            .height(26.dp)
            .background(if (live) CrichereAuctionGold.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.07f), RoundedCornerShape(13.dp))
            .padding(start = if (live) 9.dp else 10.dp, end = if (live) 11.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) {
            Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
                if (pulsing) {
                    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
                        label = "livePulse",
                    )
                    Box(
                        Modifier
                            .size(8.dp + 10.dp * pulse)
                            .background(CrichereAuctionGold.copy(alpha = 0.35f * (1f - pulse)), CircleShape),
                    )
                }
                Box(Modifier.size(8.dp).background(CrichereAuctionGold, CircleShape))
            }
            Spacer(Modifier.width(7.dp))
        }
        Text(
            when (status) {
                AuctionStatus.IN_PROGRESS -> "Live"
                AuctionStatus.NOT_STARTED -> "Not started"
                AuctionStatus.COMPLETED -> "Ended"
            },
            style = text(12.sp, FontWeight.SemiBold, 12.sp),
            color = if (live) CrichereAuctionGold else TextDim,
        )
    }
}

/**
 * U4 A4: the connection state as a pill beside the status chip, in the row that already exists, so
 * nothing below moves. Reconnecting fades in over 150 ms, phases cross-fade over 150 ms, Back online
 * fades out over 250 ms. The whole Connection lost pill is the Retry target (bigger than the 22 dp chip).
 */
@Composable
private fun ConnectionPill(phase: ConnectionPhase, onRetry: () -> Unit) {
    var shown by remember { mutableStateOf(phase) }
    if (phase != ConnectionPhase.ONLINE) shown = phase
    AnimatedVisibility(visible = phase != ConnectionPhase.ONLINE, enter = fadeIn(tween(150)), exit = fadeOut(tween(250))) {
        Crossfade(targetState = shown, animationSpec = tween(150), label = "connectionPill") { current ->
            val announcement = when (current) {
                ConnectionPhase.RECONNECTING -> "Reconnecting"
                ConnectionPhase.LOST -> "Connection lost. Retry"
                else -> "Back online"
            }
            Row(Modifier.padding(start = 8.dp)) {
                Row(
                    Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            when (current) {
                                ConnectionPhase.LOST -> Alert.copy(alpha = 0.14f)
                                ConnectionPhase.BACK_ONLINE -> LiveFeed.copy(alpha = 0.14f)
                                else -> Color.White.copy(alpha = 0.07f)
                            },
                        )
                        .then(if (current == ConnectionPhase.LOST) Modifier.clickable(onClick = onRetry) else Modifier)
                        .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announcement }
                        .padding(
                            start = if (current == ConnectionPhase.BACK_ONLINE) 8.dp else 9.dp,
                            end = if (current == ConnectionPhase.LOST) 4.dp else 11.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (current) {
                        ConnectionPhase.LOST -> {
                            Icon(painterResource(R.drawable.ic_cloud_off), contentDescription = null, tint = Alert, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Connection lost", style = tight(InstrumentSansFamily, 12.sp, FontWeight.SemiBold, 12.sp), color = Alert)
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.height(22.dp).background(Alert, RoundedCornerShape(11.dp)).padding(horizontal = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Retry", style = tight(InstrumentSansFamily, 11.5.sp, FontWeight.Bold, 11.5.sp), color = CrichereAuctionBg)
                            }
                        }
                        ConnectionPhase.BACK_ONLINE -> {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = LiveFeed, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Back online", style = tight(InstrumentSansFamily, 12.sp, FontWeight.SemiBold, 12.sp), color = LiveFeed)
                        }
                        else -> {
                            CircularProgressIndicator(color = TextDim, trackColor = Color.Transparent, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Reconnecting…", style = tight(InstrumentSansFamily, 12.sp, FontWeight.SemiBold, 12.sp), color = TextDim)
                        }
                    }
                }
            }
        }
    }
}

/**
 * L1/L2/L4: photo, name, role, current bid with the leading franchise, minimum next bid. Geometry from
 * U4 A5 (225 dp with a one-line name, 251 with two): every line height set, no font padding, and a fixed
 * 40 dp bid row, so the card no longer comes out ~10 dp taller than the board.
 */
@Composable
private fun BlockCard(auction: AuctionStateDto, minimumNextBid: Double?, franchises: List<LeagueFranchiseDto>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CrichereAuctionSurface, RoundedCornerShape(20.dp))
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val name = auction.currentPlayerName ?: "Unnamed player"
            PlayerPhoto(auction.currentPlayerPhotoUrl, name, size = 88.dp, radius = 20.dp, initialsSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("ON THE BLOCK", style = tight(JetBrainsMonoFamily, 10.sp, FontWeight.SemiBold, 12.sp, letterSpacing = 0.8.sp), color = CrichereAuctionGold)
                Spacer(Modifier.height(6.dp))
                Text(
                    name,
                    style = tight(ArchivoFamily, 22.sp, FontWeight.ExtraBold, 26.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                auction.currentPlayerRole?.let { role ->
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .height(22.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(11.dp))
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(role.label(), style = tight(InstrumentSansFamily, 11.5.sp, FontWeight.SemiBold, 11.5.sp), color = TextSoft, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Spacer(Modifier.height(14.dp))
        Text("Current bid", style = tight(InstrumentSansFamily, 12.sp, FontWeight.Medium, 16.sp), color = CrichereAuctionMuted)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.Bottom) {
            val amount = auction.currentBidAmount
            if (amount == null) {
                Text(
                    "No bids yet",
                    style = tight(InstrumentSansFamily, 20.sp, FontWeight.SemiBold, 28.sp),
                    color = TextSoft,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            } else {
                // The amount never truncates; the leader's name gives way instead.
                Text(
                    rupees(amount),
                    style = tight(JetBrainsMonoFamily, 36.sp, FontWeight.Bold, 40.sp, letterSpacing = (-0.72).sp),
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                )
                val leaderId = auction.currentLeadingFranchiseId
                if (leaderId != null) {
                    Spacer(Modifier.width(10.dp))
                    Row(
                        Modifier.weight(1f).padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FranchiseTile(leaderId, auction.currentLeadingFranchiseName.orEmpty(), franchises, size = 20.dp, radius = 6.dp, fontSize = 7.sp)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            auction.currentLeadingFranchiseName.orEmpty(),
                            style = tight(InstrumentSansFamily, 13.sp, FontWeight.SemiBold, 16.sp),
                            color = TextSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
        if (minimumNextBid != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                buildAnnotatedString {
                    append(if (auction.currentBidAmount == null) "Opening bid at least " else "Next bid at least ")
                    withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, color = TextDim)) { append(rupees(minimumNextBid)) }
                },
                style = tight(InstrumentSansFamily, 12.sp, FontWeight.Medium, 16.sp),
                color = CrichereAuctionMuted,
            )
        }
    }
}

/** L8: nobody up -- what just happened, and (organizer) what to do next. */
@Composable
private fun EmptyBlockCard(auction: AuctionStateDto, isOrganizer: Boolean) {
    val dashColor = Color.White.copy(alpha = 0.18f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(CrichereAuctionSurface, RoundedCornerShape(20.dp))
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .padding(17.dp),
    ) {
        Text("ON THE BLOCK", style = mono(10.sp, FontWeight.SemiBold, letterSpacing = 0.8.sp), color = CrichereAuctionMuted)
        Spacer(Modifier.height(8.dp))
        Text("No player up yet", style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 21.6.sp), color = Color.White)
        val lastLine = lastResultLine(auction)
        val nextStep = if (isOrganizer) "Bring up the next player." else "Waiting for the organizer to bring up the next player."
        val body = listOfNotNull(lastLine, nextStep).joinToString(" ")
        Spacer(Modifier.height(7.dp))
        Text(body, style = text(13.sp, lineHeight = 18.85.sp), color = CrichereAuctionMuted)
    }
}

/** "Last: Rohan Patil sold to Kolhapur Kings for ₹15,000." / "Last: Rohan Patil went unsold." */
private fun lastResultLine(auction: AuctionStateDto): String? = auction.lastResult?.let {
    val name = it.playerName ?: "The last player"
    if (it.sold) "Last: $name sold to ${it.franchiseName ?: "a franchise"}" + (it.amount?.let { a -> " for ${rupees(a)}." } ?: ".")
    else "Last: $name went unsold."
}

/**
 * U4 A2: live, nobody up, and no franchise can open a bid. The organizer gets the reason with a
 * breakdown (which tells them whether End or Allow exceeding purse helps); owners and spectators get a
 * neutral "Bidding has closed" -- they can't act on it, so no coral.
 */
@Composable
private fun DeadEndCard(auction: AuctionStateDto, basePrice: Double?, isOrganizer: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CrichereAuctionSurface, RoundedCornerShape(20.dp))
            .border(1.dp, if (isOrganizer) Alert.copy(alpha = 0.35f) else Hairline, RoundedCornerShape(20.dp))
            .padding(horizontal = 17.dp, vertical = 19.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(if (isOrganizer) Alert.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (isOrganizer) R.drawable.ic_block else R.drawable.ic_hourglass_empty),
                contentDescription = null,
                tint = if (isOrganizer) Alert else CrichereAuctionMuted,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (isOrganizer) "No franchise can bid on the remaining players" else "Bidding has closed",
            style = tight(ArchivoFamily, 18.sp, FontWeight.Bold, 21.6.sp),
            color = Color.White,
        )
        Spacer(Modifier.height(10.dp))
        val base = basePrice?.let { rupees(it) } ?: "base"
        Text(
            buildAnnotatedString {
                val allow = SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)
                if (!isOrganizer) {
                    append("No franchise can buy the remaining players. Waiting for the organizer to end the auction.")
                } else {
                    when (deadEndKind(auction.squadsFull, auction.purseBelowBase)) {
                        DeadEndKind.ALL_FULL -> append("Every squad is full, so nothing else can sell. End the auction to publish the results.")
                        DeadEndKind.ALL_PURSE -> append("Every purse is below the $base base price, so nothing else can sell. End the auction, or turn on ")
                        DeadEndKind.MIXED -> append("Squads are full or purses are below the $base base price, so nothing else can sell. End the auction, or turn on ")
                    }
                    if (auction.purseBelowBase > 0) {
                        withStyle(allow) { append("Allow exceeding purse") }
                        append(" to let franchises keep buying.")
                    }
                }
            },
            style = tight(InstrumentSansFamily, 13.sp, FontWeight.Normal, 18.85.sp),
            color = TextDim,
        )
        if (isOrganizer) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            Spacer(Modifier.height(12.dp))
            BreakdownRow("Squads full", "${auction.squadsFull} of ${auction.franchisesTotal}")
            Spacer(Modifier.height(6.dp))
            BreakdownRow("Purse below base price", "${auction.purseBelowBase} of ${auction.franchisesTotal}")
            Spacer(Modifier.height(6.dp))
            BreakdownRow("Left in pool", "${auction.playersPending} ${if (auction.playersPending == 1) "player" else "players"}")
        }
        lastResultLine(auction)?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = tight(InstrumentSansFamily, 12.sp, FontWeight.Normal, 16.8.sp), color = CrichereAuctionMuted)
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = tight(InstrumentSansFamily, 12.5.sp, FontWeight.Medium, 12.5.sp), color = CrichereAuctionMuted, modifier = Modifier.weight(1f))
        Text(value, style = tight(JetBrainsMonoFamily, 12.5.sp, FontWeight.Medium, 12.5.sp), color = TextSoft)
    }
}

@Composable
private fun RecentBids(bids: List<AuctionBidTickerDto>, franchises: List<LeagueFranchiseDto>) {
    Text("Recent bids", style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 13.sp), color = Color.White)
    Spacer(Modifier.height(12.dp))
    if (bids.isEmpty()) {
        Text("No bids yet.", style = text(13.sp, lineHeight = 18.2.sp), color = CrichereAuctionMuted)
        return
    }
    // "12s ago" ticks along without waiting for the next bid.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            now = System.currentTimeMillis()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        bids.take(5).forEachIndexed { index, bid ->
            val top = index == 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(if (top) CrichereAuctionGold.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FranchiseTile(bid.franchiseId, bid.franchiseName.orEmpty(), franchises, size = 24.dp, radius = 7.dp, fontSize = 9.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    bid.franchiseName ?: "Franchise",
                    style = text(13.sp, FontWeight.SemiBold, 13.sp),
                    color = if (top) Color.White else TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(rupees(bid.amount), style = mono(13.sp, FontWeight.Bold), color = if (top) CrichereAuctionGold else TextSoft)
                Spacer(Modifier.width(10.dp))
                Text(
                    timeAgo(bid.placedAt, now),
                    style = text(11.5.sp, FontWeight.Medium, 11.5.sp),
                    color = CrichereAuctionMuted,
                    modifier = Modifier.width(46.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** L5: one expandable card per franchise -- spend, then the players it won with prices. */
@Composable
private fun Results(results: List<FranchiseAuctionResultDto>, franchises: List<LeagueFranchiseDto>, purse: Double?) {
    Text("Results", style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 14.sp), color = Color.White)
    Spacer(Modifier.height(16.dp))
    if (results.isEmpty()) {
        Text("No franchises took part.", style = text(13.sp, lineHeight = 18.2.sp), color = CrichereAuctionMuted)
        return
    }
    var expandedId by rememberSaveable { mutableStateOf(results.first().franchiseId) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        results.forEach { result ->
            ResultCard(
                result = result,
                franchises = franchises,
                purse = purse,
                expanded = expandedId == result.franchiseId,
                onToggle = { expandedId = if (expandedId == result.franchiseId) "" else result.franchiseId },
            )
        }
    }
}

/** L5 / U4 L11: spend per franchise; over its purse, the amount over gets its own coral line and a Purse / Spent footer. */
@Composable
private fun ResultCard(result: FranchiseAuctionResultDto, franchises: List<LeagueFranchiseDto>, purse: Double?, expanded: Boolean, onToggle: () -> Unit) {
    var showAll by remember(result.franchiseId) { mutableStateOf(false) }
    val over = overPurseAmount(result.purseRemaining)
    val borderColor = when {
        over != null -> Alert.copy(alpha = 0.45f)
        expanded -> CrichereAuctionGold.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(CrichereAuctionSurface, RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FranchiseTile(result.franchiseId, result.franchiseName, franchises, size = 32.dp, radius = 9.dp, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(result.franchiseName, style = tight(InstrumentSansFamily, 14.sp, FontWeight.SemiBold, 14.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(
                    buildAnnotatedString {
                        val players = result.playersWon.size
                        append("$players ${if (players == 1) "player" else "players"} · ")
                        withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily)) { append(rupees(result.purseSpent)) }
                        append(" spent")
                        val left = result.purseRemaining
                        if (left != null && over == null) {
                            append(" · ")
                            withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily)) { append(rupees(left)) }
                            append(" left")
                        }
                        if (result.belowSquadMin) withStyle(SpanStyle(color = Alert)) { append(" · below min squad") }
                    },
                    style = tight(InstrumentSansFamily, 11.5.sp, FontWeight.Medium, 11.5.sp),
                    color = CrichereAuctionMuted,
                )
                if (over != null) {
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = Alert, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily)) { append(rupees(over)) }
                                append(" over purse")
                            },
                            style = tight(InstrumentSansFamily, 11.5.sp, FontWeight.SemiBold, 11.5.sp),
                            color = Alert,
                        )
                    }
                }
            }
            Icon(
                painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = CrichereAuctionMuted,
                modifier = Modifier.size(22.dp),
            )
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 8.dp)) {
                if (result.playersWon.isEmpty()) {
                    Text("No players won.", style = text(13.sp, lineHeight = 36.sp), color = CrichereAuctionMuted)
                }
                val shown = if (showAll) result.playersWon else result.playersWon.take(4)
                shown.forEach { player ->
                    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                        val name = player.playerName ?: "Unnamed player"
                        PlayerPhoto(player.photoUrl, name, size = 28.dp, radius = 8.dp, initialsSize = 10.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(name, style = text(13.sp, FontWeight.Medium, 13.sp), color = TextSoft, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(rupees(player.soldPrice), style = mono(12.5.sp, FontWeight.SemiBold), color = Color.White)
                    }
                }
                if (!showAll && result.playersWon.size > 4) {
                    Text(
                        "Show all ${result.playersWon.size}",
                        style = text(12.sp, FontWeight.SemiBold, 12.sp),
                        color = CrichereAuctionGold,
                        modifier = Modifier.fillMaxWidth().clickable { showAll = true }.padding(vertical = 5.dp),
                    )
                }
                if (over != null && purse != null) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                    Row(Modifier.fillMaxWidth().height(31.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildAnnotatedString {
                                append("Purse ")
                                withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, color = TextSoft)) { append(rupees(purse)) }
                            },
                            style = tight(InstrumentSansFamily, 12.sp, FontWeight.Medium, 12.sp),
                            color = CrichereAuctionMuted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            buildAnnotatedString {
                                append("Spent ")
                                withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily)) { append(rupees(result.purseSpent)) }
                            },
                            style = tight(InstrumentSansFamily, 12.sp, FontWeight.Medium, 12.sp),
                            color = Alert,
                        )
                    }
                }
            }
        }
    }
}

/** L3 / L9: the not-started card. */
@Composable
private fun NoticeCardDark(title: String, body: String) {
    Column(Modifier.fillMaxWidth().background(CrichereAuctionSurface, RoundedCornerShape(20.dp)).padding(16.dp)) {
        Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, tint = CrichereAuctionMuted, modifier = Modifier.padding(top = 3.dp).size(28.dp))
        Spacer(Modifier.height(11.dp))
        Text(title, style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 21.6.sp), color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(body, style = text(13.sp, lineHeight = 18.85.sp), color = CrichereAuctionMuted)
    }
}

/**
 * The docked panel: bid form (franchise owner, player up -- or a dead end, where it shows the owner's
 * own reason), organizer controls, or the viewer note. While the feed is down ([offline]) Place Bid and
 * the organizer buttons are disabled (U4 A4).
 */
@Composable
private fun Dock(state: AuctionState, auction: AuctionStateDto, viewModel: AuctionViewModel, offline: Boolean) {
    val live = auction.auctionStatus == AuctionStatus.IN_PROGRESS
    val playerUp = live && auction.currentPlayerId != null
    val ownerAtDeadEnd = state.isDeadEnd && state.myFranchiseId != null && state.dockMode != DockMode.Bid
    val canBid = (playerUp && state.myFranchiseId != null) || ownerAtDeadEnd
    val showViewerNote = playerUp && !canBid && !state.isOrganizer
    if (!canBid && !state.isOrganizer && !showViewerNote) return

    Column(
        Modifier
            .fillMaxWidth()
            .background(Dock, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .border(1.dp, Hairline, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp),
    ) {
        if (showViewerNote) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_visibility), contentDescription = null, tint = CrichereAuctionMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Only franchise owners can bid.", style = text(13.sp, FontWeight.Medium, 18.2.sp), color = TextDim)
            }
        }
        if (canBid) BidForm(state, viewModel, offline)
        if (state.isOrganizer) {
            if (canBid) Spacer(Modifier.height(16.dp))
            OrganizerControls(state, auction, viewModel, offline)
        }
    }
}

/**
 * L1 / L6 / L7, and U4 A3: when the owner leads, their squad is full, or their purse can't cover the
 * next bid, a status tile of the same 54 dp replaces the Amount field (cross-fade 180 ms) and both
 * buttons go inert -- the dock never changes height. Outbid: the field returns with "Outbid · ₹X" inside
 * it for 2 s and one haptic tick.
 */
@Composable
private fun BidForm(state: AuctionState, viewModel: AuctionViewModel, offline: Boolean) {
    val context = state.biddingContext
    val mode = state.dockMode
    val auction = state.auction
    if (context != null) {
        val over = overPurseAmount(context.purseLeft)
        Row(verticalAlignment = Alignment.Top) {
            FranchiseTile(context.franchiseId, context.franchiseName, state.league?.franchises.orEmpty(), size = 18.dp, radius = 5.dp, fontSize = 6.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                buildAnnotatedString {
                    append("Bidding as ")
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) { append(context.franchiseName) }
                    when {
                        over != null -> {
                            append(" · ")
                            withStyle(SpanStyle(color = Alert, fontWeight = FontWeight.SemiBold)) {
                                withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily)) { append(rupees(over)) }
                                append(" over purse")
                            }
                        }
                        else -> context.purseLeft?.let { append(" · ${rupees(it)} left") }
                    }
                    context.squadMax?.let { append(" · ${context.playersWon}/$it players") }
                },
                style = text(12.5.sp, FontWeight.Medium, 16.25.sp),
                color = TextDim,
                maxLines = if (over != null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
    }

    // Outbid: this franchise led the same player a moment ago and someone else now does.
    val haptics = LocalHapticFeedback.current
    var previous by remember { mutableStateOf(mode to auction?.currentPlayerId) }
    var outbidAmount by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(mode, auction?.currentPlayerId) {
        val (previousMode, previousPlayer) = previous
        if (previousMode is DockMode.Leading && mode == DockMode.Bid && previousPlayer == auction?.currentPlayerId && auction?.currentBidAmount != null) {
            outbidAmount = auction.currentBidAmount
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        previous = mode to auction?.currentPlayerId
    }
    LaunchedEffect(outbidAmount) {
        if (outbidAmount != null) {
            delay(2_000)
            outbidAmount = null
        }
    }

    val inputEnabled = !state.isBidding && !offline && mode == DockMode.Bid
    Box(Modifier.padding(top = 6.dp)) {
        Crossfade(targetState = mode, animationSpec = tween(180, easing = LinearOutSlowInEasing), label = "dockTile") { current ->
            when (current) {
                DockMode.Bid -> AmountField(
                    value = state.bidAmountInput,
                    onValueChange = viewModel::onBidAmountChanged,
                    error = state.bidError,
                    enabled = !state.isBidding && !offline,
                    outbidAmount = outbidAmount,
                )
                else -> DockStatusTile(current)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val increment = state.bidIncrement
        val inert = mode != DockMode.Bid || offline
        if (increment != null) {
            Box(
                Modifier
                    .alpha(if (state.isBidding) 0.4f else 1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, if (inert) Color.White.copy(alpha = 0.12f) else CrichereAuctionGold.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                    .clickable(enabled = inputEnabled, onClick = viewModel::onStepBid)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("+${rupees(increment)}", style = mono(14.sp, FontWeight.Bold), color = if (inert) CrichereAuctionMuted.copy(alpha = 0.5f) else CrichereAuctionGold)
            }
        }
        Row(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    when {
                        inert -> Color.White.copy(alpha = 0.08f)
                        state.isBidding -> CrichereAuctionGold.copy(alpha = 0.35f)
                        else -> CrichereAuctionGold
                    },
                )
                .clickable(enabled = inputEnabled, onClick = viewModel::placeBid),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isBidding) {
                CircularProgressIndicator(color = CrichereAuctionBg, trackColor = CrichereAuctionBg.copy(alpha = 0.25f), strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (state.isBidding) "Placing…" else "Place Bid",
                style = text(14.5.sp, FontWeight.Bold, 14.5.sp),
                color = if (inert) CrichereAuctionMuted else CrichereAuctionBg,
            )
        }
    }
}

/** U4 A3 tile: leading (gold), or a rule that stops this franchise bidding (neutral -- it's not an error). */
@Composable
private fun DockStatusTile(mode: DockMode) {
    val leading = mode is DockMode.Leading
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(if (leading) CrichereAuctionGold.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, if (leading) CrichereAuctionGold.copy(alpha = 0.35f) else Hairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (mode) {
            is DockMode.Leading -> R.drawable.ic_check_circle_filled
            is DockMode.SquadFull -> R.drawable.ic_groups
            else -> R.drawable.ic_account_balance_wallet
        }
        Icon(painterResource(icon), contentDescription = null, tint = if (leading) CrichereAuctionGold else CrichereAuctionMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val title = when (mode) {
                is DockMode.Leading -> buildAnnotatedString {
                    append("You're leading at ")
                    withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, color = CrichereAuctionGold)) { append(rupees(mode.amount)) }
                }
                is DockMode.SquadFull -> AnnotatedString("Your squad is full (${mode.playersWon}/${mode.squadMax}).")
                else -> AnnotatedString("Your purse can't cover the next bid.")
            }
            val sub = when (mode) {
                is DockMode.Leading -> AnnotatedString("Bidding reopens if someone outbids you.")
                is DockMode.SquadFull -> AnnotatedString("You can keep watching. Bidding is off for you.")
                is DockMode.PurseShort -> buildAnnotatedString {
                    append("Next bid at least ")
                    withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, color = TextDim)) { append(rupees(mode.minimumNextBid)) }
                }
                DockMode.Bid -> AnnotatedString("")
            }
            Text(title, style = tight(InstrumentSansFamily, 14.sp, FontWeight.SemiBold, 18.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(sub, style = tight(InstrumentSansFamily, 12.sp, FontWeight.Normal, 16.sp), color = CrichereAuctionMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Draws the typed amount with Indian grouping (15,500) while the text itself stays plain digits. */
private object IndianAmountTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val grouped = groupIndianAmount(text.text)
        return TransformedText(
            AnnotatedString(grouped.text),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = grouped.toTransformed(offset)
                override fun transformedToOriginal(offset: Int) = grouped.toOriginal(offset)
            },
        )
    }
}

/**
 * The ₹ amount field in the dock's dark style (U4 A6): notched label; idle 1 dp white .16, focused 2 dp
 * gold, error 2 dp coral, disabled (placing / offline) 1 dp white .08 with the value at 40%. Digits only,
 * at most 9 (₹99,99,99,999) -- extra keystrokes are ignored. [outbidAmount] shows "Outbid · ₹X" inside it.
 */
@Composable
private fun AmountField(value: String, onValueChange: (String) -> Unit, error: String?, enabled: Boolean, outbidAmount: Double?) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val thick = enabled && (error != null || focused)
    val borderColor = when {
        !enabled -> Color.White.copy(alpha = 0.08f)
        error != null -> Alert
        focused -> CrichereAuctionGold
        else -> Color.White.copy(alpha = 0.16f)
    }
    val labelColor = when {
        !enabled -> CrichereAuctionMuted.copy(alpha = 0.6f)
        error != null -> Alert
        focused -> CrichereAuctionGold
        else -> CrichereAuctionMuted
    }
    Column {
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .border(if (thick) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("₹", style = mono(18.sp, FontWeight.SemiBold), color = if (enabled) CrichereAuctionMuted else Color.White.copy(alpha = 0.4f))
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    value = value,
                    onValueChange = { text ->
                        val digits = text.filter { it.isDigit() }
                        if (digits.length <= MAX_BID_DIGITS) onValueChange(digits)
                    },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = mono(18.sp, FontWeight.SemiBold).copy(color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)),
                    cursorBrush = SolidColor(CrichereAuctionGold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = IndianAmountTransformation,
                    interactionSource = interactionSource,
                    modifier = Modifier.weight(1f),
                )
                var lastOutbid by remember { mutableStateOf(outbidAmount) }
                if (outbidAmount != null) lastOutbid = outbidAmount
                AnimatedVisibility(visible = outbidAmount != null, enter = fadeIn(tween(0)), exit = fadeOut(tween(200))) {
                    Text(
                        buildAnnotatedString {
                            append("Outbid · ")
                            lastOutbid?.let { withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily)) { append(rupees(it)) } }
                        },
                        style = tight(InstrumentSansFamily, 11.5.sp, FontWeight.SemiBold, 11.5.sp),
                        color = Alert,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (error != null) {
                    Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = Alert, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                "Amount",
                style = text(11.sp, FontWeight.Medium, 11.sp),
                color = labelColor,
                modifier = Modifier.offset(x = if (thick) 12.dp else 11.dp, y = (-6).dp).background(Dock).padding(horizontal = 4.dp),
            )
        }
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = tight(InstrumentSansFamily, 12.sp, FontWeight.Medium, 16.sp), color = Alert, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** U4 A6: ₹99,99,99,999. */
private const val MAX_BID_DIGITS = 9

/** L2 (player up), L8 (between players), L3 (not started), L5 (ended). */
@Composable
private fun OrganizerControls(state: AuctionState, auction: AuctionStateDto, viewModel: AuctionViewModel, offline: Boolean) {
    val acting = state.isActing || offline
    Text("Organizer controls", style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 13.sp), color = Color.White)
    Spacer(Modifier.height(12.dp))
    when (auction.auctionStatus) {
        AuctionStatus.NOT_STARTED -> DockButton("Start Auction", R.drawable.ic_play_arrow, primary = true, enabled = !acting, onClick = viewModel::start)
        AuctionStatus.COMPLETED -> DockButton("Undo", R.drawable.ic_undo, enabled = !acting, onClick = viewModel::undo)
        AuctionStatus.IN_PROGRESS -> when {
            auction.currentPlayerId != null -> {
                SoldButton(auction, enabled = !acting && auction.currentLeadingFranchiseId != null, onClick = viewModel::sold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DockButton("Unsold", R.drawable.ic_block, enabled = !acting, onClick = viewModel::unsold, modifier = Modifier.weight(1f))
                    // Owner decision 2026-10-02: shown but disabled while a player is up -- the server
                    // only opens the next player after Sold / Unsold.
                    DockButton("Next Player", R.drawable.ic_skip_next, enabled = false, onClick = viewModel::nextPlayer, modifier = Modifier.weight(1f))
                    DockButton("Undo", R.drawable.ic_undo, enabled = !acting, onClick = viewModel::undo, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                ExceedPurseRow(auction, viewModel, acting)
                Spacer(Modifier.height(12.dp))
                EndAuctionOutlined(enabled = !acting && !state.isEnding, onClick = viewModel::requestEnd)
            }
            // U4 A2: nothing can sell. End Auction becomes the filled main button; Next Player and Undo share
            // one secondary row; the switch is highlighted only when purses (not squads) are the block.
            state.isDeadEnd -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DockButton("Next Player", R.drawable.ic_skip_next, enabled = !acting, onClick = viewModel::nextPlayer, modifier = Modifier.weight(1f))
                    DockButton("Undo", R.drawable.ic_undo, enabled = !acting, onClick = viewModel::undo, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                if (auction.purseBelowBase > 0) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(CrichereAuctionGold.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .border(1.dp, CrichereAuctionGold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Allow exceeding purse", style = tight(InstrumentSansFamily, 13.sp, FontWeight.Medium, 16.9.sp), color = Color.White)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                if (auction.purseBelowBase == 1) "1 franchise is stopped by its purse"
                                else "${auction.purseBelowBase} franchises are stopped by their purse",
                                style = tight(InstrumentSansFamily, 11.5.sp, FontWeight.Normal, 14.95.sp),
                                color = TextDim,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        ExceedPurseSwitch(auction, viewModel, acting)
                    }
                } else {
                    ExceedPurseRow(auction, viewModel, acting)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (acting) 0.4f else 1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Alert)
                        .clickable(enabled = !acting && !state.isEnding, onClick = viewModel::requestEnd),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_stop_circle), contentDescription = null, tint = CrichereAuctionBg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("End Auction", style = text(13.sp, FontWeight.SemiBold, 13.sp), color = CrichereAuctionBg)
                }
            }
            else -> {
                DockButton("Next Player", R.drawable.ic_skip_next, primary = true, enabled = !acting, onClick = viewModel::nextPlayer)
                Spacer(Modifier.height(12.dp))
                DockButton("Undo", R.drawable.ic_undo, enabled = !acting, onClick = viewModel::undo)
                Spacer(Modifier.height(14.dp))
                ExceedPurseRow(auction, viewModel, acting)
                Spacer(Modifier.height(12.dp))
                EndAuctionOutlined(enabled = !acting && !state.isEnding, onClick = viewModel::requestEnd)
            }
        }
    }
    state.actionError?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, style = text(12.sp, FontWeight.Medium, 16.2.sp), color = Alert)
    }
}

@Composable
private fun ExceedPurseRow(auction: AuctionStateDto, viewModel: AuctionViewModel, acting: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Allow exceeding purse",
            style = text(13.sp, FontWeight.Medium, 16.9.sp),
            color = TextSoft,
            modifier = Modifier.weight(1f).padding(start = 2.dp),
        )
        ExceedPurseSwitch(auction, viewModel, acting)
    }
}

@Composable
private fun ExceedPurseSwitch(auction: AuctionStateDto, viewModel: AuctionViewModel, acting: Boolean) {
    Switch(
        checked = auction.allowExceedPurse,
        onCheckedChange = viewModel::toggleExceedPurse,
        enabled = !acting,
        colors = SwitchDefaults.colors(
            checkedThumbColor = CrichereAuctionBg,
            checkedTrackColor = CrichereAuctionGold,
            uncheckedThumbColor = CrichereAuctionMuted,
            uncheckedTrackColor = Color.White.copy(alpha = 0.14f),
            uncheckedBorderColor = Color.Transparent,
        ),
        modifier = Modifier.height(24.dp),
    )
}

@Composable
private fun EndAuctionOutlined(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Alert.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_stop_circle), contentDescription = null, tint = Alert, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("End Auction", style = text(13.sp, FontWeight.SemiBold, 13.sp), color = Alert)
    }
}

/** "Sold to Kolhapur Kings · ₹15,000" -- just "Sold", dimmed, until someone bids. */
@Composable
private fun SoldButton(auction: AuctionStateDto, enabled: Boolean, onClick: () -> Unit) {
    val leader = auction.currentLeadingFranchiseName
    val amount = auction.currentBidAmount
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (leader != null) 1f else 0.4f)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CrichereAuctionGold)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_gavel), contentDescription = null, tint = CrichereAuctionBg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            buildAnnotatedString {
                append(if (leader != null) "Sold to $leader" else "Sold")
                if (leader != null && amount != null) {
                    append(" · ")
                    withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily)) { append(rupees(amount)) }
                }
            },
            style = text(14.sp, FontWeight.Bold, 14.sp),
            color = CrichereAuctionBg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DockButton(label: String, icon: Int, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), primary: Boolean = false) {
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) CrichereAuctionGold else GhostButton)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (primary) CrichereAuctionBg else Color.White
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = text(13.sp, FontWeight.SemiBold, 13.sp), color = content, maxLines = 1)
    }
}

/** A franchise's logo, or its initials on its tile colour. */
@Composable
private fun FranchiseTile(id: String, name: String, franchises: List<LeagueFranchiseDto>, size: Dp, radius: Dp, fontSize: TextUnit) {
    val logo = franchises.firstOrNull { it.id == id }?.logoUrl
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(tileColor(id)), contentAlignment = Alignment.Center) {
        Text(shortCode(name.ifBlank { "?" }), style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, lineHeight = fontSize), color = Color.White)
        if (logo != null) {
            coil3.compose.AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Profile photo, or gold initials when there's none / it fails (board L4). */
@Composable
private fun PlayerPhoto(url: String?, name: String, size: Dp, radius: Dp, initialsSize: TextUnit) {
    val shape = RoundedCornerShape(radius)
    val initialsBox: @Composable () -> Unit = {
        Box(Modifier.size(size).background(CrichereAuctionGold.copy(alpha = 0.16f), shape), contentAlignment = Alignment.Center) {
            Text(
                initials(name),
                style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = initialsSize, lineHeight = initialsSize, letterSpacing = (-0.3).sp),
                color = CrichereAuctionGold,
            )
        }
    }
    if (url == null) {
        initialsBox()
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = name,
        contentScale = ContentScale.Crop,
        loading = { Box(Modifier.size(size).background(PhotoPlaceholder, shape)) },
        error = { initialsBox() },
        modifier = Modifier.size(size).clip(shape),
    )
}

@Composable
private fun Loading() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = CrichereAuctionGold, trackColor = Color.White.copy(alpha = 0.12f), strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(7.dp))
        Text("Loading auction…", style = text(13.sp, FontWeight.Medium, 13.sp), color = CrichereAuctionMuted)
    }
}

@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_cloud_off), contentDescription = null, tint = CrichereAuctionMuted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text("Couldn't load the auction", style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp), color = Color.White)
        Spacer(Modifier.height(10.dp))
        Text("Check your connection and try again.", style = text(13.sp, lineHeight = 18.85.sp), color = CrichereAuctionMuted)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(CrichereAuctionGold).clickable(onClick = onRetry).padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Retry", style = text(13.5.sp, FontWeight.SemiBold, 13.5.sp), color = CrichereAuctionBg)
        }
    }
}

private fun text(size: TextUnit, weight: FontWeight = FontWeight.Normal, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = InstrumentSansFamily, fontWeight = weight, fontSize = size, lineHeight = lineHeight)

/**
 * A style with its line height set and no font padding, so the line box is exactly [lineHeight] and text
 * sits centred in it -- the U4 A5 fix for cards coming out taller than the board's CSS line boxes.
 */
private fun tight(family: FontFamily, size: TextUnit, weight: FontWeight, lineHeight: TextUnit, letterSpacing: TextUnit = TextUnit.Unspecified) =
    TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None),
    )

// The ₹ glyph comes from a fallback font with a taller ascent; trimming keeps a 36sp amount on a
// 36sp line, as the board draws it (measured +11dp untrimmed on CPH2487).
private fun mono(size: TextUnit, weight: FontWeight, letterSpacing: TextUnit = TextUnit.Unspecified) =
    TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = weight,
        fontSize = size,
        lineHeight = size,
        letterSpacing = letterSpacing,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
    )

/** "12s ago", "3m ago", "1h ago" from an ISO instant; blank if it can't be parsed. */
internal fun timeAgo(iso: String, nowMillis: Long): String {
    val placed = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val seconds = Duration.between(placed, Instant.ofEpochMilli(nowMillis)).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        else -> "${seconds / 3600}h ago"
    }
}

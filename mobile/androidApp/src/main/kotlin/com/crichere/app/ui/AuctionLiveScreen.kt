package com.crichere.app.ui

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
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
        ) {
            StatusChip(auction.auctionStatus)
            if (auction.auctionStatus == AuctionStatus.IN_PROGRESS && auction.playersTotal > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (auction.currentPlayerId != null) "Player ${auction.playersSold + 1} of ${auction.playersTotal}"
                    else "${auction.playersSold} of ${auction.playersTotal} done",
                    style = text(11.5.sp, FontWeight.Medium, 11.5.sp),
                    color = CrichereAuctionMuted,
                )
            }
            if (state.connectionLost) {
                Spacer(Modifier.height(8.dp))
                ReconnectLine(onRetry = viewModel::retry)
            }
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
                    if (auction.currentPlayerId != null) {
                        BlockCard(auction, state.minimumNextBid, league?.franchises.orEmpty())
                        Spacer(Modifier.height(15.dp))
                        RecentBids(auction.recentBids, league?.franchises.orEmpty())
                    } else {
                        EmptyBlockCard(auction, state.isOrganizer)
                    }
                }
                AuctionStatus.COMPLETED -> {
                    Spacer(Modifier.height(12.dp))
                    Text("This auction has ended.", style = text(13.5.sp, FontWeight.Medium, 17.55.sp), color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Results(state.results?.franchises.orEmpty(), league?.franchises.orEmpty())
                }
            }
        }
        Dock(state, auction, viewModel)
    }
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

/** "● Live" (pulsing), "Not started", "Ended". */
@Composable
private fun StatusChip(status: AuctionStatus) {
    val live = status == AuctionStatus.IN_PROGRESS
    Row(
        Modifier
            .height(26.dp)
            .background(if (live) CrichereAuctionGold.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.07f), RoundedCornerShape(13.dp))
            .padding(start = if (live) 9.dp else 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) {
            val pulse by rememberInfiniteTransition(label = "live").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
                label = "livePulse",
            )
            Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(8.dp + 10.dp * pulse)
                        .background(CrichereAuctionGold.copy(alpha = 0.35f * (1f - pulse)), CircleShape),
                )
                Box(Modifier.size(8.dp).background(CrichereAuctionGold, CircleShape))
            }
            Spacer(Modifier.width(6.dp))
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

@Composable
private fun ReconnectLine(onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Lost connection to the live auction.", style = text(12.sp, FontWeight.Medium, 16.sp), color = Alert, modifier = Modifier.weight(1f))
        Text(
            "Retry",
            style = text(12.sp, FontWeight.SemiBold, 12.sp),
            color = CrichereAuctionGold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRetry).padding(6.dp),
        )
    }
}

/** L1/L2/L4: photo, name, role, current bid with the leading franchise, minimum next bid. */
@Composable
private fun BlockCard(auction: AuctionStateDto, minimumNextBid: Double?, franchises: List<LeagueFranchiseDto>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CrichereAuctionSurface, RoundedCornerShape(20.dp))
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .padding(15.dp),
    ) {
        Row {
            val name = auction.currentPlayerName ?: "Unnamed player"
            PlayerPhoto(auction.currentPlayerPhotoUrl, name, size = 88.dp, radius = 20.dp, initialsSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.padding(top = 7.dp)) {
                Text("ON THE BLOCK", style = mono(10.sp, FontWeight.SemiBold, letterSpacing = 0.8.sp), color = CrichereAuctionGold)
                Spacer(Modifier.height(8.dp))
                Text(
                    name,
                    style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 24.2.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                auction.currentPlayerRole?.let { role ->
                    Spacer(Modifier.height(9.dp))
                    Box(
                        Modifier
                            .height(22.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(11.dp))
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(role.label(), style = text(11.5.sp, FontWeight.SemiBold, 11.5.sp), color = TextSoft)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Spacer(Modifier.height(14.dp))
        Text("Current bid", style = text(12.sp, FontWeight.Medium, 12.sp), color = CrichereAuctionMuted)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                auction.currentBidAmount?.let { rupees(it) } ?: "No bids yet",
                style = if (auction.currentBidAmount != null) mono(36.sp, FontWeight.Bold, letterSpacing = (-0.72).sp) else text(20.sp, FontWeight.SemiBold, 36.sp),
                color = if (auction.currentBidAmount != null) Color.White else TextDim,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            val leaderId = auction.currentLeadingFranchiseId
            if (leaderId != null) {
                Spacer(Modifier.width(8.dp))
                FranchiseTile(leaderId, auction.currentLeadingFranchiseName.orEmpty(), franchises, size = 20.dp, radius = 6.dp, fontSize = 7.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    auction.currentLeadingFranchiseName.orEmpty(),
                    style = text(13.sp, FontWeight.SemiBold, 15.6.sp),
                    color = TextSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 130.dp),
                )
            }
        }
        if (minimumNextBid != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    append(if (auction.currentBidAmount == null) "Opening bid at least " else "Next bid at least ")
                    withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, color = TextDim)) { append(rupees(minimumNextBid)) }
                },
                style = text(12.5.sp, FontWeight.Medium, 12.5.sp),
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
        val last = auction.lastResult
        val lastLine = last?.let {
            val name = it.playerName ?: "The last player"
            if (it.sold) "Last: $name sold to ${it.franchiseName ?: "a franchise"}" + (it.amount?.let { a -> " for ${rupees(a)}." } ?: ".")
            else "Last: $name went unsold."
        }
        val nextStep = when {
            isOrganizer && !auction.canAnyoneBid -> "No franchise can bid on the remaining players: squads are full or purses are spent. End the auction, or allow exceeding the purse if purses are the limit."
            isOrganizer -> "Bring up the next player."
            else -> "Waiting for the organizer to bring up the next player."
        }
        val body = listOfNotNull(lastLine, nextStep).joinToString(" ")
        Spacer(Modifier.height(7.dp))
        Text(body, style = text(13.sp, lineHeight = 18.85.sp), color = CrichereAuctionMuted)
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
private fun Results(results: List<FranchiseAuctionResultDto>, franchises: List<LeagueFranchiseDto>) {
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
                expanded = expandedId == result.franchiseId,
                onToggle = { expandedId = if (expandedId == result.franchiseId) "" else result.franchiseId },
            )
        }
    }
}

@Composable
private fun ResultCard(result: FranchiseAuctionResultDto, franchises: List<LeagueFranchiseDto>, expanded: Boolean, onToggle: () -> Unit) {
    var showAll by remember(result.franchiseId) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(CrichereAuctionSurface, RoundedCornerShape(14.dp))
            .border(1.dp, if (expanded) CrichereAuctionGold.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 13.dp, end = 12.dp, top = 13.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FranchiseTile(result.franchiseId, result.franchiseName, franchises, size = 32.dp, radius = 9.dp, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(result.franchiseName, style = text(14.sp, FontWeight.SemiBold, 14.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(
                    buildAnnotatedString {
                        val players = result.playersWon.size
                        append("$players ${if (players == 1) "player" else "players"} · ")
                        withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily)) { append(rupees(result.purseSpent)) }
                        append(" spent")
                        result.purseRemaining?.let {
                            append(" · ")
                            withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily)) { append(rupees(it)) }
                            append(" left")
                        }
                        if (result.belowSquadMin) withStyle(SpanStyle(color = Alert)) { append(" · below min squad") }
                    },
                    style = text(11.5.sp, FontWeight.Medium, 13.sp),
                    color = CrichereAuctionMuted,
                )
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

/** The docked panel: bid form (franchise owner, player up), organizer controls, or the viewer note. */
@Composable
private fun Dock(state: AuctionState, auction: AuctionStateDto, viewModel: AuctionViewModel) {
    val live = auction.auctionStatus == AuctionStatus.IN_PROGRESS
    val playerUp = live && auction.currentPlayerId != null
    val canBid = playerUp && state.myFranchiseId != null
    val showViewerNote = playerUp && !canBid && !state.isOrganizer
    if (!canBid && !state.isOrganizer && !showViewerNote) return

    Column(
        Modifier
            .fillMaxWidth()
            .background(Dock, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .border(1.dp, Hairline, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 20.dp),
    ) {
        if (showViewerNote) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_visibility), contentDescription = null, tint = CrichereAuctionMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Only franchise owners can bid.", style = text(13.sp, FontWeight.Medium, 18.2.sp), color = TextDim)
            }
        }
        if (canBid) BidForm(state, viewModel)
        if (state.isOrganizer) {
            if (canBid) Spacer(Modifier.height(16.dp))
            OrganizerControls(state, auction, viewModel)
        }
    }
}

/** L1 / L6 / L7. */
@Composable
private fun BidForm(state: AuctionState, viewModel: AuctionViewModel) {
    val context = state.biddingContext
    if (context != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FranchiseTile(context.franchiseId, context.franchiseName, state.league?.franchises.orEmpty(), size = 18.dp, radius = 5.dp, fontSize = 6.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                buildAnnotatedString {
                    append("Bidding as ")
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) { append(context.franchiseName) }
                    context.purseLeft?.let { append(" · ${rupees(it)} left") }
                    context.squadMax?.let { append(" · ${context.playersWon}/$it players") }
                },
                style = text(12.5.sp, FontWeight.Medium, 16.25.sp),
                color = TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
    AmountField(
        value = state.bidAmountInput,
        onValueChange = viewModel::onBidAmountChanged,
        error = state.bidError,
        enabled = !state.isBidding,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val increment = state.bidIncrement
        if (increment != null) {
            Box(
                Modifier
                    .alpha(if (state.isBidding) 0.4f else 1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, CrichereAuctionGold.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                    .clickable(enabled = !state.isBidding, onClick = viewModel::onStepBid)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("+${rupees(increment)}", style = mono(14.sp, FontWeight.Bold), color = CrichereAuctionGold)
            }
        }
        Row(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (state.isBidding) CrichereAuctionGold.copy(alpha = 0.35f) else CrichereAuctionGold)
                .clickable(enabled = !state.isBidding, onClick = viewModel::placeBid),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isBidding) {
                CircularProgressIndicator(color = CrichereAuctionBg, trackColor = CrichereAuctionBg.copy(alpha = 0.25f), strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.isBidding) "Placing…" else "Place Bid", style = text(14.5.sp, FontWeight.Bold, 14.5.sp), color = CrichereAuctionBg)
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

/** The ₹ amount field in the dock's dark style: notched label, gold when focused, coral on error. */
@Composable
private fun AmountField(value: String, onValueChange: (String) -> Unit, error: String?, enabled: Boolean) {
    val accent = if (error != null) Alert else CrichereAuctionGold
    Column(Modifier.alpha(if (enabled) 1f else 0.55f)) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .border(2.dp, accent, RoundedCornerShape(12.dp))
                    .padding(start = 16.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("₹", style = mono(18.sp, FontWeight.SemiBold), color = CrichereAuctionMuted)
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = value,
                    onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '.' }) },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = mono(18.sp, FontWeight.SemiBold).copy(color = Color.White),
                    cursorBrush = SolidColor(CrichereAuctionGold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = IndianAmountTransformation,
                    modifier = Modifier.weight(1f),
                )
                if (error != null) {
                    Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = Alert, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                "Amount",
                style = text(11.sp, FontWeight.Medium, 11.sp),
                color = accent,
                modifier = Modifier.offset(x = 12.dp, y = (-6).dp).background(Dock).padding(horizontal = 4.dp),
            )
        }
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = text(12.sp, FontWeight.Medium, 16.2.sp), color = Alert)
        }
    }
}

/** L2 (player up), L8 (between players), L3 (not started), L5 (ended). */
@Composable
private fun OrganizerControls(state: AuctionState, auction: AuctionStateDto, viewModel: AuctionViewModel) {
    val acting = state.isActing
    Text("Organizer controls", style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 13.sp), color = Color.White)
    Spacer(Modifier.height(12.dp))
    when (auction.auctionStatus) {
        AuctionStatus.NOT_STARTED -> DockButton("Start Auction", R.drawable.ic_play_arrow, primary = true, enabled = !acting, onClick = viewModel::start)
        AuctionStatus.COMPLETED -> DockButton("Undo", R.drawable.ic_undo, enabled = !acting, onClick = viewModel::undo)
        AuctionStatus.IN_PROGRESS -> {
            if (auction.currentPlayerId != null) {
                SoldButton(auction, enabled = !acting && auction.currentLeadingFranchiseId != null, onClick = viewModel::sold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DockButton("Unsold", R.drawable.ic_block, enabled = !acting, onClick = viewModel::unsold, modifier = Modifier.weight(1f))
                    // Owner decision 2026-10-02: shown but disabled while a player is up -- the server
                    // only opens the next player after Sold / Unsold.
                    DockButton("Next Player", R.drawable.ic_skip_next, enabled = false, onClick = viewModel::nextPlayer, modifier = Modifier.weight(1f))
                    DockButton("Undo", R.drawable.ic_undo, enabled = !acting, onClick = viewModel::undo, modifier = Modifier.weight(1f))
                }
            } else {
                DockButton("Next Player", R.drawable.ic_skip_next, primary = true, enabled = !acting, onClick = viewModel::nextPlayer)
                Spacer(Modifier.height(12.dp))
                DockButton("Undo", R.drawable.ic_undo, enabled = !acting, onClick = viewModel::undo)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Allow exceeding purse",
                    style = text(13.sp, FontWeight.Medium, 16.9.sp),
                    color = TextSoft,
                    modifier = Modifier.weight(1f).padding(start = 2.dp),
                )
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
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Alert.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .clickable(enabled = !acting, onClick = viewModel::end),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_stop_circle), contentDescription = null, tint = Alert, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("End Auction", style = text(13.sp, FontWeight.SemiBold, 13.sp), color = Alert)
            }
        }
    }
    state.actionError?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, style = text(12.sp, FontWeight.Medium, 16.2.sp), color = Alert)
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

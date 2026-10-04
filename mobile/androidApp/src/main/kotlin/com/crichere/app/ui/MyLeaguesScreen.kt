package com.crichere.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.crichere.app.R
import com.crichere.app.league.LeagueSummaryDto
import com.crichere.app.league.MyLeaguesDto
import com.crichere.app.league.MyLeaguesState
import com.crichere.app.league.MyLeaguesViewModel
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereErrorBody
import com.crichere.app.ui.theme.CrichereErrorStrong
import org.koin.compose.viewmodel.koinViewModel

private val Chevron = Color(0xFF9AA39C)

/** Resolves [MyLeaguesViewModel] via Koin -- no per-instance key needed, there's only ever one "my leagues" view, unlike per-league-id screens. See `AuthNavHost`'s `MainRoute`. */
@Composable
internal fun MyLeaguesRoute(onOpenLeague: (String) -> Unit, onBrowseLeagues: () -> Unit, onCreateLeague: () -> Unit) {
    val viewModel: MyLeaguesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.retry() }

    MyLeaguesScreen(state = state, onOpenLeague = onOpenLeague, onRetry = viewModel::retry, onBrowseLeagues = onBrowseLeagues, onCreateLeague = onCreateLeague)
}

/**
 * Design M1-M3: four always-visible stacked sections -- Organizing / Playing / Franchise owner /
 * Following (see docs/PHASE3.md's Screens section) -- with empty ones hidden; an empty state when
 * every section is empty (M2), and loading / error with Retry (M3). A refresh on re-entry keeps
 * the last list on screen instead of flashing the spinner.
 */
@Composable
private fun MyLeaguesScreen(
    state: MyLeaguesState,
    onOpenLeague: (String) -> Unit,
    onRetry: () -> Unit,
    onBrowseLeagues: () -> Unit,
    onCreateLeague: () -> Unit,
) {
    val data = state.data
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().padding(start = 19.dp, end = 19.dp).height(48.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                "My leagues",
                style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 28.sp, letterSpacing = (-0.84).sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        when {
            data == null && state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp, modifier = Modifier.size(35.dp))
            }
            data == null -> LoadFailed(onRetry)
            data.isEmpty() -> EmptyState(onBrowseLeagues, onCreateLeague)
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 15.dp)) {
                leagueSection("Organizing", data.organizing, first = true, onOpenLeague = onOpenLeague)
                leagueSection("Playing", data.playing, first = data.organizing.isEmpty(), onOpenLeague = onOpenLeague)
                leagueSection("Franchise owner", data.franchiseOwner, first = data.organizing.isEmpty() && data.playing.isEmpty(), onOpenLeague = onOpenLeague)
                leagueSection("Following", data.following, first = data.organizing.isEmpty() && data.playing.isEmpty() && data.franchiseOwner.isEmpty(), onOpenLeague = onOpenLeague)
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

private fun MyLeaguesDto.isEmpty() = organizing.isEmpty() && playing.isEmpty() && franchiseOwner.isEmpty() && following.isEmpty()

private fun LazyListScope.leagueSection(title: String, leagues: List<LeagueSummaryDto>, first: Boolean, onOpenLeague: (String) -> Unit) {
    if (leagues.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title,
            style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, lineHeight = 14.5.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = if (first) 4.dp else 12.dp, bottom = 8.dp),
        )
    }
    // Index in the key too -- the backend already dedupes each list by league id (a user can own
    // more than one franchise in the same league, see docs/PHASE3.md's Decisions Made), but a
    // duplicate id here would otherwise crash this LazyColumn outright rather than just rendering
    // oddly, so this is defense-in-depth against that same class of bug recurring.
    itemsIndexed(leagues, key = { index, item -> "$title-${item.id}-$index" }) { index, league ->
        if (index > 0) Spacer(Modifier.height(8.dp))
        LeagueRow(league, onClick = { onOpenLeague(league.id) })
    }
}

@Composable
private fun LeagueRow(league: LeagueSummaryDto, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 15.dp, end = 15.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(tileColor(league.id)), contentAlignment = Alignment.Center) {
            Text(
                shortCode(league.name),
                style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, lineHeight = 11.sp),
                color = Color.White,
            )
            league.logoUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                league.name,
                style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.1.sp),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            // Only the place may be cut short, never the start date after it.
            Row {
                Text(
                    "${league.city}, ${league.state}",
                    style = pText(12.sp, lineHeight = 14.4.sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    " -- ${league.startsOn}",
                    style = pText(12.sp, lineHeight = 14.4.sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Chevron, modifier = Modifier.size(20.dp))
    }
}

/** M2 (app fix: this used to be a blank screen). */
@Composable
private fun EmptyState(onBrowseLeagues: () -> Unit, onCreateLeague: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 41.dp).padding(bottom = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_sports_cricket), contentDescription = null, tint = colors.outline, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(15.dp))
        Text("No leagues yet", style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 21.6.sp), color = colors.onBackground)
        Spacer(Modifier.height(10.dp))
        Text(
            "Join one as a player, claim a franchise, follow one, or start your own.",
            style = pText(13.sp, lineHeight = 18.85.sp),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(colors.primary).clickable(onClick = onBrowseLeagues).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Browse leagues", style = pText(13.5.sp, FontWeight.SemiBold, 13.5.sp), color = Color.White) }
            Box(
                Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).border(1.dp, colors.outline, RoundedCornerShape(21.dp)).clickable(onClick = onCreateLeague).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Create", style = pText(13.5.sp, FontWeight.SemiBold, 13.5.sp), color = colors.primary) }
        }
    }
}

/** M3: error card with Retry. */
@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 25.dp).padding(bottom = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(14.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_cloud_off), contentDescription = null, tint = CrichereErrorStrong, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Couldn't load your leagues.", style = pText(13.sp, FontWeight.Medium, 17.55.sp), color = CrichereErrorBody)
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, colors.outline, RoundedCornerShape(22.dp)).clickable(onClick = onRetry).padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Retry", style = pText(13.5.sp, FontWeight.SemiBold, 13.5.sp), color = colors.primary) }
    }
}

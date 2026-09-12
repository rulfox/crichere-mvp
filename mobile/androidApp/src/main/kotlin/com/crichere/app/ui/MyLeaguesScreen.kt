package com.crichere.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.league.LeagueSummaryDto
import com.crichere.app.league.MyLeaguesState
import com.crichere.app.league.MyLeaguesViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Resolves [MyLeaguesViewModel] via Koin -- no per-instance key needed, there's only ever one "my leagues" view, unlike per-league-id screens. See `AuthNavHost`'s `MainTab.MY_LEAGUES`. */
@Composable
internal fun MyLeaguesRoute(onOpenLeague: (String) -> Unit) {
    val viewModel: MyLeaguesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.retry() }

    MyLeaguesScreen(state = state, onOpenLeague = onOpenLeague, onRetry = viewModel::retry)
}

/** Four labeled sections -- Organizing / Playing / Franchise owner / Following -- see docs/PHASE3.md's Screens section. */
@Composable
private fun MyLeaguesScreen(state: MyLeaguesState, onOpenLeague: (String) -> Unit, onRetry: () -> Unit) {
    val data = state.data
    when {
        state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        data == null -> Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = state.errorMessage ?: "Couldn't load your leagues.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
        }

        else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            leagueSection("Organizing", data.organizing, onOpenLeague)
            leagueSection("Playing", data.playing, onOpenLeague)
            leagueSection("Franchise owner", data.franchiseOwner, onOpenLeague)
            leagueSection("Following", data.following, onOpenLeague)
        }
    }
}

private fun LazyListScope.leagueSection(
    title: String,
    leagues: List<LeagueSummaryDto>,
    onOpenLeague: (String) -> Unit,
) {
    if (leagues.isEmpty()) return
    item { Text(text = title, style = MaterialTheme.typography.titleMedium) }
    // Index in the key too -- the backend already dedupes each list by league id (a user can own
    // more than one franchise in the same league, see docs/PHASE3.md's Decisions Made), but a
    // duplicate id here would otherwise crash this LazyColumn outright rather than just rendering
    // oddly, so this is defense-in-depth against that same class of bug recurring.
    itemsIndexed(leagues, key = { index, item -> "$title-${item.id}-$index" }) { _, league ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clickable { onOpenLeague(league.id) },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(text = league.name, style = MaterialTheme.typography.titleSmall)
                Text(text = "${league.city}, ${league.state} -- ${league.startsOn}")
            }
        }
    }
}

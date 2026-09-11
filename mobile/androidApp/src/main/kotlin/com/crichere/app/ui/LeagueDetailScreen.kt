package com.crichere.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.league.LeagueAwardDto
import com.crichere.app.league.LeagueDetailState
import com.crichere.app.league.LeagueDetailViewModel
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueStatus
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Resolves [LeagueDetailViewModel] via Koin, parameterized on [leagueId] -- see `AuthNavHost`'s `MainDestination.LeagueDetail`. */
@Composable
internal fun LeagueDetailRoute(leagueId: String, onBack: () -> Unit, onEditLeague: (String) -> Unit) {
    val viewModel: LeagueDetailViewModel = koinViewModel(key = "league-detail:$leagueId") { parametersOf(leagueId) }
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
    )
}

/**
 * League Detail: read view of a single league (ground/schedule/format/capacity/fees/awards),
 * plus organizer-only Edit/Mark-completed actions gated on [LeagueDetailState.isOrganizer].
 * Content-only, same as [LeagueDashboardScreen] -- rendered above `MainRoute`'s tab `Scaffold`,
 * not inside it.
 */
@Composable
private fun LeagueDetailScreen(
    state: LeagueDetailState,
    onBack: () -> Unit,
    onEditLeague: () -> Unit,
    onMarkCompleted: () -> Unit,
    onRetry: () -> Unit,
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
                isOrganizer = state.isOrganizer,
                isCompleting = state.isCompleting,
                errorMessage = state.errorMessage,
                onEditLeague = onEditLeague,
                onMarkCompleted = onMarkCompleted,
            )
        }
    }
}

@Composable
private fun LeagueDetailContent(
    league: LeagueDto,
    isOrganizer: Boolean,
    isCompleting: Boolean,
    errorMessage: String?,
    onEditLeague: () -> Unit,
    onMarkCompleted: () -> Unit,
) {
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
                    if (errorMessage != null) {
                        Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = onEditLeague, modifier = Modifier.fillMaxWidth()) {
                        Text("Edit league")
                    }
                    if (league.status != LeagueStatus.COMPLETED) {
                        OutlinedButton(onClick = onMarkCompleted, enabled = !isCompleting, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isCompleting) "Marking completed..." else "Mark completed")
                        }
                    }
                }
            }
        }
    }
}

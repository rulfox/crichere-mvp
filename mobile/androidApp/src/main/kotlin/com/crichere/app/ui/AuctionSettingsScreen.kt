package com.crichere.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.league.AuctionSettingsState
import com.crichere.app.league.AuctionSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Resolves [AuctionSettingsViewModel] via Koin, parameterized on [leagueId] -- see `AuthNavHost`'s `MainDestination.AuctionSettings`. */
@Composable
internal fun AuctionSettingsRoute(leagueId: String, onBack: () -> Unit) {
    val viewModel: AuctionSettingsViewModel = koinViewModel(key = "auction-settings:$leagueId") { parametersOf(leagueId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.retry() }

    AuctionSettingsScreen(
        state = state,
        onBasePriceChanged = viewModel::onBasePriceChanged,
        onPurseChanged = viewModel::onPurseChanged,
        onSquadMinChanged = viewModel::onSquadMinChanged,
        onSquadMaxChanged = viewModel::onSquadMaxChanged,
        onBidIncrementChanged = viewModel::onBidIncrementChanged,
        onSubmit = viewModel::submit,
        onBack = onBack,
    )
}

/**
 * Auction Settings: five editable fields (base price, purse, squad min/max, bid increment) plus
 * a read-only auction pool/purse view -- rendered from the same loaded [com.crichere.app.league.LeagueDto]
 * already on [AuctionSettingsState.league], no second network call (see docs/PHASE4.md's Decisions
 * Made: settings and the pool view are one screen, not two).
 */
@Composable
private fun AuctionSettingsScreen(
    state: AuctionSettingsState,
    onBasePriceChanged: (String) -> Unit,
    onPurseChanged: (String) -> Unit,
    onSquadMinChanged: (String) -> Unit,
    onSquadMaxChanged: (String) -> Unit,
    onBidIncrementChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("Back") }

        val league = state.league
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            league == null -> {
                val errorMessage = state.errorMessage
                if (errorMessage != null) Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            }

            else -> {
                Text(text = "Auction Settings", style = MaterialTheme.typography.headlineSmall)

                OutlinedTextField(value = state.basePrice, onValueChange = onBasePriceChanged, label = { Text("Base price") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.purse, onValueChange = onPurseChanged, label = { Text("Purse per franchise") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.squadMin, onValueChange = onSquadMinChanged, label = { Text("Squad size (min)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.squadMax, onValueChange = onSquadMaxChanged, label = { Text("Squad size (max)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.bidIncrement, onValueChange = onBidIncrementChanged, label = { Text("Bid increment") }, modifier = Modifier.fillMaxWidth())

                if (league.auctionSquadMaxWarning) {
                    Text(
                        text = "This may be impossible to satisfy: squad max times the number of franchises required exceeds players required.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                val errorMessage = state.errorMessage
                if (errorMessage != null) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }

                Button(onClick = onSubmit, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.isSaving) "Saving..." else "Save")
                }

                Text(text = "Auction pool", style = MaterialTheme.typography.titleMedium)
                Text(text = "${league.players.size} player(s) joined")
                if (league.franchises.isNotEmpty()) {
                    Text(text = "Franchises (purse: ${state.purse.ifBlank { "not set" }} each):")
                    league.franchises.forEach { franchise ->
                        Text(text = "- ${franchise.name}")
                    }
                } else {
                    Text(text = "No franchises have claimed yet.")
                }
            }
        }
    }
}

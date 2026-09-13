package com.crichere.app.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.league.AuctionState
import com.crichere.app.league.AuctionStatus
import com.crichere.app.league.AuctionViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Resolves [AuctionViewModel] via Koin, parameterized on [leagueId] -- see `AuthNavHost`'s `MainDestination.AuctionLive`. */
@Composable
internal fun AuctionLiveRoute(leagueId: String, onBack: () -> Unit) {
    val viewModel: AuctionViewModel = koinViewModel(key = "auction-live:$leagueId") { parametersOf(leagueId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.retry() }

    AuctionLiveScreen(
        state = state,
        onBidAmountChanged = viewModel::onBidAmountChanged,
        onPlaceBid = viewModel::placeBid,
        onStart = viewModel::start,
        onNextPlayer = viewModel::nextPlayer,
        onSold = viewModel::sold,
        onUnsold = viewModel::unsold,
        onUndo = viewModel::undo,
        onEnd = viewModel::end,
        onToggleExceedPurse = viewModel::toggleExceedPurse,
        onBack = onBack,
    )
}

/**
 * The live auction (see docs/PHASE5.md) -- organizer controls (start/next-player/sold/unsold/
 * undo/end/exceed-purse toggle) and a bidding form for the caller's own franchise render
 * independently, since dual roles are allowed (an organizer can also own a franchise here, see
 * docs/PHASE3.md's Decisions Made). State comes from [AuctionState.auction], kept live by the
 * ViewModel's own SSE subscription -- this composable has no manual refresh action.
 */
@Composable
private fun AuctionLiveScreen(
    state: AuctionState,
    onBidAmountChanged: (String) -> Unit,
    onPlaceBid: () -> Unit,
    onStart: () -> Unit,
    onNextPlayer: () -> Unit,
    onSold: () -> Unit,
    onUnsold: () -> Unit,
    onUndo: () -> Unit,
    onEnd: () -> Unit,
    onToggleExceedPurse: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("Back") }

        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            state.league == null -> {
                val errorMessage = state.errorMessage
                if (errorMessage != null) Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            }

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(text = "Live Auction", style = MaterialTheme.typography.headlineSmall)
                    Text(text = "Status: ${state.auction?.auctionStatus ?: AuctionStatus.NOT_STARTED}")
                }

                val auction = state.auction
                if (auction != null && auction.currentPlayerId != null) {
                    item {
                        HorizontalDivider()
                        Text(text = auction.currentPlayerName ?: "Current player", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Current bid: ${auction.currentBidAmount ?: "none yet"}${auction.currentLeadingFranchiseName?.let { " ($it)" } ?: ""}")
                    }
                }

                if (state.myFranchiseId != null && auction?.currentPlayerId != null) {
                    item {
                        HorizontalDivider()
                        Text(text = "Your bid", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = state.bidAmountInput,
                            onValueChange = onBidAmountChanged,
                            label = { Text("Amount") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = onPlaceBid, enabled = !state.isBidding, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.isBidding) "Placing..." else "Place Bid")
                        }
                    }
                }

                if (state.isOrganizer) {
                    item {
                        HorizontalDivider()
                        Text(text = "Organizer controls", style = MaterialTheme.typography.titleMedium)
                        when (auction?.auctionStatus ?: AuctionStatus.NOT_STARTED) {
                            AuctionStatus.NOT_STARTED -> Button(onClick = onStart, enabled = !state.isActing, modifier = Modifier.fillMaxWidth()) {
                                Text("Start Auction")
                            }

                            AuctionStatus.IN_PROGRESS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onNextPlayer, enabled = !state.isActing, modifier = Modifier.fillMaxWidth()) { Text("Next Player") }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onSold, enabled = !state.isActing, modifier = Modifier.fillMaxWidth().weight(1f)) { Text("Sold") }
                                    OutlinedButton(onClick = onUnsold, enabled = !state.isActing, modifier = Modifier.fillMaxWidth().weight(1f)) { Text("Unsold") }
                                }
                                OutlinedButton(onClick = onUndo, enabled = !state.isActing, modifier = Modifier.fillMaxWidth()) { Text("Undo") }
                                OutlinedButton(onClick = onEnd, enabled = !state.isActing, modifier = Modifier.fillMaxWidth()) { Text("End Auction") }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Allow exceeding purse", modifier = Modifier.weight(1f))
                                    Switch(checked = auction?.allowExceedPurse == true, onCheckedChange = onToggleExceedPurse, enabled = !state.isActing)
                                }
                            }

                            AuctionStatus.COMPLETED -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("This auction has ended.")
                                // The sale/unsold call that completed the auction is still undoable
                                // (AuctionService.undo reverts COMPLETED back to IN_PROGRESS) -- the
                                // client has no way to know whether there's actually a last action to
                                // reverse, so this stays enabled and a no-op undo surfaces the
                                // server's "nothing to undo" error like any other rejected action.
                                OutlinedButton(onClick = onUndo, enabled = !state.isActing, modifier = Modifier.fillMaxWidth()) { Text("Undo") }
                            }
                        }
                    }
                }

                val results = state.results
                if (results != null) {
                    item {
                        HorizontalDivider()
                        Text(text = "Results", style = MaterialTheme.typography.titleMedium)
                    }
                    items(results.franchises) { franchise ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(text = franchise.franchiseName, style = MaterialTheme.typography.titleSmall)
                            Text(text = "${franchise.playersWon.size} player(s) -- spent ${franchise.purseSpent}, remaining ${franchise.purseRemaining ?: "n/a"}")
                            if (franchise.belowSquadMin) {
                                Text(text = "Below minimum squad size", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                val errorMessage = state.errorMessage
                if (errorMessage != null) {
                    item { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

package com.crichere.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.league.ManageRolesState
import com.crichere.app.league.ManageRolesViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Resolves [ManageRolesViewModel] via Koin, parameterized on [leagueId] -- see `AuthNavHost`'s `MainDestination.ManageRoles`. */
@Composable
internal fun ManageRolesRoute(leagueId: String, onBack: () -> Unit) {
    val viewModel: ManageRolesViewModel = koinViewModel(key = "manage-roles:$leagueId") { parametersOf(leagueId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.retry() }

    ManageRolesScreen(
        state = state,
        onPhoneNumberChanged = viewModel::onPhoneNumberChanged,
        onLookup = viewModel::lookup,
        onGrant = viewModel::grant,
        onRevoke = viewModel::revoke,
        onBack = onBack,
    )
}

/**
 * Manage Co-Organizers (see docs/PHASE7.md): look up another registered user by phone number,
 * confirm, then grant them full organizer authority over this league; below that, the current
 * co-organizers with a per-row revoke. The confirmation step is deliberate given what's being
 * handed over -- not a bare tap-to-grant.
 */
@Composable
private fun ManageRolesScreen(
    state: ManageRolesState,
    onPhoneNumberChanged: (String) -> Unit,
    onLookup: () -> Unit,
    onGrant: () -> Unit,
    onRevoke: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showGrantConfirm by remember { mutableStateOf(false) }

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
                Text(text = "Manage Co-Organizers", style = MaterialTheme.typography.headlineSmall)
                Text(text = "A co-organizer can do everything you can do for this league.", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = state.phoneNumberInput,
                    onValueChange = onPhoneNumberChanged,
                    label = { Text("Phone number") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = onLookup, enabled = !state.isLookingUp && state.phoneNumberInput.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.isLookingUp) "Looking up..." else "Look up")
                }

                if (state.lookupAttempted) {
                    val result = state.lookupResult
                    if (result != null) {
                        Text(text = "Found: ${result.name ?: "Unnamed user"}")
                        Button(onClick = { showGrantConfirm = true }, enabled = !state.isGranting, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.isGranting) "Granting..." else "Grant")
                        }
                    } else {
                        Text(text = "No user found with that phone number.", color = MaterialTheme.colorScheme.error)
                    }
                }

                val errorMessage = state.errorMessage
                if (errorMessage != null) Text(text = errorMessage, color = MaterialTheme.colorScheme.error)

                Text(text = "Current co-organizers", style = MaterialTheme.typography.titleMedium)
                if (league.coOrganizers.isEmpty()) {
                    Text(text = "No co-organizers yet.")
                } else {
                    league.coOrganizers.forEach { role ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = role.name ?: "Unnamed user")
                            TextButton(onClick = { onRevoke(role.id) }, enabled = role.id !in state.revokingRoleIds) {
                                Text(if (role.id in state.revokingRoleIds) "Revoking..." else "Revoke")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGrantConfirm) {
        val result = state.lookupResult
        AlertDialog(
            onDismissRequest = { showGrantConfirm = false },
            title = { Text("Grant co-organizer access?") },
            text = { Text("${result?.name ?: "This user"} will be able to do everything you can do for this league, including editing it and running the auction.") },
            confirmButton = {
                TextButton(onClick = { showGrantConfirm = false; onGrant() }) { Text("Grant") }
            },
            dismissButton = {
                TextButton(onClick = { showGrantConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

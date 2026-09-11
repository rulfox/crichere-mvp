package com.crichere.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.league.LeagueDashboardState
import com.crichere.app.league.LeagueDashboardViewModel
import com.crichere.app.league.LeagueDto
import com.crichere.app.reference.CityDto
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.StateDto
import org.koin.compose.viewmodel.koinViewModel

/** Resolves [LeagueDashboardViewModel] via Koin -- the app's new post-login landing content (see `AuthNavHost`'s `Main` destination). */
@Composable
internal fun LeagueDashboardRoute(onOpenLeague: (String) -> Unit, onCreateLeague: () -> Unit) {
    val viewModel: LeagueDashboardViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The ViewModel survives leaving and re-entering this tab (see its doc), so a fresh fetch on
    // every visit -- not just the ViewModel's own one-time init -- is what keeps a just-created or
    // just-completed league from looking like it never happened until the user notices and taps
    // Refresh themselves.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onToggleNearMe()
    }

    LeagueDashboardScreen(
        state = state,
        onStateSelected = viewModel::onStateSelected,
        onDistrictSelected = viewModel::onDistrictSelected,
        onCitySelected = viewModel::onCitySelected,
        onClearAreaFilters = viewModel::onClearAreaFilters,
        onToggleNearMe = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (granted || state.isNearMode) {
                viewModel.onToggleNearMe()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
        onRefresh = viewModel::refresh,
        onOpenLeague = onOpenLeague,
        onCreateLeague = onCreateLeague,
    )
}

/**
 * League Dashboard: list of announced leagues, filterable by State/District/City or "nearest to
 * me" (mutually exclusive -- see `LeagueDashboardViewModel`'s doc). Content-only: this is
 * rendered inside `AuthNavHost`'s `MainRoute` `Scaffold`, so it doesn't own its own top bar/nav.
 */
@Composable
private fun LeagueDashboardScreen(
    state: LeagueDashboardState,
    onStateSelected: (StateDto) -> Unit,
    onDistrictSelected: (DistrictDto) -> Unit,
    onCitySelected: (CityDto) -> Unit,
    onClearAreaFilters: () -> Unit,
    onToggleNearMe: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLeague: (String) -> Unit,
    onCreateLeague: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Leagues", style = MaterialTheme.typography.headlineSmall)

        Button(onClick = onCreateLeague, modifier = Modifier.fillMaxWidth()) {
            Text("Create a league")
        }

        DropdownSelector(
            label = "State",
            options = state.states,
            selected = state.states.firstOrNull { it.name == state.selectedState },
            optionLabel = StateDto::name,
            onSelected = onStateSelected,
            enabled = !state.isNearMode,
        )

        DropdownSelector(
            label = "District",
            options = state.districts,
            selected = state.districts.firstOrNull { it.name == state.selectedDistrict },
            optionLabel = DistrictDto::name,
            onSelected = onDistrictSelected,
            enabled = !state.isNearMode && state.selectedState != null,
        )

        DropdownSelector(
            label = "City",
            options = state.cities,
            selected = state.cities.firstOrNull { it.name == state.selectedCity },
            optionLabel = CityDto::name,
            onSelected = onCitySelected,
            enabled = !state.isNearMode && state.selectedDistrict != null,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onToggleNearMe, enabled = !state.isLocating, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        state.isLocating -> "Finding your location..."
                        state.isNearMode -> "Showing nearest leagues -- tap to clear"
                        else -> "Show nearest to me"
                    },
                )
            }
            if (!state.isNearMode && (state.selectedState != null || state.selectedDistrict != null || state.selectedCity != null)) {
                OutlinedButton(onClick = onClearAreaFilters, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear filters")
                }
            }
        }

        val errorMessage = state.errorMessage
        if (errorMessage != null) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        } else if (state.leagues.isEmpty()) {
            Text("No leagues found.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.leagues, key = { it.id }) { league ->
                    LeagueRow(league = league, onClick = { onOpenLeague(league.id) })
                }
            }
        }

        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh")
        }
    }
}

@Composable
private fun LeagueRow(league: LeagueDto, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = league.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "${league.city}, ${league.district}, ${league.state}")
            Text(text = "Starts ${league.startsOn}" + (league.format?.let { " -- $it" } ?: ""))
        }
    }
}

package com.crichere.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.ground.GroundDto
import com.crichere.app.league.AwardDraft
import com.crichere.app.league.LeagueCreationNavigationEvent
import com.crichere.app.league.LeagueCreationState
import com.crichere.app.league.LeagueCreationViewModel
import com.crichere.app.reference.CityDto
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.StateDto
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant
import java.time.ZoneOffset

/** Resolves [LeagueCreationViewModel] via Koin, parameterized on [editingLeagueId] -- `null` is create mode. */
@Composable
internal fun LeagueCreationRoute(
    editingLeagueId: String?,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: LeagueCreationViewModel = koinViewModel(key = "league-creation:$editingLeagueId") { parametersOf(editingLeagueId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is LeagueCreationNavigationEvent.Saved -> onDone(event.leagueId)
            }
        }
    }

    LeagueCreationScreen(state = state, viewModel = viewModel, onCancel = onCancel)
}

/**
 * League Creation: one continuous scrollable form (mirrors `ProfileSetupScreen` exactly -- see
 * docs/PHASE2.md's Decisions Made on why this isn't a multi-screen wizard), sectioned as
 * Basics/Location/Ground/Schedule/Format/Capacity/Fees/Awards. Reused unchanged for create and
 * edit -- [LeagueCreationState.isEditMode] only changes the heading and pre-fills fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeagueCreationScreen(state: LeagueCreationState, viewModel: LeagueCreationViewModel, onCancel: () -> Unit) {
    val context = LocalContext.current

    val logoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
            if (bytes != null) viewModel.onLogoPicked(bytes, contentType)
        }
    }
    val bannerPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
            if (bytes != null) viewModel.onBannerPicked(bytes, contentType)
        }
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = if (state.isEditMode) "Edit league" else "Create a league", style = MaterialTheme.typography.headlineSmall)

        // ---- Basics ----
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChanged,
            label = { Text("League name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChanged,
            label = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { logoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !state.isUploadingLogo,
            ) {
                Text(
                    if (state.isUploadingLogo) {
                        "Uploading logo..."
                    } else if (state.logoUrl != null || state.hasPendingLogo) {
                        "Logo selected"
                    } else {
                        "Choose a logo"
                    },
                )
            }
            TextButton(
                onClick = { bannerPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !state.isUploadingBanner,
            ) {
                Text(
                    if (state.isUploadingBanner) {
                        "Uploading banner..."
                    } else if (state.bannerUrl != null || state.hasPendingBanner) {
                        "Banner selected"
                    } else {
                        "Choose a banner"
                    },
                )
            }
        }

        // ---- Location ----
        Text(text = "Location", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = viewModel::useMyLocation, enabled = !state.isLocating, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.isLocating) "Finding your location..." else "Use my location")
        }
        DropdownSelector(
            label = "State",
            options = state.states,
            selected = state.states.firstOrNull { it.name == state.state },
            optionLabel = StateDto::name,
            onSelected = viewModel::onStateSelected,
        )
        DropdownSelector(
            label = "District",
            options = state.districts,
            selected = state.districts.firstOrNull { it.name == state.district },
            optionLabel = DistrictDto::name,
            onSelected = viewModel::onDistrictSelected,
            enabled = state.state != null,
        )
        DropdownSelector(
            label = "City",
            options = state.cities,
            selected = state.cities.firstOrNull { it.name == state.city },
            optionLabel = CityDto::name,
            onSelected = viewModel::onCitySelected,
            enabled = state.district != null,
        )

        // ---- Ground ----
        GroundSection(state = state, viewModel = viewModel)

        // ---- Schedule ----
        Text(text = "Schedule", style = MaterialTheme.typography.titleMedium)
        StartsOnPicker(startsOn = state.startsOn, onStartsOnChanged = viewModel::onStartsOnChanged)

        // ---- Format ----
        OutlinedTextField(
            value = state.format,
            onValueChange = viewModel::onFormatChanged,
            label = { Text("Format (optional, e.g. T20)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- Capacity ----
        Text(text = "Capacity", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.franchisesRequired,
            onValueChange = viewModel::onFranchisesRequiredChanged,
            label = { Text("Franchises required (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.playersRequired,
            onValueChange = viewModel::onPlayersRequiredChanged,
            label = { Text("Players required (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- Fees ----
        Text(text = "Fees", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.franchiseFee,
            onValueChange = viewModel::onFranchiseFeeChanged,
            label = { Text("Franchise fee (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.playerFee,
            onValueChange = viewModel::onPlayerFeeChanged,
            label = { Text("Player fee (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val feeSet = state.franchiseFee.isNotBlank() || state.playerFee.isNotBlank()
        OutlinedTextField(
            value = state.organizerUpiId,
            onValueChange = viewModel::onOrganizerUpiIdChanged,
            label = { Text(if (feeSet) "Your UPI ID (required to collect a fee)" else "Your UPI ID (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- Awards ----
        AwardsSection(state = state, viewModel = viewModel)

        val errorMessage = state.errorMessage
        if (errorMessage != null) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text("Cancel")
            }
            Button(onClick = viewModel::save, enabled = state.isSaveEnabled, modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (state.isSaving) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Save")
            }
        }
    }
}

@Composable
private fun GroundSection(state: LeagueCreationState, viewModel: LeagueCreationViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Ground (optional)", style = MaterialTheme.typography.titleMedium)

        if (state.groundId != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = state.groundDisplayName ?: "Ground selected", modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::onClearGround) { Text("Clear") }
            }
            return
        }

        if (state.isRegisteringNewGround) {
            OutlinedTextField(
                value = state.newGroundName,
                onValueChange = viewModel::onNewGroundNameChanged,
                label = { Text("New ground name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            GroundMapPicker(
                initialLatitude = state.newGroundLatitude,
                initialLongitude = state.newGroundLongitude,
                onPositionChanged = viewModel::onNewGroundPositionChanged,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::onCancelRegisteringNewGround, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = viewModel::registerNewGround,
                    enabled = !state.isRegisteringGround && state.newGroundName.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isRegisteringGround) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Register ground")
                }
            }
            // Also shown at the very bottom of the whole form (see LeagueCreationScreen), which
            // is scrolled far out of view from here -- repeating it next to the action that
            // actually caused it (e.g. "drag the pin first") is what makes it visible at all.
            val groundErrorMessage = state.errorMessage
            if (groundErrorMessage != null) {
                Text(text = groundErrorMessage, color = MaterialTheme.colorScheme.error)
            }
            return
        }

        OutlinedTextField(
            value = state.groundSearchQuery,
            onValueChange = viewModel::onGroundSearchQueryChanged,
            label = { Text("Search grounds") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isSearchingGrounds) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        } else {
            state.groundSearchResults.forEach { ground: GroundDto ->
                Card(onClick = { viewModel.onGroundSelected(ground) }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "${ground.name} -- ${ground.city}", modifier = Modifier.padding(12.dp))
                }
            }
        }
        TextButton(onClick = viewModel::onStartRegisteringNewGround) {
            Text("Can't find it? Register a new ground")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartsOnPicker(startsOn: String?, onStartsOnChanged: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
        Text(startsOn ?: "Pick a start date")
    }

    if (showDialog) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val isoDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                            onStartsOnChanged(isoDate)
                        }
                        showDialog = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun AwardsSection(state: LeagueCreationState, viewModel: LeagueCreationViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Awards", style = MaterialTheme.typography.titleMedium)

        // A plain Column, not LazyColumn -- this list is always a handful of rows (a handful of
        // awards per league), never large enough to need virtualization, and a LazyColumn here
        // previously needed a manually-guessed fixed height (state.awards.size * 96.dp) that
        // undercounted each row's real height, silently clipping awards past whatever fit in that
        // budget with no way to scroll to the rest -- e.g. a league's 3rd award becoming
        // inaccessible in Edit mode. A plain Column just takes the height its content needs.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.awards.forEachIndexed { index, award -> AwardRow(award = award, index = index, viewModel = viewModel) }
        }

        TextButton(onClick = viewModel::onAddAward) { Text("Add another award") }
    }
}

@Composable
private fun AwardRow(award: AwardDraft, index: Int, viewModel: LeagueCreationViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = award.name,
                onValueChange = { viewModel.onAwardNameChanged(index, it) },
                label = { Text("Award name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Cash gets the full width on its own line -- squeezed into a Row alongside the
            // Checkbox/"Trophy"/"Remove" controls (as this used to be), its "Cash (optional)"
            // label had only ~100dp to work with and wrapped across 3 lines, which was the direct
            // cause of each row being far taller than the LazyColumn height budget above assumed.
            OutlinedTextField(
                value = award.cashAmount,
                onValueChange = { viewModel.onAwardCashAmountChanged(index, it) },
                label = { Text("Cash (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = award.hasTrophy, onCheckedChange = { viewModel.onAwardTrophyToggled(index) })
                Text("Trophy", modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.onRemoveAward(index) }) { Text("Remove") }
            }
        }
    }
}

package com.crichere.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.profile.BattingStyle
import com.crichere.app.profile.BowlingStyle
import com.crichere.app.profile.PlayingRole
import com.crichere.app.profile.ProfileField
import com.crichere.app.profile.ProfileSetupState
import com.crichere.app.profile.ProfileSetupViewModel
import com.crichere.app.reference.CityDto
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.StateDto

/**
 * Profile Setup screen: first-time cricket-player onboarding (resumable, per the field order
 * name -> photo -> state -> district -> city -> role -> batting -> bowling-if-applicable) and the "edit"
 * entry point from Own Profile View, both driven by the same [ProfileSetupViewModel] (see its
 * `isEditMode` constructor parameter). Navigation on completion is handled by the caller
 * (`AuthNavHost`) via [ProfileSetupViewModel.navigationEvents].
 *
 * Photo selection uses Android's Photo Picker (`ActivityResultContracts.PickVisualMedia`) --
 * gallery selection only, no camera-capture entry point, per this task's environment note (a
 * camera entry point isn't required, and isn't added here -- see task-7-report.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(viewModel: ProfileSetupViewModel, onNavigateToOwnProfile: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect {
            onNavigateToOwnProfile()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
            if (bytes != null) viewModel.uploadPhoto(bytes, contentType)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.useMyLocation()
    }

    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.padding(horizontal = 24.dp))
        }
        return
    }

    // One requester per resumable field, so the screen can scroll the first-missing (or, in edit
    // mode, the top) field into view on load -- see ProfileSetupState.initialFocusField's KDoc
    // ("purely advisory ... e.g. auto-scroll"). Fresh instances per (re-)entry into this
    // composable, matching initialFocusField's own "computed once, on load" lifetime.
    val fieldBringIntoViewRequesters = remember { ProfileField.entries.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(Unit) {
        fieldBringIntoViewRequesters.getValue(state.initialFocusField).bringIntoView()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Set up your profile", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChanged,
            label = { Text("Full name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(fieldBringIntoViewRequesters.getValue(ProfileField.NAME)),
        )

        PhotoSection(
            state = state,
            onPickPhoto = {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.bringIntoViewRequester(fieldBringIntoViewRequesters.getValue(ProfileField.PHOTO)),
        )

        OutlinedButton(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) {
                    viewModel.useMyLocation()
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
            enabled = !state.isLocating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isLocating) "Finding your location..." else "Use my location")
        }

        DropdownSelector(
            label = "State",
            options = state.states,
            selected = state.states.firstOrNull { it.name == state.state },
            optionLabel = StateDto::name,
            onSelected = viewModel::onStateSelected,
            modifier = Modifier.bringIntoViewRequester(fieldBringIntoViewRequesters.getValue(ProfileField.STATE)),
        )

        DropdownSelector(
            label = "District",
            options = state.districts,
            selected = state.districts.firstOrNull { it.name == state.district },
            optionLabel = DistrictDto::name,
            onSelected = viewModel::onDistrictSelected,
            enabled = state.state != null,
            modifier = Modifier.bringIntoViewRequester(fieldBringIntoViewRequesters.getValue(ProfileField.DISTRICT)),
        )

        DropdownSelector(
            label = "City",
            options = state.cities,
            selected = state.cities.firstOrNull { it.name == state.city },
            optionLabel = CityDto::name,
            onSelected = viewModel::onCitySelected,
            enabled = state.district != null,
            modifier = Modifier.bringIntoViewRequester(fieldBringIntoViewRequesters.getValue(ProfileField.CITY)),
        )

        DropdownSelector(
            label = "Playing role",
            options = PlayingRole.entries,
            selected = state.playingRole,
            optionLabel = PlayingRole::displayName,
            onSelected = viewModel::onRoleSelected,
            modifier = Modifier.bringIntoViewRequester(fieldBringIntoViewRequesters.getValue(ProfileField.ROLE)),
        )

        DropdownSelector(
            label = "Batting style",
            options = BattingStyle.entries,
            selected = state.battingStyle,
            optionLabel = BattingStyle::displayName,
            onSelected = viewModel::onBattingStyleSelected,
            modifier = Modifier.bringIntoViewRequester(fieldBringIntoViewRequesters.getValue(ProfileField.BATTING)),
        )

        if (state.isBowlingStyleApplicable) {
            DropdownSelector(
                label = "Bowling style",
                options = BowlingStyle.entries,
                selected = state.bowlingStyle,
                optionLabel = BowlingStyle::displayName,
                onSelected = viewModel::onBowlingStyleSelected,
                modifier = Modifier.bringIntoViewRequester(fieldBringIntoViewRequesters.getValue(ProfileField.BOWLING)),
            )
        }

        if (state.errorMessage != null) {
            Text(text = state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = viewModel::save,
            enabled = state.isSaveEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
            } else {
                Text("Save")
            }
        }
    }
}

@Composable
private fun PhotoSection(state: ProfileSetupState, onPickPhoto: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onPickPhoto, enabled = !state.isUploadingPhoto) {
            Text(
                when {
                    state.isUploadingPhoto -> "Uploading photo..."
                    state.photoUrl != null -> "Photo selected -- tap to change"
                    else -> "Choose a profile photo"
                },
            )
        }
        val photoUploadErrorMessage = state.photoUploadErrorMessage
        if (photoUploadErrorMessage != null) {
            Text(text = photoUploadErrorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun PlayingRole.displayName(): String = when (this) {
    PlayingRole.BATSMAN -> "Batsman"
    PlayingRole.BOWLER -> "Bowler"
    PlayingRole.ALL_ROUNDER -> "All-rounder"
    PlayingRole.WICKETKEEPER -> "Wicketkeeper"
}

private fun BattingStyle.displayName(): String = when (this) {
    BattingStyle.RIGHT_HAND -> "Right-hand bat"
    BattingStyle.LEFT_HAND -> "Left-hand bat"
}

private fun BowlingStyle.displayName(): String = when (this) {
    BowlingStyle.RIGHT_ARM_FAST -> "Right-arm fast"
    BowlingStyle.RIGHT_ARM_MEDIUM -> "Right-arm medium"
    BowlingStyle.RIGHT_ARM_OFFBREAK -> "Right-arm offbreak"
    BowlingStyle.RIGHT_ARM_LEGBREAK -> "Right-arm legbreak"
    BowlingStyle.LEFT_ARM_FAST -> "Left-arm fast"
    BowlingStyle.LEFT_ARM_MEDIUM -> "Left-arm medium"
    BowlingStyle.LEFT_ARM_ORTHODOX -> "Left-arm orthodox"
    BowlingStyle.LEFT_ARM_CHINAMAN -> "Left-arm chinaman"
}

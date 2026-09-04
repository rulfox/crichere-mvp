package com.crichere.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.profile.OwnProfileNavigationEvent
import com.crichere.app.profile.OwnProfileViewModel

/**
 * Own Profile View: the signed-in user's complete cricket-player profile, with "edit" (re-enters
 * Profile Setup in edit mode) and "logout" actions. Navigation on either action is handled by the
 * caller (`AuthNavHost`) via [OwnProfileViewModel.navigationEvents].
 */
@Composable
fun OwnProfileScreen(
    viewModel: OwnProfileViewModel,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToPhoneEntry: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                OwnProfileNavigationEvent.NavigateToEditProfile -> onNavigateToEditProfile()
                OwnProfileNavigationEvent.NavigateToPhoneEntry -> onNavigateToPhoneEntry()
            }
        }
    }

    if (state.isLoading) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(modifier = Modifier.padding(horizontal = 24.dp))
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Your profile", style = MaterialTheme.typography.headlineSmall)

        if (state.errorMessage != null) {
            Text(text = state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
        }

        ProfileRow("Name", state.name)
        ProfileRow("State", state.state)
        ProfileRow("District", state.district)
        ProfileRow("City", state.city)
        ProfileRow("Role", state.playingRole?.name)
        ProfileRow("Batting style", state.battingStyle?.name)
        if (state.bowlingStyle != null) {
            ProfileRow("Bowling style", state.bowlingStyle?.name)
        }

        Button(onClick = viewModel::editProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Edit profile")
        }
        OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
            Text("Log out")
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String?) {
    Text(text = "$label: ${value ?: "--"}")
}

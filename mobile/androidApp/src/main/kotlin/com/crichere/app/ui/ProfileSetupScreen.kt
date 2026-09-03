package com.crichere.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * TODO(Task 7): replace with the real first-time cricket-player profile setup screen. Reached
 * when [com.crichere.app.auth.AuthNavigationEvent.NavigateToProfileSetup] fires after a
 * successful sign-in with `profileComplete == false` -- same forward-stub pattern Task 5 used for
 * its own placeholders.
 */
@Composable
fun ProfileSetupScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Signed in. Profile Setup lands in Task 7.")
    }
}

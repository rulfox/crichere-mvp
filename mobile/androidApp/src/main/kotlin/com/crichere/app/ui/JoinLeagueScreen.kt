package com.crichere.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.league.JoinLeagueState
import com.crichere.app.league.JoinLeagueViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Resolves [JoinLeagueViewModel] via Koin, parameterized on [leagueId] -- see `AuthNavHost`'s `MainDestination.JoinLeagueFlow`. */
@Composable
internal fun JoinLeagueRoute(
    leagueId: String,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: JoinLeagueViewModel = koinViewModel(key = "join-league:$leagueId") { parametersOf(leagueId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.retry() }
    LaunchedEffect(state.joined) { if (state.joined) onDone() }

    JoinLeagueScreen(
        state = state,
        onUploadScreenshot = viewModel::uploadScreenshot,
        onSubmit = viewModel::submit,
        onCancel = onCancel,
    )
}

/** Join-as-player flow: fee/UPI display if a fee is set, "Pay via UPI" convenience action, screenshot attach, refunds/disputes disclaimer, submit -- see docs/PHASE3.md's Screens section. */
@Composable
private fun JoinLeagueScreen(
    state: JoinLeagueState,
    onUploadScreenshot: (ByteArray, String, String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val screenshotPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
            if (bytes != null) onUploadScreenshot(bytes, contentType, "payment-proof.jpg")
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }

        val league = state.league
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            league == null -> Text(text = state.errorMessage ?: "Couldn't load this league.", color = MaterialTheme.colorScheme.error)
            else -> {
                Text(text = "Join ${league.name} as a Player", style = MaterialTheme.typography.titleLarge)

                if (league.playerFee != null) {
                    Text("Player fee: ${league.playerFee}")
                    league.organizerUpiId?.let { upiId ->
                        Text("Pay to: $upiId")
                        Button(
                            onClick = {
                                val uri = Uri.parse("upi://pay?pa=$upiId&pn=${Uri.encode(league.name)}&am=${league.playerFee}&tn=${Uri.encode("Player fee for ${league.name}")}&cu=INR")
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (_: ActivityNotFoundException) {
                                    // No UPI app installed -- the screenshot step still works without it, so this is a silent no-op.
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Pay via UPI") }
                    }

                    OutlinedButton(
                        onClick = { screenshotPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !state.isUploadingScreenshot,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                state.isUploadingScreenshot -> "Uploading..."
                                state.screenshotUrl != null -> "Screenshot attached"
                                else -> "Attach payment screenshot"
                            },
                        )
                    }

                    Text(
                        text = "Crichere doesn't process payment or handle refunds/disputes -- that's between you and the organizer directly.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                val errorMessage = state.errorMessage
                if (errorMessage != null) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }

                Button(onClick = onSubmit, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.isSubmitting) "Joining..." else "Join")
                }
            }
        }
    }
}

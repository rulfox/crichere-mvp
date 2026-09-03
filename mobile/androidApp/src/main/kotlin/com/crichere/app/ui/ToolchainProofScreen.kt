package com.crichere.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.reference.ReferenceViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Task 5's entire UI surface: no real app screens yet (those are Tasks 6-7). On load, this
 * screen's [ReferenceViewModel] fires a real GET against the locally running backend's
 * `/api/v1/reference/states` through the shared Ktor client, wired in via Koin -- proving the
 * whole toolchain (namespace, DI, HTTP client, JSON parsing) works end to end. Per this task's
 * ruling, `/api/v1/reference/states` stands in for a health-check endpoint (none exists on the
 * backend).
 */
@Composable
fun ToolchainProofScreen(viewModel: ReferenceViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Crichere mobile toolchain proof")
        Text(text = "GET /api/v1/reference/states")

        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))

            state.error != null -> {
                Text(text = "Request failed: ${state.error}")
                Button(onClick = { viewModel.loadStates() }) {
                    Text("Retry")
                }
            }

            else -> {
                Text(text = "Loaded ${state.states.size} states from the backend:")
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.states) { stateDto ->
                        Text(text = "${stateDto.code} — ${stateDto.name}")
                    }
                }
            }
        }
    }
}

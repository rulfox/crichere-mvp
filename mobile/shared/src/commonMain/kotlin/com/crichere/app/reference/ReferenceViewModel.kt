package com.crichere.app.reference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Toolchain-proof screen's ViewModel. Deliberately trivial -- Task 5's scope is proving the
 * shared `androidx.lifecycle.ViewModel` (commonMain-native since Lifecycle 2.9.0) works
 * end to end on both platforms: Android consumes it via `koinViewModel()`
 * (`androidApp/.../ui/ToolchainProofScreen.kt`), iOS wraps it in an `ObservableObject` that
 * mirrors [state] into a `@Published var` via SKIE's `Flow` -> `AsyncSequence` bridging
 * (`iosApp/iosApp/ToolchainProofViewModelWrapper.swift`).
 */
class ReferenceViewModel(
    private val referenceRepository: ReferenceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ToolchainProofState())
    val state: StateFlow<ToolchainProofState> = _state.asStateFlow()

    init {
        loadStates()
    }

    fun loadStates() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { referenceRepository.getStates() }
                .onSuccess { states ->
                    _state.update { it.copy(isLoading = false, states = states, error = null) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = throwable.message ?: "Unknown error")
                    }
                }
        }
    }
}

data class ToolchainProofState(
    val isLoading: Boolean = false,
    val states: List<StateDto> = emptyList(),
    val error: String? = null,
)

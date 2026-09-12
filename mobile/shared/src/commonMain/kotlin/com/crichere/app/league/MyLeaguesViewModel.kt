package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyLeaguesState(
    val isLoading: Boolean = true,
    val data: MyLeaguesDto? = null,
    val errorMessage: String? = null,
)

/** My Leagues tab's ViewModel -- the four lists (Organizing/Playing/Franchise owner/Following) in one call. Same explicit-retry-on-entry convention as `LeagueDetailViewModel` (no `init` block); `MyLeaguesRoute` calls [retry] in `LaunchedEffect(Unit)` on every entry so returning to this tab always shows fresh state. */
class MyLeaguesViewModel(
    private val myLeaguesRepository: MyLeaguesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyLeaguesState())
    val state: StateFlow<MyLeaguesState> = _state.asStateFlow()

    fun retry() = load()

    private fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { myLeaguesRepository.getMyLeagues() }
                .onSuccess { data -> _state.update { it.copy(isLoading = false, data = data) } }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = throwable.message ?: "Couldn't load your leagues. Please try again.")
                    }
                }
        }
    }
}

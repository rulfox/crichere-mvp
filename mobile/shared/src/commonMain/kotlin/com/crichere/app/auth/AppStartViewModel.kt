package com.crichere.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where [AppStartViewModel]'s silent startup check lands. `null` state means the check is still in flight. */
sealed interface AppStartDestination {
    data object PhoneEntry : AppStartDestination
    data object ProfileSetup : AppStartDestination
    data object OwnProfile : AppStartDestination
}

/**
 * The app-start routing check Task 6 explicitly left undone: runs once at launch, before showing
 * any of the four existing screens, to decide whether a stored session is still good.
 *
 * The three-way outcome:
 *  1. No refresh token in `SecureStore` -> [AuthRepository.refresh] returns `null` immediately
 *     (no HTTP call -- see its own doc) -> [AppStartDestination.PhoneEntry].
 *  2. Refresh token present but invalid/revoked (backend 401) -> `refresh()` also returns `null`,
 *     but only *after* clearing `SecureStore` itself (see `AuthRepository.refresh`'s 401 branch --
 *     this class does not duplicate that clearing) -> [AppStartDestination.PhoneEntry].
 *  3. Refresh token present and valid -> `refresh()` returns a real [AuthResult] -> routes
 *     directly via its `profileComplete` to [AppStartDestination.ProfileSetup]/[AppStartDestination.OwnProfile],
 *     with **no extra `/profiles/me` call** -- the refresh response already carries what's needed.
 *
 * A [SessionRefreshFailedException] (network error, 5xx -- a transient problem, not "log the user
 * out") is treated the same as case 1 for the purposes of this one-shot startup decision: there is
 * no sensible "retry loop" to run before the user can even see a screen, so this falls back to
 * Phone Entry (where the user can simply try signing in again) rather than leaving the app stuck
 * on the loading state indefinitely or crashing.
 */
class AppStartViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _destination = MutableStateFlow<AppStartDestination?>(null)

    /** `null` while the silent check is in flight -- the caller shows a minimal loading/splash state for that. */
    val destination: StateFlow<AppStartDestination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            val result = runCatching { authRepository.refresh() }.getOrNull()
            _destination.value = when {
                result == null -> AppStartDestination.PhoneEntry
                result.profileComplete -> AppStartDestination.OwnProfile
                else -> AppStartDestination.ProfileSetup
            }
        }
    }
}

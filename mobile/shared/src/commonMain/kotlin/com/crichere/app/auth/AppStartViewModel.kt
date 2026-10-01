package com.crichere.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where [AppStartViewModel]'s silent startup check lands. `null` state means the check is still
 * in flight. [Main] is the 2-tab bottom-nav shell (League Dashboard / My Profile) that replaced
 * Own Profile View as the landing screen once Phase 2 gave the app something to land on besides
 * a single profile (see docs/PHASE1.md Section 4's superseding note).
 */
sealed interface AppStartDestination {
    data object PhoneEntry : AppStartDestination
    data object ProfileSetup : AppStartDestination
    data object Main : AppStartDestination
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
 *     directly via its `profileComplete` to [AppStartDestination.ProfileSetup]/[AppStartDestination.Main],
 *     with **no extra `/profiles/me` call** -- the refresh response already carries what's needed.
 *
 * A transient failure -- [SessionRefreshFailedException] (5xx), a network error or timeout -- is
 * NOT "signed out": the session is still stored and usually valid (the backend can take ~12s to
 * cold-start). It is retried [MAX_ATTEMPTS] times with backoff; if it still fails, [isOffline]
 * turns true so the splash can offer [retry] instead of dumping a signed-in user on Phone Entry.
 * Only a `null` result (no token, or a real 401) routes to Phone Entry.
 */
class AppStartViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _destination = MutableStateFlow<AppStartDestination?>(null)

    /** `null` while the silent check is in flight -- the caller shows a minimal loading/splash state for that. */
    val destination: StateFlow<AppStartDestination?> = _destination.asStateFlow()

    private val _isOffline = MutableStateFlow(false)

    /** True once every retry of a transient failure is spent -- the splash shows "Couldn't connect" + [retry]. */
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    init {
        check()
    }

    /** Re-runs the startup check after [isOffline]; no-op otherwise. */
    fun retry() {
        if (!_isOffline.value) return
        _isOffline.value = false
        check()
    }

    private fun check() {
        viewModelScope.launch {
            repeat(MAX_ATTEMPTS) { attempt ->
                val outcome = try {
                    Result.success(authRepository.refresh())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (transient: Exception) {
                    Result.failure(transient)
                }
                outcome.onSuccess { result ->
                    _destination.value = when {
                        result == null -> AppStartDestination.PhoneEntry
                        result.profileComplete -> AppStartDestination.Main
                        else -> AppStartDestination.ProfileSetup
                    }
                    return@launch
                }
                if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAYS_MILLIS[attempt])
            }
            _isOffline.value = true
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 4
        private val RETRY_DELAYS_MILLIS = longArrayOf(1_000, 2_000, 4_000)
    }
}

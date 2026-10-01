@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.auth

import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * App-start routing, tested against a fake [AuthRepository]: no-token / invalid-token -> Phone
 * Entry; refresh succeeds -> Profile Setup or Main by `profileComplete`; a transient failure is
 * retried and then reported as offline (never Phone Entry -- the session is still stored).
 */
class AppStartViewModelTest {

    private class StubAuthRepository(private val refreshResult: () -> AuthResult?) : AuthRepository {
        var refreshCallCount = 0
            private set

        override suspend fun sendOtp(phoneNumber: String, resendToken: Any?) = error("not used in this test")
        override suspend fun verifyOtp(verificationId: String, code: String) = error("not used in this test")
        override suspend fun exchangeSession(idToken: String) = error("not used in this test")
        override suspend fun logout() = error("not used in this test")
        override suspend fun getCurrentUserId(): String? = error("not used in this test")

        override suspend fun refresh(): AuthResult? {
            refreshCallCount++
            return refreshResult()
        }
    }

    private fun authResult(profileComplete: Boolean) = AuthResult(
        userId = "u1",
        accessToken = "access",
        accessTokenExpiresAt = "2026-09-03T12:00:00Z",
        refreshToken = "refresh",
        profileComplete = profileComplete,
    )

    @Test
    fun `no refresh token -- refresh returns null with no HTTP call -- routes to Phone Entry`() = viewModelTest {
        val repository = StubAuthRepository { null }
        val viewModel = AppStartViewModel(repository)

        advanceUntilIdle()

        assertEquals(1, repository.refreshCallCount)
        assertEquals(AppStartDestination.PhoneEntry, viewModel.destination.value)
    }

    @Test
    fun `refresh token present but invalid -- refresh returns null -- routes to Phone Entry`() = viewModelTest {
        // AuthRepository.refresh() itself is responsible for clearing storage on an invalid
        // token (see AuthRepositoryTest) -- from this ViewModel's perspective, a stored-but-
        // invalid token and no token at all look identical: refresh() returns null either way.
        val repository = StubAuthRepository { null }
        val viewModel = AppStartViewModel(repository)

        advanceUntilIdle()

        assertEquals(AppStartDestination.PhoneEntry, viewModel.destination.value)
    }

    @Test
    fun `refresh succeeds with an incomplete profile -- routes to Profile Setup without a second call`() = viewModelTest {
        val repository = StubAuthRepository { authResult(profileComplete = false) }
        val viewModel = AppStartViewModel(repository)

        advanceUntilIdle()

        assertEquals(1, repository.refreshCallCount)
        assertEquals(AppStartDestination.ProfileSetup, viewModel.destination.value)
    }

    @Test
    fun `refresh succeeds with a complete profile -- routes to Own Profile`() = viewModelTest {
        val repository = StubAuthRepository { authResult(profileComplete = true) }
        val viewModel = AppStartViewModel(repository)

        advanceUntilIdle()

        assertEquals(AppStartDestination.Main, viewModel.destination.value)
    }

    @Test
    fun `destination stays null loading until the silent check completes`() = viewModelTest {
        val repository = StubAuthRepository { authResult(profileComplete = true) }
        val viewModel = AppStartViewModel(repository)

        // Before advancing the virtual clock, the coroutine launched from init{} hasn't run yet.
        assertEquals(null, viewModel.destination.value)

        advanceUntilIdle()
        assertEquals(AppStartDestination.Main, viewModel.destination.value)
    }

    @Test
    fun `a transient failure is retried and, if it persists, shows offline -- never Phone Entry`() = viewModelTest {
        val repository = StubAuthRepository { throw SessionRefreshFailedException("503") }
        val viewModel = AppStartViewModel(repository)

        advanceUntilIdle()

        assertEquals(AppStartViewModel.MAX_ATTEMPTS, repository.refreshCallCount)
        assertEquals(null, viewModel.destination.value, "a signed-in user must not be sent to Phone Entry")
        assertTrue(viewModel.isOffline.value)
    }

    @Test
    fun `a transient failure that recovers on a later attempt routes normally`() = viewModelTest {
        var calls = 0
        val repository = StubAuthRepository {
            calls++
            if (calls == 1) throw SessionRefreshFailedException("timeout") else authResult(profileComplete = true)
        }
        val viewModel = AppStartViewModel(repository)

        advanceUntilIdle()

        assertEquals(AppStartDestination.Main, viewModel.destination.value)
        assertFalse(viewModel.isOffline.value)
    }

    @Test
    fun `retry after offline re-runs the check`() = viewModelTest {
        var failing = true
        val repository = StubAuthRepository { if (failing) throw SessionRefreshFailedException("503") else authResult(profileComplete = false) }
        val viewModel = AppStartViewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.isOffline.value)

        failing = false
        viewModel.retry()
        advanceUntilIdle()

        assertFalse(viewModel.isOffline.value)
        assertEquals(AppStartDestination.ProfileSetup, viewModel.destination.value)
    }
}

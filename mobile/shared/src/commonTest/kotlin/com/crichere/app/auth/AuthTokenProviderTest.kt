package com.crichere.app.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Direct unit coverage for the exact mechanism that makes JWT rotation work client-side: proving
 * `loadTokens` reads from storage and `refreshTokens` calls `AuthRepository.refresh()` -- per
 * task-6-brief.md's explicit call-out that this "deserves real test coverage, not just 'trust the
 * wiring.'"
 */
class AuthTokenProviderTest {

    private class StubAuthRepository(private val refreshResult: () -> AuthResult?) : AuthRepository {
        var refreshCallCount = 0
            private set

        override suspend fun sendOtp(phoneNumber: String, resendToken: Any?) = error("not used in this test")
        override suspend fun verifyOtp(verificationId: String, code: String) = error("not used in this test")
        override suspend fun exchangeSession(idToken: String) = error("not used in this test")
        override suspend fun logout() = error("not used in this test")

        override suspend fun refresh(): AuthResult? {
            refreshCallCount++
            return refreshResult()
        }
    }

    @Test
    fun `loadTokens reads the persisted access and refresh tokens from storage`() = runTest {
        val storage = FakeSecureStorage(
            mapOf(SecureStorageKeys.ACCESS_TOKEN to "stored-access", SecureStorageKeys.REFRESH_TOKEN to "stored-refresh"),
        )
        val provider = AuthTokenProvider(storage) { error("refresh not needed for this test") }

        val tokens = provider.loadTokens()

        // BearerTokens has no `equals()` override, so comparing field-by-field is the only
        // reliable option -- `assertEquals` on the objects themselves would fail spuriously.
        assertEquals("stored-access", tokens?.accessToken)
        assertEquals("stored-refresh", tokens?.refreshToken)
    }

    @Test
    fun `loadTokens returns null when nothing has been persisted yet`() = runTest {
        val provider = AuthTokenProvider(FakeSecureStorage()) { error("refresh not needed for this test") }

        assertNull(provider.loadTokens())
    }

    @Test
    fun `loadTokens returns null when only one of the two tokens is present`() = runTest {
        val storage = FakeSecureStorage(mapOf(SecureStorageKeys.ACCESS_TOKEN to "orphaned-access"))
        val provider = AuthTokenProvider(storage) { error("refresh not needed for this test") }

        assertNull(provider.loadTokens())
    }

    @Test
    fun `refreshTokens calls AuthRepository refresh and maps a successful result`() = runTest {
        val authResult = AuthResult(
            userId = "u1",
            accessToken = "new-access",
            accessTokenExpiresAt = "2026-09-03T12:00:00Z",
            refreshToken = "new-refresh",
            profileComplete = true,
        )
        val stubRepository = StubAuthRepository { authResult }
        val provider = AuthTokenProvider(FakeSecureStorage()) { stubRepository }

        val tokens = provider.refreshTokens()

        assertEquals(1, stubRepository.refreshCallCount)
        assertEquals("new-access", tokens?.accessToken)
        assertEquals("new-refresh", tokens?.refreshToken)
    }

    @Test
    fun `refreshTokens returns null when AuthRepository refresh returns null`() = runTest {
        val stubRepository = StubAuthRepository { null }
        val provider = AuthTokenProvider(FakeSecureStorage()) { stubRepository }

        val tokens = provider.refreshTokens()

        assertTrue(stubRepository.refreshCallCount == 1)
        assertNull(tokens)
    }
}

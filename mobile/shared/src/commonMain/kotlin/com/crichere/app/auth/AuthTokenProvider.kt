package com.crichere.app.auth

import com.crichere.app.storage.SecureStore
import io.ktor.client.plugins.auth.providers.BearerTokens

/**
 * Bridges [SecureStore] + [AuthRepository] into the shape Ktor's `Auth` bearer plugin needs
 * (`HttpClientFactory.kt`'s `loadTokens`/`refreshTokens` callbacks). Extracted as its own class,
 * rather than inlined into `di/AppModule.kt`'s Koin lambda, specifically so this logic -- the
 * exact mechanism that makes JWT rotation work client-side, per the plan's architecture -- has
 * direct unit test coverage instead of only being exercised implicitly through a full Koin
 * container (see `AuthTokenProviderTest`).
 *
 * [authRepositoryProvider] is a lazy provider, not a plain constructor-injected [AuthRepository],
 * so Koin can wire this up without a real circular dependency: `AuthTokenProvider` is only
 * *declared* to need an `AuthRepository`, and that need is only realized (by calling the
 * provider) when [refreshTokens] actually fires -- well after the whole DI graph, `HttpClient`
 * included, has finished being registered.
 */
class AuthTokenProvider(
    private val secureStorage: SecureStore,
    private val authRepositoryProvider: () -> AuthRepository,
) {
    /** Reads whatever access/refresh token pair is currently persisted; `null` if either is missing. */
    suspend fun loadTokens(): BearerTokens? {
        val accessToken = secureStorage.get(SecureStorageKeys.ACCESS_TOKEN) ?: return null
        val refreshToken = secureStorage.get(SecureStorageKeys.REFRESH_TOKEN) ?: return null
        return BearerTokens(accessToken, refreshToken)
    }

    /**
     * Calls [AuthRepository.refresh]. `null` propagates straight through -- Ktor's `Auth` plugin
     * treats a `null` return from `refreshTokens` as "give up, surface the 401 as-is," which is
     * exactly right when the stored refresh token itself is the problem.
     */
    suspend fun refreshTokens(): BearerTokens? {
        val result = authRepositoryProvider().refresh() ?: return null
        return BearerTokens(result.accessToken, result.refreshToken)
    }
}

package com.crichere.app.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds this app's two Ktor [HttpClient] instances, both registered in Koin (see
 * `di/AppModule.kt`) so every repository shares one client/connection pool per role. The platform
 * engine (OkHttp on Android, Darwin on iOS) is picked automatically by Ktor's multiplatform
 * artifact resolution based on which single engine dependency is present on each source set's
 * classpath -- see `shared/build.gradle.kts`'s `androidMain`/`iosMain` dependency blocks. CIO is
 * deliberately not used, per the locked architecture decision in ARCHITECTURE.md.
 */
internal object HttpClientFactory {

    /**
     * The shared, authenticated client every feature repository (besides [com.crichere.app.auth.AuthRepository]
     * itself) uses. Carries the [Auth] bearer plugin, so protected endpoints get an
     * `Authorization` header automatically and a 401 triggers exactly one transparent retry via
     * [refreshTokens].
     *
     * [loadTokens] and [refreshTokens] are injected rather than this factory reading
     * `SecureStorage`/`AuthRepository` directly -- that real wiring
     * (`com.crichere.app.auth.AuthTokenProvider`) lives in `di/AppModule.kt` and is unit-tested on
     * its own, keeping this factory a pure Ktor-config concern.
     */
    fun create(
        loadTokens: suspend () -> BearerTokens?,
        refreshTokens: suspend () -> BearerTokens?,
    ): HttpClient = HttpClient {
        applyBaseConfig()

        install(Auth) {
            bearer {
                loadTokens { loadTokens() }
                refreshTokens { refreshTokens() }
            }
        }

        // Consumes GET /leagues/{id}/auction/stream (see docs/PHASE5.md). The stream itself is
        // public, but this is the one client every feature repository already shares -- no need
        // for a fourth client just to drop the Auth plugin for one endpoint.
        install(SSE)
    }

    /**
     * A second, deliberately un-authenticated client for `AuthRepository`'s own calls to
     * `/api/v1/auth/session|refresh|logout`. Those endpoints are `permitAll()` backend-side and,
     * critically, this client MUST NOT be [create]'s client: `/auth/refresh` is exactly what
     * [refreshTokens] above calls into (via `AuthRepository.refresh()`). Routing that HTTP call
     * back through the very client that owns the `Auth` plugin would mean a second, unrelated 401
     * from that same call (e.g. an already-revoked refresh token) re-enters `refreshTokens` for
     * *that* request too -- an unbounded recursion, not a one-shot retry, since Ktor's built-in
     * "retry once" guard tracks the original failing request, not further requests issued from
     * inside the refresh callback itself.
     */
    fun createAuthClient(): HttpClient = HttpClient {
        applyBaseConfig()
    }

    /**
     * A third client, deliberately with **no** [applyBaseConfig] at all -- Task 7's
     * `ProfileRepository.uploadPhoto` POSTs straight to an S3 presigned-POST `uploadUrl`
     * (`https://<bucket>.s3.<region>.amazonaws.com/`), a fully-absolute URL on a different host
     * and scheme than the backend entirely. [applyBaseConfig]'s `defaultRequest { url { takeFrom(backendBaseUrl) } }`
     * would clobber that absolute URL's host/protocol (Ktor's `URLBuilder.takeFrom` overwrites
     * every component unconditionally), and S3 needs no `Auth`/JSON-content-negotiation plugins
     * this app's own backend calls do. Kept as its own client rather than a config flag on
     * [create]/[createAuthClient] so those two stay simple, backend-only clients.
     */
    fun createUploadClient(): HttpClient = HttpClient {
        expectSuccess = false

        install(Logging) {
            level = LogLevel.INFO
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    private fun HttpClientConfig<*>.applyBaseConfig() {
        expectSuccess = false

        defaultRequest {
            url {
                protocol = URLProtocol.HTTP
                takeFrom(backendBaseUrl)
            }
        }

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }

        install(Logging) {
            level = LogLevel.INFO
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }
}

package com.crichere.app.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds the single shared Ktor [HttpClient] instance, registered in Koin as a singleton (see
 * `AppModule.kt`) so every repository shares one client/connection pool. The platform engine
 * (OkHttp on Android, Darwin on iOS) is picked automatically by Ktor's multiplatform artifact
 * resolution based on which single engine dependency is present on each source set's classpath
 * -- see `shared/build.gradle.kts`'s `androidMain`/`iosMain` dependency blocks. CIO is
 * deliberately not used, per the locked architecture decision in ARCHITECTURE.md.
 */
internal object HttpClientFactory {

    fun create(): HttpClient = HttpClient {
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

        // Task 6's landing spot: `AuthRepository` doesn't exist yet (that's what Task 6 builds),
        // so there is nothing real to load/refresh tokens from yet. Returning null tokens means
        // this plugin adds no Authorization header today, which is correct -- every endpoint this
        // client calls so far (`/api/v1/reference/states`) is `permitAll()` and needs none. The
        // plugin is installed now purely so its structure (this exact callback shape) is already
        // in place for Task 6 to fill in, rather than bolted on later.
        install(Auth) {
            bearer {
                loadTokens {
                    // TODO(Task 6): read the persisted access/refresh tokens from SecureStorage
                    // via AuthRepository and return them as BearerTokens.
                    null
                }
                refreshTokens {
                    // TODO(Task 6): call AuthRepository.refresh(), persist the new tokens via
                    // SecureStorage, and return them as BearerTokens. Returning null here means
                    // a 401 is surfaced as-is rather than retried -- correct until Task 6 wires
                    // real refresh logic in.
                    null as BearerTokens?
                }
            }
        }
    }
}

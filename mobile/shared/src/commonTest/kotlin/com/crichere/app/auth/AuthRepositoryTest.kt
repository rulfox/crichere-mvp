package com.crichere.app.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `commonTest` (`kotlin.test`) coverage for [KtorAuthRepository] against a Ktor `MockEngine`, per
 * Task 5's established pattern in `ReferenceRepositoryTest` -- real HTTP request shapes asserted
 * (path, method, body), `profileComplete` threaded through, tokens persisted to a fake/in-memory
 * `SecureStore`. [FakePhoneAuthClient] stands in for the real Firebase SDK boundary (see its doc).
 */
class AuthRepositoryTest {

    private fun mockHttpClient(handler: suspend MockRequestHandleScope.(request: HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            defaultRequest { url("http://localhost/") }
            engine { addHandler(handler) }
        }

    private fun requestBodyText(request: HttpRequestData): String =
        (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    @Test
    fun `sendOtp delegates straight to the fake PhoneAuthClient`() = runTest {
        val phoneAuthClient = FakePhoneAuthClient()
        val repository = KtorAuthRepository(mockHttpClient { error("no HTTP call expected") }, phoneAuthClient, FakeSecureStorage(), authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } })

        val result = repository.sendOtp("+919876543210")

        assertTrue(result.isSuccess)
        assertEquals(1, phoneAuthClient.sendCallCount)
        assertEquals(listOf("+919876543210"), phoneAuthClient.sentPhoneNumbers)
    }

    @Test
    fun `verifyOtp delegates straight to the fake PhoneAuthClient`() = runTest {
        val phoneAuthClient = FakePhoneAuthClient()
        val repository = KtorAuthRepository(mockHttpClient { error("no HTTP call expected") }, phoneAuthClient, FakeSecureStorage(), authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } })

        val result = repository.verifyOtp("verification-id", "123456")

        assertEquals(OtpVerification.FirebaseIdToken("fake-firebase-id-token"), result.getOrThrow())
        assertEquals(listOf("123456"), phoneAuthClient.verifiedCodes)
    }

    @Test
    fun `exchangeSession posts the id token and persists the returned tokens`() = runTest {
        val storage = FakeSecureStorage()
        val httpClient = mockHttpClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/auth/session", request.url.encodedPath)
            assertTrue(requestBodyText(request).contains("\"idToken\":\"real-firebase-id-token\""))
            respond(
                content = authResponseJson(profileComplete = false),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = KtorAuthRepository(httpClient, FakePhoneAuthClient(), storage, authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } })

        val result = repository.exchangeSession("real-firebase-id-token")

        assertEquals("access-1", result.accessToken)
        assertEquals("refresh-1", result.refreshToken)
        assertEquals(false, result.profileComplete)
        assertEquals("access-1", storage.snapshot()[SecureStorageKeys.ACCESS_TOKEN])
        assertEquals("refresh-1", storage.snapshot()[SecureStorageKeys.REFRESH_TOKEN])
    }

    @Test
    fun `exchangeSession registers the device token when one is available`() = runTest {
        val httpClient = mockHttpClient { request ->
            respond(
                content = authResponseJson(profileComplete = false),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val deviceTokenRepository = FakeDeviceTokenRepository()
        val repository = KtorAuthRepository(
            httpClient, FakePhoneAuthClient(), FakeSecureStorage(),
            authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } },
            deviceTokenProvider = FakeDeviceTokenProvider("fcm-token-a"),
            deviceTokenRepository = deviceTokenRepository,
        )

        repository.exchangeSession("real-firebase-id-token")

        assertEquals(listOf("fcm-token-a" to "ANDROID"), deviceTokenRepository.registerCalls)
    }

    @Test
    fun `exchangeSession with no device token available registers nothing`() = runTest {
        val httpClient = mockHttpClient { request ->
            respond(
                content = authResponseJson(profileComplete = false),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val deviceTokenRepository = FakeDeviceTokenRepository()
        val repository = KtorAuthRepository(
            httpClient, FakePhoneAuthClient(), FakeSecureStorage(),
            authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } },
            deviceTokenProvider = FakeDeviceTokenProvider(null),
            deviceTokenRepository = deviceTokenRepository,
        )

        repository.exchangeSession("real-firebase-id-token")

        assertTrue(deviceTokenRepository.registerCalls.isEmpty())
    }

    @Test
    fun `logout unregisters the device token before clearing local state`() = runTest {
        val storage = FakeSecureStorage(mapOf(SecureStorageKeys.ACCESS_TOKEN to "access", SecureStorageKeys.REFRESH_TOKEN to "refresh"))
        val httpClient = mockHttpClient { request ->
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val deviceTokenRepository = FakeDeviceTokenRepository()
        val repository = KtorAuthRepository(
            httpClient, FakePhoneAuthClient(), storage,
            authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } },
            deviceTokenProvider = FakeDeviceTokenProvider("fcm-token-a"),
            deviceTokenRepository = deviceTokenRepository,
        )

        repository.logout()

        assertEquals(listOf("fcm-token-a"), deviceTokenRepository.unregisterCalls)
        assertNull(storage.snapshot()[SecureStorageKeys.ACCESS_TOKEN])
    }

    @Test
    fun `exchangeSession clears the authenticated client's cached bearer token so the next request uses the new one`() = runTest {
        // Reproduces a real on-device bug: Ktor's `bearer` auth provider caches whatever
        // loadTokens() returned the first time it was needed and does not re-read it before every
        // request -- only after a 401 triggers refreshTokens(). Without exchangeSession clearing
        // that cache, a fresh sign-in as a different user (log out, sign in as someone else, no
        // app restart) would keep sending the *previous* user's access token on every subsequent
        // request, surfacing as a 403 on the first organizer-only action the new user tries.
        val storage = FakeSecureStorage()
        storage.set(SecureStorageKeys.ACCESS_TOKEN, "stale-access-token")
        storage.set(SecureStorageKeys.REFRESH_TOKEN, "stale-refresh-token")

        var capturedAuthHeader: String? = null
        val authenticatedClient = HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            defaultRequest { url("http://localhost/") }
            install(Auth) {
                bearer {
                    loadTokens {
                        val access = storage.get(SecureStorageKeys.ACCESS_TOKEN) ?: return@loadTokens null
                        val refresh = storage.get(SecureStorageKeys.REFRESH_TOKEN) ?: return@loadTokens null
                        BearerTokens(access, refresh)
                    }
                }
            }
            engine {
                addHandler { request ->
                    capturedAuthHeader = request.headers[HttpHeaders.Authorization]
                    respond(content = "{}", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }

        // Prime the client's cache with the stale token, same as any request a still-open screen
        // might have already made before this fresh sign-in.
        authenticatedClient.get("/anything")
        assertEquals("Bearer stale-access-token", capturedAuthHeader)

        val sessionHttpClient = mockHttpClient { request ->
            respond(
                content = authResponseJson(profileComplete = true),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = KtorAuthRepository(
            sessionHttpClient,
            FakePhoneAuthClient(),
            storage,
            authenticatedHttpClientProvider = { authenticatedClient },
        )

        repository.exchangeSession("a-different-user's-firebase-id-token")

        authenticatedClient.get("/anything")
        assertEquals("Bearer access-1", capturedAuthHeader, "still using the previous user's cached token")
    }

    @Test
    fun `exchangeSession throws on a non-2xx response instead of silently swallowing it`() = runTest {
        val httpClient = mockHttpClient { request ->
            respond(content = "{}", status = HttpStatusCode.Unauthorized, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val repository = KtorAuthRepository(httpClient, FakePhoneAuthClient(), FakeSecureStorage(), authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } })

        assertFailsWith<SessionExchangeFailedException> { repository.exchangeSession("bad-token") }
    }

    @Test
    fun `refresh returns null without an HTTP call when nothing is stored yet`() = runTest {
        val repository = KtorAuthRepository(mockHttpClient { error("no HTTP call expected") }, FakePhoneAuthClient(), FakeSecureStorage(), authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } })

        assertNull(repository.refresh())
    }

    @Test
    fun `refresh posts the stored refresh token and persists the rotated pair`() = runTest {
        val storage = FakeSecureStorage(
            mapOf(SecureStorageKeys.ACCESS_TOKEN to "stale-access", SecureStorageKeys.REFRESH_TOKEN to "stored-refresh"),
        )
        val httpClient = mockHttpClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/auth/refresh", request.url.encodedPath)
            assertTrue(requestBodyText(request).contains("\"refreshToken\":\"stored-refresh\""))
            respond(
                content = authResponseJson(profileComplete = true),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = KtorAuthRepository(httpClient, FakePhoneAuthClient(), storage, authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } })

        val result = repository.refresh()

        assertEquals("access-1", result?.accessToken)
        assertEquals(true, result?.profileComplete)
        assertEquals("access-1", storage.snapshot()[SecureStorageKeys.ACCESS_TOKEN])
        assertEquals("refresh-1", storage.snapshot()[SecureStorageKeys.REFRESH_TOKEN])
    }

    @Test
    fun `refresh returns null and clears storage on a 401 -- the stored refresh token itself is invalid`() = runTest {
        val storage = FakeSecureStorage(
            mapOf(SecureStorageKeys.ACCESS_TOKEN to "stale-access", SecureStorageKeys.REFRESH_TOKEN to "revoked-refresh"),
        )
        val httpClient = mockHttpClient { respond(content = "{}", status = HttpStatusCode.Unauthorized) }
        val repository = KtorAuthRepository(httpClient, FakePhoneAuthClient(), storage, authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } })

        assertNull(repository.refresh())
        // Task 7's app-start routing relies on this clearing already happening here, rather than
        // duplicating it at every refresh() call site.
        assertNull(storage.snapshot()[SecureStorageKeys.ACCESS_TOKEN])
        assertNull(storage.snapshot()[SecureStorageKeys.REFRESH_TOKEN])
    }

    @Test
    fun `refresh throws on a 500 instead of treating it like an invalid token`() = runTest {
        val storage = FakeSecureStorage(mapOf(SecureStorageKeys.REFRESH_TOKEN to "some-refresh"))
        val httpClient = mockHttpClient { respond(content = "{}", status = HttpStatusCode.InternalServerError) }
        val repository = KtorAuthRepository(httpClient, FakePhoneAuthClient(), storage, authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } })

        assertFailsWith<SessionRefreshFailedException> { repository.refresh() }
    }

    @Test
    fun `logout posts the stored refresh token and clears storage even though the backend answers 204`() = runTest {
        val storage = FakeSecureStorage(
            mapOf(SecureStorageKeys.ACCESS_TOKEN to "access", SecureStorageKeys.REFRESH_TOKEN to "refresh-to-revoke"),
        )
        val httpClient = mockHttpClient { request ->
            assertEquals("/api/v1/auth/logout", request.url.encodedPath)
            assertTrue(requestBodyText(request).contains("\"refreshToken\":\"refresh-to-revoke\""))
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val repository = KtorAuthRepository(httpClient, FakePhoneAuthClient(), storage, authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } })

        repository.logout()

        assertNull(storage.snapshot()[SecureStorageKeys.ACCESS_TOKEN])
        assertNull(storage.snapshot()[SecureStorageKeys.REFRESH_TOKEN])
    }

    @Test
    fun `logout clears storage locally even when there is nothing to revoke`() = runTest {
        val storage = FakeSecureStorage()
        val repository = KtorAuthRepository(mockHttpClient { error("no HTTP call expected") }, FakePhoneAuthClient(), storage, authenticatedHttpClientProvider = { mockHttpClient { error("no HTTP call expected") } })

        repository.logout()

        assertNull(storage.snapshot()[SecureStorageKeys.ACCESS_TOKEN])
    }

    private fun authResponseJson(profileComplete: Boolean): String = """
        {
            "userId": "11111111-1111-1111-1111-111111111111",
            "accessToken": "access-1",
            "tokenType": "Bearer",
            "accessTokenExpiresAt": "2026-09-03T12:00:00Z",
            "refreshToken": "refresh-1",
            "profileComplete": $profileComplete
        }
    """.trimIndent()
}

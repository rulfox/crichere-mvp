package com.crichere.app.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
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
        val repository = KtorAuthRepository(mockHttpClient { error("no HTTP call expected") }, phoneAuthClient, FakeSecureStorage())

        val result = repository.sendOtp("+919876543210")

        assertTrue(result.isSuccess)
        assertEquals(1, phoneAuthClient.sendCallCount)
        assertEquals(listOf("+919876543210"), phoneAuthClient.sentPhoneNumbers)
    }

    @Test
    fun `verifyOtp delegates straight to the fake PhoneAuthClient`() = runTest {
        val phoneAuthClient = FakePhoneAuthClient()
        val repository = KtorAuthRepository(mockHttpClient { error("no HTTP call expected") }, phoneAuthClient, FakeSecureStorage())

        val result = repository.verifyOtp("verification-id", "123456")

        assertEquals("fake-firebase-id-token", result.getOrThrow())
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
        val repository = KtorAuthRepository(httpClient, FakePhoneAuthClient(), storage)

        val result = repository.exchangeSession("real-firebase-id-token")

        assertEquals("access-1", result.accessToken)
        assertEquals("refresh-1", result.refreshToken)
        assertEquals(false, result.profileComplete)
        assertEquals("access-1", storage.snapshot()[SecureStorageKeys.ACCESS_TOKEN])
        assertEquals("refresh-1", storage.snapshot()[SecureStorageKeys.REFRESH_TOKEN])
    }

    @Test
    fun `exchangeSession throws on a non-2xx response instead of silently swallowing it`() = runTest {
        val httpClient = mockHttpClient { request ->
            respond(content = "{}", status = HttpStatusCode.Unauthorized, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val repository = KtorAuthRepository(httpClient, FakePhoneAuthClient(), FakeSecureStorage())

        assertFailsWith<SessionExchangeFailedException> { repository.exchangeSession("bad-token") }
    }

    @Test
    fun `refresh returns null without an HTTP call when nothing is stored yet`() = runTest {
        val repository = KtorAuthRepository(mockHttpClient { error("no HTTP call expected") }, FakePhoneAuthClient(), FakeSecureStorage())

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
        val repository = KtorAuthRepository(httpClient, FakePhoneAuthClient(), storage)

        val result = repository.refresh()

        assertEquals("access-1", result?.accessToken)
        assertEquals(true, result?.profileComplete)
        assertEquals("access-1", storage.snapshot()[SecureStorageKeys.ACCESS_TOKEN])
        assertEquals("refresh-1", storage.snapshot()[SecureStorageKeys.REFRESH_TOKEN])
    }

    @Test
    fun `refresh returns null on a 401 -- the stored refresh token itself is invalid`() = runTest {
        val storage = FakeSecureStorage(mapOf(SecureStorageKeys.REFRESH_TOKEN to "revoked-refresh"))
        val httpClient = mockHttpClient { respond(content = "{}", status = HttpStatusCode.Unauthorized) }
        val repository = KtorAuthRepository(httpClient, FakePhoneAuthClient(), storage)

        assertNull(repository.refresh())
    }

    @Test
    fun `refresh throws on a 500 instead of treating it like an invalid token`() = runTest {
        val storage = FakeSecureStorage(mapOf(SecureStorageKeys.REFRESH_TOKEN to "some-refresh"))
        val httpClient = mockHttpClient { respond(content = "{}", status = HttpStatusCode.InternalServerError) }
        val repository = KtorAuthRepository(httpClient, FakePhoneAuthClient(), storage)

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
        val repository = KtorAuthRepository(httpClient, FakePhoneAuthClient(), storage)

        repository.logout()

        assertNull(storage.snapshot()[SecureStorageKeys.ACCESS_TOKEN])
        assertNull(storage.snapshot()[SecureStorageKeys.REFRESH_TOKEN])
    }

    @Test
    fun `logout clears storage locally even when there is nothing to revoke`() = runTest {
        val storage = FakeSecureStorage()
        val repository = KtorAuthRepository(mockHttpClient { error("no HTTP call expected") }, FakePhoneAuthClient(), storage)

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

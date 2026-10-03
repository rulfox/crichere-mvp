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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [KtorAuthRepository]'s provider routing and [BackendOtpClient]'s error mapping, against a Ktor
 * `MockEngine` that answers by path (docs/PHASE12.md). The Firebase side is the usual
 * [FakePhoneAuthClient], so "Firebase untouched" is asserted by it never being called.
 */
class BackendOtpAuthTest {

    private val calls = mutableListOf<Pair<HttpMethod, String>>()
    private val bodies = mutableMapOf<String, String>()

    private fun client(handler: suspend MockRequestHandleScope.(path: String) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            defaultRequest { url("http://localhost/") }
            engine {
                addHandler { request: HttpRequestData ->
                    val path = request.url.encodedPath
                    calls += request.method to path
                    (request.body as? OutgoingContent.ByteArrayContent)?.let { bodies[path] = it.bytes().decodeToString() }
                    handler(path)
                }
            }
        }

    private fun repository(
        phoneAuthClient: FakePhoneAuthClient = FakePhoneAuthClient(),
        storage: FakeSecureStorage = FakeSecureStorage(),
        handler: suspend MockRequestHandleScope.(path: String) -> HttpResponseData,
    ): KtorAuthRepository {
        val authClient = client(handler)
        return KtorAuthRepository(
            authClient,
            phoneAuthClient,
            storage,
            authenticatedHttpClientProvider = { client { error("no authenticated call expected") } },
            backendOtpClient = BackendOtpClient(authClient),
        )
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.problem(code: String, status: HttpStatusCode, extra: String = "") =
        respond(
            """{"status":${status.value},"code":"$code"$extra}""",
            status,
            headersOf(HttpHeaders.ContentType, "application/problem+json"),
        )

    private val configMsg91 = """{"otpProvider":"msg91"}"""
    private val configFirebase = """{"otpProvider":"firebase"}"""
    private val challengeJson =
        """{"challengeId":"chal-1","expiresAt":"2026-01-01T00:05:00Z","resendAvailableAt":"2026-01-01T00:01:00Z"}"""
    private val sessionJson = """
        {"userId":"u-1","accessToken":"acc-1","tokenType":"Bearer","accessTokenExpiresAt":"2026-01-01T00:15:00Z",
         "refreshToken":"ref-1","profileComplete":false}
    """.trimIndent()

    // ---------------------------------------------------------------- provider routing

    @Test
    fun `a fresh send uses the backend OTP when the config says msg91 and never touches Firebase`() = runTest {
        val firebase = FakePhoneAuthClient()
        val repo = repository(firebase) { path ->
            when (path) {
                "/api/v1/auth/config" -> json(configMsg91)
                "/api/v1/auth/otp/send" -> json(challengeJson)
                else -> error("unexpected $path")
            }
        }

        val handle = repo.sendOtp("+919876543210").getOrThrow()

        assertEquals("chal-1", handle.verificationId)
        assertEquals(BackendResendToken("chal-1"), handle.resendToken)
        assertEquals(0, firebase.sendCallCount)
        assertTrue(bodies.getValue("/api/v1/auth/otp/send").contains("\"phoneNumber\":\"+919876543210\""))
    }

    @Test
    fun `a fresh send uses Firebase when the config says firebase`() = runTest {
        val firebase = FakePhoneAuthClient()
        val repo = repository(firebase) { path ->
            if (path == "/api/v1/auth/config") json(configFirebase) else error("unexpected $path")
        }

        repo.sendOtp("+919876543210").getOrThrow()

        assertEquals(1, firebase.sendCallCount)
        assertEquals(listOf(HttpMethod.Get to "/api/v1/auth/config"), calls)
    }

    @Test
    fun `an unreachable or malformed config falls back to Firebase`() = runTest {
        val firebase = FakePhoneAuthClient()
        listOf<suspend MockRequestHandleScope.(String) -> HttpResponseData>(
            { error("backend down") },
            { problem("INTERNAL_ERROR", HttpStatusCode.InternalServerError) },
            { json("""{"otpProvider":"carrier-pigeon"}""") },
        ).forEach { failing ->
            repository(firebase, handler = failing).sendOtp("+919876543210").getOrThrow()
        }
        assertEquals(3, firebase.sendCallCount)
    }

    @Test
    fun `a backend resend goes to the same challenge without re-reading the config`() = runTest {
        val repo = repository { path ->
            when (path) {
                "/api/v1/auth/otp/resend" -> json(challengeJson)
                else -> error("unexpected $path")
            }
        }

        repo.sendOtp("+919876543210", BackendResendToken("chal-1")).getOrThrow()

        assertEquals(listOf(HttpMethod.Post to "/api/v1/auth/otp/resend"), calls)
        assertTrue(bodies.getValue("/api/v1/auth/otp/resend").contains("\"challengeId\":\"chal-1\""))
    }

    @Test
    fun `a Firebase resend stays on Firebase even if the config would now say msg91`() = runTest {
        val firebase = FakePhoneAuthClient()
        val repo = repository(firebase) { error("no HTTP expected: ${it}") }

        repo.sendOtp("+919876543210", "platform-resend-token").getOrThrow()

        assertEquals(1, firebase.sendCallCount)
        assertEquals(emptyList(), calls)
    }

    // ---------------------------------------------------------------- verify

    @Test
    fun `verify through the backend persists the issued session and returns it`() = runTest {
        val storage = FakeSecureStorage()
        val repo = repository(storage = storage) { path ->
            when (path) {
                "/api/v1/auth/config" -> json(configMsg91)
                "/api/v1/auth/otp/send" -> json(challengeJson)
                "/api/v1/auth/otp/verify" -> json(sessionJson)
                else -> error("unexpected $path")
            }
        }
        repo.sendOtp("+919876543210").getOrThrow()

        val verification = repo.verifyOtp("chal-1", "123456").getOrThrow()

        val session = assertIs<OtpVerification.BackendSession>(verification).session
        assertEquals("acc-1", session.accessToken)
        assertEquals("acc-1", storage.snapshot()[SecureStorageKeys.ACCESS_TOKEN])
        assertEquals("ref-1", storage.snapshot()[SecureStorageKeys.REFRESH_TOKEN])
        assertEquals("u-1", storage.snapshot()[SecureStorageKeys.USER_ID])
        val sent = bodies.getValue("/api/v1/auth/otp/verify")
        assertTrue(sent.contains("\"challengeId\":\"chal-1\"") && sent.contains("\"code\":\"123456\""))
        assertTrue(!sent.contains("phone"), "verify must not carry a phone number")
    }

    @Test
    fun `verify with Firebase still yields an id token for the exchange step`() = runTest {
        val firebase = FakePhoneAuthClient()
        val repo = repository(firebase) { path ->
            if (path == "/api/v1/auth/config") json(configFirebase) else error("unexpected $path")
        }
        repo.sendOtp("+919876543210").getOrThrow()

        assertEquals(
            OtpVerification.FirebaseIdToken("fake-firebase-id-token"),
            repo.verifyOtp("fake-verification-id", "123456").getOrThrow(),
        )
    }

    @Test
    fun `a wrong code carries the server's attempts remaining`() = runTest {
        val repo = backendRepo { problem("INVALID_OTP", HttpStatusCode.BadRequest, ""","attemptsRemaining":3""") }

        val e = assertFailsWith<InvalidOtpCodeException> { repo.verifyOtp("chal-1", "000000").getOrThrow() }

        assertEquals(3, e.attemptsRemaining)
    }

    @Test
    fun `an expired or exhausted challenge maps to OtpExpiredException`() = runTest {
        val repo = backendRepo { problem("OTP_EXPIRED", HttpStatusCode.Gone) }
        assertFailsWith<OtpExpiredException> { repo.verifyOtp("chal-1", "123456").getOrThrow() }
    }

    @Test
    fun `a failed verify persists nothing`() = runTest {
        val storage = FakeSecureStorage()
        val repo = backendRepo(storage) { problem("INVALID_OTP", HttpStatusCode.BadRequest, ""","attemptsRemaining":1""") }

        runCatching { repo.verifyOtp("chal-1", "000000").getOrThrow() }

        assertEquals(emptyMap(), storage.snapshot())
    }

    // ---------------------------------------------------------------- send errors are user-safe

    @Test
    fun `a rate limit says how long to wait`() = runTest {
        val repo = sendFailingRepo { problem("RATE_LIMIT_EXCEEDED", HttpStatusCode.TooManyRequests, ""","retryAfterSeconds":42""") }
        val e = assertFailsWith<OtpRequestFailedException> { repo.sendOtp("+919876543210").getOrThrow() }
        assertTrue(e.message!!.contains("42 seconds"))
    }

    @Test
    fun `an invalid number, a resend limit and an outage each map to a fixed message`() = runTest {
        val cases = mapOf(
            "INVALID_PHONE_NUMBER" to HttpStatusCode.BadRequest to "Enter a valid Indian mobile number.",
            "OTP_RESEND_LIMIT" to HttpStatusCode.Conflict to "No more resends available. Please request a new code.",
            "OTP_UNAVAILABLE" to HttpStatusCode.ServiceUnavailable to "We couldn't send a code right now. Please try again later.",
        )
        cases.forEach { (codeAndStatus, expected) ->
            val (code, status) = codeAndStatus
            val repo = sendFailingRepo { problem(code, status) }
            val e = assertFailsWith<OtpRequestFailedException> { repo.sendOtp("+919876543210").getOrThrow() }
            assertEquals(expected, e.message)
        }
    }

    @Test
    fun `a network failure never leaks its raw message`() = runTest {
        val repo = repository { path ->
            if (path == "/api/v1/auth/config") json(configMsg91) else error("socket closed at 10.0.0.7:8080")
        }
        val e = assertFailsWith<OtpRequestFailedException> { repo.sendOtp("+919876543210").getOrThrow() }
        assertTrue(!e.message!!.contains("10.0.0.7"))
    }

    // ---------------------------------------------------------------- helpers

    /** A repository already past a successful msg91 send, so [verifyOtp] routes to the backend. */
    private suspend fun backendRepo(
        storage: FakeSecureStorage = FakeSecureStorage(),
        verifyResponse: suspend MockRequestHandleScope.() -> HttpResponseData,
    ): KtorAuthRepository {
        val repo = repository(storage = storage) { path ->
            when (path) {
                "/api/v1/auth/config" -> json(configMsg91)
                "/api/v1/auth/otp/send" -> json(challengeJson)
                "/api/v1/auth/otp/verify" -> verifyResponse()
                else -> error("unexpected $path")
            }
        }
        repo.sendOtp("+919876543210").getOrThrow()
        return repo
    }

    /** A repository whose msg91 send endpoint answers with [sendResponse]. */
    private fun sendFailingRepo(sendResponse: suspend MockRequestHandleScope.() -> HttpResponseData) =
        repository { path ->
            when (path) {
                "/api/v1/auth/config" -> json(configMsg91)
                "/api/v1/auth/otp/send" -> sendResponse()
                else -> error("unexpected $path")
            }
        }
}

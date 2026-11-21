package com.crichere.backend.admin

import com.crichere.backend.common.AbstractWebIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The client IP behind a proxy (docs/PHASE20.md Step 11). Needs a real Tomcat: MockMvc doesn't run the
 * RemoteIpValve. Loopback stands in for Railway's proxy range here; production trusts only 100.64.0.0/10. Whether Railway
 * overwrites a client-sent X-Real-IP can only be checked against production (docs/PHASE20.md Step 11).
 * The admin sign-in limiter is keyed by the resolved IP, so it shows which IP the backend saw.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1",
        "crichere.admin.sign-in-capacity=2",
    ],
)
class ClientIpIntegrationTest : AbstractWebIntegrationTest() {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var limiter: AdminSignInRateLimiter

    private val http = HttpClient.newHttpClient()

    @BeforeEach
    fun resetLimiter() = limiter.reset()

    @Test
    fun `the IP comes from the proxy's X-Real-IP, so each client has its own limit`() {
        repeat(2) { assertNotEquals(429, signIn("203.0.113.9")) }
        assertEquals(429, signIn("203.0.113.9"), "same client, limit reached")
        assertNotEquals(429, signIn("203.0.113.10"), "another client has its own limit, so the proxy's address isn't the key")
    }

    @Test
    fun `X-Forwarded-For is not used`() {
        // Railway's X-Forwarded-For ends with a different address on every request; keying on it disabled the limits.
        repeat(2) { i -> assertNotEquals(429, signIn("203.0.113.20", forwardedFor = "203.0.113.20, 10.9.9.$i")) }
        assertEquals(429, signIn("203.0.113.20", forwardedFor = "203.0.113.20, 10.9.9.7"))
    }

    private fun signIn(realIp: String, forwardedFor: String? = null): Int =
        http.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/v1/admin/session"))
                .header("Origin", ORIGIN)
                .header(ADMIN_HEADER, "1")
                .header("Content-Type", "application/json")
                .header("X-Real-IP", realIp)
                .apply { if (forwardedFor != null) header("X-Forwarded-For", forwardedFor) }
                .POST(HttpRequest.BodyPublishers.ofString("""{"credential":"not.a.token"}"""))
                .build(),
            HttpResponse.BodyHandlers.discarding(),
        ).statusCode()

    private companion object {
        const val ORIGIN = "https://admin.test"
    }
}

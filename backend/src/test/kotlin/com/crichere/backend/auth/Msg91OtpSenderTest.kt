package com.crichere.backend.auth

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.time.Duration
import kotlin.test.assertEquals

/**
 * Exercises [Msg91OtpSender] against a local stub server.
 *
 * **This proves the sender behaves according to the contract it *assumes*, not MSG91's real
 * contract** -- see the class doc on [Msg91OtpSender] and docs/PHASE12.md Phase 0. If the live
 * API differs, these tests will still pass and the spike's findings must update both the
 * sender and this stub.
 */
class Msg91OtpSenderTest {

    private lateinit var server: HttpServer
    private val seen = mutableListOf<Seen>()
    private var status = 200
    private var responseBody = """{"type":"success","message":"req-123"}"""

    private data class Seen(val path: String, val authkey: String?, val body: String)

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex: HttpExchange ->
                seen += Seen(ex.requestURI.path, ex.requestHeaders.getFirst("authkey"), ex.requestBody.readBytes().decodeToString())
                val bytes = responseBody.toByteArray()
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(status, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            start()
        }
    }

    @AfterEach
    fun stopServer() = server.stop(0)

    private fun sender(authKey: String = "secret-key", widgetId: String = "widget-1") = Msg91OtpSender(
        Msg91Properties(
            authKey = authKey,
            widgetId = widgetId,
            baseUrl = "http://127.0.0.1:${server.address.port}",
            connectTimeout = Duration.ofSeconds(2),
            readTimeout = Duration.ofSeconds(2),
        ),
    )

    @Test
    fun `send posts the identifier without a plus, with the authkey header, and returns the request id`() {
        val reqId = sender().send("+919876543210")

        assertEquals("req-123", reqId)
        val call = seen.single()
        assertEquals("/api/v5/widget/sendOtp", call.path)
        assertEquals("secret-key", call.authkey)
        assertEquals(true, call.body.contains("\"identifier\":\"919876543210\""))
        assertEquals(true, call.body.contains("\"widgetId\":\"widget-1\""))
    }

    @Test
    fun `verify maps success to VALID and anything else to INVALID`() {
        assertEquals(OtpCheckResult.VALID, sender().verify("req-123", "123456"))

        responseBody = """{"type":"error","message":"OTP not match"}"""
        assertEquals(OtpCheckResult.INVALID, sender().verify("req-123", "000000"))
    }

    @Test
    fun `a provider rejection on send is a generic unavailable failure`() {
        responseBody = """{"type":"error","message":"insufficient balance"}"""
        assertThrows<OtpUnavailableException> { sender().send("+919876543210") }
    }

    @Test
    fun `a 5xx is a generic unavailable failure, on send and on verify`() {
        status = 502
        assertThrows<OtpUnavailableException> { sender().send("+919876543210") }
        assertThrows<OtpUnavailableException> { sender().verify("req-123", "123456") }
    }

    @Test
    fun `blank credentials fail closed without making any request`() {
        assertThrows<OtpUnavailableException> { sender(authKey = "").send("+919876543210") }
        assertThrows<OtpUnavailableException> { sender(widgetId = "").verify("r", "123456") }
        assertEquals(0, seen.size)
    }

    @Test
    fun `a success without a request id is treated as a failure`() {
        responseBody = """{"type":"success"}"""
        assertThrows<OtpUnavailableException> { sender().send("+919876543210") }
    }
}

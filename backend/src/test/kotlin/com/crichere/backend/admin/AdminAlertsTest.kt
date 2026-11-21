package com.crichere.backend.admin

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.services.ses.model.SendDataPoint
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminAlertsTest {

    @Test
    fun `clean strips control and invisible characters, collapses space and bounds length`() {
        assertEquals("a b c", AdminSignInAlertListener.clean(" a\r\n\tb\u0000\u200Bc "))
        assertEquals("unknown", AdminSignInAlertListener.clean(null))
        assertEquals("unknown", AdminSignInAlertListener.clean("\u202E\n"))
        assertEquals(200, AdminSignInAlertListener.clean("x".repeat(500)).length)
    }

    @Test
    fun `sign-in body shows the time in IST`() {
        val body = AdminSignInAlertListener.signInBody(
            AdminSignedIn("a@example.com", Instant.parse("2026-11-21T10:30:00Z"), "1.2.3.4", "Chrome"),
        )
        assertTrue("Time: 21 Nov 2026, 16:00:00 IST" in body, body)
        assertTrue("IP address: 1.2.3.4" in body, body)
        assertTrue("Browser: Chrome" in body, body)
    }

    @Test
    fun `send statistics add up every data point`() {
        val points = listOf(
            SendDataPoint.builder().deliveryAttempts(10).bounces(1).complaints(0).rejects(2).build(),
            SendDataPoint.builder().deliveryAttempts(5).bounces(2).complaints(1).rejects(0).build(),
            SendDataPoint.builder().build(),
        )
        assertEquals(SesSendStats(15, 3, 1, 2), SesSendStats.of(points))
    }

    @Test
    fun `alerts are off unless region, sender and recipients are all set`() {
        assertFalse(AlertProperties().configured)
        assertFalse(AlertProperties(sesRegion = "ap-south-1", from = "a@x.com", to = " , ").configured)
        val on = AlertProperties(sesRegion = "ap-south-1", from = "a@x.com", to = "b@x.com, c@x.com")
        assertTrue(on.configured)
        assertEquals(listOf("b@x.com", "c@x.com"), on.recipients)
    }

    @Test
    fun `not configured means no AWS call at all`() {
        val credentials = mockk<AwsCredentialsProvider>()
        val gateway = SesAlertGateway(AlertProperties(), credentials)
        gateway.send("subject", "body")
        assertNull(gateway.lastTwoWeeks())
        verify(exactly = 0) { credentials.resolveCredentials() }
    }
}

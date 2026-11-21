package com.crichere.backend.admin

import com.crichere.backend.common.AbstractWebIntegrationTest
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Collections
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

data class SentAlert(val subject: String, val body: String)

/** Records alerts instead of calling SES; [failNext] makes the next send throw. */
class RecordingAlertMailer : AlertMailer {
    val sent: MutableList<SentAlert> = Collections.synchronizedList(mutableListOf())

    @Volatile var failNext = false

    override fun send(subject: String, body: String) {
        if (failNext) {
            failNext = false
            throw IllegalStateException("SES down")
        }
        sent += SentAlert(subject, body)
    }
}

@TestConfiguration
class RecordingAlertConfiguration {
    @Bean
    @Primary
    fun recordingAlertMailer() = RecordingAlertMailer()
}

/** New-report and sign-in emails over a real database (docs/PHASE20.md Step 4). */
@Import(TestGoogleJwksConfiguration::class, RecordingAlertConfiguration::class)
class AdminAlertsIntegrationTest : AbstractWebIntegrationTest() {

    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var mailer: RecordingAlertMailer
    @Autowired private lateinit var reportAlerts: ReportAlertService
    @Autowired private lateinit var signInLimiter: AdminSignInRateLimiter

    @BeforeEach
    fun reset() {
        // Shared database: push every earlier report (this class's and other classes') out of the count window.
        jdbc.update("update reports set created_at = created_at - interval '1 day' where created_at > now() - interval '1 day'")
        jdbc.update("update alert_state set last_sent_at = null, watermark = now() - interval '5 minutes' where kind = 'NEW_REPORTS'")
        mailer.sent.clear()
        mailer.failNext = false
        signInLimiter.reset()
    }

    // ------------------------------------------------------------------ new reports

    @Test
    fun `no new reports, no email`() {
        assertFalse(reportAlerts.runOnce())
        assertTrue(mailer.sent.isEmpty())
    }

    @Test
    fun `new reports send one email with a count and a link, and nothing that identifies anyone`() {
        insertReport(details = "secret-detail-text")
        insertReport()

        assertTrue(reportAlerts.runOnce())
        val mail = mailer.sent.single()
        assertEquals("Crichere: 2 new reports", mail.subject)
        assertTrue("2 new reports since the last alert." in mail.body, mail.body)
        assertTrue("https://admin.test/reports" in mail.body, mail.body)
        assertFalse("secret-detail-text" in mail.body)
        assertFalse("Snapshot Name" in mail.body)
        assertFalse("IMPERSONATION" in mail.body)

        assertFalse(reportAlerts.runOnce(), "same reports are never counted twice")
    }

    @Test
    fun `at most one email per 15 minutes, and the next counts only what came after`() {
        insertReport(ageSeconds = 240)
        assertTrue(reportAlerts.runOnce())
        // Pretend the first email went out 3 minutes ago, then a report came in a minute ago.
        jdbc.update("update alert_state set last_sent_at = now() - interval '3 minutes', watermark = now() - interval '3 minutes' where kind = 'NEW_REPORTS'")
        insertReport(ageSeconds = 60)
        assertFalse(reportAlerts.runOnce())
        assertEquals(1, mailer.sent.size)

        jdbc.update("update alert_state set last_sent_at = now() - interval '16 minutes' where kind = 'NEW_REPORTS'")
        assertTrue(reportAlerts.runOnce())
        assertEquals("Crichere: 1 new report", mailer.sent.last().subject)
    }

    @Test
    fun `a report from the last few seconds waits for the next run`() {
        insertReport(ageSeconds = 1)
        assertFalse(reportAlerts.runOnce())
    }

    @Test
    fun `a failed send changes nothing, so the next run retries`() {
        insertReport()
        mailer.failNext = true
        assertFailsWith<IllegalStateException> { reportAlerts.runOnce() }
        assertEquals(null, jdbc.queryForObject("select last_sent_at from alert_state where kind = 'NEW_REPORTS'", java.sql.Timestamp::class.java))

        assertTrue(reportAlerts.runOnce())
        assertEquals("Crichere: 1 new report", mailer.sent.single().subject)
    }

    // ------------------------------------------------------------------ sign-in

    @Test
    fun `a sign-in is emailed with time, IP and a cleaned browser string`() {
        signIn(idToken(ADMIN_EMAIL), userAgent = "Mozilla/5.0 \u202E\u200Bmoc.elpmaxe").andExpect(status().isOk)

        val mail = awaitMail()
        assertEquals("Crichere admin sign-in: $ADMIN_EMAIL", mail.subject)
        assertTrue("IP address: 127.0.0.1" in mail.body, mail.body)
        // CR/LF never get this far: Spring Security's firewall rejects them in headers. AdminAlertsTest covers them.
        assertTrue("Browser: Mozilla/5.0 moc.elpmaxe\n" in mail.body, mail.body)
        assertTrue(" IST" in mail.body, mail.body)
        assertFalse("\u202E" in mail.body || "\u200B" in mail.body)
    }

    @Test
    fun `a refused sign-in sends nothing`() {
        signIn(idToken("someone@example.com")).andExpect(status().isUnauthorized)
        Thread.sleep(500)
        assertTrue(mailer.sent.isEmpty())
    }

    // ------------------------------------------------------------------ helpers

    private fun insertReport(details: String? = null, ageSeconds: Int = 60) {
        jdbc.update(
            """
            insert into reports (id, target_type, reason, details, snapshot, created_at)
            values (?, 'USER', 'IMPERSONATION', ?, '{"name":"Snapshot Name"}', now() - make_interval(secs => ?))
            """,
            UUID.randomUUID(), details, ageSeconds,
        )
    }

    private fun idToken(email: String): String {
        val claims = JWTClaimsSet.Builder()
            .issuer("https://accounts.google.com").audience(CLIENT_ID).subject("1234567890")
            .claim("email", email).claim("email_verified", true)
            .issueTime(Date()).expirationTime(Date(System.currentTimeMillis() + 600_000))
            .build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TestGoogleKeys.key.keyID).build(), claims)
            .apply { sign(RSASSASigner(TestGoogleKeys.key)) }.serialize()
    }

    private fun signIn(token: String, userAgent: String = "TestBrowser/1.0") =
        mockMvc.perform(
            post("/api/v1/admin/session").header("Origin", ORIGIN).header(ADMIN_HEADER, "1")
                .header(HttpHeaders.USER_AGENT, userAgent)
                .contentType(MediaType.APPLICATION_JSON).content("""{"credential":"$token"}"""),
        )

    /** The sign-in alert is sent on another thread after commit. */
    private fun awaitMail(): SentAlert {
        val deadline = System.currentTimeMillis() + 5_000
        while (mailer.sent.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(25)
        return mailer.sent.single()
    }

    private companion object {
        const val ORIGIN = "https://admin.test"
        const val CLIENT_ID = "test-admin-client.apps.googleusercontent.com"
        const val ADMIN_EMAIL = "admin@example.com"
    }
}

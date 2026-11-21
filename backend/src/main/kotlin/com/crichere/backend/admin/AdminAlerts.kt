package com.crichere.backend.admin

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.SendDataPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Admin email alerts (docs/PHASE20.md A7, Step 4). Blank region, sender or recipients turn sending off. */
@ConfigurationProperties(prefix = "crichere.alerts")
data class AlertProperties(
    val sesRegion: String = "",
    /** e.g. `Crichere Admin <alerts@crichere.com>`; must be on a verified SES identity. */
    val from: String = "",
    /** Comma-separated. */
    val to: String = "",
    /** Off in tests, which call [ReportAlertService.runOnce] directly. */
    val reportJobEnabled: Boolean = true,
) {
    val recipients: List<String>
        get() = to.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val configured: Boolean
        get() = sesRegion.isNotBlank() && from.isNotBlank() && recipients.isNotEmpty()
}

@Configuration
@EnableConfigurationProperties(AlertProperties::class)
class AlertConfiguration

/** Sends a plain-text alert to the configured admins. A `fun interface` so tests can record mail instead. */
fun interface AlertMailer {
    /** @throws Exception the email couldn't be handed to SES; callers decide whether to retry. */
    fun send(subject: String, body: String)
}

/** SES send counts over the last two weeks, for the system health card (G4). */
data class SesSendStats(val deliveryAttempts: Long, val bounces: Long, val complaints: Long, val rejects: Long) {
    companion object {
        fun of(points: Collection<SendDataPoint>) = SesSendStats(
            deliveryAttempts = points.sumOf { it.deliveryAttempts() ?: 0L },
            bounces = points.sumOf { it.bounces() ?: 0L },
            complaints = points.sumOf { it.complaints() ?: 0L },
            rejects = points.sumOf { it.rejects() ?: 0L },
        )
    }
}

fun interface SesStatistics {
    /** Null when SES isn't configured here (local, CI). */
    fun lastTwoWeeks(): SesSendStats?
}

/**
 * The real [AlertMailer] and [SesStatistics], on the classic SES API (v2 has no send statistics).
 * Needs `ses:SendEmail` on the sender's identity and `ses:GetSendStatistics`. Not configured means
 * it logs and returns, the same fail-soft posture as the S3 deleter.
 */
@Component
class SesAlertGateway(
    private val properties: AlertProperties,
    private val credentialsProvider: AwsCredentialsProvider,
) : AlertMailer, SesStatistics {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Built on first use, so missing AWS setup can't fail startup. */
    private val client: SesClient by lazy {
        SesClient.builder().region(Region.of(properties.sesRegion)).credentialsProvider(credentialsProvider).build()
    }

    override fun send(subject: String, body: String) {
        if (!properties.configured) {
            log.info("Alerts not configured; skipped email \"{}\"", subject)
            return
        }
        client.sendEmail { req ->
            req.source(properties.from)
                .destination { it.toAddresses(properties.recipients) }
                .message { m ->
                    m.subject { it.data(subject).charset("UTF-8") }
                        .body { b -> b.text { it.data(body).charset("UTF-8") } }
                }
        }
    }

    override fun lastTwoWeeks(): SesSendStats? {
        if (!properties.configured) return null
        return SesSendStats.of(client.sendStatistics.sendDataPoints())
    }
}

/**
 * At most one "new reports" email per 15 minutes, count and link only: no names, reasons or
 * details ever leave the database by email. State is one locked row in `alert_state`, and the
 * email goes out before that row's update commits, so a failed send is retried next minute and a
 * restart neither loses nor repeats an alert.
 */
@Service
class ReportAlertService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val tx: TransactionTemplate,
    private val mailer: AlertMailer,
    private val admin: AdminProperties,
) {
    /** @return true when an email went out. */
    fun runOnce(): Boolean = tx.execute {
        val none = MapSqlParameterSource()
        // Skip locked: another instance is on it this minute.
        val state = jdbc.query(
            """
            select watermark, last_sent_at > now() - interval '15 minutes' as too_soon,
                   now() - interval '10 seconds' as cutoff
            from alert_state where kind = 'NEW_REPORTS' for update skip locked
            """,
            none,
        ) { rs, _ -> Triple(rs.getTimestamp("watermark"), rs.getBoolean("too_soon"), rs.getTimestamp("cutoff")) }
            .firstOrNull() ?: return@execute false
        val (watermark, tooSoon, cutoff) = state
        if (tooSoon) return@execute false

        // The cutoff trails now() so a report whose transaction is still committing is counted next time, not lost.
        val window = MapSqlParameterSource("from", watermark).addValue("to", cutoff)
        val fresh = jdbc.queryForObject(
            "select count(*) from reports where created_at > :from and created_at <= :to",
            window,
            Long::class.java,
        ) ?: 0L
        if (fresh == 0L) return@execute false
        val open = jdbc.queryForObject("select count(*) from reports where status = 'OPEN'", none, Long::class.java) ?: 0L

        mailer.send(subject(fresh), body(fresh, open))
        jdbc.update("update alert_state set last_sent_at = now(), watermark = :to where kind = 'NEW_REPORTS'", window)
        true
    } == true

    private fun subject(fresh: Long) = "Crichere: $fresh new ${plural(fresh, "report")}"

    private fun body(fresh: Long, open: Long): String = buildString {
        appendLine("$fresh new ${plural(fresh, "report")} since the last alert. $open open in total.")
        appendLine()
        appendLine("Review them in the admin site: ${admin.origin.trimEnd('/')}/reports")
        appendLine()
        appendLine("At most one of these emails goes out every 15 minutes.")
    }

    private fun plural(n: Long, word: String) = if (n == 1L) word else "${word}s"
}

@Component
class ReportAlertJob(private val service: ReportAlertService, private val properties: AlertProperties) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    fun run() {
        if (!properties.reportJobEnabled) return
        try {
            service.runOnce()
        } catch (e: Exception) {
            log.error("New-report alert failed; will retry next minute", e)
        }
    }
}

/** Published by [AdminSessionService.signIn]; emailed only once the session row commits. */
data class AdminSignedIn(val email: String, val at: Instant, val clientIp: String?, val userAgent: String?)

@Component
class AdminSignInAlertListener(private val mailer: AlertMailer) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSignIn(event: AdminSignedIn) {
        try {
            mailer.send("Crichere admin sign-in: ${event.email}", signInBody(event))
        } catch (e: Exception) {
            // Never affects the sign-in itself; the audit log still has the row.
            log.error("Admin sign-in alert failed for {}", event.email, e)
        }
    }

    companion object {
        private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
        private val TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss 'IST'", Locale.ENGLISH)
        private const val TEXT_LIMIT = 200

        fun signInBody(event: AdminSignedIn): String = buildString {
            appendLine("${event.email} signed in to the Crichere admin site.")
            appendLine()
            appendLine("Time: ${TIME.format(event.at.atZone(IST))}")
            appendLine("IP address: ${clean(event.clientIp)}")
            appendLine("Browser: ${clean(event.userAgent)}")
            appendLine()
            appendLine("Not you? Sign in and use \"Sign out everywhere\", then secure that Google account.")
        }

        /** Caller-supplied text: no control or invisible formatting characters, one line, bounded. */
        fun clean(text: String?): String {
            val cleaned = text.orEmpty()
                .map { if (it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt()) ' ' else it }
                .joinToString("")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(TEXT_LIMIT)
            return cleaned.ifEmpty { "unknown" }
        }
    }
}

package com.crichere.backend.admin

import com.crichere.backend.auth.AuthRateLimiter
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.Date
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class MetricRow(val day: LocalDate, val metric: String, val dimension: String, val value: Long)

data class ReleaseMarker(val id: UUID, val day: LocalDate, val label: String, val createdBy: String)

data class HistoryResponse(val from: LocalDate, val to: LocalDate, val rows: List<MetricRow>, val markers: List<ReleaseMarker>)

data class ModerationSummary(
    val openReports: Long,
    val oldestOpenAt: Instant?,
    val openByReason: Map<String, Long>,
    val suspendedAccounts: Long,
    val imagesRemoved30d: Long,
)

data class TodayResponse(val live: LiveSnapshot, val moderation: ModerationSummary)

data class JobHealth(val status: String?, val startedAt: Instant?, val finishedAt: Instant?, val detail: String?)

data class OtpHealth(val codesSentToday: Long, val expiredUnusedToday: Long, val failureRate: Double?)

data class SmsHealth(val sentToday: Long, val dailyCap: Long, val resetsAt: Instant)

data class SesHealth(val configured: Boolean, val deliveryAttempts14d: Long?, val bounces14d: Long?, val complaints14d: Long?)

data class HealthResponse(
    val checkedAt: Instant,
    val nightly: JobHealth,
    val otp: OtpHealth,
    val sms: SmsHealth,
    /** MSG91 balance: not read yet; owner task O9 checks whether the plan exposes an API. */
    val msg91Balance: Long?,
    val ses: SesHealth,
)

data class NewReleaseMarker(val day: LocalDate, @field:NotBlank @field:Size(max = 120) val label: String)

/**
 * Reads for the admin overview (docs/PHASE20.md Step 8). History comes only from `metrics_daily`;
 * today from memory; the small moderation and health reads are cached for a minute so a dashboard
 * left open costs one query a minute at most.
 */
@Service
class AdminInsightsService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val live: LiveMetrics,
    private val rateLimiter: AuthRateLimiter,
    private val ses: SesStatistics,
    private val alerts: AlertProperties,
    private val clock: Clock,
) {
    private class Cached<T>(val value: T, val until: Instant)

    private val moderationCache = ConcurrentHashMap<String, Cached<ModerationSummary>>()
    private val healthCache = ConcurrentHashMap<String, Cached<HealthResponse>>()

    fun history(from: LocalDate, to: LocalDate): HistoryResponse {
        if (from.isAfter(to)) throw AdminConflictException("BAD_RANGE", "The start is after the end.")
        if (Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() > MAX_DAYS) {
            throw AdminConflictException("BAD_RANGE", "At most $MAX_DAYS days at once.")
        }
        val p = MapSqlParameterSource("from", Date.valueOf(from)).addValue("to", Date.valueOf(to))
        val rows = jdbc.query(
            "select day, metric, dimension, value from metrics_daily where day between :from and :to order by day, metric, dimension",
            p,
        ) { rs, _ -> MetricRow(rs.getDate("day").toLocalDate(), rs.getString("metric"), rs.getString("dimension"), rs.getLong("value")) }
        return HistoryResponse(from, to, rows, markers(from, to))
    }

    fun today(): TodayResponse = TodayResponse(live.snapshot(), moderation())

    fun moderation(): ModerationSummary = cached(moderationCache) {
        val none = MapSqlParameterSource()
        val open = jdbc.query(
            "select reason, count(*) as n, min(created_at) as oldest from reports where status = 'OPEN' group by reason",
            none,
        ) { rs, _ -> Triple(rs.getString("reason"), rs.getLong("n"), rs.getTimestamp("oldest")?.toInstant()) }
        ModerationSummary(
            openReports = open.sumOf { it.second },
            oldestOpenAt = open.mapNotNull { it.third }.minOrNull(),
            openByReason = open.associate { it.first to it.second },
            suspendedAccounts = jdbc.queryForObject(
                "select count(*) from users where suspended_at is not null and deleted_at is null",
                none,
                Long::class.java,
            ) ?: 0L,
            imagesRemoved30d = jdbc.queryForObject(
                "select count(*) from moderation_actions where action = 'REMOVE_IMAGE' and created_at > now() - interval '30 days'",
                none,
                Long::class.java,
            ) ?: 0L,
        )
    }

    fun health(): HealthResponse = cached(healthCache) {
        val nightly = jdbc.query(
            "select status, started_at, finished_at, detail from job_runs where job = :job order by started_at desc limit 1",
            MapSqlParameterSource("job", AdminMetricsService.JOB_NIGHTLY),
        ) { rs, _ ->
            JobHealth(rs.getString("status"), rs.getTimestamp("started_at")?.toInstant(), rs.getTimestamp("finished_at")?.toInstant(), rs.getString("detail"))
        }.firstOrNull() ?: JobHealth(null, null, null, null)

        val since = Timestamp.from(LocalDate.now(clock.withZone(IST)).atStartOfDay(IST).toInstant())
        val otp = jdbc.queryForMap(
            """
            select count(*) as sent,
                   count(*) filter (where consumed_at is null and expires_at < now()) as expired_unused,
                   count(*) filter (where consumed_at is not null or expires_at < now()) as settled
            from otp_challenges where created_at >= :since
            """,
            MapSqlParameterSource("since", since),
        )
        val settled = (otp["settled"] as Number).toLong()
        val expiredUnused = (otp["expired_unused"] as Number).toLong()

        val (smsSent, cap) = rateLimiter.globalOtpSendsToday()
        val nextUtcMidnight = LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val stats = runCatching { ses.lastTwoWeeks() }.getOrNull()
        HealthResponse(
            checkedAt = clock.instant(),
            nightly = nightly,
            otp = OtpHealth((otp["sent"] as Number).toLong(), expiredUnused, if (settled == 0L) null else expiredUnused.toDouble() / settled),
            sms = SmsHealth(smsSent, cap, nextUtcMidnight),
            msg91Balance = null,
            ses = SesHealth(alerts.configured, stats?.deliveryAttempts, stats?.bounces, stats?.complaints),
        )
    }

    fun markers(from: LocalDate, to: LocalDate): List<ReleaseMarker> =
        jdbc.query(
            "select id, day, label, created_by from release_markers where day between :from and :to order by day",
            MapSqlParameterSource("from", Date.valueOf(from)).addValue("to", Date.valueOf(to)),
        ) { rs, _ -> ReleaseMarker(rs.getObject("id", UUID::class.java), rs.getDate("day").toLocalDate(), rs.getString("label"), rs.getString("created_by")) }

    fun addMarker(day: LocalDate, label: String, adminEmail: String): ReleaseMarker {
        val marker = ReleaseMarker(UUID.randomUUID(), day, label, adminEmail)
        jdbc.update(
            "insert into release_markers (id, day, label, created_by) values (:id, :day, :label, :by)",
            MapSqlParameterSource("id", marker.id).addValue("day", Date.valueOf(day)).addValue("label", label).addValue("by", adminEmail),
        )
        return marker
    }

    fun deleteMarker(id: UUID) {
        if (jdbc.update("delete from release_markers where id = :id", MapSqlParameterSource("id", id)) == 0) {
            throw AdminNotFoundException("Release marker")
        }
    }

    /** History is stable until the nightly run: cache in the browser until 00:15 IST. */
    fun secondsUntilNextSummary(): Long {
        val now = clock.instant()
        var next = LocalDate.now(clock.withZone(IST)).atTime(LocalTime.of(0, 15)).atZone(IST).toInstant()
        if (!next.isAfter(now)) next = next.plus(Duration.ofDays(1))
        return Duration.between(now, next).seconds
    }

    fun clearCaches() {
        moderationCache.clear()
        healthCache.clear()
    }

    private fun <T> cached(cache: ConcurrentHashMap<String, Cached<T>>, load: () -> T): T {
        val now = clock.instant()
        cache[KEY]?.takeIf { it.until.isAfter(now) }?.let { return it.value }
        return load().also { cache[KEY] = Cached(it, now.plusSeconds(CACHE_SECONDS)) }
    }

    private companion object {
        const val MAX_DAYS = 400L
        const val CACHE_SECONDS = 60L
        const val KEY = "v"
    }
}

@RestController
@RequestMapping("/api/v1/admin")
class AdminInsightsController(
    private val insights: AdminInsightsService,
    private val audit: AdminAuditWriter,
    private val objectMapper: ObjectMapper,
) {
    @GetMapping("/insights/history")
    fun history(
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
        @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
    ): ResponseEntity<HistoryResponse> {
        val body = insights.history(from, to)
        val etag = "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(body))).take(32) + "\""
        val cache = CacheControl.maxAge(Duration.ofSeconds(insights.secondsUntilNextSummary())).cachePrivate()
        if (ifNoneMatch == etag) return ResponseEntity.status(304).eTag(etag).cacheControl(cache).build()
        return ResponseEntity.ok().eTag(etag).cacheControl(cache).body(body)
    }

    @GetMapping("/insights/today")
    fun today(): ResponseEntity<TodayResponse> = noStore(insights.today())

    @GetMapping("/health")
    fun health(): ResponseEntity<HealthResponse> = noStore(insights.health())

    @GetMapping("/release-markers")
    fun markers(@RequestParam from: LocalDate, @RequestParam to: LocalDate): List<ReleaseMarker> = insights.markers(from, to)

    @PostMapping("/release-markers")
    fun addMarker(@Valid @RequestBody body: NewReleaseMarker, @AuthenticationPrincipal admin: AdminPrincipal): ReleaseMarker {
        val marker = insights.addMarker(body.day, body.label.trim(), admin.email)
        audit.record("ADD_RELEASE_MARKER", admin.email, "${marker.day}: ${marker.label}")
        return marker
    }

    @DeleteMapping("/release-markers/{id}")
    fun deleteMarker(@PathVariable id: UUID, @AuthenticationPrincipal admin: AdminPrincipal): ResponseEntity<Void> {
        insights.deleteMarker(id)
        audit.record("DELETE_RELEASE_MARKER", admin.email, id.toString())
        return ResponseEntity.noContent().build()
    }

    private fun <T : Any> noStore(body: T): ResponseEntity<T> = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body)
}

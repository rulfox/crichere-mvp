package com.crichere.backend.admin

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.sql.Date
import java.sql.Timestamp
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

@ConfigurationProperties(prefix = "crichere.metrics")
data class MetricsProperties(
    /** Off in tests, which call [AdminMetricsService] directly. */
    val nightlyEnabled: Boolean = true,
    /** Days that new-user cohorts keep being recounted (profile done, joined a league). */
    val cohortDays: Long = 35,
    /** Sign-up weeks shown in the retention table. */
    val retentionWeeks: Long = 12,
    /** The longest range one backfill call may cover. */
    val maxBackfillDays: Long = 400,
)

@Configuration
@EnableConfigurationProperties(MetricsProperties::class)
class MetricsConfiguration

/** One metric name per chart input; dimensions only where a chart splits by them. */
object Metric {
    const val NEW_USERS = "new_users"
    const val USERS_TOTAL = "users_total"
    const val DAU = "active_users_day"
    const val WAU = "active_users_week"
    const val MAU = "active_users_month"
    const val LEAGUES_CREATED = "leagues_created"
    const val ACTIVE_LEAGUES = "active_leagues"
    const val LEAGUES_BY_STATUS = "leagues_by_status"
    const val JOINS = "joins"
    const val BIDS = "bids"
    const val AUCTIONS_COMPLETED = "auctions_completed"
    const val REPORTS_OPENED = "reports_opened"
    const val REPORTS_CLOSED = "reports_closed"
    const val REPORT_CLOSE_MEDIAN_MINUTES_30D = "report_close_median_minutes_30d"
    const val OTP_PEOPLE_SENT = "otp_people_sent"
    const val OTP_PEOPLE_SIGNED_IN = "otp_people_signed_in"
    const val COHORT_PROFILE_DONE = "cohort_profile_done"
    const val COHORT_JOINED = "cohort_joined"
    const val USERS_BY_DISTRICT = "users_by_district"
    const val RETENTION_COHORT_SIZE = "retention_cohort_size"
    const val RETENTION_WEEK = "retention_week"
}

data class MetricsRunResult(val ran: Boolean, val detail: String)

/**
 * The nightly summary (docs/PHASE20.md section 4, Step 7): everything the history charts read, written
 * once into `metrics_daily` so viewing the dashboard costs nothing. Internal accounts are left out.
 * Idempotent: every value is an upsert, so a rerun or a backfill of the same day gives the same rows.
 */
@Service
class AdminMetricsService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val tx: TransactionTemplate,
    private val properties: MetricsProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Yesterday in full, the rolling figures, then the retention purges. */
    fun nightly(): MetricsRunResult {
        val today = LocalDate.now(clock.withZone(IST))
        return runJob(JOB_NIGHTLY) {
            computeDay(today.minusDays(1), includeSnapshots = true, includeOtp = true)
            computeRolling(today)
            val purged = purge()
            "day ${today.minusDays(1)}; purged $purged"
        }
    }

    /**
     * Past days, oldest first. Snapshot metrics (status split, districts) and the OTP funnel can't be
     * rebuilt for the past, so only flows and counts that their timestamps support are written.
     */
    fun backfill(from: LocalDate, to: LocalDate): MetricsRunResult {
        val today = LocalDate.now(clock.withZone(IST))
        require(!from.isAfter(to)) { "from is after to" }
        require(to.isBefore(today)) { "to must be before today" }
        require(from.plusDays(properties.maxBackfillDays).isAfter(to)) { "at most ${properties.maxBackfillDays} days at once" }
        return runJob(JOB_BACKFILL) {
            var day = from
            while (!day.isAfter(to)) {
                computeDay(day, includeSnapshots = false, includeOtp = false)
                day = day.plusDays(1)
            }
            computeRolling(today)
            "days $from to $to"
        }
    }

    /**
     * One run at a time across instances (transaction-scoped advisory lock), recorded in `job_runs`
     * for the system health card. A skipped run (lock held) is recorded as such.
     */
    private fun runJob(job: String, work: () -> String): MetricsRunResult {
        val runId = UUID.randomUUID()
        jdbc.update(
            "insert into job_runs (id, job, started_at, status) values (:id, :job, now(), 'RUNNING')",
            MapSqlParameterSource("id", runId).addValue("job", job),
        )
        return try {
            val detail = tx.execute {
                val locked = jdbc.queryForObject("select pg_try_advisory_xact_lock(:key)", MapSqlParameterSource("key", LOCK_KEY), Boolean::class.java)
                if (locked != true) return@execute null
                jdbc.update("set local statement_timeout = '120s'", MapSqlParameterSource())
                work()
            }
            val result = if (detail == null) MetricsRunResult(false, "another run holds the lock") else MetricsRunResult(true, detail)
            finish(runId, "SUCCEEDED", result.detail)
            result
        } catch (e: Exception) {
            log.error("Metrics job {} failed", job, e)
            finish(runId, "FAILED", e.message?.take(500) ?: e.javaClass.simpleName)
            throw e
        }
    }

    private fun finish(runId: UUID, status: String, detail: String) {
        jdbc.update(
            "update job_runs set finished_at = now(), status = :status, detail = :detail where id = :id",
            MapSqlParameterSource("id", runId).addValue("status", status).addValue("detail", detail),
        )
    }

    // ------------------------------------------------------------------ one day

    internal fun computeDay(day: LocalDate, includeSnapshots: Boolean, includeOtp: Boolean) {
        val p = bounds(day)
        fun put(metric: String, sql: String) = upsert(day, metric, "", count(sql, p))

        put(Metric.NEW_USERS, "select count(*) from users where created_at >= :start and created_at < :end and not is_internal")
        put(
            Metric.USERS_TOTAL,
            "select count(*) from users where created_at < :end and not is_internal and (deleted_at is null or deleted_at >= :end)",
        )
        put(Metric.DAU, "$ACTIVE where a.day = cast(:day as date)")
        put(Metric.WAU, "$ACTIVE where a.day between cast(:day as date) - 6 and cast(:day as date)")
        put(Metric.MAU, "$ACTIVE where a.day between cast(:day as date) - 29 and cast(:day as date)")
        put(Metric.LEAGUES_CREATED, "$LEAGUES where l.created_at >= :start and l.created_at < :end")
        put(Metric.ACTIVE_LEAGUES, "$LEAGUES where l.created_at < :end and (l.completed_at is null or l.completed_at >= :end)")
        put(
            Metric.JOINS,
            "select count(*) from league_players p join users u on u.id = p.user_id " +
                "where p.joined_at >= :start and p.joined_at < :end and not u.is_internal",
        )
        put(
            Metric.BIDS,
            "select count(*) from auction_bids b join league_franchises f on f.id = b.franchise_id " +
                "join users u on u.id = f.owner_user_id where b.placed_at >= :start and b.placed_at < :end and not u.is_internal",
        )
        put(Metric.AUCTIONS_COMPLETED, "$LEAGUES where l.auction_completed_at >= :start and l.auction_completed_at < :end")
        put(
            Metric.REPORTS_OPENED,
            "select count(*) from reports r left join users u on u.id = r.reporter_id " +
                "where r.created_at >= :start and r.created_at < :end and not coalesce(u.is_internal, false)",
        )
        put(Metric.REPORTS_CLOSED, "select count(*) from reports where resolved_at >= :start and resolved_at < :end")
        put(
            Metric.REPORT_CLOSE_MEDIAN_MINUTES_30D,
            "select coalesce(round(percentile_cont(0.5) within group (order by extract(epoch from resolved_at - created_at) / 60)), 0) " +
                "from reports where resolved_at >= cast(:start as timestamptz) - interval '29 days' and resolved_at < :end",
        )

        if (includeOtp) {
            // Counted now because otp_challenges rows are deleted a day after they expire (section 3).
            val otp = "select count(distinct c.phone_lookup_hash) from otp_challenges c left join users u on u.phone_lookup_hash = c.phone_lookup_hash " +
                "where c.created_at >= :start and c.created_at < :end and not coalesce(u.is_internal, false)"
            put(Metric.OTP_PEOPLE_SENT, otp)
            // "Signed in" = the number reached an account. A returning user who never entered the code counts too.
            put(Metric.OTP_PEOPLE_SIGNED_IN, "$otp and u.id is not null")
        }

        if (includeSnapshots) {
            replaceSplit(
                day,
                Metric.LEAGUES_BY_STATUS,
                """
                select case
                         when l.completed_at is not null then 'completed'
                         when l.auction_status = 'IN_PROGRESS' then 'auction_live'
                         when l.auction_status = 'COMPLETED' then 'auction_done'
                         else 'open'
                       end as dim, count(*) as value
                from leagues l join users u on u.id = l.organizer_user_id where not u.is_internal
                group by 1
                """,
                p,
            )
            replaceSplit(
                day,
                Metric.USERS_BY_DISTRICT,
                """
                select pr.state || '|' || pr.district as dim, count(*) as value
                from users u join profiles pr on pr.user_id = u.id
                where not u.is_internal and u.deleted_at is null and pr.state is not null and pr.district is not null
                group by 1
                """,
                p,
            )
        }
    }

    // ------------------------------------------------------------------ rolling recounts

    /** New-user cohorts keep progressing after their day, so recent ones are recounted every night. */
    internal fun computeRolling(today: LocalDate) {
        val p = MapSqlParameterSource("from", Timestamp.from(today.minusDays(properties.cohortDays).atStartOfDay(IST).toInstant()))
            .addValue("to", Timestamp.from(today.atStartOfDay(IST).toInstant()))
        val signupDay = "(u.created_at at time zone 'Asia/Kolkata')::date"
        upsertGrouped(
            Metric.COHORT_PROFILE_DONE,
            """
            select $signupDay as day, count(*) as value from users u join profiles pr on pr.user_id = u.id
            where u.created_at >= :from and u.created_at < :to and not u.is_internal $PROFILE_DONE
            group by 1
            """,
            p,
        )
        upsertGrouped(
            Metric.COHORT_JOINED,
            """
            select $signupDay as day, count(*) as value from users u
            where u.created_at >= :from and u.created_at < :to and not u.is_internal
              and exists (select 1 from league_players lp where lp.user_id = u.id)
            group by 1
            """,
            p,
        )
        computeRetention(today)
    }

    /** Weekly sign-up cohorts (Monday start, IST): size, and how many were active in each later week. */
    private fun computeRetention(today: LocalDate) {
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstWeek = thisWeek.minusWeeks(properties.retentionWeeks - 1)
        val p = MapSqlParameterSource("first", Date.valueOf(firstWeek))
            .addValue("firstAt", Timestamp.from(firstWeek.atStartOfDay(IST).toInstant()))
        val week = "date_trunc('week', (u.created_at at time zone 'Asia/Kolkata'))::date"
        upsertGrouped(
            Metric.RETENTION_COHORT_SIZE,
            "select $week as day, count(*) as value from users u where u.created_at >= :firstAt and not u.is_internal group by 1",
            p,
        )
        val rows = jdbc.query(
            """
            select $week as cohort, (date_trunc('week', a.day)::date - $week) / 7 as k, count(distinct a.user_id) as value
            from users u join user_activity_days a on a.user_id = u.id
            where u.created_at >= :firstAt and not u.is_internal and a.day >= :first
            group by 1, 2
            """,
            p,
        ) { rs, _ -> Triple(rs.getDate("cohort").toLocalDate(), rs.getInt("k"), rs.getLong("value")) }
        rows.filter { it.second >= 0 }.forEach { (cohort, k, value) -> upsert(cohort, Metric.RETENTION_WEEK, "w$k", value) }
    }

    // ------------------------------------------------------------------ retention purges

    /**
     * Privacy Policy section 4: activity days after 13 months; closed reports and moderation records
     * 24 months after closing (an action on a still-suspended account stays); finished job rows after 90 days.
     */
    internal fun purge(): String {
        val none = MapSqlParameterSource()
        val activity = jdbc.update("delete from user_activity_days where day < (now() at time zone 'Asia/Kolkata')::date - interval '13 months'", none)
        val reports = jdbc.update("delete from reports where status <> 'OPEN' and resolved_at < now() - interval '24 months'", none)
        val actions = jdbc.update(
            """
            delete from moderation_actions m where m.created_at < now() - interval '24 months'
              and not (m.action = 'SUSPEND' and exists (select 1 from users u where u.id = m.user_id and u.suspended_at is not null))
              and not exists (select 1 from reports r where r.id = m.report_id and r.status = 'OPEN')
            """,
            none,
        )
        val jobs = jdbc.update("delete from job_runs where started_at < now() - interval '90 days' and status <> 'RUNNING'", none)
        return "activity $activity, reports $reports, actions $actions, job runs $jobs"
    }

    // ------------------------------------------------------------------ helpers

    private fun bounds(day: LocalDate) = MapSqlParameterSource("day", Date.valueOf(day))
        .addValue("start", Timestamp.from(day.atStartOfDay(IST).toInstant()))
        .addValue("end", Timestamp.from(day.plusDays(1).atStartOfDay(IST).toInstant()))

    private fun count(sql: String, p: MapSqlParameterSource): Long = jdbc.queryForObject(sql, p, Long::class.java) ?: 0L

    private fun upsert(day: LocalDate, metric: String, dimension: String, value: Long) {
        jdbc.update(
            """
            insert into metrics_daily (day, metric, dimension, value) values (:day, :metric, :dim, :value)
            on conflict (day, metric, dimension) do update set value = excluded.value
            """,
            MapSqlParameterSource("day", Date.valueOf(day)).addValue("metric", metric).addValue("dim", dimension).addValue("value", value),
        )
    }

    /** A split (dimension -> value) for one day; rows for values that disappeared are removed. */
    private fun replaceSplit(day: LocalDate, metric: String, sql: String, p: MapSqlParameterSource) {
        jdbc.update(
            "delete from metrics_daily where day = :day and metric = :metric",
            MapSqlParameterSource("day", Date.valueOf(day)).addValue("metric", metric),
        )
        jdbc.query(sql, p) { rs, _ -> rs.getString("dim").take(DIMENSION_LIMIT) to rs.getLong("value") }
            .forEach { (dim, value) -> upsert(day, metric, dim, value) }
    }

    private fun upsertGrouped(metric: String, sql: String, p: MapSqlParameterSource) {
        jdbc.query(sql, p) { rs, _ -> rs.getDate("day").toLocalDate() to rs.getLong("value") }
            .forEach { (day, value) -> upsert(day, metric, "", value) }
    }

    companion object {
        const val JOB_NIGHTLY = "metrics_nightly"
        const val JOB_BACKFILL = "metrics_backfill"
        private const val LOCK_KEY = 2020_0007L
        private const val DIMENSION_LIMIT = 120

        private const val ACTIVE =
            "select count(distinct a.user_id) from user_activity_days a join users u on u.id = a.user_id and not u.is_internal"
        private const val LEAGUES = "select count(*) from leagues l join users u on u.id = l.organizer_user_id and not u.is_internal"

        /** ProfileCompletionService.isComplete in SQL. */
        const val PROFILE_DONE = """
            and coalesce(pr.name, '') <> '' and (coalesce(pr.photo_url, '') <> '' or pr.photo_removed)
            and coalesce(pr.state, '') <> '' and coalesce(pr.district, '') <> '' and pr.batting_style is not null
            and pr.playing_role is not null and (pr.playing_role not in ('BOWLER', 'ALL_ROUNDER') or pr.bowling_style is not null)
        """
    }
}

@Component
class AdminMetricsJob(private val service: AdminMetricsService, private val properties: MetricsProperties) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Kolkata")
    fun run() {
        if (!properties.nightlyEnabled) return
        try {
            service.nightly()
        } catch (e: Exception) {
            log.error("Nightly metrics run failed; the health card shows it", e)
        }
    }
}

data class BackfillRequest(val from: LocalDate, val to: LocalDate)

/** Rebuilds past days of history, e.g. after a metric is added (docs/PHASE20.md Step 7). Audited. */
@RestController
class AdminMetricsController(private val service: AdminMetricsService, private val audit: AdminAuditWriter) {
    @PostMapping("/api/v1/admin/metrics/backfill")
    fun backfill(
        @RequestBody body: BackfillRequest,
        @AuthenticationPrincipal admin: AdminPrincipal,
    ): ResponseEntity<MetricsRunResult> {
        val result = try {
            service.backfill(body.from, body.to)
        } catch (e: IllegalArgumentException) {
            throw AdminConflictException("BAD_RANGE", e.message ?: "Invalid range.")
        }
        audit.record("METRICS_BACKFILL", admin.email, "${body.from} to ${body.to}: ${result.detail}")
        return ResponseEntity.ok(result)
    }
}

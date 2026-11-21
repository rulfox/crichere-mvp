package com.crichere.backend.admin

import com.crichere.backend.auth.VerifiedFirebaseToken
import com.crichere.backend.common.AbstractWebIntegrationTest
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.mockk.every
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.sql.Date
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Date as JavaDate
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The nightly summary, backfill and retention purges (docs/PHASE20.md Step 7). */
@Import(TestGoogleJwksConfiguration::class)
class AdminMetricsIntegrationTest : AbstractWebIntegrationTest() {

    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var metrics: AdminMetricsService
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var signInLimiter: AdminSignInRateLimiter

    // ------------------------------------------------------------------ one day

    @Test
    fun `a past day counts flows, active users and reports, leaving internal accounts out`() {
        val day = LocalDate.of(2025, 3, 12)
        val a = newUserOn(day)
        val b = newUserOn(day)
        val internal = newUserOn(day)
        jdbc.update("update users set is_internal = true where id = ?", internal)

        listOf(a, internal).forEach { activity(it, day) }
        activity(b, day.minusDays(3))
        activity(b, day.minusDays(20))
        listOf(30L, 90L, 600L).forEach { minutes ->
            jdbc.update(
                "insert into reports (id, target_type, reason, snapshot, status, created_at, resolved_at) " +
                    "values (?, 'USER', 'IMPERSONATION', '{}', 'DISMISSED', ?::timestamptz - make_interval(mins => ?), ?::timestamptz)",
                UUID.randomUUID(), "$day 15:00:00+05:30", minutes.toInt(), "$day 15:00:00+05:30",
            )
        }

        metrics.computeDay(day, includeSnapshots = false, includeOtp = false)
        metrics.computeDay(day, includeSnapshots = false, includeOtp = false) // idempotent

        assertEquals(2, value(day, Metric.NEW_USERS))
        assertEquals(1, value(day, Metric.DAU))
        assertEquals(2, value(day, Metric.WAU))
        assertEquals(2, value(day, Metric.MAU))
        assertEquals(3, value(day, Metric.REPORTS_CLOSED))
        assertEquals(90, value(day, Metric.REPORT_CLOSE_MEDIAN_MINUTES_30D))
        assertEquals(1, jdbc.queryForObject("select count(*) from metrics_daily where day = ? and metric = ?", Long::class.java, Date.valueOf(day), Metric.NEW_USERS))
    }

    @Test
    fun `snapshots split leagues by status and users by district`() {
        val today = LocalDate.now(IST)
        val organizer = signInNewUser()
        jdbc.update("insert into profiles (user_id, name, state, district) values (?, 'Asha', 'Kerala', 'Alappuzha')", organizer.id)
        createLeague(organizer.token)

        metrics.computeDay(today, includeSnapshots = true, includeOtp = true)

        val split = jdbc.queryForList(
            "select dimension, value from metrics_daily where day = ? and metric = ?",
            Date.valueOf(today), Metric.LEAGUES_BY_STATUS,
        ).associate { it["dimension"] as String to (it["value"] as Number).toLong() }
        val leagues = jdbc.queryForObject(
            "select count(*) from leagues l join users u on u.id = l.organizer_user_id where not u.is_internal",
            Long::class.java,
        )
        assertEquals(leagues, split.values.sum())
        assertTrue((split["open"] ?: 0) >= 1, split.toString())
        assertTrue(value(today, Metric.USERS_BY_DISTRICT, "Kerala|Alappuzha") >= 1)
        assertTrue(value(today, Metric.OTP_PEOPLE_SENT) >= 0)
    }

    // ------------------------------------------------------------------ rolling

    @Test
    fun `new-user cohorts and weekly retention are recounted`() {
        val today = LocalDate.now(IST)
        val week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(6)
        val user = newUserOn(week.plusDays(1))
        activity(user, week.plusDays(1))
        activity(user, week.plusDays(8))
        activity(user, week.plusDays(23))

        val recent = newUserOn(today.minusDays(2))
        jdbc.update(
            "insert into profiles (user_id, name, photo_url, state, district, playing_role, batting_style) " +
                "values (?, 'Ravi', 'https://x/p.jpg', 'Kerala', 'Thrissur', 'BATSMAN', 'RIGHT_HAND')",
            recent,
        )

        metrics.computeRolling(today)

        assertEquals(1, value(week, Metric.RETENTION_COHORT_SIZE))
        assertEquals(1, value(week, Metric.RETENTION_WEEK, "w0"))
        assertEquals(1, value(week, Metric.RETENTION_WEEK, "w1"))
        assertEquals(0, value(week, Metric.RETENTION_WEEK, "w2"))
        assertEquals(1, value(week, Metric.RETENTION_WEEK, "w3"))
        assertTrue(value(today.minusDays(2), Metric.COHORT_PROFILE_DONE) >= 1)
    }

    // ------------------------------------------------------------------ purges

    @Test
    fun `old activity, closed reports and moderation records are purged by the policy's periods`() {
        val user = signInNewUser().id
        val today = LocalDate.now(IST)
        activity(user, today.minusMonths(14))
        activity(user, today.minusMonths(12))
        val oldClosed = UUID.randomUUID()
        val oldOpen = UUID.randomUUID()
        jdbc.update("insert into reports (id, target_type, reason, snapshot, status, created_at, resolved_at) values (?, 'USER', 'UNDER_18', '{}', 'RESOLVED', now() - interval '26 months', now() - interval '25 months')", oldClosed)
        jdbc.update("insert into reports (id, target_type, reason, snapshot, created_at) values (?, 'USER', 'UNDER_18', '{}', now() - interval '26 months')", oldOpen)
        val oldAction = UUID.randomUUID()
        val keptSuspension = UUID.randomUUID()
        jdbc.update("insert into moderation_actions (id, action, note, created_at) values (?, 'DISMISS_REPORT', 'x', now() - interval '25 months')", oldAction)
        jdbc.update("update users set suspended_at = now() where id = ?", user)
        jdbc.update("insert into moderation_actions (id, action, user_id, created_at) values (?, 'SUSPEND', ?, now() - interval '25 months')", keptSuspension, user)

        metrics.purge()

        assertEquals(listOf(Date.valueOf(today.minusMonths(12))), jdbc.queryForList("select day from user_activity_days where user_id = ?", Date::class.java, user))
        assertFalse(exists("reports", oldClosed))
        assertTrue(exists("reports", oldOpen))
        assertFalse(exists("moderation_actions", oldAction))
        assertTrue(exists("moderation_actions", keptSuspension))
    }

    // ------------------------------------------------------------------ job runs

    @Test
    fun `the nightly run is recorded, and a second run while one holds the lock is skipped`() {
        val result = metrics.nightly()
        assertTrue(result.ran)
        assertEquals("SUCCEEDED", lastRun(AdminMetricsService.JOB_NIGHTLY))

        dataSource.connection.use { held ->
            held.createStatement().use { it.execute("select pg_advisory_lock(20200007)") }
            try {
                val skipped = metrics.nightly()
                assertFalse(skipped.ran)
            } finally {
                held.createStatement().use { it.execute("select pg_advisory_unlock(20200007)") }
            }
        }
    }

    @Test
    fun `backfill is an audited admin action with a bounded range`() {
        signInLimiter.reset()
        val cookie = adminCookie()
        backfill(cookie, "2025-01-10", "2025-01-01").andExpect(status().isConflict).andExpect(jsonPath("$.code").value("BAD_RANGE"))
        backfill(cookie, "2023-01-01", "2025-01-01").andExpect(status().isConflict)
        backfill(cookie, LocalDate.now(IST).toString(), LocalDate.now(IST).toString()).andExpect(status().isConflict)

        backfill(cookie, "2025-01-01", "2025-01-03").andExpect(status().isOk).andExpect(jsonPath("$.ran").value(true))
        assertEquals(3, jdbc.queryForObject("select count(distinct day) from metrics_daily where metric = 'new_users' and day between '2025-01-01' and '2025-01-03'", Long::class.java))
        assertEquals(
            1,
            jdbc.queryForObject("select count(*) from moderation_actions where action = 'METRICS_BACKFILL' and note like '2025-01-01 to 2025-01-03%'", Long::class.java),
        )
        assertEquals("SUCCEEDED", lastRun(AdminMetricsService.JOB_BACKFILL))
    }

    // ------------------------------------------------------------------ helpers

    private data class AppUser(val token: String, val id: UUID)

    private fun value(day: LocalDate, metric: String, dimension: String = ""): Long =
        jdbc.queryForList(
            "select value from metrics_daily where day = ? and metric = ? and dimension = ?",
            Long::class.java, Date.valueOf(day), metric, dimension,
        ).firstOrNull() ?: 0L

    private fun newUserOn(day: LocalDate): UUID {
        val id = signInNewUser().id
        jdbc.update("update users set created_at = ?::timestamptz where id = ?", "$day 12:00:00+05:30", id)
        return id
    }

    private fun activity(userId: UUID, day: LocalDate) {
        jdbc.update("insert into user_activity_days (user_id, day) values (?, ?) on conflict do nothing", userId, Date.valueOf(day))
    }

    private fun exists(table: String, id: UUID): Boolean =
        jdbc.queryForObject("select exists(select 1 from $table where id = ?)", Boolean::class.java, id) == true

    private fun lastRun(job: String): String? =
        jdbc.queryForList("select status from job_runs where job = ? and status <> 'RUNNING' order by started_at desc limit 1", String::class.java, job)
            .firstOrNull()

    private fun backfill(cookie: Cookie, from: String, to: String) = mockMvc.perform(
        post("/api/v1/admin/metrics/backfill").cookie(cookie).header("Origin", ORIGIN).header(ADMIN_HEADER, "1")
            .contentType(MediaType.APPLICATION_JSON).content("""{"from":"$from","to":"$to"}"""),
    )

    private fun adminCookie(): Cookie {
        val claims = JWTClaimsSet.Builder()
            .issuer("https://accounts.google.com").audience(CLIENT_ID).subject("1234567890")
            .claim("email", "admin@example.com").claim("email_verified", true)
            .issueTime(JavaDate()).expirationTime(JavaDate(System.currentTimeMillis() + 600_000))
            .build()
        val token = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TestGoogleKeys.key.keyID).build(), claims)
            .apply { sign(RSASSASigner(TestGoogleKeys.key)) }.serialize()
        val setCookie = mockMvc.perform(
            post("/api/v1/admin/session").header("Origin", ORIGIN).header(ADMIN_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON).content("""{"credential":"$token"}"""),
        ).andExpect(status().isOk).andReturn().response.getHeader(HttpHeaders.SET_COOKIE)!!
        return Cookie(ADMIN_COOKIE, setCookie.substringAfter("$ADMIN_COOKIE=").substringBefore(";"))
    }

    private fun signInNewUser(): AppUser {
        val phone = "+9196" + UUID.randomUUID().mostSignificantBits.toString().filter { it.isDigit() }.take(8).padEnd(8, '1')
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)
        val body = mockMvc.perform(
            post("/api/v1/auth/session").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("idToken" to "a-valid-firebase-id-token", "termsAccepted" to true))),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        @Suppress("UNCHECKED_CAST")
        val session = objectMapper.readValue(body, Map::class.java) as Map<String, Any?>
        return AppUser(session["accessToken"] as String, UUID.fromString(session["userId"] as String))
    }

    private fun createLeague(token: String) {
        fun appPost(path: String, body: Map<String, Any?>): Map<*, *> = objectMapper.readValue(
            mockMvc.perform(
                post(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)),
            ).andExpect(status().isOk).andReturn().response.contentAsString,
            Map::class.java,
        )
        val groundId = appPost(
            "/api/v1/grounds",
            mapOf("name" to "Test Ground", "state" to "Kerala", "district" to "Alappuzha", "latitude" to 9.5, "longitude" to 76.3),
        )["id"]
        appPost(
            "/api/v1/leagues",
            mapOf("name" to "Metrics League", "state" to "Kerala", "district" to "Alappuzha", "groundId" to groundId, "startsOn" to "2026-12-12", "playersRequired" to 10),
        )
    }

    private companion object {
        const val ORIGIN = "https://admin.test"
        const val CLIENT_ID = "test-admin-client.apps.googleusercontent.com"
    }
}

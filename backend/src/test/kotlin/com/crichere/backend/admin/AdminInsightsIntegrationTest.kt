package com.crichere.backend.admin

import com.crichere.backend.common.AbstractWebIntegrationTest
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** History, today, health and release markers for the overview (docs/PHASE20.md Step 8). */
@Import(TestGoogleJwksConfiguration::class)
class AdminInsightsIntegrationTest : AbstractWebIntegrationTest() {

    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var insights: AdminInsightsService
    @Autowired private lateinit var metrics: AdminMetricsService
    @Autowired private lateinit var signInLimiter: AdminSignInRateLimiter

    private lateinit var admin: Cookie

    @BeforeEach
    fun setUp() {
        signInLimiter.reset()
        insights.clearCaches()
        admin = adminCookie()
    }

    @Test
    fun `every insight route needs an admin session`() {
        listOf("/api/v1/admin/insights/history?from=2025-01-01&to=2025-01-02", "/api/v1/admin/insights/today", "/api/v1/admin/health")
            .forEach { mockMvc.perform(get(it)).andExpect(status().isUnauthorized) }
    }

    @Test
    fun `history reads the summary for a range, cached privately until the next run, with an ETag`() {
        jdbc.update(
            "insert into metrics_daily (day, metric, dimension, value) values " +
                "('2024-06-01', 'new_users', '', 7), ('2024-06-02', 'new_users', '', 9), ('2024-07-01', 'new_users', '', 99)",
        )
        val path = "/api/v1/admin/insights/history?from=2024-06-01&to=2024-06-30"
        val result = mockMvc.perform(get(path).cookie(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rows.length()").value(2))
            .andExpect(jsonPath("$.rows[1].value").value(9))
            .andReturn()
        val cache = result.response.getHeader(HttpHeaders.CACHE_CONTROL)!!
        assertTrue("private" in cache && "max-age=" in cache, cache)
        val etag = result.response.getHeader(HttpHeaders.ETAG)!!

        mockMvc.perform(get(path).cookie(admin).header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isNotModified)
        mockMvc.perform(get("/api/v1/admin/insights/history?from=2024-06-30&to=2024-06-01").cookie(admin))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("BAD_RANGE"))
        mockMvc.perform(get("/api/v1/admin/insights/history?from=2023-01-01&to=2024-06-01").cookie(admin))
            .andExpect(status().isConflict)
    }

    @Test
    fun `today combines live counters and the open reports summary, never cached`() {
        jdbc.update(
            "insert into reports (id, target_type, reason, snapshot, created_at) values (?, 'USER', 'UNDER_18', '{}', now() - interval '19 hours')",
            UUID.randomUUID(),
        )
        mockMvc.perform(get("/api/v1/admin/insights/today").cookie(admin))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.live.today.signUps").isNumber)
            .andExpect(jsonPath("$.moderation.openByReason.UNDER_18").isNumber)
            .andExpect(jsonPath("$.moderation.oldestOpenAt").isNotEmpty)
    }

    @Test
    fun `health shows the last nightly run, SMS against the cap, and SES as not configured here`() {
        metrics.nightly()
        mockMvc.perform(get("/api/v1/admin/health").cookie(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nightly.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.sms.dailyCap").value(2000))
            .andExpect(jsonPath("$.otp.codesSentToday").isNumber)
            .andExpect(jsonPath("$.ses.configured").value(false))
            .andExpect(jsonPath("$.msg91Balance").doesNotExist())
    }

    @Test
    fun `release markers are added and removed as audited admin actions`() {
        val json = MediaType.APPLICATION_JSON
        mockMvc.perform(post("/api/v1/admin/release-markers").cookie(admin).contentType(json).content("""{"day":"2026-11-20","label":"v1.0"}"""))
            .andExpect(status().isForbidden)
        val body = mockMvc.perform(
            post("/api/v1/admin/release-markers").cookie(admin).header("Origin", ORIGIN).header(ADMIN_HEADER, "1")
                .contentType(json).content("""{"day":"2026-11-20","label":"  v1.0 on Play "}"""),
        ).andExpect(status().isOk).andExpect(jsonPath("$.label").value("v1.0 on Play")).andReturn().response.contentAsString
        val id = objectMapper.readValue(body, Map::class.java)["id"] as String

        mockMvc.perform(get("/api/v1/admin/insights/history?from=2026-11-01&to=2026-11-30").cookie(admin))
            .andExpect(jsonPath("$.markers[?(@.id == '$id')].label").value("v1.0 on Play"))
        val remove = delete("/api/v1/admin/release-markers/$id")
        mockMvc.perform(remove.cookie(admin).header("Origin", ORIGIN).header(ADMIN_HEADER, "1")).andExpect(status().isNoContent)
        mockMvc.perform(delete("/api/v1/admin/release-markers/$id").cookie(admin).header("Origin", ORIGIN).header(ADMIN_HEADER, "1"))
            .andExpect(status().isNotFound)
        val audited = jdbc.queryForObject(
            "select count(*) from moderation_actions where (action = 'ADD_RELEASE_MARKER' and note like '%v1.0 on Play') or (action = 'DELETE_RELEASE_MARKER' and note = ?)",
            Long::class.java,
            id,
        )
        assertEquals(2, audited)
    }

    private fun adminCookie(): Cookie {
        val claims = JWTClaimsSet.Builder()
            .issuer("https://accounts.google.com").audience(CLIENT_ID).subject("1234567890")
            .claim("email", "admin@example.com").claim("email_verified", true)
            .issueTime(Date()).expirationTime(Date(System.currentTimeMillis() + 600_000))
            .build()
        val token = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TestGoogleKeys.key.keyID).build(), claims)
            .apply { sign(RSASSASigner(TestGoogleKeys.key)) }.serialize()
        val setCookie = mockMvc.perform(
            post("/api/v1/admin/session").header("Origin", ORIGIN).header(ADMIN_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON).content("""{"credential":"$token"}"""),
        ).andExpect(status().isOk).andReturn().response.getHeader(HttpHeaders.SET_COOKIE)!!
        return Cookie(ADMIN_COOKIE, setCookie.substringAfter("$ADMIN_COOKIE=").substringBefore(";"))
    }

    private companion object {
        const val ORIGIN = "https://admin.test"
        const val CLIENT_ID = "test-admin-client.apps.googleusercontent.com"
    }
}

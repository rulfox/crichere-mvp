package com.crichere.backend.admin

import com.crichere.backend.account.AccountPurgeService
import com.crichere.backend.account.PurgeOutcome
import com.crichere.backend.auth.VerifiedFirebaseToken
import com.crichere.backend.common.AbstractWebIntegrationTest
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.mockk.every
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Activity days, today's counters and the admin live stream (docs/PHASE20.md Step 6). */
@Import(TestGoogleJwksConfiguration::class)
class AdminLiveIntegrationTest : AbstractWebIntegrationTest() {

    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var activity: ActivityTracker
    @Autowired private lateinit var metrics: LiveMetrics
    @Autowired private lateinit var stream: AdminLiveStream
    @Autowired private lateinit var events: ApplicationEventPublisher
    @Autowired private lateinit var signInLimiter: AdminSignInRateLimiter
    @Autowired private lateinit var purge: AccountPurgeService

    @BeforeEach
    fun reset() {
        signInLimiter.reset()
        metrics.reseed()
    }

    // ------------------------------------------------------------------ activity days

    @Test
    fun `a successful signed-in request records today once, and failures or anonymous calls don't`() {
        val user = signInNewUser()
        repeat(2) { appGet(user.token, "/api/v1/blocks").andExpect(status().isOk) }
        activity.flush()
        assertEquals(1, activityRows(user.id))

        val other = signInNewUser()
        appGet(other.token, "/api/v1/leagues/${UUID.randomUUID()}/auction/results").andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/blocks")).andExpect(status().isUnauthorized)
        activity.flush()
        assertEquals(0, activityRows(other.id))
    }

    @Test
    fun `a suspended account's requests don't count`() {
        val user = signInNewUser()
        jdbc.update("update users set suspended_at = now() where id = ?", user.id)
        appGet(user.token, "/api/v1/blocks")
        activity.flush()
        assertEquals(0, activityRows(user.id))
    }

    @Test
    fun `erasing an account removes its activity rows and today's trace`() {
        val user = signInNewUser()
        appGet(user.token, "/api/v1/blocks").andExpect(status().isOk)
        activity.flush()
        assertEquals(1, activityRows(user.id))

        activity.erase(user.id)
        assertEquals(0, activityRows(user.id))
        assertFalse(user.id in activity.todayIds())
    }

    @Test
    fun `the account purge erases activity rows`() {
        val user = signInNewUser()
        appGet(user.token, "/api/v1/blocks").andExpect(status().isOk)
        activity.flush()
        jdbc.update("update users set deletion_requested_at = now() - interval '30 days' where id = ?", user.id)

        assertEquals(PurgeOutcome.PURGED, purge.purgeDue(Instant.now())[user.id])
        assertEquals(0, activityRows(user.id))
    }

    // ------------------------------------------------------------------ today's counters

    @Test
    fun `sign-ups, leagues, joins and reports count after commit, with district-only feed lines`() {
        val before = metrics.snapshot().today
        val organizer = signInNewUser()
        val player = signInNewUser()
        val leagueId = createLeague(organizer.token)
        appPost(player.token, "/api/v1/leagues/$leagueId/players", emptyMap()).andExpect(status().isOk)
        appPost(
            player.token,
            "/api/v1/reports",
            mapOf("targetType" to "LEAGUE", "targetLeagueId" to leagueId, "reason" to "FAKE_OR_SCAM_LEAGUE"),
        ).andExpect(status().isCreated)

        val after = metrics.snapshot()
        assertEquals(before.signUps + 2, after.today.signUps)
        assertEquals(before.leagues + 1, after.today.leagues)
        assertEquals(before.joins + 1, after.today.joins)
        assertEquals(before.reports + 1, after.today.reports)
        assertEquals(LocalDate.now(IST), after.day)

        val types = after.feed.take(4).map { it.type }
        assertEquals(listOf("REPORT_FILED", "PLAYER_JOINED", "LEAGUE_CREATED", "SIGN_UP"), types)
        assertEquals("Bengaluru Urban", after.feed[1].district)
        val json = objectMapper.writeValueAsString(after)
        assertFalse(organizer.id.toString() in json || player.id.toString() in json || "Weekend League" in json, json)
    }

    @Test
    fun `a reseed gives the same numbers the live counters reached`() {
        val organizer = signInNewUser()
        createLeague(organizer.token)
        val live = metrics.snapshot().today
        metrics.reseed()
        val reseeded = metrics.snapshot().today
        assertEquals(live.signUps, reseeded.signUps)
        assertEquals(live.leagues, reseeded.leagues)
        assertEquals(live.joins, reseeded.joins)
        assertEquals(live.reports, reseeded.reports)
    }

    @Test
    fun `internal accounts are left out`() {
        val tester = signInNewUser()
        jdbc.update("update users set is_internal = true where id = ?", tester.id)
        metrics.setInternal(tester.id, true)
        val before = metrics.snapshot()

        createLeague(tester.token)
        appGet(tester.token, "/api/v1/blocks").andExpect(status().isOk)

        val after = metrics.snapshot()
        assertEquals(before.today.leagues, after.today.leagues)
        assertEquals(before.feed, after.feed)
        assertEquals(before.today.activeUsers, after.today.activeUsers)
    }

    @Test
    fun `live auctions and bids per minute follow auction events`() {
        val organizer = signInNewUser()
        val leagueId = UUID.randomUUID()
        val before = metrics.snapshot().now

        events.publishEvent(LiveEvent.AuctionState(organizer.id, leagueId, live = true, completed = false, district = "Mysuru"))
        repeat(3) { events.publishEvent(LiveEvent.BidPlaced(organizer.id)) }
        val during = metrics.snapshot()
        assertEquals(before.liveAuctions + 1, during.now.liveAuctions)
        assertEquals(before.bidsLastMinute + 3, during.now.bidsLastMinute)
        assertEquals(FeedItem("AUCTION_STARTED", "Mysuru", during.feed.first().at), during.feed.first())

        events.publishEvent(LiveEvent.AuctionState(organizer.id, leagueId, live = false, completed = true, district = "Mysuru"))
        val ended = metrics.snapshot()
        assertEquals(before.liveAuctions, ended.now.liveAuctions)
        assertEquals("AUCTION_FINISHED", ended.feed.first().type)
    }

    // ------------------------------------------------------------------ live stream

    @Test
    fun `the live stream needs an admin session and opens with a snapshot`() {
        mockMvc.perform(get("/api/v1/admin/live")).andExpect(status().isUnauthorized)
        val user = signInNewUser()
        appGet(user.token, "/api/v1/admin/live").andExpect(status().isUnauthorized)

        val cookie = adminCookie()
        val result = mockMvc.perform(get("/api/v1/admin/live").cookie(cookie).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted()).andReturn()
        val body = result.response.contentAsString
        assertTrue("event:snapshot" in body, body)
        assertTrue("\"activeUsers\"" in body, body)
        assertEquals("no-store", result.response.getHeader(HttpHeaders.CACHE_CONTROL))
        signOut(cookie)
    }

    @Test
    fun `a tick sends a new snapshot only when something changed`() {
        val cookie = adminCookie()
        val result = mockMvc.perform(get("/api/v1/admin/live").cookie(cookie)).andExpect(request().asyncStarted()).andReturn()
        stream.tick()
        val afterFirstTick = result.response.contentAsString.split("event:snapshot").size
        stream.tick()
        assertEquals(afterFirstTick, result.response.contentAsString.split("event:snapshot").size, "nothing changed, nothing sent")

        events.publishEvent(LiveEvent.ReportFiled(UUID.randomUUID()))
        stream.tick()
        assertEquals(afterFirstTick + 1, result.response.contentAsString.split("event:snapshot").size)
        repeat(2) { stream.tick() }
        assertTrue("event:ping" in result.response.contentAsString, "a quiet stream still sends a ping")
        signOut(cookie)
    }

    @Test
    fun `at most three streams per session, and signing out closes them`() {
        val cookie = adminCookie()
        val start = stream.subscriberCount()
        repeat(AdminLiveStream.MAX_PER_SESSION) {
            mockMvc.perform(get("/api/v1/admin/live").cookie(cookie)).andExpect(request().asyncStarted())
        }
        mockMvc.perform(get("/api/v1/admin/live").cookie(cookie)).andExpect(status().isTooManyRequests)
        assertEquals(start + AdminLiveStream.MAX_PER_SESSION, stream.subscriberCount())

        signOut(cookie)
        assertEquals(start, stream.subscriberCount())
    }

    @Test
    fun `a closed stream's async dispatch is not denied`() {
        // The servlet container re-dispatches (DispatcherType.ASYNC) when an emitter completes. The session
        // filter doesn't run again on that dispatch, so it must not be authorized as anonymous: in production
        // that logged "Access Denied ... response is already committed" every time a stream ended.
        val cookie = adminCookie()
        val result = mockMvc.perform(get("/api/v1/admin/live").cookie(cookie)).andExpect(request().asyncStarted()).andReturn()
        signOut(cookie)
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk)
    }

    @Test
    fun `sign out everywhere closes every stream of that admin`() {
        val first = adminCookie()
        val second = adminCookie()
        val start = stream.subscriberCount()
        mockMvc.perform(get("/api/v1/admin/live").cookie(first)).andExpect(request().asyncStarted())
        mockMvc.perform(get("/api/v1/admin/live").cookie(second)).andExpect(request().asyncStarted())
        assertEquals(start + 2, stream.subscriberCount())

        mockMvc.perform(post("/api/v1/admin/session/sign-out-everywhere").header("Origin", ORIGIN).header(ADMIN_HEADER, "1").cookie(first))
            .andExpect(status().isNoContent)
        assertEquals(start, stream.subscriberCount())
    }

    // ------------------------------------------------------------------ helpers

    private data class AppUser(val token: String, val id: UUID)

    private fun activityRows(userId: UUID): Long =
        jdbc.queryForObject("select count(*) from user_activity_days where user_id = ?", Long::class.java, userId)!!

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

    private fun signOut(cookie: Cookie) {
        mockMvc.perform(delete("/api/v1/admin/session").header("Origin", ORIGIN).header(ADMIN_HEADER, "1").cookie(cookie))
            .andExpect(status().isNoContent)
    }

    private fun appGet(token: String, path: String) = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

    private fun appPost(token: String, path: String, body: Map<String, Any?>) = mockMvc.perform(
        post(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)),
    )

    private fun signInNewUser(): AppUser {
        val phone = "+9197" + UUID.randomUUID().mostSignificantBits.toString().filter { it.isDigit() }.take(8).padEnd(8, '5')
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)
        val session = mockMvc.perform(
            post("/api/v1/auth/session").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("idToken" to "a-valid-firebase-id-token", "termsAccepted" to true))),
        ).andExpect(status().isOk).andReturn().json()
        return AppUser(session["accessToken"] as String, UUID.fromString(session["userId"] as String))
    }

    private fun createLeague(token: String): String {
        val groundId = appPost(
            token,
            "/api/v1/grounds",
            mapOf("name" to "Test Ground", "state" to "Karnataka", "district" to "Bengaluru Urban", "latitude" to 12.9, "longitude" to 77.6),
        ).andExpect(status().isOk).andReturn().json()["id"]
        return appPost(
            token,
            "/api/v1/leagues",
            mapOf(
                "name" to "Weekend League", "state" to "Karnataka", "district" to "Bengaluru Urban", "groundId" to groundId,
                "startsOn" to "2026-12-12", "playersRequired" to 10,
            ),
        ).andExpect(status().isOk).andReturn().json()["id"] as String
    }

    @Suppress("UNCHECKED_CAST")
    private fun MvcResult.json(): Map<String, Any?> = objectMapper.readValue(response.contentAsString, Map::class.java) as Map<String, Any?>

    private companion object {
        const val ORIGIN = "https://admin.test"
        const val CLIENT_ID = "test-admin-client.apps.googleusercontent.com"
    }
}

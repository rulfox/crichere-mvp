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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** League and user browsers, phone lookup, internal flag and search (docs/PHASE20.md Step 9). */
@Import(TestGoogleJwksConfiguration::class)
class AdminBrowseIntegrationTest : AbstractWebIntegrationTest() {

    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var signInLimiter: AdminSignInRateLimiter

    private lateinit var admin: Cookie
    private val token = "Zq" + UUID.randomUUID().toString().take(8)

    @BeforeEach
    fun setUp() {
        signInLimiter.reset()
        admin = adminCookie()
    }

    // ------------------------------------------------------------------ leagues

    @Test
    fun `the league list filters, counts per row and pages with a capped total`() {
        val organizer = signInNewUser("Org $token")
        val ids = (1..3).map { createLeague(organizer.token, "$token League $it") }
        val player = signInNewUser("Player $token")
        appPost(player.token, "/api/v1/leagues/${ids[2]}/players", emptyMap()).andExpect(status().isOk)
        appPost(player.token, "/api/v1/reports", mapOf("targetType" to "LEAGUE", "targetLeagueId" to ids[2], "reason" to "FAKE_OR_SCAM_LEAGUE"))
            .andExpect(status().isCreated)

        val first = adminGet("/api/v1/admin/leagues?q=$token&limit=2").andExpect(status().isOk)
            .andExpect(jsonPath("$.total.count").value(3))
            .andExpect(jsonPath("$.total.capped").value(false))
            .andExpect(jsonPath("$.items[0].id").value(ids[2]))
            .andExpect(jsonPath("$.items[0].players").value(1))
            .andExpect(jsonPath("$.items[0].openReports").value(1))
            .andExpect(jsonPath("$.items[0].status").value("open"))
            .andExpect(jsonPath("$.items[0].organizerName").value("Org $token"))
            .andReturn().json()
        val cursor = first["nextCursor"] as String
        adminGet("/api/v1/admin/leagues?q=$token&limit=2&cursor=$cursor").andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(ids[0]))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
            .andExpect(jsonPath("$.total").doesNotExist())

        val byName = adminGet("/api/v1/admin/leagues?q=$token&sort=name&limit=1").andReturn().json()
        adminGet("/api/v1/admin/leagues?q=$token&sort=name&limit=1&cursor=${byName["nextCursor"]}")
            .andExpect(jsonPath("$.items[0].name").value("$token League 2"))
        adminGet("/api/v1/admin/leagues?q=$token&hasOpenReports=true").andExpect(jsonPath("$.items.length()").value(1))
        adminGet("/api/v1/admin/leagues?q=$token&status=completed").andExpect(jsonPath("$.items.length()").value(0))

        adminGet("/api/v1/admin/leagues?sort=sideways").andExpect(status().isConflict)
        adminGet("/api/v1/admin/leagues?cursor=junk").andExpect(status().isConflict).andExpect(jsonPath("$.code").value("BAD_CURSOR"))
    }

    @Test
    fun `league tabs show details, people and a timeline`() {
        val organizer = signInNewUser("Org $token")
        val leagueId = createLeague(organizer.token, "$token Tabs")
        val player = signInNewUser("Player $token")
        appPost(player.token, "/api/v1/leagues/$leagueId/players", emptyMap()).andExpect(status().isOk)

        adminGet("/api/v1/admin/leagues/$leagueId").andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("$token Tabs"))
            .andExpect(jsonPath("$.organizer.name").value("Org $token"))
            .andExpect(jsonPath("$.groundName").value("Test Ground"))
            .andExpect(jsonPath("$.status").value("open"))
        adminGet("/api/v1/admin/leagues/$leagueId/players").andExpect(status().isOk)
            .andExpect(jsonPath("$[0].person.name").value("Player $token"))
            .andExpect(jsonPath("$[0].fee.state").value("NO_FEE"))
        adminGet("/api/v1/admin/leagues/$leagueId/timeline").andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.kind == 'PLAYER_JOINED')]").exists())
            .andExpect(jsonPath("$[?(@.kind == 'LEAGUE_CREATED')]").exists())
        listOf("franchises", "auction", "awards", "moderation").forEach { adminGet("/api/v1/admin/leagues/$leagueId/$it").andExpect(status().isOk) }
        adminGet("/api/v1/admin/leagues/${UUID.randomUUID()}").andExpect(status().isNotFound)
    }

    @Test
    fun `opening a payment proof is a guarded, audited action`() {
        val organizer = signInNewUser("Org $token")
        val leagueId = createLeague(organizer.token, "$token Paid")
        val player = signInNewUser("Payer $token")
        jdbc.update("update leagues set player_fee = 300 where id = ?::uuid", leagueId)
        val rowId = UUID.randomUUID()
        jdbc.update(
            "insert into league_players (id, league_id, user_id, payment_screenshot_url) values (?, ?::uuid, ?, ?)",
            rowId, leagueId, player.id, "https://example.com/proof.jpg",
        )

        mockMvc.perform(post("/api/v1/admin/payment-proofs/player/$rowId").cookie(admin)).andExpect(status().isForbidden)
        action(post("/api/v1/admin/payment-proofs/player/$rowId"), null).andExpect(status().isOk)
            .andExpect(jsonPath("$.payer.name").value("Payer $token"))
            .andExpect(jsonPath("$.fee").value(300))
        assertEquals(1, count("select count(*) from moderation_actions where action = 'VIEW_PAYMENT_PROOF' and user_id = ?", player.id))
        adminGet("/api/v1/admin/leagues/$leagueId/players").andExpect(jsonPath("$[0].fee.state").value("PROOF_UPLOADED"))
        action(post("/api/v1/admin/payment-proofs/player/${UUID.randomUUID()}"), null).andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------ users

    @Test
    fun `the user list shows status and role counts, and hides internal accounts unless asked`() {
        val organizer = signInNewUser("Org $token")
        createLeague(organizer.token, "$token Users")
        val quiet = signInNewUser(null)
        jdbc.update("insert into profiles (user_id, name) values (?, ?)", quiet.id, "Quiet $token")

        adminGet("/api/v1/admin/users?q=$token").andExpect(status().isOk)
            .andExpect(jsonPath("$.total.count").value(2))
            .andExpect(jsonPath("$.items[?(@.name == 'Org $token')].organizes").value(1))
            .andExpect(jsonPath("$.items[?(@.name == 'Quiet $token')].status").value("profile_incomplete"))

        action(put("/api/v1/admin/users/${quiet.id}/internal"), mapOf("internal" to true)).andExpect(status().isNoContent)
        assertEquals(true, jdbc.queryForObject("select is_internal from users where id = ?", Boolean::class.java, quiet.id))
        assertEquals(1, count("select count(*) from moderation_actions where action = 'MARK_INTERNAL' and user_id = ?", quiet.id))
        adminGet("/api/v1/admin/users?q=$token").andExpect(jsonPath("$.items.length()").value(1))
        adminGet("/api/v1/admin/users?q=$token&includeInternal=true").andExpect(jsonPath("$.items.length()").value(2))
        adminGet("/api/v1/admin/users?status=whatever").andExpect(status().isConflict)
    }

    @Test
    fun `user tabs never show the phone number`() {
        val user = signInNewUser("Person $token")
        jdbc.update("insert into user_activity_days (user_id, day) values (?, current_date)", user.id)
        val profile = adminGet("/api/v1/admin/users/${user.id}").andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Person $token"))
            .andExpect(jsonPath("$.termsVersion").isNotEmpty)
            .andReturn().response.contentAsString
        assertFalse(user.phone.drop(3) in profile, profile)
        assertFalse("phone" in profile.lowercase(), profile)
        adminGet("/api/v1/admin/users/${user.id}/activity").andExpect(jsonPath("$.length()").value(1))
        adminGet("/api/v1/admin/users/${user.id}/account").andExpect(jsonPath("$.appSessions").value(1))
        listOf("leagues", "auction", "moderation").forEach { adminGet("/api/v1/admin/users/${user.id}/$it").andExpect(status().isOk) }
        adminGet("/api/v1/admin/users/${UUID.randomUUID()}").andExpect(status().isNotFound)
    }

    @Test
    fun `phone lookup matches only a full number, and is audited without the number`() {
        val user = signInNewUser("Lookup $token")
        val local = user.phone.removePrefix("+91")

        action(post("/api/v1/admin/users/lookup"), mapOf("phone" to "+91 ${local.take(5)} ${local.drop(5)}")).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(user.id.toString()))
        action(post("/api/v1/admin/users/lookup"), mapOf("phone" to local.take(6))).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        val notes = jdbc.queryForList("select note from moderation_actions where action = 'PHONE_LOOKUP' and admin_email = 'admin@example.com'", String::class.java)
        assertTrue(notes.isNotEmpty())
        assertTrue(notes.none { it != null && (local in it || local.take(6) in it) }, notes.toString())
    }

    // ------------------------------------------------------------------ search

    @Test
    fun `search finds leagues and people by name or exact id`() {
        val organizer = signInNewUser("Org $token")
        val leagueId = createLeague(organizer.token, "$token Search")
        adminGet("/api/v1/admin/search?q=$token").andExpect(status().isOk)
            .andExpect(jsonPath("$.leagues[0].id").value(leagueId))
            .andExpect(jsonPath("$.users[0].id").value(organizer.id.toString()))
        adminGet("/api/v1/admin/search?q=$leagueId").andExpect(jsonPath("$.leagues.length()").value(1)).andExpect(jsonPath("$.users.length()").value(0))
        adminGet("/api/v1/admin/search?q=${organizer.id}").andExpect(jsonPath("$.users.length()").value(1))
        adminGet("/api/v1/admin/search?q=z").andExpect(jsonPath("$.leagues.length()").value(0))
        mockMvc.perform(get("/api/v1/admin/search?q=$token")).andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------ helpers

    private data class AppUser(val token: String, val id: UUID, val phone: String)

    private fun adminGet(path: String): ResultActions = mockMvc.perform(get(path).cookie(admin))

    private fun action(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, body: Map<String, Any?>?): ResultActions {
        val request = builder.cookie(admin).header("Origin", ORIGIN).header(ADMIN_HEADER, "1")
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        return mockMvc.perform(request)
    }

    private fun count(sql: String, vararg args: Any): Long = jdbc.queryForObject(sql, Long::class.java, *args)!!

    private fun appPost(token: String, path: String, body: Map<String, Any?>) = mockMvc.perform(
        post(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)),
    )

    private fun signInNewUser(name: String?): AppUser {
        val phone = "+9195" + UUID.randomUUID().mostSignificantBits.toString().filter { it.isDigit() }.take(8).padEnd(8, '2')
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)
        val session = mockMvc.perform(
            post("/api/v1/auth/session").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("idToken" to "a-valid-firebase-id-token", "termsAccepted" to true))),
        ).andExpect(status().isOk).andReturn().json()
        val user = AppUser(session["accessToken"] as String, UUID.fromString(session["userId"] as String), phone)
        if (name != null) {
            jdbc.update(
                "insert into profiles (user_id, name, photo_url, state, district, playing_role, batting_style) values (?, ?, 'https://x/p.jpg', 'Kerala', 'Alappuzha', 'BATSMAN', 'RIGHT_HAND')",
                user.id, name,
            )
        }
        return user
    }

    private fun createLeague(token: String, name: String): String {
        val groundId = appPost(
            token,
            "/api/v1/grounds",
            mapOf("name" to "Test Ground", "state" to "Kerala", "district" to "Alappuzha", "latitude" to 9.5, "longitude" to 76.3),
        ).andExpect(status().isOk).andReturn().json()["id"]
        return appPost(
            token,
            "/api/v1/leagues",
            mapOf("name" to name, "state" to "Kerala", "district" to "Alappuzha", "groundId" to groundId, "startsOn" to "2026-12-12", "playersRequired" to 10),
        ).andExpect(status().isOk).andReturn().json()["id"] as String
    }

    @Suppress("UNCHECKED_CAST")
    private fun MvcResult.json(): Map<String, Any?> = objectMapper.readValue(response.contentAsString, Map::class.java) as Map<String, Any?>

    private fun adminCookie(): Cookie {
        val claims = JWTClaimsSet.Builder()
            .issuer("https://accounts.google.com").audience(CLIENT_ID).subject("1234567890")
            .claim("email", "admin@example.com").claim("email_verified", true)
            .issueTime(Date()).expirationTime(Date(System.currentTimeMillis() + 600_000))
            .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TestGoogleKeys.key.keyID).build(), claims)
            .apply { sign(RSASSASigner(TestGoogleKeys.key)) }.serialize()
        val setCookie = mockMvc.perform(
            post("/api/v1/admin/session").header("Origin", ORIGIN).header(ADMIN_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON).content("""{"credential":"$jwt"}"""),
        ).andExpect(status().isOk).andReturn().response.getHeader(HttpHeaders.SET_COOKIE)!!
        return Cookie(ADMIN_COOKIE, setCookie.substringAfter("$ADMIN_COOKIE=").substringBefore(";"))
    }

    private companion object {
        const val ORIGIN = "https://admin.test"
        const val CLIENT_ID = "test-admin-client.apps.googleusercontent.com"
    }
}

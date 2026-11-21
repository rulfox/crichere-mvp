package com.crichere.backend.admin

import com.crichere.backend.auth.VerifiedFirebaseToken
import com.crichere.backend.common.AbstractWebIntegrationTest
import com.crichere.backend.moderation.RecordedDeletes
import com.crichere.backend.moderation.RecordingDeleterConfiguration
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

/** The admin moderation API over real HTTP and a real database (docs/PHASE20.md Step 3). */
@Import(TestGoogleJwksConfiguration::class, RecordingDeleterConfiguration::class)
class AdminModerationFlowIntegrationTest : AbstractWebIntegrationTest() {

    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var deletedObjects: RecordedDeletes
    @Autowired private lateinit var signInLimiter: AdminSignInRateLimiter

    private lateinit var admin: Cookie

    @BeforeEach
    fun signInAdmin() {
        signInLimiter.reset()
        admin = adminCookie()
    }

    // ------------------------------------------------------------------ access

    @Test
    fun `every moderation route needs an admin session`() {
        mockMvc.perform(get("/api/v1/admin/reports")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/admin/suspended")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/admin/audit")).andExpect(status().isUnauthorized)
        val appUser = signInNewUser()
        mockMvc.perform(get("/api/v1/admin/reports").header(HttpHeaders.AUTHORIZATION, "Bearer ${appUser.token}"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `actions need the admin-site guard and a note`() {
        val reportId = insertReport(targetUser = signInNewUser().id)
        mockMvc.perform(
            post("/api/v1/admin/reports/$reportId/dismiss").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("""{"note":"spam"}"""),
        ).andExpect(status().isForbidden)
        action("/api/v1/admin/reports/$reportId/dismiss", mapOf("note" to "   ")).andExpect(status().isBadRequest)
        action("/api/v1/admin/reports/$reportId/dismiss", emptyMap()).andExpect(status().isBadRequest)
        action("/api/v1/admin/reports/$reportId/dismiss", mapOf("note" to "x".repeat(1001))).andExpect(status().isBadRequest)
        assertEquals("OPEN", reportStatus(reportId))
    }

    // ------------------------------------------------------------------ reports

    @Test
    fun `open reports list oldest first and page with a cursor`() {
        val target = signInNewUser().id
        val ids = (0 until 3).map { insertReport(targetUser = target, reason = "SPAM_OR_MISLEADING", ageMinutes = 1_000_000L - it) }

        val first = adminGet("/api/v1/admin/reports?status=OPEN&reason=SPAM_OR_MISLEADING&limit=2").andExpect(status().isOk).andReturn().json()
        @Suppress("UNCHECKED_CAST")
        val firstItems = first["items"] as List<Map<String, Any?>>
        assertEquals(2, firstItems.size)
        assertEquals(ids[0].toString(), firstItems[0]["id"])
        assertEquals(ids[1].toString(), firstItems[1]["id"])
        assertEquals(3, (firstItems[0]["openOnTarget"] as Number).toInt())
        val cursor = assertNotNull(first["nextCursor"] as String?)

        adminGet("/api/v1/admin/reports?status=OPEN&reason=SPAM_OR_MISLEADING&limit=2&cursor=$cursor")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].id").value(ids[2].toString()))
    }

    @Test
    fun `bad filters and cursors are refused`() {
        adminGet("/api/v1/admin/reports?status=WHATEVER").andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("BAD_FILTER"))
        adminGet("/api/v1/admin/reports?cursor=garbage").andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("BAD_CURSOR"))
    }

    @Test
    fun `report detail shows evidence, target, private reporter and history`() {
        val reporter = signInNewUser()
        val target = signInNewUser()
        jdbc.update("insert into profiles (user_id, name, district) values (?, 'Ravi', 'Mysuru')", target.id)
        val reportId = insertReport(targetUser = target.id, reporter = reporter.id, details = "fake name")

        adminGet("/api/v1/admin/reports/$reportId")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.details").value("fake name"))
            .andExpect(jsonPath("$.evidence.name").value("Snapshot Name"))
            .andExpect(jsonPath("$.target.kind").value("USER"))
            .andExpect(jsonPath("$.target.name").value("Ravi"))
            .andExpect(jsonPath("$.target.openReports").value(1))
            .andExpect(jsonPath("$.reporter.id").value(reporter.id.toString()))
            .andExpect(jsonPath("$.reporter.reportsFiled").value(1))
            .andExpect(jsonPath("$.history.length()").value(0))

        adminGet("/api/v1/admin/reports/${UUID.randomUUID()}").andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `dismiss and resolve close a report once and are audited with the admin's email`() {
        val target = signInNewUser().id
        val dismissed = insertReport(targetUser = target)
        val resolved = insertReport(targetUser = target, reason = "SPAM_OR_MISLEADING")

        action("/api/v1/admin/reports/$dismissed/dismiss", mapOf("note" to "  not a problem  ")).andExpect(status().isNoContent)
        action("/api/v1/admin/reports/$resolved/resolve", mapOf("note" to "handled")).andExpect(status().isNoContent)
        assertEquals("DISMISSED", reportStatus(dismissed))
        assertEquals("RESOLVED", reportStatus(resolved))

        action("/api/v1/admin/reports/$dismissed/resolve", mapOf("note" to "again")).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("REPORT_NOT_OPEN"))
        action("/api/v1/admin/reports/${UUID.randomUUID()}/dismiss", mapOf("note" to "x")).andExpect(status().isNotFound)

        val row = jdbc.queryForMap("select action, admin_email, note from moderation_actions where report_id = ?", dismissed)
        assertEquals("DISMISS_REPORT", row["action"])
        assertEquals(ADMIN_EMAIL, row["admin_email"])
        assertEquals("not a problem", row["note"])

        adminGet("/api/v1/admin/reports/$dismissed")
            .andExpect(jsonPath("$.status").value("DISMISSED"))
            .andExpect(jsonPath("$.resolvedAt").isNotEmpty)
            .andExpect(jsonPath("$.history[0].action").value("DISMISS_REPORT"))
            .andExpect(jsonPath("$.history[0].adminEmail").value(ADMIN_EMAIL))
    }

    // ------------------------------------------------------------------ images

    @Test
    fun `removing an unused photo in the owner's folder clears it, tells them, and deletes the file`() {
        val owner = signInNewUser().id
        val key = "users/$owner/profile-${UUID.randomUUID()}"
        jdbc.update("insert into profiles (user_id, name, photo_url) values (?, 'Asha', ?)", owner, "$BUCKET/$key?v=2")
        val reportId = insertReport(targetUser = owner, reason = "INAPPROPRIATE_IMAGE")

        removeImage("PHOTO", owner, reportId).andExpect(status().isOk)
            .andExpect(jsonPath("$.ownerId").value(owner.toString()))
            .andExpect(jsonPath("$.storedFile").value("DELETED"))

        val profile = jdbc.queryForMap("select photo_url, photo_removed from profiles where user_id = ?", owner)
        assertNull(profile["photo_url"])
        assertEquals(true, profile["photo_removed"])
        assertTrue(key in deletedObjects.keys)
        assertEquals(1, count("select count(*) from moderation_notices where user_id = ? and kind = 'PHOTO'", owner))
        val audit = jdbc.queryForMap("select admin_email, user_id from moderation_actions where report_id = ? and action = 'REMOVE_IMAGE'", reportId)
        assertEquals(ADMIN_EMAIL, audit["admin_email"])
        assertEquals(owner, audit["user_id"])

        removeImage("PHOTO", owner, reportId).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("NO_IMAGE"))
    }

    @Test
    fun `a file someone else still uses is kept`() {
        val owner = signInNewUser().id
        val other = signInNewUser().id
        val key = "users/$owner/profile-${UUID.randomUUID()}"
        jdbc.update("insert into profiles (user_id, name, photo_url) values (?, 'A', ?)", owner, "$BUCKET/$key")
        jdbc.update("insert into profiles (user_id, name, photo_url) values (?, 'B', ?)", other, "$BUCKET/$key?v=9")

        removeImage("PHOTO", owner).andExpect(status().isOk).andExpect(jsonPath("$.storedFile").value("KEPT_STILL_USED"))
        assertFalse(key in deletedObjects.keys)
        assertNotNull(jdbc.queryForObject("select photo_url from profiles where user_id = ?", String::class.java, other))
    }

    @Test
    fun `a file outside the row's own folder, or not in our bucket, is kept`() {
        val owner = signInNewUser().id
        val foreignKey = "users/${UUID.randomUUID()}/profile-${UUID.randomUUID()}"
        jdbc.update("insert into profiles (user_id, name, photo_url) values (?, 'A', ?)", owner, "$BUCKET/$foreignKey")
        removeImage("PHOTO", owner).andExpect(status().isOk).andExpect(jsonPath("$.storedFile").value("KEPT_NOT_OWNED"))
        assertFalse(foreignKey in deletedObjects.keys)

        val second = signInNewUser().id
        jdbc.update("insert into profiles (user_id, name, photo_url) values (?, 'B', ?)", second, "https://example.com/${UUID.randomUUID()}.jpg")
        removeImage("PHOTO", second).andExpect(status().isOk).andExpect(jsonPath("$.storedFile").value("KEPT_UNKNOWN_LOCATION"))
    }

    @Test
    fun `a league logo is removed and the organizer is told`() {
        val organizer = signInNewUser()
        val leagueId = createLeague(organizer.token)
        val key = "leagues/$leagueId/logo-${UUID.randomUUID()}"
        jdbc.update("update leagues set logo_url = ? where id = ?", "$BUCKET/$key", leagueId)

        removeImage("LEAGUE_LOGO", leagueId).andExpect(status().isOk)
            .andExpect(jsonPath("$.ownerId").value(organizer.id.toString()))
            .andExpect(jsonPath("$.storedFile").value("DELETED"))
        assertEquals(true, jdbc.queryForObject("select logo_removed from leagues where id = ?", Boolean::class.java, leagueId))
        assertEquals(1, count("select count(*) from moderation_notices where user_id = ? and kind = 'LEAGUE_LOGO'", organizer.id))
    }

    @Test
    fun `removing an image of something that doesn't exist is not found`() {
        removeImage("PHOTO", UUID.randomUUID()).andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------ suspension

    @Test
    fun `suspend signs the person out and lists them, unsuspend reverses it`() {
        val person = signInNewUser()
        val reportId = insertReport(targetUser = person.id)

        action("/api/v1/admin/users/${person.id}/suspend", mapOf("note" to "threats", "reportId" to reportId))
            .andExpect(status().isNoContent)
        assertNotNull(jdbc.queryForObject("select suspended_at from users where id = ?", java.sql.Timestamp::class.java, person.id))
        assertEquals(0, count("select count(*) from refresh_tokens where user_id = ? and revoked_at is null", person.id))
        assertEquals(0, count("select count(*) from device_tokens where user_id = ?", person.id))

        val suspended = adminGet("/api/v1/admin/suspended").andExpect(status().isOk).andReturn().json()
        @Suppress("UNCHECKED_CAST")
        val entry = (suspended["accounts"] as List<Map<String, Any?>>).single { it["userId"] == person.id.toString() }
        assertEquals(ADMIN_EMAIL, entry["by"])
        assertEquals("threats", entry["note"])
        assertEquals(reportId.toString(), entry["reportId"])

        action("/api/v1/admin/users/${person.id}/suspend", mapOf("note" to "again")).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_SUSPENDED"))

        action("/api/v1/admin/users/${person.id}/unsuspend", mapOf("note" to "appeal accepted")).andExpect(status().isNoContent)
        assertNull(jdbc.queryForObject("select suspended_at from users where id = ?", java.sql.Timestamp::class.java, person.id))
        action("/api/v1/admin/users/${person.id}/unsuspend", mapOf("note" to "again")).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("NOT_SUSPENDED"))
    }

    @Test
    fun `suspending an unknown account is not found`() {
        action("/api/v1/admin/users/${UUID.randomUUID()}/suspend", mapOf("note" to "x")).andExpect(status().isNotFound)
        action("/api/v1/admin/users/${UUID.randomUUID()}/unsuspend", mapOf("note" to "x")).andExpect(status().isNotFound)
    }

    @Test
    fun `an organizer of a live auction is only suspended when it's marked urgent`() {
        val organizer = signInNewUser()
        val leagueId = createLeague(organizer.token)
        jdbc.update("update leagues set auction_status = 'IN_PROGRESS' where id = ?", leagueId)

        action("/api/v1/admin/users/${organizer.id}/suspend", mapOf("note" to "scam")).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ORGANIZER_OF_LIVE_AUCTION"))
        assertNull(jdbc.queryForObject("select suspended_at from users where id = ?", java.sql.Timestamp::class.java, organizer.id))

        action("/api/v1/admin/users/${organizer.id}/suspend", mapOf("note" to "urgent safety", "duringAuction" to true))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `a blocked number is listed and can be released once`() {
        val hash = "test-hash-${UUID.randomUUID()}"
        jdbc.update("insert into suspended_phone_hashes (phone_lookup_hash) values (?)", hash)

        adminGet("/api/v1/admin/suspended").andExpect(status().isOk)
            .andExpect(jsonPath("$.blockedNumbers[?(@.hash == '$hash')]").exists())
        action("/api/v1/admin/suspended-numbers/$hash/release", mapOf("note" to "account deleted, appeal ok"))
            .andExpect(status().isNoContent)
        assertEquals(0, count("select count(*) from suspended_phone_hashes where phone_lookup_hash = ?", hash))
        action("/api/v1/admin/suspended-numbers/$hash/release", mapOf("note" to "again")).andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------ audit log

    @Test
    fun `the audit log filters by action and admin, shows script rows as script, and pages`() {
        val note = "audit-${UUID.randomUUID()}"
        repeat(3) { jdbc.update("insert into moderation_actions (id, action, note) values (?, 'TEST_SCRIPT_ACTION', ?)", UUID.randomUUID(), note) }

        val first = adminGet("/api/v1/admin/audit?action=TEST_SCRIPT_ACTION&admin=script&limit=2").andExpect(status().isOk).andReturn().json()
        @Suppress("UNCHECKED_CAST")
        val items = first["items"] as List<Map<String, Any?>>
        assertEquals(2, items.size)
        assertTrue(items.all { it["adminEmail"] == "script" })
        val cursor = assertNotNull(first["nextCursor"] as String?)
        adminGet("/api/v1/admin/audit?action=TEST_SCRIPT_ACTION&admin=script&limit=2&cursor=$cursor")
            .andExpect(status().isOk).andExpect(jsonPath("$.items.length()").value(1))

        adminGet("/api/v1/admin/audit?action=ADMIN_SIGN_IN&admin=${ADMIN_EMAIL.uppercase()}&limit=1")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].adminEmail").value(ADMIN_EMAIL))
    }

    // ------------------------------------------------------------------ helpers

    private data class AppUser(val token: String, val id: UUID)

    private fun adminCookie(): Cookie {
        val claims = JWTClaimsSet.Builder()
            .issuer("https://accounts.google.com").audience(CLIENT_ID).subject("1234567890")
            .claim("email", ADMIN_EMAIL).claim("email_verified", true)
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

    private fun adminGet(path: String): ResultActions = mockMvc.perform(get(path).cookie(admin))

    private fun action(path: String, body: Map<String, Any?>): ResultActions =
        mockMvc.perform(
            post(path).cookie(admin).header("Origin", ORIGIN).header(ADMIN_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)),
        )

    private fun removeImage(kind: String, targetId: UUID, reportId: UUID? = null): ResultActions =
        action("/api/v1/admin/images/remove", mapOf("kind" to kind, "targetId" to targetId, "reportId" to reportId, "note" to "nudity"))

    private fun insertReport(
        targetUser: UUID,
        reporter: UUID? = null,
        reason: String = "IMPERSONATION",
        details: String? = null,
        ageMinutes: Long = 0,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            insert into reports (id, reporter_id, target_type, target_user_id, reason, details, snapshot, created_at)
            values (?, ?, 'USER', ?, ?, ?, '{"name":"Snapshot Name"}', now() - make_interval(mins => ?))
            """,
            id, reporter, targetUser, reason, details, ageMinutes.toInt(),
        )
        return id
    }

    private fun reportStatus(id: UUID): String = jdbc.queryForObject("select status from reports where id = ?", String::class.java, id)!!

    private fun count(sql: String, vararg args: Any): Long = jdbc.queryForObject(sql, Long::class.java, *args)!!

    private fun signInNewUser(): AppUser {
        val phone = "+9198" + UUID.randomUUID().mostSignificantBits.toString().filter { it.isDigit() }.take(8).padEnd(8, '3')
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)
        val session = mockMvc.perform(
            post("/api/v1/auth/session").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("idToken" to "a-valid-firebase-id-token", "termsAccepted" to true))),
        ).andExpect(status().isOk).andReturn().json()
        return AppUser(session["accessToken"] as String, UUID.fromString(session["userId"] as String))
    }

    private fun createLeague(token: String): UUID {
        fun appPost(path: String, body: Map<String, Any?>) = mockMvc.perform(
            post(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)),
        ).andExpect(status().isOk).andReturn().json()
        val groundId = appPost(
            "/api/v1/grounds",
            mapOf("name" to "Test Ground", "state" to "Karnataka", "district" to "Bengaluru Urban", "latitude" to 12.9, "longitude" to 77.6),
        )["id"]
        val league = appPost(
            "/api/v1/leagues",
            mapOf(
                "name" to "Admin Test League", "state" to "Karnataka", "district" to "Bengaluru Urban", "groundId" to groundId,
                "startsOn" to "2026-12-12", "playersRequired" to 10,
            ),
        )
        return UUID.fromString(league["id"] as String)
    }

    @Suppress("UNCHECKED_CAST")
    private fun MvcResult.json(): Map<String, Any?> = objectMapper.readValue(response.contentAsString, Map::class.java) as Map<String, Any?>

    private companion object {
        const val ORIGIN = "https://admin.test"
        const val CLIENT_ID = "test-admin-client.apps.googleusercontent.com"
        const val ADMIN_EMAIL = "admin@example.com"
        const val BUCKET = "https://bucket.s3.ap-south-1.amazonaws.com"
    }
}

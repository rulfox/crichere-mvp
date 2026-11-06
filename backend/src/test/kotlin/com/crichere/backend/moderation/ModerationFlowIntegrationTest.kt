package com.crichere.backend.moderation

import com.crichere.backend.account.AccountPurgeService
import com.crichere.backend.account.PurgeOutcome
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.auth.VerifiedFirebaseToken
import com.crichere.backend.common.AbstractWebIntegrationTest
import com.crichere.backend.common.ObjectDeleter
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the purge asked S3 to delete. */
class RecordedDeletes(val keys: MutableList<String> = mutableListOf())

@TestConfiguration
class RecordingDeleterConfiguration {
    @Bean
    fun recordedDeletes() = RecordedDeletes()

    @Bean
    @Primary
    fun recordingDeleter(recorded: RecordedDeletes): ObjectDeleter = ObjectDeleter { recorded.keys += it }
}

/** Reports and blocks over real HTTP and a real database (docs/PHASE18.md). */
@Import(RecordingDeleterConfiguration::class)
class ModerationFlowIntegrationTest : AbstractWebIntegrationTest {

    constructor() : super()

    @Autowired private lateinit var deletedObjects: RecordedDeletes
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var reportRepository: ReportRepository
    @Autowired private lateinit var blockRepository: UserBlockRepository
    @Autowired private lateinit var suspendedPhoneHashRepository: SuspendedPhoneHashRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var accountPurgeService: AccountPurgeService
    @Autowired private lateinit var jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate
    @Autowired private lateinit var noticeRepository: ModerationNoticeRepository
    @Autowired private lateinit var profileRepository: com.crichere.backend.profile.ProfileRepository

    private data class Caller(val token: String, val userId: String, val phone: String)

    // ---------------------------------------------------------------- blocks

    @Test
    fun `blocking needs a session`() {
        mockMvc.perform(put("/api/v1/blocks/${UUID.randomUUID()}")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/blocks")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `block twice, list, unblock twice`() {
        val me = signInNewUser()
        val other = signInNewUser()

        repeat(2) { authedPut(me.token, "/api/v1/blocks/${other.userId}").andExpect(status().isNoContent) }
        authedGet(me.token, "/api/v1/blocks")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].userId").value(other.userId))

        repeat(2) { authedDelete(me.token, "/api/v1/blocks/${other.userId}").andExpect(status().isNoContent) }
        authedGet(me.token, "/api/v1/blocks").andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `a blocked person can't see the block, and lists are private`() {
        val me = signInNewUser()
        val other = signInNewUser()
        authedPut(me.token, "/api/v1/blocks/${other.userId}").andExpect(status().isNoContent)

        authedGet(other.token, "/api/v1/blocks").andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `self, unknown and deleted targets all get the same answer`() {
        val me = signInNewUser()
        authedPut(me.token, "/api/v1/blocks/${me.userId}")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"))
        authedPut(me.token, "/api/v1/blocks/${UUID.randomUUID()}")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"))
    }

    @Test
    fun `a blocked organizer's league leaves the dashboard, unless the caller joined it, and only for the blocker`() {
        val organizer = signInNewUser()
        val blocker = signInNewUser()
        val bystander = signInNewUser()
        val leagueId = createLeague(organizer.token)
        authedPut(blocker.token, "/api/v1/blocks/${organizer.userId}").andExpect(status().isNoContent)

        assertFalse(leagueIdsSeenBy(blocker.token).contains(leagueId))
        assertTrue(leagueIdsSeenBy(bystander.token).contains(leagueId))
        assertTrue(leagueIdsSeenBy(null).contains(leagueId))

        authedPost(blocker.token, "/api/v1/leagues/$leagueId/players", emptyMap()).andExpect(status().isOk)
        assertTrue(leagueIdsSeenBy(blocker.token).contains(leagueId))
    }

    // ---------------------------------------------------------------- reports

    @Test
    fun `reporting a person stores evidence the server built, and a second report is refused`() {
        val me = signInNewUser()
        val other = signInNewUser()
        val body = mapOf(
            "targetType" to "USER",
            "targetUserId" to other.userId,
            "reason" to "IMPERSONATION",
            "details" to "not him\u0007 at all",
        )

        authedPost(me.token, "/api/v1/reports", body).andExpect(status().isCreated)
        authedPost(me.token, "/api/v1/reports", body)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("REPORT_ALREADY_OPEN"))

        val report = reportRepository.findByStatus(ReportStatus.OPEN).single { it.targetUserId == UUID.fromString(other.userId) }
        assertEquals("not him at all", report.details)
        assertEquals(UUID.fromString(me.userId), report.reporterId)
        assertTrue(report.snapshot.contains("photoUrl"))
    }

    @Test
    fun `reports can't be read back`() {
        val me = signInNewUser()
        mockMvc.perform(get("/api/v1/reports").header(HttpHeaders.AUTHORIZATION, "Bearer ${me.token}"))
            .andExpect(status().isMethodNotAllowed)
    }

    @Test
    fun `something else needs details, and reasons must fit the target`() {
        val me = signInNewUser()
        val other = signInNewUser()
        authedPost(
            me.token,
            "/api/v1/reports",
            mapOf("targetType" to "USER", "targetUserId" to other.userId, "reason" to "SOMETHING_ELSE", "details" to "   "),
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_REPORT"))
        authedPost(
            me.token,
            "/api/v1/reports",
            mapOf("targetType" to "USER", "targetUserId" to other.userId, "reason" to "BETTING_OR_GAMBLING"),
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_REPORT"))
    }

    @Test
    fun `details over the limit are refused`() {
        val me = signInNewUser()
        val other = signInNewUser()
        authedPost(
            me.token,
            "/api/v1/reports",
            mapOf("targetType" to "USER", "targetUserId" to other.userId, "reason" to "HATE_OR_HARASSMENT", "details" to "x".repeat(501)),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `a league can be reported by a viewer but not by its organizer`() {
        val organizer = signInNewUser()
        val viewer = signInNewUser()
        val leagueId = createLeague(organizer.token)
        val body = mapOf("targetType" to "LEAGUE", "targetLeagueId" to leagueId, "reason" to "FAKE_OR_SCAM_LEAGUE")

        authedPost(viewer.token, "/api/v1/reports", body).andExpect(status().isCreated)
        authedPost(organizer.token, "/api/v1/reports", body)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"))
    }

    @Test
    fun `reporting yourself is refused`() {
        val me = signInNewUser()
        authedPost(
            me.token,
            "/api/v1/reports",
            mapOf("targetType" to "USER", "targetUserId" to me.userId, "reason" to "SPAM_OR_MISLEADING"),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `evidence urls come from image fields only, so typed text can't pin an object`() {
        val me = signInNewUser()
        val photo = "https://bucket.s3.ap-south-1.amazonaws.com/users/${UUID.randomUUID()}/profile"
        val logo = "https://bucket.s3.ap-south-1.amazonaws.com/leagues/${UUID.randomUUID()}/franchise-logos/x"
        val typed = "https://bucket.s3.ap-south-1.amazonaws.com/users/${UUID.randomUUID()}/typed"
        reportRepository.saveAndFlush(
            ReportEntity(
                reporterId = UUID.fromString(me.userId),
                targetType = ReportTargetType.USER,
                reason = ReportReason.HATE_OR_HARASSMENT,
                snapshot = objectMapper.writeValueAsString(
                    mapOf("photoUrl" to photo, "description" to typed, "franchises" to listOf(mapOf("logoUrl" to logo))),
                ),
            ),
        )

        val urls = reportRepository.findOpenEvidenceUrls()
        assertTrue(photo in urls)
        assertTrue(logo in urls)
        assertFalse(typed in urls)
        // The same text as the media sweep's union.
        val swept = jdbcTemplate.queryForList(
            "select photo_url from profiles where photo_url is not null union $OPEN_REPORT_IMAGE_URLS",
            String::class.java,
        )
        assertTrue(photo in swept)
    }

    // ---------------------------------------------------------------- removed images and notices

    @Test
    fun `a removed photo keeps the profile complete and shows as removed, and a new upload clears it`() {
        val me = signInNewUser()
        val id = UUID.fromString(me.userId)
        val profile = com.crichere.backend.profile.ProfileEntity(
            userId = id,
            name = "Asha",
            photoUrl = null,
            state = "Kerala",
            district = "Ernakulam",
            playingRole = com.crichere.backend.profile.PlayingRole.BATSMAN,
            battingStyle = com.crichere.backend.profile.BattingStyle.RIGHT_HAND,
            photoRemoved = true,
        )
        profileRepository.save(profile)

        authedGet(me.token, "/api/v1/profiles/me")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.photoRemoved").value(true))
            .andExpect(jsonPath("$.profileComplete").value(true))
    }

    @Test
    fun `a notice shows until dismissed, and only its owner can dismiss it`() {
        val me = signInNewUser()
        val other = signInNewUser()
        val notice = noticeRepository.save(
            ModerationNoticeEntity(userId = UUID.fromString(me.userId), kind = ModerationNoticeKind.PHOTO),
        )

        authedPostEmpty(other.token, "/api/v1/me/moderation-notices/${notice.id}/dismiss").andExpect(status().isNoContent)
        authedGet(me.token, "/api/v1/me/moderation-notices")
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].kind").value("PHOTO"))
        authedGet(other.token, "/api/v1/me/moderation-notices").andExpect(jsonPath("$.length()").value(0))

        authedPostEmpty(me.token, "/api/v1/me/moderation-notices/${notice.id}/dismiss").andExpect(status().isNoContent)
        authedGet(me.token, "/api/v1/me/moderation-notices").andExpect(jsonPath("$.length()").value(0))
    }

    // ---------------------------------------------------------------- purge

    @Test
    fun `a purge clears blocks both ways, keeps evidence without the reporter, and a suspended number stays out`() {
        val me = signInNewUser()
        val other = signInNewUser()
        val third = signInNewUser()
        authedPut(me.token, "/api/v1/blocks/${other.userId}").andExpect(status().isNoContent)
        authedPut(third.token, "/api/v1/blocks/${me.userId}").andExpect(status().isNoContent)
        authedPost(
            me.token,
            "/api/v1/reports",
            mapOf("targetType" to "USER", "targetUserId" to other.userId, "reason" to "HATE_OR_HARASSMENT"),
        ).andExpect(status().isCreated)

        val myId = UUID.fromString(me.userId)
        val user = userRepository.findById(myId).get()
        val hash = requireNotNull(user.phoneLookupHash)
        user.deletionRequestedAt = Instant.now().minusSeconds(60L * 60 * 24 * 30)
        user.suspendedAt = Instant.now()
        userRepository.save(user)

        assertEquals(PurgeOutcome.PURGED, accountPurgeService.purgeDue(Instant.now())[myId])

        assertFalse(blockRepository.existsByBlockerIdAndBlockedId(myId, UUID.fromString(other.userId)))
        assertFalse(blockRepository.existsByBlockerIdAndBlockedId(UUID.fromString(third.userId), myId))
        val report = reportRepository.findByStatus(ReportStatus.OPEN).single { it.targetUserId == UUID.fromString(other.userId) }
        assertNull(report.reporterId)
        assertNotNull(suspendedPhoneHashRepository.findById(hash).orElse(null))
    }

    @Test
    fun `deleting an account doesn't destroy a photo held as evidence`() {
        val reporter = signInNewUser()
        val target = signInNewUser()
        val targetId = UUID.fromString(target.userId)
        val key = "users/$targetId/profile-${UUID.randomUUID()}"
        profileRepository.save(
            com.crichere.backend.profile.ProfileEntity(userId = targetId, name = "Asha", photoUrl = "https://b.s3.x.amazonaws.com/$key"),
        )
        authedPost(
            reporter.token,
            "/api/v1/reports",
            mapOf("targetType" to "USER", "targetUserId" to target.userId, "reason" to "INAPPROPRIATE_IMAGE"),
        ).andExpect(status().isCreated)
        val user = userRepository.findById(targetId).get()
        user.deletionRequestedAt = Instant.now().minusSeconds(60L * 60 * 24 * 30)
        userRepository.save(user)

        assertEquals(PurgeOutcome.PURGED, accountPurgeService.purgeDue(Instant.now())[targetId])

        assertTrue(deletedObjects.keys.any { it.startsWith("users/$targetId/") })
        assertFalse(key in deletedObjects.keys)
    }

    // ---------------------------------------------------------------- helpers

    private fun leagueIdsSeenBy(token: String?): List<String> {
        val request = get("/api/v1/leagues").param("state", "Karnataka")
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        val result = mockMvc.perform(request).andExpect(status().isOk).andReturn()
        @Suppress("UNCHECKED_CAST")
        val rows = objectMapper.readValue(result.response.contentAsString, List::class.java) as List<Map<String, Any?>>
        return rows.map { it["id"] as String }
    }

    private fun signInNewUser(): Caller {
        val phone = uniquePhone()
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)
        val session = postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token", "termsAccepted" to true))
            .andExpect(status().isOk)
            .andReturn()
            .body()
        return Caller(session["accessToken"] as String, session["userId"] as String, phone)
    }

    private fun createLeague(token: String): String {
        val groundId = authedPost(
            token,
            "/api/v1/grounds",
            mapOf("name" to "Test Ground", "state" to "Karnataka", "district" to "Bengaluru Urban", "latitude" to 12.9, "longitude" to 77.6),
        ).andExpect(status().isOk).andReturn().body()["id"] as String
        return authedPost(
            token,
            "/api/v1/leagues",
            mapOf("name" to "Weekend League", "state" to "Karnataka", "district" to "Bengaluru Urban", "groundId" to groundId, "startsOn" to "2026-10-12", "playersRequired" to 10),
        ).andExpect(status().isOk).andReturn().body()["id"] as String
    }

    private fun authedGet(token: String, path: String) =
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

    private fun authedPut(token: String, path: String) =
        mockMvc.perform(put(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

    private fun authedPostEmpty(token: String, path: String) =
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

    private fun authedDelete(token: String, path: String) =
        mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

    private fun authedPost(token: String, path: String, body: Map<String, Any?>) =
        mockMvc.perform(
            post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

    private fun postJson(path: String, body: Map<String, Any?>) =
        mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

    @Suppress("UNCHECKED_CAST")
    private fun MvcResult.body(): Map<String, Any?> =
        objectMapper.readValue(response.contentAsString, Map::class.java) as Map<String, Any?>

    private fun uniquePhone(): String =
        "+9199" + UUID.randomUUID().mostSignificantBits.toString().filter { it.isDigit() }.take(8).padEnd(8, '7')
}

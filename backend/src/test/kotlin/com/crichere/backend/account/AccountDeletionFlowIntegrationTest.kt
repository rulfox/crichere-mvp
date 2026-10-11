package com.crichere.backend.account

import com.crichere.backend.auth.RefreshTokenRepository
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.auth.VerifiedFirebaseToken
import com.crichere.backend.common.AbstractWebIntegrationTest
import com.crichere.backend.league.AuctionStatus
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.league.LeagueRoleRepository
import com.crichere.backend.player.PlayerRepository
import com.crichere.backend.profile.ProfileRepository
import io.mockk.every
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Account deletion over real HTTP and a real database (docs/PHASE16.md). The purge is driven directly with a clock past the grace period. */
class AccountDeletionFlowIntegrationTest : AbstractWebIntegrationTest() {

    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var accountPurgeService: AccountPurgeService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var profileRepository: ProfileRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var leagueRepository: LeagueRepository
    @Autowired private lateinit var leagueRoleRepository: LeagueRoleRepository
    @Autowired private lateinit var playerRepository: PlayerRepository

    private data class Session(val userId: UUID, val accessToken: String, val refreshToken: String, val phone: String, val accountRestored: Boolean)

    private val afterGrace: Instant get() = Instant.now().plus(Duration.ofDays(15))

    @Test
    fun `deletion endpoints require authentication`() {
        mockMvc.perform(get("/api/v1/me/deletion")).andExpect(status().isUnauthorized)
        postJson("/api/v1/me/deletion", mapOf("confirm" to "DELETE")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a request without the typed confirmation is rejected and changes nothing`() {
        val user = signIn()
        authedPost(user.accessToken, "/api/v1/me/deletion", mapOf("confirm" to "delete"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"))
        assertNull(userRepository.findById(user.userId).orElseThrow().deletionRequestedAt)
    }

    @Test
    fun `requesting deletion ends every session and signing in again cancels it`() {
        val user = signIn()
        authedGet(user.accessToken, "/api/v1/me/deletion")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.blockers").isEmpty)
            .andExpect(jsonPath("$.gracePeriodDays").value(14))

        authedPost(user.accessToken, "/api/v1/me/deletion", mapOf("confirm" to "DELETE"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.purgeAfter").exists())
        assertNotNull(userRepository.findById(user.userId).orElseThrow().deletionRequestedAt)
        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to user.refreshToken)).andExpect(status().isUnauthorized)

        val again = signIn(user.phone)
        assertEquals(user.userId, again.userId)
        assertTrue(again.accountRestored)
        assertNull(userRepository.findById(user.userId).orElseThrow().deletionRequestedAt)
        assertEquals(PurgeOutcome.SKIPPED, accountPurgeService.purgeDue(afterGrace)[user.userId] ?: PurgeOutcome.SKIPPED)
        assertNull(userRepository.findById(user.userId).orElseThrow().deletedAt)
    }

    @Test
    fun `a refresh token that escaped the revocation still gets no session while deletion is pending`() {
        val user = signIn()
        authedPost(user.accessToken, "/api/v1/me/deletion", mapOf("confirm" to "DELETE")).andExpect(status().isAccepted)

        // Simulate a refresh racing the request: its new token was written after the bulk revoke.
        refreshTokenRepository.findAll().filter { it.userId == user.userId }.forEach {
            it.revokedAt = null
            refreshTokenRepository.save(it)
        }
        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to user.refreshToken)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `an organizer without a co-organizer is blocked until the league has one`() {
        val organizer = signIn()
        val leagueId = createLeague(organizer.accessToken)

        authedPost(organizer.accessToken, "/api/v1/me/deletion", mapOf("confirm" to "DELETE"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_BLOCKED"))
            .andExpect(jsonPath("$.blockers[0].leagueId").value(leagueId))
            .andExpect(jsonPath("$.blockers[0].reason").value("NO_CO_ORGANIZER"))

        val coOrganizer = signIn()
        authedPost(organizer.accessToken, "/api/v1/leagues/$leagueId/roles", mapOf("userId" to coOrganizer.userId.toString()))
            .andExpect(status().isOk)
        authedGet(organizer.accessToken, "/api/v1/me/deletion").andExpect(jsonPath("$.blockers").isEmpty)
    }

    @Test
    fun `a player in a running auction is blocked, and a due purge waits for the auction to end`() {
        val organizer = signIn()
        val leagueId = createLeague(organizer.accessToken)
        val player = signIn()
        authedPost(player.accessToken, "/api/v1/leagues/$leagueId/players", emptyMap()).andExpect(status().isOk)
        setAuctionStatus(leagueId, AuctionStatus.IN_PROGRESS)

        authedPost(player.accessToken, "/api/v1/me/deletion", mapOf("confirm" to "DELETE"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.blockers[0].reason").value("AUCTION_LIVE"))

        // Requested before the auction started: the purge postpones instead of erasing mid-auction.
        setAuctionStatus(leagueId, AuctionStatus.NOT_STARTED)
        authedPost(player.accessToken, "/api/v1/me/deletion", mapOf("confirm" to "DELETE")).andExpect(status().isAccepted)
        setAuctionStatus(leagueId, AuctionStatus.IN_PROGRESS)
        assertEquals(PurgeOutcome.POSTPONED, accountPurgeService.purgeDue(afterGrace)[player.userId])
        assertNull(userRepository.findById(player.userId).orElseThrow().deletedAt)

        setAuctionStatus(leagueId, AuctionStatus.COMPLETED)
        assertEquals(PurgeOutcome.PURGED, accountPurgeService.purgeDue(afterGrace)[player.userId])
    }

    @Test
    fun `the purge erases personal data, hands leagues over and keeps history as Deleted user`() {
        val organizer = signIn()
        setProfile(organizer.accessToken, "Ravi Organizer")
        val organizedLeagueId = createLeague(organizer.accessToken)
        val coOrganizer = signIn()
        val laterCoOrganizer = signIn()
        authedPost(organizer.accessToken, "/api/v1/leagues/$organizedLeagueId/roles", mapOf("userId" to coOrganizer.userId.toString())).andExpect(status().isOk)
        authedPost(organizer.accessToken, "/api/v1/leagues/$organizedLeagueId/roles", mapOf("userId" to laterCoOrganizer.userId.toString())).andExpect(status().isOk)

        // Plays in one league still taking sign-ups and one whose auction already ran.
        val otherOrganizer = signIn()
        val openLeagueId = createLeague(otherOrganizer.accessToken)
        val auctionedLeagueId = createLeague(otherOrganizer.accessToken)
        authedPost(organizer.accessToken, "/api/v1/leagues/$openLeagueId/players", emptyMap()).andExpect(status().isOk)
        authedPost(organizer.accessToken, "/api/v1/leagues/$auctionedLeagueId/players", emptyMap()).andExpect(status().isOk)
        setAuctionStatus(auctionedLeagueId, AuctionStatus.COMPLETED)
        // Roster rows carry the profile photo (docs/PHASE17.md).
        authedGet(otherOrganizer.accessToken, "/api/v1/leagues/$auctionedLeagueId")
            .andExpect(jsonPath("$.players[?(@.userId=='${organizer.userId}')].photoUrl", contains("https://example.com/p.jpg")))

        // The screen shows who gets the league and the erase date before anything is requested.
        authedGet(organizer.accessToken, "/api/v1/me/deletion")
            .andExpect(jsonPath("$.blockers").isEmpty)
            .andExpect(jsonPath("$.erasesOn").exists())
            .andExpect(jsonPath("$.handovers.length()").value(1))
            .andExpect(jsonPath("$.handovers[0].leagueId").value(organizedLeagueId))
            .andExpect(jsonPath("$.handovers[0].newOrganizerUserId").value(coOrganizer.userId.toString()))

        authedPost(organizer.accessToken, "/api/v1/me/deletion", mapOf("confirm" to "DELETE")).andExpect(status().isAccepted)

        // Not due yet: nothing happens inside the grace period.
        assertNull(accountPurgeService.purgeDue(Instant.now())[organizer.userId])

        assertEquals(PurgeOutcome.PURGED, accountPurgeService.purgeDue(afterGrace)[organizer.userId])

        val tombstone = userRepository.findById(organizer.userId).orElseThrow()
        assertNotNull(tombstone.deletedAt)
        assertNull(tombstone.phoneLookupHash)
        assertNull(tombstone.phoneEncrypted)
        val profile = profileRepository.findById(organizer.userId).orElseThrow()
        assertEquals("Deleted user", profile.name)
        assertNull(profile.photoUrl)
        assertNull(profile.state)
        assertTrue(refreshTokenRepository.findAll().none { it.userId == organizer.userId })

        // Earliest co-organizer took over; their own grant is gone, the later one's stays.
        val handedOver = leagueRepository.findById(UUID.fromString(organizedLeagueId)).orElseThrow()
        assertEquals(coOrganizer.userId, handedOver.organizerUserId)
        assertNotNull(handedOver.organizerHandoverAt)
        val activeRoles = leagueRoleRepository.findByLeagueIdAndRevokedAtIsNull(UUID.fromString(organizedLeagueId)).map { it.userId }
        assertEquals(listOf(laterCoOrganizer.userId), activeRoles)
        authedGet(coOrganizer.accessToken, "/api/v1/leagues/$organizedLeagueId")
            .andExpect(jsonPath("$.organizerUserId").value(coOrganizer.userId.toString()))
            .andExpect(jsonPath("$.organizerHandoverAt").exists())
            .andExpect(jsonPath("$.showHandoverNotice").value(true))

        // The notice is the new organizer's alone; once dismissed it stays gone.
        authedGet(laterCoOrganizer.accessToken, "/api/v1/leagues/$organizedLeagueId")
            .andExpect(jsonPath("$.showHandoverNotice").value(false))
        authedPost(laterCoOrganizer.accessToken, "/api/v1/leagues/$organizedLeagueId/handover-notice/dismiss", emptyMap())
            .andExpect(status().isForbidden)
        authedPost(coOrganizer.accessToken, "/api/v1/leagues/$organizedLeagueId/handover-notice/dismiss", emptyMap())
            .andExpect(status().isNoContent)
        authedPost(coOrganizer.accessToken, "/api/v1/leagues/$organizedLeagueId/handover-notice/dismiss", emptyMap())
            .andExpect(status().isNoContent)
        authedGet(coOrganizer.accessToken, "/api/v1/leagues/$organizedLeagueId")
            .andExpect(jsonPath("$.showHandoverNotice").value(false))

        // Open league: withdrawn. Auctioned league: kept, shown as Deleted user.
        val rows = playerRepository.findByUserId(organizer.userId).associateBy { it.leagueId.toString() }
        assertNotNull(rows.getValue(openLeagueId).removedAt)
        assertNull(rows.getValue(auctionedLeagueId).removedAt)
        authedGet(otherOrganizer.accessToken, "/api/v1/leagues/$auctionedLeagueId")
            .andExpect(jsonPath("$.players[?(@.userId=='${organizer.userId}')].name").value("Deleted user"))
            .andExpect(jsonPath("$.players[?(@.userId=='${organizer.userId}')].deleted").value(true))
            .andExpect(jsonPath("$.players[?(@.userId=='${organizer.userId}')].photoUrl", contains(nullValue())))

        // The number is free again, as a brand-new account. A second run finds nothing to do.
        val fresh = signIn(organizer.phone)
        assertNotEquals(organizer.userId, fresh.userId)
        assertEquals(false, fresh.accountRestored)
        assertNull(accountPurgeService.purgeDue(afterGrace)[organizer.userId])
    }

    @Test
    fun `the only co-organizer of a leaving organizer cannot step down`() {
        val organizer = signIn()
        val leagueId = createLeague(organizer.accessToken)
        val coOrganizer = signIn()
        val league = authedPost(organizer.accessToken, "/api/v1/leagues/$leagueId/roles", mapOf("userId" to coOrganizer.userId.toString()))
            .andExpect(status().isOk).andReturn().body()
        @Suppress("UNCHECKED_CAST")
        val roleId = ((league["coOrganizers"] as List<Map<String, Any?>>).single())["id"] as String
        authedPost(organizer.accessToken, "/api/v1/me/deletion", mapOf("confirm" to "DELETE")).andExpect(status().isAccepted)

        mockMvc.perform(delete("/api/v1/leagues/$leagueId/roles/$roleId").header(HttpHeaders.AUTHORIZATION, "Bearer ${coOrganizer.accessToken}"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("LAST_SUCCESSOR"))
    }

    // ---------------------------------------------------------------- helpers

    private fun signIn(phone: String = uniquePhone()): Session {
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)
        val body = postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
            .andExpect(status().isOk).andReturn().body()
        return Session(
            userId = UUID.fromString(body["userId"] as String),
            accessToken = body["accessToken"] as String,
            refreshToken = body["refreshToken"] as String,
            phone = phone,
            accountRestored = body["accountRestored"] as Boolean,
        )
    }

    private fun setProfile(token: String, name: String) {
        mockMvc.perform(
            put("/api/v1/profiles/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to name, "photoUrl" to "https://example.com/p.jpg"))),
        ).andExpect(status().isOk)
    }

    private fun createLeague(token: String): String {
        val groundId = authedPost(
            token,
            "/api/v1/grounds",
            mapOf("name" to "Test Ground", "state" to "Karnataka", "district" to "Bengaluru Urban", "latitude" to 12.97, "longitude" to 77.59),
        ).andExpect(status().isOk).andReturn().body()["id"] as String
        return authedPost(
            token,
            "/api/v1/leagues",
            mapOf("name" to "Deletion Test League", "state" to "Karnataka", "district" to "Bengaluru Urban", "groundId" to groundId, "startsOn" to "2026-12-12"),
        ).andExpect(status().isOk).andReturn().body()["id"] as String
    }

    private fun setAuctionStatus(leagueId: String, auctionStatus: AuctionStatus) {
        val league = leagueRepository.findById(UUID.fromString(leagueId)).orElseThrow()
        league.auctionStatus = auctionStatus
        leagueRepository.save(league)
    }

    private fun authedGet(token: String, path: String) =
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

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
        "+9198" + UUID.randomUUID().mostSignificantBits.toString().filter { it.isDigit() }.take(8).padEnd(8, '7')
}

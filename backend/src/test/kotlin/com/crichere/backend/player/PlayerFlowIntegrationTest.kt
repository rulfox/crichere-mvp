package com.crichere.backend.player

import com.crichere.backend.auth.VerifiedFirebaseToken
import com.crichere.backend.common.AbstractWebIntegrationTest
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** Join/remove/leave-request/approve/dismiss over real HTTP -- see `LeagueFlowIntegrationTest`'s class doc for the shared testing posture. */
class PlayerFlowIntegrationTest : AbstractWebIntegrationTest {

    constructor() : super()

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var organizerToken: String

    @BeforeEach
    fun signIn() {
        organizerToken = signInNewUser()
    }

    private fun signInNewUser(): String {
        val phone = uniquePhone()
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)
        val session = postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
            .andExpect(status().isOk)
            .andReturn()
            .body()
        return session["accessToken"] as String
    }

    private fun createLeague(token: String, playersRequired: Int? = null, playerFee: Any? = null, organizerUpiId: String? = null): String {
        val body = mapOf(
            "name" to "Weekend Box Cricket League",
            "state" to "Karnataka",
            "district" to "Bengaluru Urban",
            "city" to "Bengaluru",
            "startsOn" to "2026-10-12",
            "playersRequired" to playersRequired,
            "playerFee" to playerFee,
            "organizerUpiId" to organizerUpiId,
        )
        return authedPost(token, "/api/v1/leagues", body)
            .andExpect(status().isOk)
            .andReturn()
            .body()["id"] as String
    }

    @Test
    fun `joining requires authentication`() {
        val leagueId = createLeague(organizerToken)
        mockMvc.perform(
            post("/api/v1/leagues/$leagueId/players").contentType(MediaType.APPLICATION_JSON).content("{}"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a free join with no fee succeeds instantly and shows up on the league`() {
        val leagueId = createLeague(organizerToken)
        val playerToken = signInNewUser()

        authedPost(playerToken, "/api/v1/leagues/$leagueId/players", emptyMap())
            .andExpect(status().isOk)

        authedGet(organizerToken, "/api/v1/leagues/$leagueId")
            .andExpect(jsonPath("$.players.length()").value(1))
    }

    @Test
    fun `a non-https paymentScreenshotUrl is rejected -- for example a local file URI`() {
        val leagueId = createLeague(organizerToken)
        val playerToken = signInNewUser()

        authedPost(playerToken, "/api/v1/leagues/$leagueId/players", mapOf("paymentScreenshotUrl" to "file:///storage/emulated/0/DCIM/private.jpg"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `joining twice is rejected as already joined`() {
        val leagueId = createLeague(organizerToken)
        val playerToken = signInNewUser()
        authedPost(playerToken, "/api/v1/leagues/$leagueId/players", emptyMap()).andExpect(status().isOk)

        authedPost(playerToken, "/api/v1/leagues/$leagueId/players", emptyMap())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_JOINED"))
    }

    @Test
    fun `joining once capacity is full is rejected`() {
        val leagueId = createLeague(organizerToken, playersRequired = 1)
        val firstPlayer = signInNewUser()
        val secondPlayer = signInNewUser()
        authedPost(firstPlayer, "/api/v1/leagues/$leagueId/players", emptyMap()).andExpect(status().isOk)

        authedPost(secondPlayer, "/api/v1/leagues/$leagueId/players", emptyMap())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CAPACITY_FULL"))
    }

    @Test
    fun `joining a league with a player fee requires a payment screenshot`() {
        val leagueId = createLeague(organizerToken, playerFee = 100, organizerUpiId = "organizer@upi")
        val playerToken = signInNewUser()

        authedPost(playerToken, "/api/v1/leagues/$leagueId/players", emptyMap())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("PAYMENT_SCREENSHOT_REQUIRED"))

        authedPost(playerToken, "/api/v1/leagues/$leagueId/players", mapOf("paymentScreenshotUrl" to "https://example.com/proof.jpg"))
            .andExpect(status().isOk)
    }

    @Test
    fun `only the organizer can remove a player`() {
        val leagueId = createLeague(organizerToken)
        val playerToken = signInNewUser()
        val playerId = authedPost(playerToken, "/api/v1/leagues/$leagueId/players", emptyMap())
            .andExpect(status().isOk)
            .andReturn()
            .body()["id"] as String

        mockMvc.perform(
            delete("/api/v1/leagues/$leagueId/players/$playerId").header(HttpHeaders.AUTHORIZATION, "Bearer $playerToken"),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            delete("/api/v1/leagues/$leagueId/players/$playerId").header(HttpHeaders.AUTHORIZATION, "Bearer $organizerToken"),
        ).andExpect(status().isOk)
    }

    @Test
    fun `a leave request only succeeds for the player's own user, and organizer approval frees the slot for a new join`() {
        val leagueId = createLeague(organizerToken, playersRequired = 1)
        val playerToken = signInNewUser()
        val playerId = authedPost(playerToken, "/api/v1/leagues/$leagueId/players", emptyMap())
            .andExpect(status().isOk)
            .andReturn()
            .body()["id"] as String

        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/players/$playerId/leave-request")
            .andExpect(status().isForbidden)

        authedPostNoBody(playerToken, "/api/v1/leagues/$leagueId/players/$playerId/leave-request")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.leaveRequestedAt").exists())

        // Full capacity -- a second player can't join yet.
        val secondPlayer = signInNewUser()
        authedPost(secondPlayer, "/api/v1/leagues/$leagueId/players", emptyMap())
            .andExpect(status().isConflict)

        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/players/$playerId/leave-request/approve")
            .andExpect(status().isOk)

        // Slot is free now.
        authedPost(secondPlayer, "/api/v1/leagues/$leagueId/players", emptyMap())
            .andExpect(status().isOk)
    }

    @Test
    fun `approving or dismissing a leave request with none pending is rejected`() {
        val leagueId = createLeague(organizerToken)
        val playerToken = signInNewUser()
        val playerId = authedPost(playerToken, "/api/v1/leagues/$leagueId/players", emptyMap())
            .andExpect(status().isOk)
            .andReturn()
            .body()["id"] as String

        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/players/$playerId/leave-request/approve")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("NO_LEAVE_REQUEST_PENDING"))
    }

    @Test
    fun `a stranger cannot see the payment screenshot, but the organizer and the player themselves can`() {
        val leagueId = createLeague(organizerToken, playerFee = 100, organizerUpiId = "organizer@upi")
        val playerToken = signInNewUser()
        authedPost(playerToken, "/api/v1/leagues/$leagueId/players", mapOf("paymentScreenshotUrl" to "https://example.com/proof.jpg"))
            .andExpect(status().isOk)

        val stranger = signInNewUser()
        authedGet(stranger, "/api/v1/leagues/$leagueId")
            .andExpect(jsonPath("$.players[0].paymentScreenshotUrl").doesNotExist())

        authedGet(organizerToken, "/api/v1/leagues/$leagueId")
            .andExpect(jsonPath("$.players[0].paymentScreenshotUrl").value("https://example.com/proof.jpg"))

        authedGet(playerToken, "/api/v1/leagues/$leagueId")
            .andExpect(jsonPath("$.players[0].paymentScreenshotUrl").value("https://example.com/proof.jpg"))
    }

    // ---------------------------------------------------------------- helpers

    private fun authedGet(token: String, path: String) =
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

    private fun authedPost(token: String, path: String, body: Map<String, Any?>) =
        mockMvc.perform(
            post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

    private fun authedPostNoBody(token: String, path: String) =
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

    private fun postJson(path: String, body: Map<String, Any?>) =
        mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

    private fun MvcResult.body(): Map<String, Any?> = responseBodyOf(this)

    @Suppress("UNCHECKED_CAST")
    private fun responseBodyOf(result: MvcResult): Map<String, Any?> =
        objectMapper.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any?>

    private fun uniquePhone(): String =
        "+9199" + UUID.randomUUID().mostSignificantBits.toString().filter { it.isDigit() }.take(8).padEnd(8, '7')
}

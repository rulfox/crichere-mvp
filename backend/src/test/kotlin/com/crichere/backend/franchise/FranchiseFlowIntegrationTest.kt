package com.crichere.backend.franchise

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** Claim/remove/leave-request/approve/dismiss/logo-upload over real HTTP -- see `LeagueFlowIntegrationTest`'s class doc for the shared testing posture. */
class FranchiseFlowIntegrationTest : AbstractWebIntegrationTest {

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

    private fun createLeague(token: String, franchisesRequired: Int? = null, franchiseFee: Any? = null, organizerUpiId: String? = null): String {
        val body = mapOf(
            "name" to "Weekend Box Cricket League",
            "state" to "Karnataka",
            "district" to "Bengaluru Urban",
            "city" to "Bengaluru",
            "startsOn" to "2026-10-12",
            "franchisesRequired" to franchisesRequired,
            "franchiseFee" to franchiseFee,
            "organizerUpiId" to organizerUpiId,
        )
        return authedPost(token, "/api/v1/leagues", body)
            .andExpect(status().isOk)
            .andReturn()
            .body()["id"] as String
    }

    private fun claimBody(name: String = "Chennai Kings", screenshotUrl: String? = null) =
        mapOf("name" to name, "paymentScreenshotUrl" to screenshotUrl)

    @Test
    fun `a free claim with no fee succeeds instantly and shows up on the league`() {
        val leagueId = createLeague(organizerToken)
        val ownerToken = signInNewUser()

        authedPost(ownerToken, "/api/v1/leagues/$leagueId/franchises", claimBody())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Chennai Kings"))

        authedGet(organizerToken, "/api/v1/leagues/$leagueId")
            .andExpect(jsonPath("$.franchises.length()").value(1))
    }

    @Test
    fun `a second claim by the same user in the same league succeeds`() {
        val leagueId = createLeague(organizerToken)
        val ownerToken = signInNewUser()
        authedPost(ownerToken, "/api/v1/leagues/$leagueId/franchises", claimBody("Chennai Kings")).andExpect(status().isOk)

        authedPost(ownerToken, "/api/v1/leagues/$leagueId/franchises", claimBody("Bengaluru Blasters"))
            .andExpect(status().isOk)

        authedGet(organizerToken, "/api/v1/leagues/$leagueId")
            .andExpect(jsonPath("$.franchises.length()").value(2))
    }

    @Test
    fun `claiming once capacity is full is rejected`() {
        val leagueId = createLeague(organizerToken, franchisesRequired = 1)
        val firstOwner = signInNewUser()
        val secondOwner = signInNewUser()
        authedPost(firstOwner, "/api/v1/leagues/$leagueId/franchises", claimBody()).andExpect(status().isOk)

        authedPost(secondOwner, "/api/v1/leagues/$leagueId/franchises", claimBody())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CAPACITY_FULL"))
    }

    @Test
    fun `claiming a franchise in a league with a franchise fee requires a payment screenshot`() {
        val leagueId = createLeague(organizerToken, franchiseFee = 5000, organizerUpiId = "organizer@upi")
        val ownerToken = signInNewUser()

        authedPost(ownerToken, "/api/v1/leagues/$leagueId/franchises", claimBody())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("PAYMENT_SCREENSHOT_REQUIRED"))

        authedPost(ownerToken, "/api/v1/leagues/$leagueId/franchises", claimBody(screenshotUrl = "https://example.com/proof.jpg"))
            .andExpect(status().isOk)
    }

    @Test
    fun `only the organizer can remove a franchise`() {
        val leagueId = createLeague(organizerToken)
        val ownerToken = signInNewUser()
        val franchiseId = authedPost(ownerToken, "/api/v1/leagues/$leagueId/franchises", claimBody())
            .andExpect(status().isOk)
            .andReturn()
            .body()["id"] as String

        mockMvc.perform(
            delete("/api/v1/leagues/$leagueId/franchises/$franchiseId").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken"),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            delete("/api/v1/leagues/$leagueId/franchises/$franchiseId").header(HttpHeaders.AUTHORIZATION, "Bearer $organizerToken"),
        ).andExpect(status().isOk)
    }

    @Test
    fun `a leave request only succeeds for the franchise's own owner, and organizer approval frees the slot`() {
        val leagueId = createLeague(organizerToken, franchisesRequired = 1)
        val ownerToken = signInNewUser()
        val franchiseId = authedPost(ownerToken, "/api/v1/leagues/$leagueId/franchises", claimBody())
            .andExpect(status().isOk)
            .andReturn()
            .body()["id"] as String

        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/franchises/$franchiseId/leave-request")
            .andExpect(status().isForbidden)

        authedPostNoBody(ownerToken, "/api/v1/leagues/$leagueId/franchises/$franchiseId/leave-request")
            .andExpect(status().isOk)

        val secondOwner = signInNewUser()
        authedPost(secondOwner, "/api/v1/leagues/$leagueId/franchises", claimBody())
            .andExpect(status().isConflict)

        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/franchises/$franchiseId/leave-request/approve")
            .andExpect(status().isOk)

        authedPost(secondOwner, "/api/v1/leagues/$leagueId/franchises", claimBody())
            .andExpect(status().isOk)
    }

    @Test
    fun `only the organizer or the franchise's own owner can request a logo upload url`() {
        val leagueId = createLeague(organizerToken)
        val ownerToken = signInNewUser()
        val franchiseId = authedPost(ownerToken, "/api/v1/leagues/$leagueId/franchises", claimBody())
            .andExpect(status().isOk)
            .andReturn()
            .body()["id"] as String

        val stranger = signInNewUser()
        authedPostNoBody(stranger, "/api/v1/leagues/$leagueId/franchises/$franchiseId/logo-upload-url")
            .andExpect(status().isForbidden)

        authedPostNoBody(ownerToken, "/api/v1/leagues/$leagueId/franchises/$franchiseId/logo-upload-url")
            .andExpect(status().isServiceUnavailable) // S3 not configured in this environment -- same posture as league logo upload.
    }

    // ---------------------------------------------------------------- helpers

    private fun authedGet(token: String, path: String) =
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

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

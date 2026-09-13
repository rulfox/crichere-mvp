package com.crichere.backend.league

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** Co-organizer role delegation over real HTTP (docs/PHASE7.md) -- see `AuctionFlowIntegrationTest`'s class doc for the shared testing posture. */
class RoleFlowIntegrationTest : AbstractWebIntegrationTest {

    constructor() : super()

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var organizerToken: String
    private lateinit var organizerPhone: String

    @BeforeEach
    fun signIn() {
        organizerPhone = uniquePhone()
        organizerToken = signInWithPhone(organizerPhone)
    }

    private fun signInWithPhone(phone: String): String {
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)
        val session = postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
            .andExpect(status().isOk)
            .andReturn()
            .body()
        return session["accessToken"] as String
    }

    private fun signInNewUser(): Pair<String, String> {
        val phone = uniquePhone()
        return signInWithPhone(phone) to phone
    }

    private fun createLeague(token: String): String {
        val body = mapOf(
            "name" to "Weekend Box Cricket League",
            "state" to "Karnataka",
            "district" to "Bengaluru Urban",
            "city" to "Bengaluru",
            "startsOn" to "2026-10-12",
        )
        return authedPost(token, "/api/v1/leagues", body).andExpect(status().isOk).andReturn().body()["id"] as String
    }

    @Test
    fun `a granted co-organizer can edit the league and drive the auction -- revoking immediately blocks it again`() {
        val leagueId = createLeague(organizerToken)
        val (delegateToken, delegatePhone) = signInNewUser()

        val delegateUserId = authedPost(organizerToken, "/api/v1/leagues/$leagueId/roles/lookup", mapOf("phoneNumber" to delegatePhone))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").doesNotExist())
            .andReturn()
            .body()["userId"] as String

        // Not yet granted -- editing still rejected.
        authedPut(delegateToken, "/api/v1/leagues/$leagueId", editBody("Renamed")).andExpect(status().isForbidden)

        val grantResult = authedPost(organizerToken, "/api/v1/leagues/$leagueId/roles", mapOf("userId" to delegateUserId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coOrganizers.length()").value(1))
            .andReturn()
        val roleId = ((grantResult.body()["coOrganizers"] as List<*>).first() as Map<*, *>)["id"] as String

        // Granted -- both a plain league edit and an auction-control action now succeed for the delegate.
        authedPut(delegateToken, "/api/v1/leagues/$leagueId", editBody("Renamed")).andExpect(status().isOk)
        authedPut(delegateToken, "/api/v1/leagues/$leagueId/auction-settings", mapOf("basePrice" to 100, "purse" to 1000, "squadMin" to 1, "squadMax" to 5, "bidIncrement" to 50))
            .andExpect(status().isOk)

        authedDelete(organizerToken, "/api/v1/leagues/$leagueId/roles/$roleId").andExpect(status().isOk)
            .andExpect(jsonPath("$.coOrganizers.length()").value(0))

        // Revoked -- immediately rejected again, no separate sign-out/token-refresh needed.
        authedPut(delegateToken, "/api/v1/leagues/$leagueId", editBody("Renamed Again")).andExpect(status().isForbidden)
    }

    private fun editBody(name: String) = mapOf(
        "name" to name,
        "state" to "Karnataka",
        "district" to "Bengaluru Urban",
        "city" to "Bengaluru",
        "startsOn" to "2026-10-12",
    )

    @Test
    fun `lookup 404s for a phone number with no registered account`() {
        val leagueId = createLeague(organizerToken)
        authedPost(organizerToken, "/api/v1/leagues/$leagueId/roles/lookup", mapOf("phoneNumber" to uniquePhone()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `only an organizer or co-organizer can look up, grant, or revoke`() {
        val leagueId = createLeague(organizerToken)
        val (strangerToken, _) = signInNewUser()
        val (_, targetPhone) = signInNewUser()

        authedPost(strangerToken, "/api/v1/leagues/$leagueId/roles/lookup", mapOf("phoneNumber" to targetPhone)).andExpect(status().isForbidden)
        authedPost(strangerToken, "/api/v1/leagues/$leagueId/roles", mapOf("userId" to UUID.randomUUID().toString())).andExpect(status().isForbidden)
        authedDelete(strangerToken, "/api/v1/leagues/$leagueId/roles/${UUID.randomUUID()}").andExpect(status().isForbidden)
    }

    @Test
    fun `granting a duplicate active role, or granting to the league's own organizer, both 409`() {
        val leagueId = createLeague(organizerToken)
        val (_, delegatePhone) = signInNewUser()
        val delegateUserId = authedPost(organizerToken, "/api/v1/leagues/$leagueId/roles/lookup", mapOf("phoneNumber" to delegatePhone))
            .andReturn().body()["userId"] as String

        authedPost(organizerToken, "/api/v1/leagues/$leagueId/roles", mapOf("userId" to delegateUserId)).andExpect(status().isOk)
        authedPost(organizerToken, "/api/v1/leagues/$leagueId/roles", mapOf("userId" to delegateUserId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ROLE_ALREADY_GRANTED"))

        val organizerUserId = authedPost(organizerToken, "/api/v1/leagues/$leagueId/roles/lookup", mapOf("phoneNumber" to organizerPhone))
            .andReturn().body()["userId"] as String
        authedPost(organizerToken, "/api/v1/leagues/$leagueId/roles", mapOf("userId" to organizerUserId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CANNOT_GRANT_ROLE_TO_ORGANIZER"))
    }

    @Test
    fun `a co-organizer can revoke their own grant`() {
        val leagueId = createLeague(organizerToken)
        val (delegateToken, delegatePhone) = signInNewUser()
        val delegateUserId = authedPost(organizerToken, "/api/v1/leagues/$leagueId/roles/lookup", mapOf("phoneNumber" to delegatePhone))
            .andReturn().body()["userId"] as String
        val roleId = (authedPost(organizerToken, "/api/v1/leagues/$leagueId/roles", mapOf("userId" to delegateUserId))
            .andReturn().body()["coOrganizers"] as List<*>).first().let { (it as Map<*, *>)["id"] as String }

        authedDelete(delegateToken, "/api/v1/leagues/$leagueId/roles/$roleId").andExpect(status().isOk)
        authedPut(delegateToken, "/api/v1/leagues/$leagueId", editBody("Should fail")).andExpect(status().isForbidden)
    }

    @Test
    fun `the phone-lookup rate limit trips after 10 attempts in the window`() {
        val leagueId = createLeague(organizerToken)
        repeat(10) {
            authedPost(organizerToken, "/api/v1/leagues/$leagueId/roles/lookup", mapOf("phoneNumber" to uniquePhone()))
                .andExpect(status().isNotFound)
        }
        authedPost(organizerToken, "/api/v1/leagues/$leagueId/roles/lookup", mapOf("phoneNumber" to uniquePhone()))
            .andExpect(status().isTooManyRequests)
    }

    // ---------------------------------------------------------------- helpers

    private fun authedPost(token: String, path: String, body: Map<String, Any?>) =
        mockMvc.perform(
            post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

    private fun authedPut(token: String, path: String, body: Map<String, Any?>) =
        mockMvc.perform(
            put(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

    private fun authedDelete(token: String, path: String) =
        mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

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

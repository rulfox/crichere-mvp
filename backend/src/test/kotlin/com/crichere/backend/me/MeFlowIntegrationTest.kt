package com.crichere.backend.me

import com.crichere.backend.auth.VerifiedFirebaseToken
import com.crichere.backend.common.AbstractWebIntegrationTest
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** `GET /api/v1/me/leagues` over real HTTP -- see `LeagueFlowIntegrationTest`'s class doc for the shared testing posture. */
class MeFlowIntegrationTest : AbstractWebIntegrationTest {

    constructor() : super()

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun signInNewUser(): String {
        val phone = uniquePhone()
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)
        val session = postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
            .andExpect(status().isOk)
            .andReturn()
            .body()
        return session["accessToken"] as String
    }

    private fun createLeague(token: String): String =
        authedPost(
            token,
            "/api/v1/leagues",
            mapOf("name" to "Weekend Box Cricket League", "state" to "Karnataka", "district" to "Bengaluru Urban", "city" to "Bengaluru", "startsOn" to "2026-10-12"),
        ).andExpect(status().isOk).andReturn().body()["id"] as String

    @Test
    fun `my leagues requires authentication`() {
        mockMvc.perform(get("/api/v1/me/leagues")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a user who organizes, plays in, owns a franchise in, and follows different leagues sees all four lists populated`() {
        val organizerToken = signInNewUser()
        val organizedLeagueId = createLeague(organizerToken)

        val otherOrganizerToken = signInNewUser()
        val playedLeagueId = createLeague(otherOrganizerToken)
        authedPost(organizerToken, "/api/v1/leagues/$playedLeagueId/players", emptyMap()).andExpect(status().isOk)

        val franchiseLeagueId = createLeague(otherOrganizerToken)
        authedPost(organizerToken, "/api/v1/leagues/$franchiseLeagueId/franchises", mapOf("name" to "Chennai Kings")).andExpect(status().isOk)

        val followedLeagueId = createLeague(otherOrganizerToken)
        authedPostNoBody(organizerToken, "/api/v1/leagues/$followedLeagueId/follow").andExpect(status().isOk)

        authedGet(organizerToken, "/api/v1/me/leagues")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.organizing[?(@.id=='$organizedLeagueId')]").exists())
            .andExpect(jsonPath("$.playing[?(@.id=='$playedLeagueId')]").exists())
            .andExpect(jsonPath("$.franchiseOwner[?(@.id=='$franchiseLeagueId')]").exists())
            .andExpect(jsonPath("$.following[?(@.id=='$followedLeagueId')]").exists())
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

package com.crichere.backend.league

import com.crichere.backend.auth.VerifiedFirebaseToken
import com.crichere.backend.common.AbstractWebIntegrationTest
import io.mockk.every
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

/** Follow/unfollow over real HTTP -- see `LeagueFlowIntegrationTest`'s class doc for the shared testing posture. */
class LeagueFollowFlowIntegrationTest : AbstractWebIntegrationTest {

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
    fun `following requires authentication`() {
        val organizerToken = signInNewUser()
        val leagueId = createLeague(organizerToken)
        mockMvc.perform(post("/api/v1/leagues/$leagueId/follow")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `following twice does not fail, and isFollowing is reflected on the next GET`() {
        val organizerToken = signInNewUser()
        val leagueId = createLeague(organizerToken)
        val followerToken = signInNewUser()

        authedGet(followerToken, "/api/v1/leagues/$leagueId").andExpect(jsonPath("$.isFollowing").value(false))

        authedPostNoBody(followerToken, "/api/v1/leagues/$leagueId/follow").andExpect(status().isOk)
        authedPostNoBody(followerToken, "/api/v1/leagues/$leagueId/follow").andExpect(status().isOk)

        authedGet(followerToken, "/api/v1/leagues/$leagueId").andExpect(jsonPath("$.isFollowing").value(true))

        // Someone else's isFollowing stays false -- it's per-caller, not a league-wide flag.
        val strangerToken = signInNewUser()
        authedGet(strangerToken, "/api/v1/leagues/$leagueId").andExpect(jsonPath("$.isFollowing").value(false))
    }

    @Test
    fun `unfollowing when not following does not fail, and clears isFollowing after actually following`() {
        val organizerToken = signInNewUser()
        val leagueId = createLeague(organizerToken)
        val followerToken = signInNewUser()

        mockMvc.perform(
            delete("/api/v1/leagues/$leagueId/follow").header(HttpHeaders.AUTHORIZATION, "Bearer $followerToken"),
        ).andExpect(status().isOk)

        authedPostNoBody(followerToken, "/api/v1/leagues/$leagueId/follow").andExpect(status().isOk)
        mockMvc.perform(
            delete("/api/v1/leagues/$leagueId/follow").header(HttpHeaders.AUTHORIZATION, "Bearer $followerToken"),
        ).andExpect(status().isOk)

        authedGet(followerToken, "/api/v1/leagues/$leagueId").andExpect(jsonPath("$.isFollowing").value(false))
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

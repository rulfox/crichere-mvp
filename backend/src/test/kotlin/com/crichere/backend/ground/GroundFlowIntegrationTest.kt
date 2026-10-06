package com.crichere.backend.ground

import com.crichere.backend.auth.VerifiedFirebaseToken
import com.crichere.backend.common.AbstractWebIntegrationTest
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
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
import kotlin.test.assertTrue

/**
 * `GET /api/v1/grounds` (public) and `POST /api/v1/grounds` (authenticated) over real HTTP
 * against a real Postgres -- only [com.crichere.backend.auth.FirebaseTokenVerifier] is mocked
 * (see [AbstractWebIntegrationTest]).
 */
class GroundFlowIntegrationTest : AbstractWebIntegrationTest {

    constructor() : super()

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var accessToken: String

    @BeforeEach
    fun signIn() {
        val phone = uniquePhone()
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)

        val session = postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
            .andExpect(status().isOk)
            .andReturn()
            .body()
        accessToken = session["accessToken"] as String
    }

    @Test
    fun `search is public, no Authorization header needed`() {
        mockMvc.perform(get("/api/v1/grounds")).andExpect(status().isOk)
    }

    @Test
    fun `registering a ground requires authentication`() {
        mockMvc.perform(
            post("/api/v1/grounds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a real ground is registered and then found by search`() {
        val created = authedPost(
            "/api/v1/grounds",
            mapOf(
                "name" to "Chepauk Ground ${UUID.randomUUID()}",
                "state" to "Tamil Nadu",
                "district" to "Chennai",
                "latitude" to 13.0827,
                "longitude" to 80.2707,
            ),
        )
            .andExpect(status().isOk)
            .andReturn()
            .body()

        val groundId = created["id"] as String

        val found = mockMvc.perform(get("/api/v1/grounds").param("search", "Chepauk"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        assertTrue(readList(found).any { it["id"] == groundId })
    }

    @Test
    fun `an incomplete registration fails bean validation with the standard problem detail shape`() {
        authedPost("/api/v1/grounds", mapOf("name" to "", "state" to "Tamil Nadu", "district" to "Chennai"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `an out-of-range latitude fails bean validation`() {
        authedPost(
            "/api/v1/grounds",
            mapOf(
                "name" to "Somewhere",
                "state" to "Tamil Nadu",
                "district" to "Chennai",
                "latitude" to 200.0,
                "longitude" to 80.0,
            ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    // ---------------------------------------------------------------- helpers

    private fun authedPost(path: String, body: Map<String, Any?>) =
        mockMvc.perform(
            post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

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

    @Suppress("UNCHECKED_CAST")
    private fun readList(json: String): List<Map<String, Any?>> =
        objectMapper.readValue(json, List::class.java) as List<Map<String, Any?>>

    private fun uniquePhone(): String =
        "+9199" + UUID.randomUUID().mostSignificantBits.toString().filter { it.isDigit() }.take(8).padEnd(8, '7')
}

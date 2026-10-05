package com.crichere.backend.profile

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The full resumable-onboarding flow over real HTTP against a real Postgres:
 * `/auth/session` -> `/profiles/me` (GET, fresh) -> `/profiles/me` (PUT) ->
 * `/profiles/me` (GET again) -> `/auth/refresh` -> `/auth/logout`.
 *
 * Only [com.crichere.backend.auth.FirebaseTokenVerifier] is mocked (see
 * [AbstractWebIntegrationTest]); everything else -- routing, Bean Validation, the cross-field
 * bowling-style rule, the security filter chain, JPA, Flyway -- is the real thing. AWS/S3 is
 * never configured in this environment (`crichere.aws.s3.bucket`/`region` are blank in every
 * profile, same as production before AWS setup happens), so the photo-upload test here only
 * proves the endpoint degrades gracefully -- see `PhotoUploadEndpointIntegrationTest` for the
 * happy-path presign shape, exercised with a fake credentials provider.
 */
class ProfileFlowIntegrationTest : AbstractWebIntegrationTest {

    constructor() : super()

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var accessToken: String
    private lateinit var refreshToken: String
    private lateinit var userId: UUID

    @BeforeEach
    fun signIn() {
        val phone = uniquePhone()
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)

        val session = postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
            .andExpect(status().isOk)
            .andReturn()
            .body()
        accessToken = session["accessToken"] as String
        refreshToken = session["refreshToken"] as String
        userId = UUID.fromString(session["userId"] as String)
    }

    // ---------------------------------------------------------------- the resumable onboarding flow

    @Test
    fun `a brand-new user's profile is 200 with every optional field null, not 404`() {
        val body = authedGet("/api/v1/profiles/me")
            .andExpect(status().isOk)
            .andReturn()
            .body()

        assertEquals(userId.toString(), body["userId"])
        for (field in listOf("name", "photoUrl", "country", "state", "district", "city", "playingRole", "battingStyle", "bowlingStyle")) {
            assertNull(body[field], "$field should be null for a user with no profile row yet")
        }
        assertEquals(false, body["profileComplete"])
    }

    @Test
    fun `filling every required field for a role completes the profile, and it persists`() {
        val batsman = mapOf(
            "name" to "Virat Kohli",
            "photoUrl" to "https://cdn.crichere.app/photos/virat.jpg",
            "state" to "Delhi",
            "district" to "New Delhi",
            "city" to "New Delhi",
            "playingRole" to "BATSMAN",
            "battingStyle" to "RIGHT_HAND",
        )

        val putResponse = authedPut("/api/v1/profiles/me", batsman)
            .andExpect(status().isOk)
            .andReturn()
            .body()
        assertEquals(true, putResponse["profileComplete"])
        assertEquals("Virat Kohli", putResponse["name"])

        val getResponse = authedGet("/api/v1/profiles/me")
            .andExpect(status().isOk)
            .andReturn()
            .body()
        assertEquals("Virat Kohli", getResponse["name"])
        assertEquals("BATSMAN", getResponse["playingRole"])
        assertEquals(true, getResponse["profileComplete"])
        assertNull(getResponse["bowlingStyle"])
    }

    @Test
    fun `profileComplete agrees across profiles-me and auth-refresh -- one shared rule`() {
        val bowler = mapOf(
            "name" to "Jasprit Bumrah",
            "photoUrl" to "https://cdn.crichere.app/photos/jasprit.jpg",
            "state" to "Gujarat",
            "district" to "Ahmedabad",
            "city" to "Ahmedabad",
            "playingRole" to "BOWLER",
            "battingStyle" to "RIGHT_HAND",
            "bowlingStyle" to "RIGHT_ARM_FAST",
        )
        authedPut("/api/v1/profiles/me", bowler).andExpect(status().isOk)

        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to refreshToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.profileComplete").value(true))

        authedGet("/api/v1/profiles/me")
            .andExpect(jsonPath("$.profileComplete").value(true))
    }

    @Test
    fun `PUT is a full replace -- a later PUT without earlier fields clears them`() {
        authedPut(
            "/api/v1/profiles/me",
            mapOf(
                "name" to "First Save",
                "photoUrl" to "https://cdn.crichere.app/photos/x.jpg",
                "state" to "Karnataka",
                "district" to "Bengaluru Urban",
                "city" to "Bengaluru",
                "playingRole" to "BATSMAN",
                "battingStyle" to "RIGHT_HAND",
            ),
        ).andExpect(status().isOk)

        val second = authedPut("/api/v1/profiles/me", mapOf("name" to "Second Save"))
            .andExpect(status().isOk)
            .andReturn()
            .body()

        assertEquals("Second Save", second["name"])
        assertNull(second["photoUrl"])
        assertNull(second["state"])
        assertNull(second["district"])
        assertNull(second["playingRole"])
        assertEquals(false, second["profileComplete"])
    }

    // ---------------------------------------------------------------- role/bowling-style validation

    @Test
    fun `a non-https photo URL is rejected`() {
        authedPut("/api/v1/profiles/me", mapOf("photoUrl" to "http://example.com/me.jpg"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `bowling style supplied for a batsman is rejected with a problem detail`() {
        authedPut(
            "/api/v1/profiles/me",
            mapOf("playingRole" to "BATSMAN", "battingStyle" to "RIGHT_HAND", "bowlingStyle" to "RIGHT_ARM_FAST"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://api.crichere.app/problems/invalid-bowling-style"))
            .andExpect(jsonPath("$.title").value("Invalid bowling style"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_BOWLING_STYLE"))
            .andExpect(jsonPath("$.detail").isNotEmpty)
            .andExpect(jsonPath("$.instance").value("/api/v1/profiles/me"))
    }

    @Test
    fun `a missing bowling style for a bowler is rejected with a problem detail`() {
        authedPut("/api/v1/profiles/me", mapOf("playingRole" to "BOWLER", "battingStyle" to "RIGHT_HAND"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://api.crichere.app/problems/invalid-bowling-style"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_BOWLING_STYLE"))
    }

    @Test
    fun `an all-rounder without a bowling style is rejected`() {
        authedPut("/api/v1/profiles/me", mapOf("playingRole" to "ALL_ROUNDER", "battingStyle" to "RIGHT_HAND"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_BOWLING_STYLE"))
    }

    @Test
    fun `a wicketkeeper needs no bowling style`() {
        authedPut(
            "/api/v1/profiles/me",
            mapOf(
                "name" to "MS Dhoni",
                "photoUrl" to "https://cdn.crichere.app/photos/dhoni.jpg",
                "state" to "Jharkhand",
                "district" to "Ranchi",
                "city" to "Ranchi",
                "playingRole" to "WICKETKEEPER",
                "battingStyle" to "RIGHT_HAND",
            ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.profileComplete").value(true))
    }

    @Test
    fun `a name that is too long fails bean validation with the standard problem detail shape`() {
        authedPut("/api/v1/profiles/me", mapOf("name" to "x".repeat(101)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors.name").isNotEmpty)
    }

    // ---------------------------------------------------------------- auth boundary

    @Test
    fun `profile endpoints require authentication`() {
        mockMvc.perform(get("/api/v1/profiles/me")).andExpect(status().isUnauthorized)
        mockMvc.perform(
            put("/api/v1/profiles/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/api/v1/profiles/me/photo-upload-url")).andExpect(status().isUnauthorized)
    }

    // ---------------------------------------------------------------- photo upload (AWS unconfigured here)

    @Test
    fun `the photo upload endpoint reports 503 when S3 is not configured, not a crash`() {
        authedPost("/api/v1/profiles/me/photo-upload-url")
            .andExpect(status().isServiceUnavailable)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://api.crichere.app/problems/photo-upload-unavailable"))
            .andExpect(jsonPath("$.code").value("PHOTO_UPLOAD_UNAVAILABLE"))
    }

    // ---------------------------------------------------------------- helpers

    private fun authedGet(path: String) =
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"))

    private fun authedPut(path: String, body: Map<String, Any?>) =
        mockMvc.perform(
            put(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

    private fun authedPost(path: String) =
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"))

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

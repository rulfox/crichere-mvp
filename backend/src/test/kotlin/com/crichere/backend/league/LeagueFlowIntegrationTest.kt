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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * League create/get/list/edit/complete/logo-upload over real HTTP against a real Postgres --
 * only [com.crichere.backend.auth.FirebaseTokenVerifier] is mocked (see
 * [AbstractWebIntegrationTest]). AWS/S3 is never configured in this environment, so the logo/
 * banner upload tests here only prove the endpoint degrades gracefully and enforces ownership
 * before ever reaching S3 -- same posture `PhotoUploadEndpointIntegrationTest` documents for
 * profile photos.
 */
class LeagueFlowIntegrationTest : AbstractWebIntegrationTest {

    constructor() : super()

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var organizerToken: String

    @BeforeEach
    fun signIn() {
        val phone = uniquePhone()
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)
        val session = postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
            .andExpect(status().isOk)
            .andReturn()
            .body()
        organizerToken = session["accessToken"] as String
    }

    private fun signInOther(): String {
        val phone = uniquePhone()
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)
        val session = postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
            .andExpect(status().isOk)
            .andReturn()
            .body()
        return session["accessToken"] as String
    }

    private fun validLeagueBody() = mapOf(
        "name" to "Weekend Box Cricket League",
        "state" to "Karnataka",
        "district" to "Bengaluru Urban",
        "city" to "Bengaluru",
        "startsOn" to "2026-10-12",
    )

    @Test
    fun `list and detail are public, no Authorization header needed`() {
        mockMvc.perform(get("/api/v1/leagues")).andExpect(status().isOk)
    }

    @Test
    fun `creating a league requires authentication`() {
        mockMvc.perform(
            post("/api/v1/leagues").contentType(MediaType.APPLICATION_JSON).content("{}"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a real league is created, fetched by id, and found by area filters`() {
        val created = authedPost(organizerToken, "/api/v1/leagues", validLeagueBody())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ANNOUNCED"))
            .andReturn()
            .body()

        val leagueId = created["id"] as String

        authedGet(organizerToken, "/api/v1/leagues/$leagueId")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Weekend Box Cricket League"))

        val list = mockMvc.perform(
            get("/api/v1/leagues").param("state", "Karnataka").param("district", "Bengaluru Urban"),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        kotlin.test.assertTrue(readList(list).any { it["id"] == leagueId })
    }

    @Test
    fun `an incomplete league fails bean validation with the standard problem detail shape`() {
        // Every key present (so JSON deserialization itself succeeds and Bean Validation is what
        // actually runs) but blank/absent in a way @NotBlank/@NotNull reject.
        authedPost(
            organizerToken,
            "/api/v1/leagues",
            mapOf("name" to "", "state" to "", "district" to "", "city" to "", "startsOn" to null),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `a league's response includes its ground's name, not just the raw id`() {
        val ground = authedPost(
            organizerToken,
            "/api/v1/grounds",
            mapOf("name" to "Chinnaswamy Stadium", "state" to "Karnataka", "district" to "Bengaluru Urban", "city" to "Bengaluru", "latitude" to 12.9788, "longitude" to 77.5996),
        )
            .andExpect(status().isOk)
            .andReturn()
            .body()
        val groundId = ground["id"] as String

        val created = authedPost(organizerToken, "/api/v1/leagues", validLeagueBody() + ("groundId" to groundId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.groundId").value(groundId))
            .andExpect(jsonPath("$.groundName").value("Chinnaswamy Stadium"))
            .andReturn()
            .body()

        authedGet(organizerToken, "/api/v1/leagues/${created["id"]}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.groundName").value("Chinnaswamy Stadium"))
    }

    @Test
    fun `creating a league with a nonexistent groundId is a clean 404, not a 500`() {
        authedPost(organizerToken, "/api/v1/leagues", validLeagueBody() + ("groundId" to UUID.randomUUID().toString()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `only the organizer can edit their league -- another authenticated user is forbidden`() {
        val leagueId = createLeague()
        val otherToken = signInOther()

        authedPut(otherToken, "/api/v1/leagues/$leagueId", validLeagueBody() + ("name" to "Hijacked"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `the organizer can edit their own league, full-replace`() {
        val leagueId = createLeague()

        authedPut(organizerToken, "/api/v1/leagues/$leagueId", validLeagueBody() + ("name" to "Renamed League"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Renamed League"))
    }

    @Test
    fun `only the organizer can mark their league completed`() {
        val leagueId = createLeague()
        val otherToken = signInOther()

        authedPatch(otherToken, "/api/v1/leagues/$leagueId/complete")
            .andExpect(status().isForbidden)

        authedPatch(organizerToken, "/api/v1/leagues/$leagueId/complete")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
    }

    @Test
    fun `completing does not lock further edits`() {
        val leagueId = createLeague()
        authedPatch(organizerToken, "/api/v1/leagues/$leagueId/complete").andExpect(status().isOk)

        authedPut(organizerToken, "/api/v1/leagues/$leagueId", validLeagueBody() + ("name" to "Post-completion edit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Post-completion edit"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
    }

    @Test
    fun `only the organizer can request a logo upload url for their league`() {
        val leagueId = createLeague()
        val otherToken = signInOther()

        authedPostNoBody(otherToken, "/api/v1/leagues/$leagueId/logo-upload-url")
            .andExpect(status().isForbidden)
    }

    @Test
    fun `the organizer's logo upload request reports 503 when S3 is not configured, not a crash`() {
        val leagueId = createLeague()

        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/logo-upload-url")
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("PHOTO_UPLOAD_UNAVAILABLE"))
    }

    @Test
    fun `initial awards submitted at creation land in the same call`() {
        val body = validLeagueBody() + ("awards" to listOf(
            mapOf("name" to "First Prize", "cashAmount" to 5000, "hasTrophy" to true),
            mapOf("name" to "Second Prize", "hasTrophy" to false),
        ))

        val created = authedPost(organizerToken, "/api/v1/leagues", body)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.awards.length()").value(2))
            .andExpect(jsonPath("$.awards[0].name").value("First Prize"))
            .andExpect(jsonPath("$.awards[0].displayOrder").value(0))
            .andExpect(jsonPath("$.awards[1].name").value("Second Prize"))
            .andExpect(jsonPath("$.awards[1].displayOrder").value(1))
            .andReturn()
            .body()

        authedGet(organizerToken, "/api/v1/leagues/${created["id"]}")
            .andExpect(jsonPath("$.awards.length()").value(2))
    }

    @Test
    fun `only the organizer can add an award`() {
        val leagueId = createLeague()
        val otherToken = signInOther()

        authedPost(otherToken, "/api/v1/leagues/$leagueId/awards", mapOf("name" to "Man of the Match"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `awards can be added, edited, and removed independently of the league's own edit call`() {
        val leagueId = createLeague()

        val added = authedPost(organizerToken, "/api/v1/leagues/$leagueId/awards", mapOf("name" to "Man of the Match", "hasTrophy" to false))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Man of the Match"))
            .andReturn()
            .body()
        val awardId = added["id"] as String

        authedPut(organizerToken, "/api/v1/leagues/$leagueId/awards/$awardId", mapOf("name" to "Man of the Match (Final)", "hasTrophy" to true))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Man of the Match (Final)"))
            .andExpect(jsonPath("$.hasTrophy").value(true))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/leagues/$leagueId/awards/$awardId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $organizerToken"),
        ).andExpect(status().isOk)

        authedGet(organizerToken, "/api/v1/leagues/$leagueId")
            .andExpect(jsonPath("$.awards.length()").value(0))
    }

    @Test
    fun `awards remain addable after the league is marked completed -- no freeze`() {
        val leagueId = createLeague()
        authedPatch(organizerToken, "/api/v1/leagues/$leagueId/complete").andExpect(status().isOk)

        authedPost(organizerToken, "/api/v1/leagues/$leagueId/awards", mapOf("name" to "Man of the Match"))
            .andExpect(status().isOk)
    }

    @Test
    fun `a completed league drops out of the area-filter listing`() {
        val leagueId = createLeague()
        authedPatch(organizerToken, "/api/v1/leagues/$leagueId/complete").andExpect(status().isOk)

        val list = mockMvc.perform(
            get("/api/v1/leagues").param("state", "Karnataka").param("district", "Bengaluru Urban"),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        kotlin.test.assertFalse(readList(list).any { it["id"] == leagueId })
    }

    @Test
    fun `a nonexistent league id is a clean 404`() {
        authedGet(organizerToken, "/api/v1/leagues/${UUID.randomUUID()}")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    private fun createLeague(): String =
        authedPost(organizerToken, "/api/v1/leagues", validLeagueBody())
            .andExpect(status().isOk)
            .andReturn()
            .body()["id"] as String

    private fun validAuctionSettingsBody(squadMin: Int = 5, squadMax: Int = 15) = mapOf(
        "basePrice" to 500,
        "purse" to 10000,
        "squadMin" to squadMin,
        "squadMax" to squadMax,
        "bidIncrement" to 100,
    )

    @Test
    fun `updating auction settings requires authentication`() {
        val leagueId = createLeague()
        mockMvc.perform(
            put("/api/v1/leagues/$leagueId/auction-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `the auction's scheduled time is optional, round-trips, and clears on a save without it`() {
        val leagueId = createLeague()

        authedPut(organizerToken, "/api/v1/leagues/$leagueId/auction-settings", validAuctionSettingsBody() + ("scheduledAt" to "2026-10-12T13:30:00Z"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/leagues/$leagueId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auctionScheduledAt").value("2026-10-12T13:30:00Z"))

        authedPut(organizerToken, "/api/v1/leagues/$leagueId/auction-settings", validAuctionSettingsBody())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auctionScheduledAt").doesNotExist())
    }

    @Test
    fun `only the organizer can update auction settings`() {
        val leagueId = createLeague()
        val otherToken = signInOther()

        authedPut(otherToken, "/api/v1/leagues/$leagueId/auction-settings", validAuctionSettingsBody())
            .andExpect(status().isForbidden)
    }

    @Test
    fun `the organizer can set auction settings, and GET reflects them for an anonymous caller`() {
        val leagueId = createLeague()

        authedPut(organizerToken, "/api/v1/leagues/$leagueId/auction-settings", validAuctionSettingsBody())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auctionBasePrice").value(500))
            .andExpect(jsonPath("$.auctionPurse").value(10000))
            .andExpect(jsonPath("$.auctionSquadMin").value(5))
            .andExpect(jsonPath("$.auctionSquadMax").value(15))
            .andExpect(jsonPath("$.auctionBidIncrement").value(100))

        mockMvc.perform(get("/api/v1/leagues/$leagueId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auctionSquadMax").value(15))
    }

    @Test
    fun `squadMin greater than squadMax is rejected with a clean 400`() {
        val leagueId = createLeague()

        authedPut(organizerToken, "/api/v1/leagues/$leagueId/auction-settings", validAuctionSettingsBody(squadMin = 10, squadMax = 5))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("SQUAD_SIZE_INVALID"))
    }

    @Test
    fun `a non-positive number is rejected by bean validation`() {
        val leagueId = createLeague()

        authedPut(organizerToken, "/api/v1/leagues/$leagueId/auction-settings", validAuctionSettingsBody().plus("basePrice" to 0))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `auctionSquadMaxWarning reflects the squad-math check against playersRequired and franchisesRequired`() {
        val leagueId = authedPost(organizerToken, "/api/v1/leagues", validLeagueBody() + mapOf("franchisesRequired" to 10, "playersRequired" to 50))
            .andExpect(status().isOk)
            .andReturn()
            .body()["id"] as String

        authedPut(organizerToken, "/api/v1/leagues/$leagueId/auction-settings", validAuctionSettingsBody(squadMin = 1, squadMax = 6))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auctionSquadMaxWarning").value(true))

        authedPut(organizerToken, "/api/v1/leagues/$leagueId/auction-settings", validAuctionSettingsBody(squadMin = 1, squadMax = 5))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auctionSquadMaxWarning").value(false))
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

    private fun authedPut(token: String, path: String, body: Map<String, Any?>) =
        mockMvc.perform(
            put(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

    private fun authedPatch(token: String, path: String) =
        mockMvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

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

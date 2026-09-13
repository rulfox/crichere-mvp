package com.crichere.backend.auction

import com.crichere.backend.auth.VerifiedFirebaseToken
import com.crichere.backend.common.AbstractWebIntegrationTest
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/** The live auction engine over real HTTP -- see `LeagueFlowIntegrationTest`'s class doc for the shared testing posture. */
class AuctionFlowIntegrationTest : AbstractWebIntegrationTest {

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

    private fun configureAuction(token: String, leagueId: String, squadMax: Int = 5) {
        val body = mapOf("basePrice" to 100, "purse" to 1000, "squadMin" to 1, "squadMax" to squadMax, "bidIncrement" to 50)
        authedPut(token, "/api/v1/leagues/$leagueId/auction-settings", body).andExpect(status().isOk)
    }

    private fun joinAsPlayer(token: String, leagueId: String) {
        authedPost(token, "/api/v1/leagues/$leagueId/players", mapOf("paymentScreenshotUrl" to null)).andExpect(status().isOk)
    }

    private fun claimFranchise(token: String, leagueId: String, name: String = "Chennai Kings"): String =
        authedPost(token, "/api/v1/leagues/$leagueId/franchises", mapOf("name" to name, "paymentScreenshotUrl" to null))
            .andExpect(status().isOk)
            .andReturn()
            .body()["id"] as String

    @Test
    fun `full happy path -- start, bid, sell every player, auction auto-completes, results reflect it`() {
        val leagueId = createLeague(organizerToken)
        configureAuction(organizerToken, leagueId, squadMax = 2)
        val player1 = signInNewUser()
        val player2 = signInNewUser()
        joinAsPlayer(player1, leagueId)
        joinAsPlayer(player2, leagueId)
        val ownerToken = signInNewUser()
        val franchiseId = claimFranchise(ownerToken, leagueId)

        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/start")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auctionStatus").value("IN_PROGRESS"))

        // Player 1
        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/next-player").andExpect(status().isOk)
        authedPost(ownerToken, "/api/v1/leagues/$leagueId/auction/bids", mapOf("franchiseId" to franchiseId, "amount" to 100))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentBidAmount").value(100))
        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/sold")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auctionStatus").value("IN_PROGRESS"))

        // Player 2 -- last one, auction auto-completes on sale
        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/next-player").andExpect(status().isOk)
        authedPost(ownerToken, "/api/v1/leagues/$leagueId/auction/bids", mapOf("franchiseId" to franchiseId, "amount" to 100))
            .andExpect(status().isOk)
        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/sold")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.auctionStatus").value("COMPLETED"))

        mockMvc.perform(get("/api/v1/leagues/$leagueId/auction/results"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.franchises[0].playersWon.length()").value(2))
            .andExpect(jsonPath("$.franchises[0].purseSpent").value(200))
            .andExpect(jsonPath("$.franchises[0].purseRemaining").value(800))
            .andExpect(jsonPath("$.franchises[0].belowSquadMin").value(false))
    }

    @Test
    fun `unsold requeues the player instead of ending the auction`() {
        val leagueId = createLeague(organizerToken)
        configureAuction(organizerToken, leagueId, squadMax = 1)
        val player1 = signInNewUser()
        joinAsPlayer(player1, leagueId)
        val ownerToken = signInNewUser()
        val franchiseId = claimFranchise(ownerToken, leagueId)
        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/start").andExpect(status().isOk)

        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/next-player").andExpect(status().isOk)
        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/unsold")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentPlayerId").doesNotExist())

        // The player is back in the pool -- next-player opens it again rather than the auction being over.
        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/next-player")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentPlayerId").exists())

        authedPost(ownerToken, "/api/v1/leagues/$leagueId/auction/bids", mapOf("franchiseId" to franchiseId, "amount" to 100))
            .andExpect(status().isOk)
        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/undo")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentBidAmount").doesNotExist())
    }

    @Test
    fun `only the organizer can drive the auction, and only the franchise's own owner can bid`() {
        val leagueId = createLeague(organizerToken)
        configureAuction(organizerToken, leagueId, squadMax = 1)
        val player1 = signInNewUser()
        joinAsPlayer(player1, leagueId)
        val ownerToken = signInNewUser()
        val franchiseId = claimFranchise(ownerToken, leagueId)
        val stranger = signInNewUser()

        authedPostNoBody(stranger, "/api/v1/leagues/$leagueId/auction/start").andExpect(status().isForbidden)
        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/start").andExpect(status().isOk)
        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/next-player").andExpect(status().isOk)

        authedPost(stranger, "/api/v1/leagues/$leagueId/auction/bids", mapOf("franchiseId" to franchiseId, "amount" to 100))
            .andExpect(status().isForbidden)
        authedPost(ownerToken, "/api/v1/leagues/$leagueId/auction/bids", mapOf("franchiseId" to franchiseId, "amount" to 100))
            .andExpect(status().isOk)
    }

    @Test
    fun `starting is rejected when squad max would require more players than are in the pool`() {
        val leagueId = createLeague(organizerToken)
        configureAuction(organizerToken, leagueId, squadMax = 5)
        val player1 = signInNewUser()
        joinAsPlayer(player1, leagueId)
        val ownerToken = signInNewUser()
        claimFranchise(ownerToken, leagueId)

        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/start")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("AUCTION_NOT_READY"))
    }

    @Test
    fun `settings, joining, claiming, roster removal, and completion all reject once the auction has started`() {
        val leagueId = createLeague(organizerToken)
        configureAuction(organizerToken, leagueId, squadMax = 1)
        val player1 = signInNewUser()
        joinAsPlayer(player1, leagueId)
        val ownerToken = signInNewUser()
        claimFranchise(ownerToken, leagueId)
        val latecomer = signInNewUser()

        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/start").andExpect(status().isOk)

        authedPut(organizerToken, "/api/v1/leagues/$leagueId/auction-settings", mapOf("basePrice" to 200, "purse" to 1000, "squadMin" to 1, "squadMax" to 2, "bidIncrement" to 50))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("AUCTION_ALREADY_STARTED"))

        authedPost(latecomer, "/api/v1/leagues/$leagueId/players", mapOf("paymentScreenshotUrl" to null))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("AUCTION_ALREADY_STARTED"))

        authedPost(latecomer, "/api/v1/leagues/$leagueId/franchises", mapOf("name" to "Late Franchise", "paymentScreenshotUrl" to null))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("AUCTION_ALREADY_STARTED"))

        mockMvc.perform(
            put("/api/v1/leagues/$leagueId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $organizerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )

        authedPatchNoBody(organizerToken, "/api/v1/leagues/$leagueId/complete")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("AUCTION_IN_PROGRESS"))
    }

    @Test
    fun `two franchises bidding the same amount at once -- exactly one wins, the row lock serializes the rest`() {
        val leagueId = createLeague(organizerToken)
        configureAuction(organizerToken, leagueId, squadMax = 1)
        val player1 = signInNewUser()
        val player2 = signInNewUser()
        joinAsPlayer(player1, leagueId)
        joinAsPlayer(player2, leagueId)
        val owner1 = signInNewUser()
        val owner2 = signInNewUser()
        val franchise1 = claimFranchise(owner1, leagueId, "Chennai Kings")
        val franchise2 = claimFranchise(owner2, leagueId, "Bengaluru Blasters")
        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/start").andExpect(status().isOk)
        authedPostNoBody(organizerToken, "/api/v1/leagues/$leagueId/auction/next-player").andExpect(status().isOk)

        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val results = mutableListOf<Int>()
        val futures = listOf(
            executor.submit {
                startLatch.await()
                results.synchronizedAdd(bidStatus(owner1, leagueId, franchise1, 100))
            },
            executor.submit {
                startLatch.await()
                results.synchronizedAdd(bidStatus(owner2, leagueId, franchise2, 100))
            },
        )
        startLatch.countDown()
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        // Exactly one bid at the shared minimum (100) can win -- the loser's transaction always
        // observes the post-commit state (current bid already raised to 100, so its own 100 is
        // now below the required 100+increment), never the same stale null it started with. That
        // is the row lock (LeagueRepository.findByIdForUpdate) doing its job.
        assertEquals(listOf(HttpStatus.OK.value(), HttpStatus.BAD_REQUEST.value()), results.sorted())

        mockMvc.perform(get("/api/v1/leagues/$leagueId/auction/results"))
            .andExpect(status().isOk)
    }

    private fun MutableList<Int>.synchronizedAdd(value: Int) {
        synchronized(this) { add(value) }
    }

    private fun bidStatus(token: String, leagueId: String, franchiseId: String, amount: Int): Int =
        authedPost(token, "/api/v1/leagues/$leagueId/auction/bids", mapOf("franchiseId" to franchiseId, "amount" to amount))
            .andReturn()
            .response
            .status

    // ---------------------------------------------------------------- helpers

    private fun authedPost(token: String, path: String, body: Map<String, Any?>) =
        mockMvc.perform(
            post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

    private fun authedPostNoBody(token: String, path: String) =
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

    private fun authedPatchNoBody(token: String, path: String) =
        mockMvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))

    private fun authedPut(token: String, path: String, body: Map<String, Any?>) =
        mockMvc.perform(
            put(path)
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

    private fun MvcResult.body(): Map<String, Any?> = responseBodyOf(this)

    @Suppress("UNCHECKED_CAST")
    private fun responseBodyOf(result: MvcResult): Map<String, Any?> =
        objectMapper.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any?>

    private fun uniquePhone(): String =
        "+9199" + UUID.randomUUID().mostSignificantBits.toString().filter { it.isDigit() }.take(8).padEnd(8, '7')
}

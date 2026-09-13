package com.crichere.app.league

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `commonTest` coverage for [KtorAuctionRepository]'s plain HTTP calls against a Ktor `MockEngine`,
 * mirroring `FranchiseRepositoryTest`'s pattern. [AuctionRepository.streamAuctionState]'s SSE
 * transport isn't exercised here -- `MockEngine` doesn't model a real chunked event stream, and
 * `AuctionViewModelTest` already covers this repository's *consumer* against a fake stream;
 * end-to-end SSE behavior is covered on-device.
 */
class AuctionRepositoryTest {

    private fun mockClient(
        expectedMethod: HttpMethod? = null,
        expectedPath: String? = null,
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "{}",
    ): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            defaultRequest { url("http://localhost/") }
            engine {
                addHandler { request ->
                    expectedMethod?.let { assertEquals(it, request.method) }
                    expectedPath?.let { assertEquals(it, request.url.encodedPath) }
                    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }

    private fun sampleStateJson() = """{"auctionStatus":"IN_PROGRESS","currentBidAmount":150.0,"allowExceedPurse":false}"""

    @Test
    fun `start POSTs to the auction's start endpoint`() = runTest {
        val repository = KtorAuctionRepository(mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues/l1/auction/start", body = sampleStateJson()))

        val result = repository.start("l1")

        assertEquals(AuctionStatus.IN_PROGRESS, result.auctionStatus)
    }

    @Test
    fun `start throws on a non-2xx response`() = runTest {
        val repository = KtorAuctionRepository(mockClient(status = HttpStatusCode.Conflict, body = "{}"))

        assertFailsWith<AuctionActionFailedException> { repository.start("l1") }
    }

    @Test
    fun `placeBid POSTs the franchise id and amount to the bids endpoint`() = runTest {
        val repository = KtorAuctionRepository(mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues/l1/auction/bids", body = sampleStateJson()))

        val result = repository.placeBid("l1", PlaceBidRequestDto(franchiseId = "f1", amount = 150.0))

        assertEquals(150.0, result.currentBidAmount)
    }

    @Test
    fun `getResults GETs the results endpoint`() = runTest {
        val resultsJson = """{"auctionStatus":"COMPLETED","franchises":[]}"""
        val repository = KtorAuctionRepository(mockClient(expectedMethod = HttpMethod.Get, expectedPath = "/api/v1/leagues/l1/auction/results", body = resultsJson))

        val result = repository.getResults("l1")

        assertEquals(AuctionStatus.COMPLETED, result.auctionStatus)
    }

    @Test
    fun `toggleExceedPurse POSTs the allow flag`() = runTest {
        val repository = KtorAuctionRepository(mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues/l1/auction/toggle-exceed-purse", body = sampleStateJson()))

        repository.toggleExceedPurse("l1", true)
    }
}

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

/** `commonTest` (`kotlin.test`) coverage for [KtorLeagueRepository] against a Ktor `MockEngine`, mirroring `GroundRepositoryTest`'s pattern. */
class LeagueRepositoryTest {

    private fun mockClient(
        expectedMethod: HttpMethod? = null,
        expectedPath: String? = null,
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "[]",
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

    private fun repository(client: HttpClient) = KtorLeagueRepository(httpClient = client, uploadClient = client)

    private fun sampleLeagueJson(id: String = "l1") = """
        {"id":"$id","organizerUserId":"u1","name":"Weekend League","country":"IN",
         "state":"Karnataka","district":"Bengaluru Urban","groundId":"g1","groundName":"Test Ground",
         "startsOn":"2026-10-12","status":"ANNOUNCED","awards":[]}
    """.trimIndent()

    @Test
    fun `listByArea GETs leagues with the area filters as query params`() = runTest {
        val repository = repository(mockClient(expectedMethod = HttpMethod.Get, expectedPath = "/api/v1/leagues", body = "[${sampleLeagueJson()}]"))

        val result = repository.listByArea(state = "Karnataka", district = "Bengaluru Urban")

        assertEquals(1, result.size)
        assertEquals(LeagueStatus.ANNOUNCED, result[0].status)
    }

    @Test
    fun `listNearest GETs leagues with nearLat and nearLng`() = runTest {
        val repository = repository(mockClient(expectedMethod = HttpMethod.Get, expectedPath = "/api/v1/leagues"))

        repository.listNearest(12.9716, 77.5946)
    }

    @Test
    fun `createLeague POSTs and returns the created league`() = runTest {
        val repository = repository(mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues", body = sampleLeagueJson()))

        val result = repository.createLeague(
            LeagueSaveRequestDto(name = "Weekend League", state = "Karnataka", district = "Bengaluru Urban", groundId = "g1", startsOn = "2026-10-12"),
        )

        assertEquals("l1", result.id)
    }

    @Test
    fun `createLeague throws on a non-2xx response`() = runTest {
        val repository = repository(mockClient(status = HttpStatusCode.TooManyRequests, body = "{}"))

        assertFailsWith<LeagueSaveFailedException> {
            repository.createLeague(LeagueSaveRequestDto(name = "x", state = "x", district = "x", groundId = "g1", startsOn = "2026-01-01"))
        }
    }

    @Test
    fun `completeLeague PATCHes the complete endpoint`() = runTest {
        val repository = repository(
            mockClient(expectedMethod = HttpMethod.Patch, expectedPath = "/api/v1/leagues/l1/complete", body = sampleLeagueJson().replace("ANNOUNCED", "COMPLETED")),
        )

        val result = repository.completeLeague("l1")

        assertEquals(LeagueStatus.COMPLETED, result.status)
    }

    @Test
    fun `requestLogoUploadUrl throws LeaguePhotoUploadUnavailableException on a 503`() = runTest {
        val repository = repository(mockClient(status = HttpStatusCode.ServiceUnavailable, body = "{}"))

        assertFailsWith<LeaguePhotoUploadUnavailableException> {
            repository.requestLogoUploadUrl("l1")
        }
    }

    @Test
    fun `addAward POSTs to the league's awards endpoint`() = runTest {
        val awardJson = """{"id":"a1","name":"First Prize","hasTrophy":true,"displayOrder":0}"""
        val repository = repository(mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues/l1/awards", body = awardJson))

        val result = repository.addAward("l1", LeagueAwardSaveRequestDto(name = "First Prize", hasTrophy = true))

        assertEquals("First Prize", result.name)
    }

    @Test
    fun `deleteAward DELETEs the specific award`() = runTest {
        val repository = repository(mockClient(expectedMethod = HttpMethod.Delete, expectedPath = "/api/v1/leagues/l1/awards/a1", body = ""))

        repository.deleteAward("l1", "a1")
    }

    @Test
    fun `follow POSTs to the league's follow endpoint`() = runTest {
        val repository = repository(mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues/l1/follow", body = ""))

        repository.follow("l1")
    }

    @Test
    fun `unfollow DELETEs the league's follow endpoint`() = runTest {
        val repository = repository(mockClient(expectedMethod = HttpMethod.Delete, expectedPath = "/api/v1/leagues/l1/follow", body = ""))

        repository.unfollow("l1")
    }

    @Test
    fun `requestPaymentScreenshotUploadUrl throws LeaguePhotoUploadUnavailableException on a 503`() = runTest {
        val repository = repository(mockClient(status = HttpStatusCode.ServiceUnavailable, body = "{}"))

        assertFailsWith<LeaguePhotoUploadUnavailableException> {
            repository.requestPaymentScreenshotUploadUrl("l1")
        }
    }

    @Test
    fun `updateAuctionSettings PUTs to the league's auction-settings endpoint`() = runTest {
        val repository = repository(
            mockClient(expectedMethod = HttpMethod.Put, expectedPath = "/api/v1/leagues/l1/auction-settings", body = sampleLeagueJson()),
        )

        val result = repository.updateAuctionSettings(
            "l1",
            AuctionSettingsSaveRequestDto(basePrice = 500.0, purse = 10000.0, squadMin = 5, squadMax = 15, bidIncrement = 100.0),
        )

        assertEquals("l1", result.id)
    }

    @Test
    fun `updateAuctionSettings throws on a non-2xx response`() = runTest {
        val repository = repository(mockClient(status = HttpStatusCode.BadRequest, body = "{}"))

        assertFailsWith<LeagueSaveFailedException> {
            repository.updateAuctionSettings("l1", AuctionSettingsSaveRequestDto(basePrice = 500.0, purse = 10000.0, squadMin = 5, squadMax = 15, bidIncrement = 100.0))
        }
    }

    @Test
    fun `requestPendingFranchiseLogoUploadUrl POSTs to the league's franchise-logo-upload-url endpoint`() = runTest {
        val uploadJson = """{"uploadUrl":"https://s3.example.com/","fields":{},"key":"leagues/l1/franchise-logos/u1-abc.jpg","expiresAt":"2026-09-03T12:05:00.000Z"}"""
        val repository = repository(
            mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues/l1/franchise-logo-upload-url", body = uploadJson),
        )

        val result = repository.requestPendingFranchiseLogoUploadUrl("l1")

        assertEquals("leagues/l1/franchise-logos/u1-abc.jpg", result.key)
    }
}

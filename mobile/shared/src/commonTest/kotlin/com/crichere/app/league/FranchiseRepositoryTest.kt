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

/** `commonTest` coverage for [KtorFranchiseRepository] against a Ktor `MockEngine`, mirroring `LeagueRepositoryTest`'s pattern. */
class FranchiseRepositoryTest {

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

    private fun sampleFranchiseJson() = """{"id":"f1","ownerUserId":"u1","ownerName":"Aswin","name":"Chennai Kings","joinedAt":"2026-09-12T00:00:00Z"}"""

    @Test
    fun `claim POSTs to the league's franchises endpoint and returns the created row`() = runTest {
        val repository = KtorFranchiseRepository(mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues/l1/franchises", body = sampleFranchiseJson()))

        val result = repository.claim("l1", LeagueFranchiseClaimRequestDto(name = "Chennai Kings"))

        assertEquals("Chennai Kings", result.name)
    }

    @Test
    fun `claim throws on a non-2xx response`() = runTest {
        val repository = KtorFranchiseRepository(mockClient(status = HttpStatusCode.Conflict, body = "{}"))

        assertFailsWith<LeagueFranchiseActionFailedException> {
            repository.claim("l1", LeagueFranchiseClaimRequestDto(name = "Chennai Kings"))
        }
    }

    @Test
    fun `remove DELETEs the specific franchise`() = runTest {
        val repository = KtorFranchiseRepository(mockClient(expectedMethod = HttpMethod.Delete, expectedPath = "/api/v1/leagues/l1/franchises/f1", body = ""))

        repository.remove("l1", "f1")
    }

    @Test
    fun `requestFranchiseLogoUploadUrl POSTs to the franchise's logo-upload-url endpoint`() = runTest {
        val uploadJson = """{"uploadUrl":"https://s3.example.com/","fields":{},"key":"franchises/f1/logo.jpg","expiresAt":"2026-09-03T12:05:00.000Z"}"""
        val repository = KtorFranchiseRepository(mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues/l1/franchises/f1/logo-upload-url", body = uploadJson))

        val result = repository.requestFranchiseLogoUploadUrl("l1", "f1")

        assertEquals("franchises/f1/logo.jpg", result.key)
    }
}

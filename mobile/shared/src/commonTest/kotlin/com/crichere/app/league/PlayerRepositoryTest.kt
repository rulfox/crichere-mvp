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

/** `commonTest` coverage for [KtorPlayerRepository] against a Ktor `MockEngine`, mirroring `LeagueRepositoryTest`'s pattern. */
class PlayerRepositoryTest {

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

    private fun samplePlayerJson() = """{"id":"p1","userId":"u1","name":"Aswin","joinedAt":"2026-09-12T00:00:00Z"}"""

    @Test
    fun `join POSTs to the league's players endpoint and returns the created row`() = runTest {
        val repository = KtorPlayerRepository(mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues/l1/players", body = samplePlayerJson()))

        val result = repository.join("l1", LeaguePlayerJoinRequestDto())

        assertEquals("p1", result.id)
    }

    @Test
    fun `join throws on a non-2xx response`() = runTest {
        val repository = KtorPlayerRepository(mockClient(status = HttpStatusCode.Conflict, body = "{}"))

        assertFailsWith<LeaguePlayerActionFailedException> {
            repository.join("l1", LeaguePlayerJoinRequestDto())
        }
    }

    @Test
    fun `remove DELETEs the specific player`() = runTest {
        val repository = KtorPlayerRepository(mockClient(expectedMethod = HttpMethod.Delete, expectedPath = "/api/v1/leagues/l1/players/p1", body = ""))

        repository.remove("l1", "p1")
    }

    @Test
    fun `requestLeave POSTs to the leave-request endpoint`() = runTest {
        val repository = KtorPlayerRepository(mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues/l1/players/p1/leave-request", body = samplePlayerJson()))

        repository.requestLeave("l1", "p1")
    }

    @Test
    fun `approveLeave POSTs to the leave-request approve endpoint`() = runTest {
        val repository = KtorPlayerRepository(mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues/l1/players/p1/leave-request/approve", body = samplePlayerJson()))

        repository.approveLeave("l1", "p1")
    }

    @Test
    fun `dismissLeave POSTs to the leave-request dismiss endpoint`() = runTest {
        val repository = KtorPlayerRepository(mockClient(expectedMethod = HttpMethod.Post, expectedPath = "/api/v1/leagues/l1/players/p1/leave-request/dismiss", body = samplePlayerJson()))

        repository.dismissLeave("l1", "p1")
    }
}

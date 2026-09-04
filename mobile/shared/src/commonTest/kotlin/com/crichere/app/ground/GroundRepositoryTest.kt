package com.crichere.app.ground

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `commonTest` (`kotlin.test`) coverage for [KtorGroundRepository] against a Ktor `MockEngine`, mirroring `ReferenceRepositoryTest`'s pattern. */
class GroundRepositoryTest {

    private fun mockClient(
        expectedPath: String = "/api/v1/grounds",
        expectedQuery: Map<String, String> = emptyMap(),
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "[]",
    ): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            defaultRequest { url("http://localhost/") }
            engine {
                addHandler { request ->
                    assertEquals(expectedPath, request.url.encodedPath)
                    expectedQuery.forEach { (key, value) -> assertEquals(value, request.url.parameters[key]) }
                    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }

    @Test
    fun `search sends every provided filter as a query param`() = runTest {
        val repository = KtorGroundRepository(
            mockClient(expectedQuery = mapOf("search" to "mrf", "state" to "Tamil Nadu", "district" to "Chennai", "city" to "Chennai")),
        )

        repository.search(search = "mrf", state = "Tamil Nadu", district = "Chennai", city = "Chennai")
    }

    @Test
    fun `search parses a real-shaped backend response`() = runTest {
        val json = """
            [{"id":"g1","name":"MRF Ground","state":"Tamil Nadu","district":"Chennai","city":"Chennai","latitude":13.0827,"longitude":80.2707}]
        """.trimIndent()
        val repository = KtorGroundRepository(mockClient(body = json))

        val results = repository.search()

        assertEquals(1, results.size)
        assertEquals("MRF Ground", results[0].name)
        assertEquals(13.0827, results[0].latitude)
    }

    @Test
    fun `registerGround posts the request and returns the created ground`() = runTest {
        val json = """{"id":"g1","name":"New Ground","state":"Karnataka","district":"Bengaluru Urban","city":"Bengaluru","latitude":12.9716,"longitude":77.5946}"""
        val repository = KtorGroundRepository(mockClient(body = json))

        val result = repository.registerGround(
            GroundCreateRequestDto(name = "New Ground", state = "Karnataka", district = "Bengaluru Urban", city = "Bengaluru", latitude = 12.9716, longitude = 77.5946),
        )

        assertEquals("g1", result.id)
    }

    @Test
    fun `registerGround throws on a non-2xx response`() = runTest {
        val repository = KtorGroundRepository(mockClient(status = HttpStatusCode.BadRequest, body = "{}"))

        assertFailsWith<GroundSaveFailedException> {
            repository.registerGround(GroundCreateRequestDto(name = "x", state = "x", district = "x", city = "x", latitude = 0.0, longitude = 0.0))
        }
    }

    @Test
    fun `search returns an empty list when nothing matches`() = runTest {
        val repository = KtorGroundRepository(mockClient(body = "[]"))

        assertTrue(repository.search(search = "nonexistent").isEmpty())
    }
}

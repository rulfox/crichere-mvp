package com.crichere.app.reference

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

/**
 * `commonTest` (`kotlin.test`) coverage for the one piece of real logic Task 5 adds:
 * [KtorReferenceRepository] parsing a real-shaped JSON response into [StateDto]s. Uses Ktor's
 * `MockEngine` rather than hitting a real backend -- this is a unit test of the
 * repository/serialization wiring, not a replacement for the real-emulator/real-backend
 * verification described in task-5-report.md.
 */
class ReferenceRepositoryTest {

    private fun mockClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = STATES_JSON,
    ): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            defaultRequest {
                url("http://localhost/")
            }
            engine {
                addHandler { request ->
                    assertEquals("/api/v1/reference/states", request.url.encodedPath)
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }

    @Test
    fun `getStates parses a real-shaped backend response`() = runTest {
        val repository = KtorReferenceRepository(mockClient())

        val states = repository.getStates()

        assertEquals(3, states.size)
        assertEquals(StateDto(code = "KA", name = "Karnataka"), states[0])
        assertEquals(StateDto(code = "MH", name = "Maharashtra"), states[1])
        assertEquals(StateDto(code = "TN", name = "Tamil Nadu"), states[2])
    }

    @Test
    fun `getStates returns an empty list when the backend has none seeded`() = runTest {
        val repository = KtorReferenceRepository(mockClient(body = "[]"))

        val states = repository.getStates()

        assertTrue(states.isEmpty())
    }

    @Test
    fun `getStates propagates a server error instead of silently swallowing it`() = runTest {
        val repository = KtorReferenceRepository(
            mockClient(status = HttpStatusCode.InternalServerError, body = "{}"),
        )

        assertFailsWith<Exception> { repository.getStates() }
    }

    private companion object {
        val STATES_JSON = """
            [
                {"code":"KA","name":"Karnataka"},
                {"code":"MH","name":"Maharashtra"},
                {"code":"TN","name":"Tamil Nadu"}
            ]
        """.trimIndent()
    }
}

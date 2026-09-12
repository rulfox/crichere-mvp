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

/** `commonTest` coverage for [KtorMyLeaguesRepository] against a Ktor `MockEngine`, mirroring `LeagueRepositoryTest`'s pattern. */
class MyLeaguesRepositoryTest {

    @Test
    fun `getMyLeagues GETs the me leagues endpoint and parses all four lists`() = runTest {
        val body = """
            {"organizing":[{"id":"l1","name":"Organized","city":"Bengaluru","state":"Karnataka","startsOn":"2026-10-12","status":"ANNOUNCED"}],
             "playing":[], "franchiseOwner":[], "following":[]}
        """.trimIndent()
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            defaultRequest { url("http://localhost/") }
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/api/v1/me/leagues", request.url.encodedPath)
                    respond(content = body, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        val repository = KtorMyLeaguesRepository(client)

        val result = repository.getMyLeagues()

        assertEquals(1, result.organizing.size)
        assertEquals("Organized", result.organizing[0].name)
    }
}

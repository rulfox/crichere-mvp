package com.crichere.app.league

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** `commonMain` use case for the My Leagues screen's four lists (see docs/PHASE3.md). A single-method repository since `GET /api/v1/me/leagues` is its own resource root, distinct from `LeagueRepository`'s `/api/v1/leagues`. */
interface MyLeaguesRepository {
    suspend fun getMyLeagues(): MyLeaguesDto
}

internal class KtorMyLeaguesRepository(
    private val httpClient: HttpClient,
) : MyLeaguesRepository {

    override suspend fun getMyLeagues(): MyLeaguesDto =
        httpClient.get("/api/v1/me/leagues").body()
}

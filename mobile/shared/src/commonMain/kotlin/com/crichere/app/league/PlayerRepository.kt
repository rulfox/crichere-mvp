package com.crichere.app.league

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Thrown by [PlayerRepository]'s join/remove/leave-request calls for anything other than a clean 2xx. */
class LeaguePlayerActionFailedException(message: String) : Exception(message)

/** `commonMain` use cases for joining a league as a player, and the request-and-approve leave flow (see docs/PHASE3.md). Follows [LeagueRepository]'s established interface+`Ktor*Impl` pattern. */
interface PlayerRepository {
    suspend fun join(leagueId: String, request: LeaguePlayerJoinRequestDto): LeaguePlayerDto

    suspend fun remove(leagueId: String, playerId: String)

    suspend fun requestLeave(leagueId: String, playerId: String): LeaguePlayerDto

    suspend fun approveLeave(leagueId: String, playerId: String): LeaguePlayerDto

    suspend fun dismissLeave(leagueId: String, playerId: String): LeaguePlayerDto
}

internal class KtorPlayerRepository(
    private val httpClient: HttpClient,
) : PlayerRepository {

    override suspend fun join(leagueId: String, request: LeaguePlayerJoinRequestDto): LeaguePlayerDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/players") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw LeaguePlayerActionFailedException("Join failed with status ${response.status}")
        return response.body()
    }

    override suspend fun remove(leagueId: String, playerId: String) {
        val response = httpClient.delete("/api/v1/leagues/$leagueId/players/$playerId")
        if (!response.status.isSuccess()) throw LeaguePlayerActionFailedException("Remove failed with status ${response.status}")
    }

    override suspend fun requestLeave(leagueId: String, playerId: String): LeaguePlayerDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/players/$playerId/leave-request")
        if (!response.status.isSuccess()) throw LeaguePlayerActionFailedException("Leave request failed with status ${response.status}")
        return response.body()
    }

    override suspend fun approveLeave(leagueId: String, playerId: String): LeaguePlayerDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/players/$playerId/leave-request/approve")
        if (!response.status.isSuccess()) throw LeaguePlayerActionFailedException("Approve leave failed with status ${response.status}")
        return response.body()
    }

    override suspend fun dismissLeave(leagueId: String, playerId: String): LeaguePlayerDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/players/$playerId/leave-request/dismiss")
        if (!response.status.isSuccess()) throw LeaguePlayerActionFailedException("Dismiss leave failed with status ${response.status}")
        return response.body()
    }
}

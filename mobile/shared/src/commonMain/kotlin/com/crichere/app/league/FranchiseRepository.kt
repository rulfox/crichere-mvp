package com.crichere.app.league

import com.crichere.app.upload.PhotoUploadInfoDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Thrown by [FranchiseRepository]'s claim/remove/leave-request calls for anything other than a clean 2xx. */
class LeagueFranchiseActionFailedException(message: String) : Exception(message)

/** `commonMain` use cases for claiming a franchise in a league, and its request-and-approve leave flow (see docs/PHASE3.md). Follows [LeagueRepository]'s established interface+`Ktor*Impl` pattern. */
interface FranchiseRepository {
    suspend fun claim(leagueId: String, request: LeagueFranchiseClaimRequestDto): LeagueFranchiseDto

    suspend fun remove(leagueId: String, franchiseId: String)

    suspend fun requestLeave(leagueId: String, franchiseId: String): LeagueFranchiseDto

    suspend fun approveLeave(leagueId: String, franchiseId: String): LeagueFranchiseDto

    suspend fun dismissLeave(leagueId: String, franchiseId: String): LeagueFranchiseDto

    /** Throws [LeagueFranchiseActionFailedException] wrapping a `503` -- same posture as [LeagueRepository.requestLogoUploadUrl]. */
    suspend fun requestFranchiseLogoUploadUrl(leagueId: String, franchiseId: String): PhotoUploadInfoDto
}

internal class KtorFranchiseRepository(
    private val httpClient: HttpClient,
) : FranchiseRepository {

    override suspend fun claim(leagueId: String, request: LeagueFranchiseClaimRequestDto): LeagueFranchiseDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/franchises") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw LeagueFranchiseActionFailedException("Claim failed with status ${response.status}")
        return response.body()
    }

    override suspend fun remove(leagueId: String, franchiseId: String) {
        val response = httpClient.delete("/api/v1/leagues/$leagueId/franchises/$franchiseId")
        if (!response.status.isSuccess()) throw LeagueFranchiseActionFailedException("Remove failed with status ${response.status}")
    }

    override suspend fun requestLeave(leagueId: String, franchiseId: String): LeagueFranchiseDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/franchises/$franchiseId/leave-request")
        if (!response.status.isSuccess()) throw LeagueFranchiseActionFailedException("Leave request failed with status ${response.status}")
        return response.body()
    }

    override suspend fun approveLeave(leagueId: String, franchiseId: String): LeagueFranchiseDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/franchises/$franchiseId/leave-request/approve")
        if (!response.status.isSuccess()) throw LeagueFranchiseActionFailedException("Approve leave failed with status ${response.status}")
        return response.body()
    }

    override suspend fun dismissLeave(leagueId: String, franchiseId: String): LeagueFranchiseDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/franchises/$franchiseId/leave-request/dismiss")
        if (!response.status.isSuccess()) throw LeagueFranchiseActionFailedException("Dismiss leave failed with status ${response.status}")
        return response.body()
    }

    override suspend fun requestFranchiseLogoUploadUrl(leagueId: String, franchiseId: String): PhotoUploadInfoDto {
        val response: HttpResponse = httpClient.post("/api/v1/leagues/$leagueId/franchises/$franchiseId/logo-upload-url")
        if (response.status == HttpStatusCode.ServiceUnavailable) throw LeagueFranchiseActionFailedException("Logo upload is unavailable right now.")
        if (!response.status.isSuccess()) throw LeagueFranchiseActionFailedException("Logo upload URL request failed with status ${response.status}")
        return response.body()
    }
}

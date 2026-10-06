package com.crichere.app.league

import com.crichere.app.network.problemCode
import com.crichere.app.upload.PhotoUploadInfoDto
import com.crichere.app.upload.uploadPhotoViaPresignedPost
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Thrown by [LeagueRepository]'s create/edit/complete/award calls for anything other than a clean 2xx.
 * [code] is the backend's problem `code` (e.g. `AUCTION_ALREADY_STARTED`) where the caller reads it.
 */
class LeagueSaveFailedException(message: String, val code: String? = null) : Exception(message)

/**
 * Thrown by [LeagueRepository.requestLogoUploadUrl]/[requestBannerUploadUrl] on the real `503`
 * this environment's unconfigured AWS S3 setup returns -- same shape as
 * [com.crichere.app.profile.PhotoUploadUnavailableException].
 */
class LeaguePhotoUploadUnavailableException(
    message: String = "Photo upload is unavailable right now. Please try again later.",
) : Exception(message)

/**
 * `commonMain` league use cases against the real backend -- follows
 * [com.crichere.app.reference.ReferenceRepository]'s established interface+`Ktor*Impl` pattern.
 * `GET` (list/detail) is public; every mutation needs the default authenticated client.
 */
interface LeagueRepository {
    /** `GET /api/v1/leagues` with the area filters. Mutually exclusive with [listNearest] in the UI. */
    suspend fun listByArea(state: String? = null, district: String? = null): List<LeagueDto>

    /** `GET /api/v1/leagues?nearLat=&nearLng=`, ordered by distance to each league's ground (every league has one). */
    suspend fun listNearest(latitude: Double, longitude: Double): List<LeagueDto>

    suspend fun getLeague(id: String): LeagueDto

    suspend fun createLeague(request: LeagueSaveRequestDto): LeagueDto

    suspend fun updateLeague(id: String, request: LeagueSaveRequestDto): LeagueDto

    /** `PUT /api/v1/leagues/{id}/auction-settings` -- organizer-only, full-replace (see docs/PHASE4.md). */
    suspend fun updateAuctionSettings(id: String, request: AuctionSettingsSaveRequestDto): LeagueDto

    suspend fun completeLeague(id: String): LeagueDto

    /** Throws [LeaguePhotoUploadUnavailableException] on a `503`. */
    suspend fun requestLogoUploadUrl(leagueId: String): PhotoUploadInfoDto

    /** Throws [LeaguePhotoUploadUnavailableException] on a `503`. */
    suspend fun requestBannerUploadUrl(leagueId: String): PhotoUploadInfoDto

    /** See [uploadPhotoViaPresignedPost] -- this is that function, scoped to [filename]. */
    suspend fun uploadPhoto(
        uploadInfo: PhotoUploadInfoDto,
        bytes: ByteArray,
        contentType: String,
        filename: String,
        onProgress: (Float) -> Unit = {},
    ): String

    suspend fun addAward(leagueId: String, request: LeagueAwardSaveRequestDto): LeagueAwardDto

    suspend fun updateAward(leagueId: String, awardId: String, request: LeagueAwardSaveRequestDto): LeagueAwardDto

    suspend fun deleteAward(leagueId: String, awardId: String)

    /** Throws [LeaguePhotoUploadUnavailableException] on a `503`. Serves both the player-join and franchise-claim flows -- see docs/PHASE3.md. */
    suspend fun requestPaymentScreenshotUploadUrl(leagueId: String): PhotoUploadInfoDto

    /** Throws [LeaguePhotoUploadUnavailableException] on a `503`. Used before a franchise claim exists, so the resulting URL can be included directly in the claim request -- see docs/PHASE3.md. */
    suspend fun requestPendingFranchiseLogoUploadUrl(leagueId: String): PhotoUploadInfoDto

    /** Idempotent -- following twice is a no-op, not an error. */
    suspend fun follow(id: String)

    /** Idempotent -- unfollowing when not following is a no-op, not an error. */
    suspend fun unfollow(id: String)
}

internal class KtorLeagueRepository(
    private val httpClient: HttpClient,
    private val uploadClient: HttpClient,
) : LeagueRepository {

    override suspend fun listByArea(state: String?, district: String?): List<LeagueDto> =
        httpClient.get("/api/v1/leagues") {
            state?.let { parameter("state", it) }
            district?.let { parameter("district", it) }
        }.body()

    override suspend fun listNearest(latitude: Double, longitude: Double): List<LeagueDto> =
        httpClient.get("/api/v1/leagues") {
            parameter("nearLat", latitude)
            parameter("nearLng", longitude)
        }.body()

    override suspend fun getLeague(id: String): LeagueDto =
        httpClient.get("/api/v1/leagues/$id").body()

    override suspend fun createLeague(request: LeagueSaveRequestDto): LeagueDto {
        val response = httpClient.post("/api/v1/leagues") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("League create failed with status ${response.status}")
        return response.body()
    }

    override suspend fun updateLeague(id: String, request: LeagueSaveRequestDto): LeagueDto {
        val response = httpClient.put("/api/v1/leagues/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("League update failed with status ${response.status}")
        return response.body()
    }

    override suspend fun updateAuctionSettings(id: String, request: AuctionSettingsSaveRequestDto): LeagueDto {
        val response = httpClient.put("/api/v1/leagues/$id/auction-settings") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("Auction settings save failed with status ${response.status}", response.problemCode())
        return response.body()
    }

    override suspend fun completeLeague(id: String): LeagueDto {
        val response = httpClient.patch("/api/v1/leagues/$id/complete")
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("League complete failed with status ${response.status}", response.problemCode())
        return response.body()
    }

    override suspend fun requestLogoUploadUrl(leagueId: String): PhotoUploadInfoDto {
        val response: HttpResponse = httpClient.post("/api/v1/leagues/$leagueId/logo-upload-url")
        if (response.status == HttpStatusCode.ServiceUnavailable) throw LeaguePhotoUploadUnavailableException()
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("Logo upload URL request failed with status ${response.status}")
        return response.body()
    }

    override suspend fun requestBannerUploadUrl(leagueId: String): PhotoUploadInfoDto {
        val response: HttpResponse = httpClient.post("/api/v1/leagues/$leagueId/banner-upload-url")
        if (response.status == HttpStatusCode.ServiceUnavailable) throw LeaguePhotoUploadUnavailableException()
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("Banner upload URL request failed with status ${response.status}")
        return response.body()
    }

    override suspend fun uploadPhoto(
        uploadInfo: PhotoUploadInfoDto,
        bytes: ByteArray,
        contentType: String,
        filename: String,
        onProgress: (Float) -> Unit,
    ): String = uploadPhotoViaPresignedPost(uploadClient, uploadInfo, bytes, contentType, filename, onProgress)

    override suspend fun addAward(leagueId: String, request: LeagueAwardSaveRequestDto): LeagueAwardDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/awards") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("Award create failed with status ${response.status}")
        return response.body()
    }

    override suspend fun updateAward(leagueId: String, awardId: String, request: LeagueAwardSaveRequestDto): LeagueAwardDto {
        val response = httpClient.put("/api/v1/leagues/$leagueId/awards/$awardId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("Award update failed with status ${response.status}")
        return response.body()
    }

    override suspend fun deleteAward(leagueId: String, awardId: String) {
        val response = httpClient.delete("/api/v1/leagues/$leagueId/awards/$awardId")
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("Award delete failed with status ${response.status}")
    }

    override suspend fun requestPaymentScreenshotUploadUrl(leagueId: String): PhotoUploadInfoDto {
        val response: HttpResponse = httpClient.post("/api/v1/leagues/$leagueId/payment-screenshot-upload-url")
        if (response.status == HttpStatusCode.ServiceUnavailable) throw LeaguePhotoUploadUnavailableException()
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("Payment screenshot upload URL request failed with status ${response.status}")
        return response.body()
    }

    override suspend fun requestPendingFranchiseLogoUploadUrl(leagueId: String): PhotoUploadInfoDto {
        val response: HttpResponse = httpClient.post("/api/v1/leagues/$leagueId/franchise-logo-upload-url")
        if (response.status == HttpStatusCode.ServiceUnavailable) throw LeaguePhotoUploadUnavailableException()
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("Franchise logo upload URL request failed with status ${response.status}")
        return response.body()
    }

    override suspend fun follow(id: String) {
        val response = httpClient.post("/api/v1/leagues/$id/follow")
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("Follow failed with status ${response.status}")
    }

    override suspend fun unfollow(id: String) {
        val response = httpClient.delete("/api/v1/leagues/$id/follow")
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("Unfollow failed with status ${response.status}")
    }
}

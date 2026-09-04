package com.crichere.app.league

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

/** Thrown by [LeagueRepository]'s create/edit/complete/award calls for anything other than a clean 2xx. */
class LeagueSaveFailedException(message: String) : Exception(message)

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
    suspend fun listByArea(state: String? = null, district: String? = null, city: String? = null): List<LeagueDto>

    /** `GET /api/v1/leagues?nearLat=&nearLng=`. Only leagues with a ground attached participate. */
    suspend fun listNearest(latitude: Double, longitude: Double): List<LeagueDto>

    suspend fun getLeague(id: String): LeagueDto

    suspend fun createLeague(request: LeagueSaveRequestDto): LeagueDto

    suspend fun updateLeague(id: String, request: LeagueSaveRequestDto): LeagueDto

    suspend fun completeLeague(id: String): LeagueDto

    /** Throws [LeaguePhotoUploadUnavailableException] on a `503`. */
    suspend fun requestLogoUploadUrl(leagueId: String): PhotoUploadInfoDto

    /** Throws [LeaguePhotoUploadUnavailableException] on a `503`. */
    suspend fun requestBannerUploadUrl(leagueId: String): PhotoUploadInfoDto

    /** See [uploadPhotoViaPresignedPost] -- this is that function, scoped to [filename]. */
    suspend fun uploadPhoto(uploadInfo: PhotoUploadInfoDto, bytes: ByteArray, contentType: String, filename: String): String

    suspend fun addAward(leagueId: String, request: LeagueAwardSaveRequestDto): LeagueAwardDto

    suspend fun updateAward(leagueId: String, awardId: String, request: LeagueAwardSaveRequestDto): LeagueAwardDto

    suspend fun deleteAward(leagueId: String, awardId: String)
}

internal class KtorLeagueRepository(
    private val httpClient: HttpClient,
    private val uploadClient: HttpClient,
) : LeagueRepository {

    override suspend fun listByArea(state: String?, district: String?, city: String?): List<LeagueDto> =
        httpClient.get("/api/v1/leagues") {
            state?.let { parameter("state", it) }
            district?.let { parameter("district", it) }
            city?.let { parameter("city", it) }
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

    override suspend fun completeLeague(id: String): LeagueDto {
        val response = httpClient.patch("/api/v1/leagues/$id/complete")
        if (!response.status.isSuccess()) throw LeagueSaveFailedException("League complete failed with status ${response.status}")
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

    override suspend fun uploadPhoto(uploadInfo: PhotoUploadInfoDto, bytes: ByteArray, contentType: String, filename: String): String =
        uploadPhotoViaPresignedPost(uploadClient, uploadInfo, bytes, contentType, filename)

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
}

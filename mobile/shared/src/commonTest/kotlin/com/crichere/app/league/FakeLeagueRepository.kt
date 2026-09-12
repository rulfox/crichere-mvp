package com.crichere.app.league

import com.crichere.app.upload.PhotoUploadInfoDto
import kotlinx.coroutines.delay

/** In-memory [LeagueRepository] test double -- `commonTest` has no real backend/S3 to hit. */
class FakeLeagueRepository(
    var leaguesByArea: List<LeagueDto> = emptyList(),
    var leaguesNearest: List<LeagueDto> = emptyList(),
) : LeagueRepository {

    val listByAreaCalls = mutableListOf<Triple<String?, String?, String?>>()
    val listNearestCalls = mutableListOf<Pair<Double, Double>>()
    var listByAreaError: Throwable? = null
    var listNearestError: Throwable? = null

    var nextCreated: LeagueDto? = null
    var createError: Throwable? = null
    val createdRequests = mutableListOf<LeagueSaveRequestDto>()

    var nextUpdated: LeagueDto? = null
    var nextCompleted: LeagueDto? = null

    var photoUploadInfo: PhotoUploadInfoDto = PhotoUploadInfoDto(
        uploadUrl = "https://crichere-media-dev.s3.ap-south-1.amazonaws.com/",
        fields = mapOf("key" to "leagues/l1/logo.jpg"),
        key = "leagues/l1/logo.jpg",
        expiresAt = "2026-09-03T12:05:00.000Z",
    )
    var uploadedPhotoUrl: String = "https://crichere-media-dev.s3.ap-south-1.amazonaws.com/leagues/l1/logo.jpg"

    var nextAward: LeagueAwardDto? = null
    val addAwardRequests = mutableListOf<LeagueAwardSaveRequestDto>()
    var deleteAwardCallCount = 0
        private set

    val followCalls = mutableListOf<String>()
    val unfollowCalls = mutableListOf<String>()
    var followError: Throwable? = null
    var unfollowError: Throwable? = null

    var nextAuctionSettingsUpdated: LeagueDto? = null
    var updateAuctionSettingsError: Throwable? = null
    val updateAuctionSettingsRequests = mutableListOf<AuctionSettingsSaveRequestDto>()

    override suspend fun listByArea(state: String?, district: String?, city: String?): List<LeagueDto> {
        listByAreaCalls += Triple(state, district, city)
        listByAreaError?.let { throw it }
        return leaguesByArea
    }

    override suspend fun listNearest(latitude: Double, longitude: Double): List<LeagueDto> {
        listNearestCalls += latitude to longitude
        listNearestError?.let { throw it }
        return leaguesNearest
    }

    /** Simulates a slow response -- lets tests prove a stale, still in-flight [getLeague] call gets cancelled rather than overwriting a newer one's state. */
    var getLeagueDelayMillis: Long = 0

    override suspend fun getLeague(id: String): LeagueDto {
        if (getLeagueDelayMillis > 0) delay(getLeagueDelayMillis)
        return leaguesByArea.first { it.id == id }
    }

    override suspend fun createLeague(request: LeagueSaveRequestDto): LeagueDto {
        createdRequests += request
        createError?.let { throw it }
        return nextCreated ?: error("nextCreated not stubbed")
    }

    override suspend fun updateLeague(id: String, request: LeagueSaveRequestDto): LeagueDto =
        nextUpdated ?: error("nextUpdated not stubbed")

    override suspend fun completeLeague(id: String): LeagueDto =
        nextCompleted ?: error("nextCompleted not stubbed")

    override suspend fun requestLogoUploadUrl(leagueId: String): PhotoUploadInfoDto = photoUploadInfo

    override suspend fun requestBannerUploadUrl(leagueId: String): PhotoUploadInfoDto = photoUploadInfo

    override suspend fun uploadPhoto(uploadInfo: PhotoUploadInfoDto, bytes: ByteArray, contentType: String, filename: String): String =
        uploadedPhotoUrl

    override suspend fun addAward(leagueId: String, request: LeagueAwardSaveRequestDto): LeagueAwardDto {
        addAwardRequests += request
        return nextAward ?: error("nextAward not stubbed")
    }

    override suspend fun updateAward(leagueId: String, awardId: String, request: LeagueAwardSaveRequestDto): LeagueAwardDto =
        nextAward ?: error("nextAward not stubbed")

    override suspend fun deleteAward(leagueId: String, awardId: String) {
        deleteAwardCallCount++
    }

    override suspend fun requestPaymentScreenshotUploadUrl(leagueId: String): PhotoUploadInfoDto = photoUploadInfo

    override suspend fun requestPendingFranchiseLogoUploadUrl(leagueId: String): PhotoUploadInfoDto = photoUploadInfo

    override suspend fun follow(id: String) {
        followCalls += id
        followError?.let { throw it }
    }

    override suspend fun unfollow(id: String) {
        unfollowCalls += id
        unfollowError?.let { throw it }
    }

    override suspend fun updateAuctionSettings(id: String, request: AuctionSettingsSaveRequestDto): LeagueDto {
        updateAuctionSettingsRequests += request
        updateAuctionSettingsError?.let { throw it }
        return nextAuctionSettingsUpdated ?: error("nextAuctionSettingsUpdated not stubbed")
    }
}

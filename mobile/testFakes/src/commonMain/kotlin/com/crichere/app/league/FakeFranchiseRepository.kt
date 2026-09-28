package com.crichere.app.league

import com.crichere.app.upload.PhotoUploadInfoDto

/** In-memory [FranchiseRepository] test double -- no real backend to hit in tests. */
class FakeFranchiseRepository : FranchiseRepository {

    var nextClaimed: LeagueFranchiseDto? = null
    var claimError: Throwable? = null
    val claimRequests = mutableListOf<Pair<String, LeagueFranchiseClaimRequestDto>>()

    var removeError: Throwable? = null
    val removeCalls = mutableListOf<Pair<String, String>>()

    var nextLeaveRequested: LeagueFranchiseDto? = null
    var requestLeaveError: Throwable? = null

    var nextApproved: LeagueFranchiseDto? = null
    var approveLeaveError: Throwable? = null

    var nextDismissed: LeagueFranchiseDto? = null
    var dismissLeaveError: Throwable? = null

    var logoUploadInfo: PhotoUploadInfoDto = PhotoUploadInfoDto(
        uploadUrl = "https://crichere-media-dev.s3.ap-south-1.amazonaws.com/",
        fields = mapOf("key" to "franchises/f1/logo.jpg"),
        key = "franchises/f1/logo.jpg",
        expiresAt = "2026-09-03T12:05:00.000Z",
    )

    override suspend fun claim(leagueId: String, request: LeagueFranchiseClaimRequestDto): LeagueFranchiseDto {
        claimRequests += leagueId to request
        claimError?.let { throw it }
        return nextClaimed ?: error("nextClaimed not stubbed")
    }

    override suspend fun remove(leagueId: String, franchiseId: String) {
        removeCalls += leagueId to franchiseId
        removeError?.let { throw it }
    }

    override suspend fun requestLeave(leagueId: String, franchiseId: String): LeagueFranchiseDto {
        requestLeaveError?.let { throw it }
        return nextLeaveRequested ?: error("nextLeaveRequested not stubbed")
    }

    override suspend fun approveLeave(leagueId: String, franchiseId: String): LeagueFranchiseDto {
        approveLeaveError?.let { throw it }
        return nextApproved ?: error("nextApproved not stubbed")
    }

    override suspend fun dismissLeave(leagueId: String, franchiseId: String): LeagueFranchiseDto {
        dismissLeaveError?.let { throw it }
        return nextDismissed ?: error("nextDismissed not stubbed")
    }

    override suspend fun requestFranchiseLogoUploadUrl(leagueId: String, franchiseId: String): PhotoUploadInfoDto = logoUploadInfo
}

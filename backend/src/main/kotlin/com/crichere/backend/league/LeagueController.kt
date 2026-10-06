package com.crichere.backend.league

import com.crichere.backend.common.PhotoUploadUrlResponse
import com.crichere.backend.league.dto.AuctionSettingsSaveRequest
import com.crichere.backend.league.dto.LeagueAwardResponse
import com.crichere.backend.league.dto.LeagueAwardSaveRequest
import com.crichere.backend.league.dto.LeagueResponse
import com.crichere.backend.league.dto.LeagueSaveRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * League discovery + creation (Phase 2, no auction mechanics). `GET` endpoints are public
 * (`permitAll()` in `SecurityConfig`, matching grounds' public-read posture); every mutating
 * endpoint is authenticated and organizer-scoped -- see [LeagueService].
 */
@RestController
@RequestMapping("/api/v1/leagues")
class LeagueController(
    private val leagueService: LeagueService,
) {

    /**
     * `nearLat`/`nearLng` (both present) list by nearest-to-that-point instead of the area
     * filters -- mutually exclusive in the UI, see docs/PHASE2.md's Decisions Made. Any other
     * combination (including one of the pair alone) falls back to the area filters.
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) district: String?,
        @RequestParam(required = false) nearLat: Double?,
        @RequestParam(required = false) nearLng: Double?,
        @AuthenticationPrincipal(errorOnInvalidType = false) callerId: UUID?,
    ): List<LeagueResponse> =
        if (nearLat != null && nearLng != null) {
            leagueService.listNearest(nearLat, nearLng, callerId)
        } else {
            leagueService.listByArea(state, district, callerId)
        }

    /** `callerId` is `null` for an anonymous caller -- see [LeagueService.getLeague]'s doc. */
    @GetMapping("/{id}")
    fun getLeague(
        @PathVariable id: UUID,
        @AuthenticationPrincipal(errorOnInvalidType = false) callerId: UUID?,
    ): LeagueResponse = leagueService.getLeague(id, callerId)

    @PostMapping
    fun create(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: LeagueSaveRequest,
    ): LeagueResponse = leagueService.create(userId, request)

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: LeagueSaveRequest,
    ): LeagueResponse = leagueService.update(id, userId, request)

    @PutMapping("/{id}/auction-settings")
    fun updateAuctionSettings(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: AuctionSettingsSaveRequest,
    ): LeagueResponse = leagueService.updateAuctionSettings(id, userId, request)

    @PatchMapping("/{id}/complete")
    fun complete(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): LeagueResponse = leagueService.complete(id, userId)

    @PostMapping("/{id}/logo-upload-url")
    fun createLogoUploadUrl(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): PhotoUploadUrlResponse = leagueService.createLogoUploadUrl(id, userId)

    @PostMapping("/{id}/banner-upload-url")
    fun createBannerUploadUrl(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): PhotoUploadUrlResponse = leagueService.createBannerUploadUrl(id, userId)

    @PostMapping("/{id}/payment-screenshot-upload-url")
    fun createPaymentScreenshotUploadUrl(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): PhotoUploadUrlResponse = leagueService.createPaymentScreenshotUploadUrl(id, userId)

    @PostMapping("/{id}/franchise-logo-upload-url")
    fun createFranchiseLogoUploadUrl(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): PhotoUploadUrlResponse = leagueService.createFranchiseLogoUploadUrl(id, userId)

    @PostMapping("/{id}/follow")
    fun follow(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
    ) = leagueService.follow(id, userId)

    @DeleteMapping("/{id}/follow")
    fun unfollow(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
    ) = leagueService.unfollow(id, userId)

    @PostMapping("/{id}/awards")
    fun addAward(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: LeagueAwardSaveRequest,
    ): LeagueAwardResponse = leagueService.addAward(id, userId, request)

    @PutMapping("/{id}/awards/{awardId}")
    fun updateAward(
        @PathVariable id: UUID,
        @PathVariable awardId: UUID,
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: LeagueAwardSaveRequest,
    ): LeagueAwardResponse = leagueService.updateAward(id, awardId, userId, request)

    @DeleteMapping("/{id}/awards/{awardId}")
    fun deleteAward(
        @PathVariable id: UUID,
        @PathVariable awardId: UUID,
        @AuthenticationPrincipal userId: UUID,
    ) = leagueService.deleteAward(id, awardId, userId)
}

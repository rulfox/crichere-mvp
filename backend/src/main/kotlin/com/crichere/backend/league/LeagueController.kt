package com.crichere.backend.league

import com.crichere.backend.common.PhotoUploadUrlResponse
import com.crichere.backend.league.dto.LeagueResponse
import com.crichere.backend.league.dto.LeagueSaveRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
        @RequestParam(required = false) city: String?,
        @RequestParam(required = false) nearLat: Double?,
        @RequestParam(required = false) nearLng: Double?,
    ): List<LeagueResponse> =
        if (nearLat != null && nearLng != null) {
            leagueService.listNearest(nearLat, nearLng)
        } else {
            leagueService.listByArea(state, district, city)
        }

    @GetMapping("/{id}")
    fun getLeague(@PathVariable id: UUID): LeagueResponse = leagueService.getLeague(id)

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
}

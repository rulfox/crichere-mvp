package com.crichere.backend.franchise

import com.crichere.backend.common.PhotoUploadUrlResponse
import com.crichere.backend.franchise.dto.LeagueFranchiseClaimRequest
import com.crichere.backend.franchise.dto.LeagueFranchiseResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Claiming a franchise in a league, and its request-and-approve leave flow -- see [FranchiseService]. Every endpoint here is authenticated. */
@RestController
@RequestMapping("/api/v1/leagues/{leagueId}/franchises")
class FranchiseController(
    private val franchiseService: FranchiseService,
) {

    @PostMapping
    fun claim(
        @PathVariable leagueId: UUID,
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: LeagueFranchiseClaimRequest,
    ): LeagueFranchiseResponse = franchiseService.claim(leagueId, userId, request)

    @DeleteMapping("/{franchiseId}")
    fun remove(
        @PathVariable leagueId: UUID,
        @PathVariable franchiseId: UUID,
        @AuthenticationPrincipal userId: UUID,
    ) = franchiseService.remove(leagueId, franchiseId, userId)

    @PostMapping("/{franchiseId}/leave-request")
    fun requestLeave(
        @PathVariable leagueId: UUID,
        @PathVariable franchiseId: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): LeagueFranchiseResponse = franchiseService.requestLeave(leagueId, franchiseId, userId)

    @PostMapping("/{franchiseId}/leave-request/approve")
    fun approveLeave(
        @PathVariable leagueId: UUID,
        @PathVariable franchiseId: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): LeagueFranchiseResponse = franchiseService.approveLeave(leagueId, franchiseId, userId)

    @PostMapping("/{franchiseId}/leave-request/dismiss")
    fun dismissLeave(
        @PathVariable leagueId: UUID,
        @PathVariable franchiseId: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): LeagueFranchiseResponse = franchiseService.dismissLeave(leagueId, franchiseId, userId)

    @PostMapping("/{franchiseId}/logo-upload-url")
    fun createLogoUploadUrl(
        @PathVariable leagueId: UUID,
        @PathVariable franchiseId: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): PhotoUploadUrlResponse = franchiseService.createLogoUploadUrl(leagueId, franchiseId, userId)
}

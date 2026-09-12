package com.crichere.backend.franchise.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Body of `POST /api/v1/leagues/{leagueId}/franchises`. [paymentScreenshotUrl] is required only when the league has a `franchiseFee` set -- checked in `FranchiseService`. */
data class LeagueFranchiseClaimRequest(
    @field:NotBlank(message = "name is required")
    @field:Size(max = 200, message = "name must be at most 200 characters")
    val name: String,

    val logoUrl: String? = null,

    @field:Size(max = 2048, message = "paymentScreenshotUrl must be at most 2048 characters")
    val paymentScreenshotUrl: String? = null,
)

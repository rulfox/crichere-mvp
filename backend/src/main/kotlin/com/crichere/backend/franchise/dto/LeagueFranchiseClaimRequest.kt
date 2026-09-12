package com.crichere.backend.franchise.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Body of `POST /api/v1/leagues/{leagueId}/franchises`. [paymentScreenshotUrl] is required only
 * when the league has a `franchiseFee` set -- checked in `FranchiseService`.
 *
 * [logoUrl]/[paymentScreenshotUrl] are restricted to `https://` -- both are later opened directly
 * by a viewer's device (a franchise logo once image rendering exists; the payment screenshot
 * already is, via `ScreenshotViewerScreen.kt`'s `URL(imageUrl).openStream()`). Without this, a
 * caller could submit a `file://`/`content://` URI and have it silently read on another user's
 * device.
 */
data class LeagueFranchiseClaimRequest(
    @field:NotBlank(message = "name is required")
    @field:Size(max = 200, message = "name must be at most 200 characters")
    val name: String,

    @field:Size(max = 2048, message = "logoUrl must be at most 2048 characters")
    @field:Pattern(regexp = "^https://.*", message = "logoUrl must be an https URL")
    val logoUrl: String? = null,

    @field:Size(max = 2048, message = "paymentScreenshotUrl must be at most 2048 characters")
    @field:Pattern(regexp = "^https://.*", message = "paymentScreenshotUrl must be an https URL")
    val paymentScreenshotUrl: String? = null,
)

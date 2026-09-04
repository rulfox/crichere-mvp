package com.crichere.backend.league.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/**
 * Body of `POST`/`PUT /api/v1/leagues/{id}/awards[/{awardId}]`, and one entry of
 * [LeagueSaveRequest]'s optional initial `awards` list at creation time.
 */
data class LeagueAwardSaveRequest(
    @field:NotBlank(message = "name is required")
    @field:Size(max = 100, message = "name must be at most 100 characters")
    val name: String,

    @field:DecimalMin(value = "0.0", message = "cashAmount cannot be negative")
    val cashAmount: BigDecimal? = null,

    val hasTrophy: Boolean = false,
)

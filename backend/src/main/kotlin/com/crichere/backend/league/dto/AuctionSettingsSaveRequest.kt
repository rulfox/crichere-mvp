package com.crichere.backend.league.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

/**
 * Body of `PUT /api/v1/leagues/{id}/auction-settings` -- a dedicated save action, deliberately
 * separate from [LeagueSaveRequest]/League Creation (see docs/PHASE4.md's Decisions Made: these
 * fields are meaningless to most leagues until they're actually planning an auction, weeks after
 * creation). Full-replace, all fields required together -- same posture every other multi-field
 * save in this app already has, no partial-save support.
 */
data class AuctionSettingsSaveRequest(
    @field:NotNull(message = "basePrice is required")
    @field:Positive(message = "basePrice must be positive")
    val basePrice: BigDecimal?,

    @field:NotNull(message = "purse is required")
    @field:Positive(message = "purse must be positive")
    val purse: BigDecimal?,

    @field:NotNull(message = "squadMin is required")
    @field:Positive(message = "squadMin must be positive")
    val squadMin: Int?,

    @field:NotNull(message = "squadMax is required")
    @field:Positive(message = "squadMax must be positive")
    val squadMax: Int?,

    @field:NotNull(message = "bidIncrement is required")
    @field:Positive(message = "bidIncrement must be positive")
    val bidIncrement: BigDecimal?,
)

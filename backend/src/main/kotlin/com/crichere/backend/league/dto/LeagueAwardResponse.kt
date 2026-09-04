package com.crichere.backend.league.dto

import java.math.BigDecimal
import java.util.UUID

/** One row of a league's `awards` list (embedded in [LeagueResponse], and the response of awards create/edit). */
data class LeagueAwardResponse(
    val id: UUID,
    val name: String,
    val cashAmount: BigDecimal?,
    val hasTrophy: Boolean,
    val displayOrder: Int,
)

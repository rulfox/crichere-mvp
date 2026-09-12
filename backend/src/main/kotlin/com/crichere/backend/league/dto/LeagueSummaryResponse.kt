package com.crichere.backend.league.dto

import com.crichere.backend.league.LeagueStatus
import java.time.LocalDate
import java.util.UUID

/** One row of `GET /api/v1/me/leagues`'s four lists -- lighter than [LeagueResponse], since none of those lists need awards/players/franchises/ground nested four times over. */
data class LeagueSummaryResponse(
    val id: UUID,
    val name: String,
    val logoUrl: String?,
    val city: String,
    val state: String,
    val startsOn: LocalDate,
    val status: LeagueStatus,
)

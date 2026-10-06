package com.crichere.backend.league.dto

import com.crichere.backend.league.LeagueStatus
import java.time.LocalDate
import java.util.UUID

/** One row of `GET /api/v1/me/leagues`'s four lists -- lighter than [LeagueResponse], since none of those lists need awards/players/franchises/ground nested four times over. */
data class LeagueSummaryResponse(
    val id: UUID,
    val name: String,
    val logoUrl: String?,
    val district: String,
    val state: String,
    /** My leagues row reads "<ground> · <district> · <date>" (design update #6, M1). */
    val groundName: String,
    val startsOn: LocalDate,
    val status: LeagueStatus,
)

package com.crichere.backend.league

import com.crichere.backend.league.dto.LeagueSummaryResponse

/**
 * Shared response mapping for the lightweight `GET /api/v1/me/leagues` rows -- see
 * [com.crichere.backend.me.MeService], which loads the [groundName]s for all rows in one query.
 */
fun LeagueEntity.toSummaryResponse(groundName: String) = LeagueSummaryResponse(
    id = requireNotNull(id),
    name = name,
    logoUrl = logoUrl,
    district = district,
    state = state,
    groundName = groundName,
    startsOn = startsOn,
    status = status,
)

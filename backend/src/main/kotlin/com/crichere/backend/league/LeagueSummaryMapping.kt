package com.crichere.backend.league

import com.crichere.backend.league.dto.LeagueSummaryResponse

/** Shared response mapping for the lightweight `GET /api/v1/me/leagues` rows -- see [com.crichere.backend.me.MeService]. */
fun LeagueEntity.toSummaryResponse() = LeagueSummaryResponse(
    id = requireNotNull(id),
    name = name,
    logoUrl = logoUrl,
    city = city,
    state = state,
    startsOn = startsOn,
    status = status,
)

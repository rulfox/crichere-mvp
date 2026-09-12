package com.crichere.backend.me.dto

import com.crichere.backend.league.dto.LeagueSummaryResponse

/** Body of `GET /api/v1/me/leagues` -- all four lists in one round trip, since the My Leagues screen renders all four at once. */
data class MyLeaguesResponse(
    val organizing: List<LeagueSummaryResponse>,
    val playing: List<LeagueSummaryResponse>,
    val franchiseOwner: List<LeagueSummaryResponse>,
    val following: List<LeagueSummaryResponse>,
)

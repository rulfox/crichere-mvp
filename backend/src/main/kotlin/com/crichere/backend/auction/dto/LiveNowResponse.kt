package com.crichere.backend.auction.dto

import java.util.UUID

/** `GET /api/v1/auctions/live-now` -- the league the landing page's "Watch live" links open (docs/PHASE11.md D5). */
data class LiveNowResponse(
    val leagueId: UUID,
    val leagueName: String,
)

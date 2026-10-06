package com.crichere.app.league

import kotlinx.serialization.Serializable

/** Mirrors the backend's `LeagueSummaryResponse` -- one row of any `GET /api/v1/me/leagues` list. Lighter than [LeagueDto], since none of those lists need awards/players/franchises/ground nested four times over. */
@Serializable
data class LeagueSummaryDto(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val district: String,
    val state: String,
    val groundName: String,
    val startsOn: String,
    val status: LeagueStatus,
)

/** Mirrors the backend's `MyLeaguesResponse` -- body of `GET /api/v1/me/leagues`. */
@Serializable
data class MyLeaguesDto(
    val organizing: List<LeagueSummaryDto> = emptyList(),
    val playing: List<LeagueSummaryDto> = emptyList(),
    val franchiseOwner: List<LeagueSummaryDto> = emptyList(),
    val following: List<LeagueSummaryDto> = emptyList(),
)

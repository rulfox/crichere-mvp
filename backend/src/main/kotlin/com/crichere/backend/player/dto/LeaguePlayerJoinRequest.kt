package com.crichere.backend.player.dto

import jakarta.validation.constraints.Size

/** Body of `POST /api/v1/leagues/{leagueId}/players`. [paymentScreenshotUrl] is required only when the league has a `playerFee` set -- checked in `PlayerService`, not a bean-validation annotation, since the rule reads a sibling entity's field. */
data class LeaguePlayerJoinRequest(
    @field:Size(max = 2048, message = "paymentScreenshotUrl must be at most 2048 characters")
    val paymentScreenshotUrl: String? = null,
)

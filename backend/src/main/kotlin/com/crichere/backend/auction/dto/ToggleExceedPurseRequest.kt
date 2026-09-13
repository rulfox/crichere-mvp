package com.crichere.backend.auction.dto

import jakarta.validation.constraints.NotNull

/** Body of `POST /leagues/{id}/auction/toggle-exceed-purse`. */
data class ToggleExceedPurseRequest(
    @field:NotNull
    val allow: Boolean?,
)

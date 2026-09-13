package com.crichere.backend.auction.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.util.UUID

/** Body of `POST /leagues/{id}/auction/bids`. */
data class PlaceBidRequest(
    @field:NotNull
    val franchiseId: UUID?,
    @field:NotNull
    @field:Positive
    val amount: BigDecimal?,
)

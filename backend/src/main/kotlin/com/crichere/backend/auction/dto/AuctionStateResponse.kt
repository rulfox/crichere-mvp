package com.crichere.backend.auction.dto

import com.crichere.backend.league.AuctionStatus
import java.math.BigDecimal
import java.util.UUID

/**
 * The live auction state -- returned by every mutating auction endpoint and broadcast verbatim
 * over the SSE stream (`GET /leagues/{id}/auction/stream`) so a client's in-memory state after a
 * direct call and after a stream event are always the exact same shape. Public, no auth (see
 * docs/PHASE5.md's Decisions Made -- the stream and live state are public like the rest of the league).
 */
data class AuctionStateResponse(
    val auctionStatus: AuctionStatus,
    val currentPlayerId: UUID?,
    val currentPlayerName: String?,
    val currentBidAmount: BigDecimal?,
    val currentLeadingFranchiseId: UUID?,
    val currentLeadingFranchiseName: String?,
    val allowExceedPurse: Boolean,
)

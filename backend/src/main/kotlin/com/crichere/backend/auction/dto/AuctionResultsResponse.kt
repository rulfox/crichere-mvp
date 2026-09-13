package com.crichere.backend.auction.dto

import com.crichere.backend.league.AuctionStatus
import java.math.BigDecimal
import java.util.UUID

/** `GET /leagues/{id}/auction/results` -- public, no auth (see docs/PHASE5.md's Features). */
data class AuctionResultsResponse(
    val auctionStatus: AuctionStatus,
    val franchises: List<FranchiseAuctionResultResponse>,
)

data class FranchiseAuctionResultResponse(
    val franchiseId: UUID,
    val franchiseName: String,
    val playersWon: List<PlayerAuctionResultResponse>,
    val purseSpent: BigDecimal,
    /** `null` if `auctionPurse` was never configured -- shouldn't happen for a league that ran an auction, kept nullable rather than crashing if it somehow did. */
    val purseRemaining: BigDecimal?,
    /** `true` if this franchise's won-player count is below `auctionSquadMin` -- informational only, see docs/PHASE4.md's Decisions Made. */
    val belowSquadMin: Boolean,
)

data class PlayerAuctionResultResponse(
    val playerId: UUID,
    val userId: UUID,
    val playerName: String?,
    val soldPrice: BigDecimal,
)

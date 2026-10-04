package com.crichere.backend.auction.dto

import com.crichere.backend.league.AuctionStatus
import com.crichere.backend.profile.BattingStyle
import com.crichere.backend.profile.BowlingStyle
import com.crichere.backend.profile.PlayingRole
import java.math.BigDecimal
import java.time.Instant
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
    /** Public bid ticker (docs/PHASE6.md) -- last 8 unreversed bids on [currentPlayerId], newest
     * first. Always empty when [currentPlayerId] is null. */
    val recentBids: List<AuctionBidTickerResponse>,
    /** The current player's profile photo and role (screen L, 2026-10-02) -- public like the name. */
    val currentPlayerPhotoUrl: String? = null,
    val currentPlayerRole: PlayingRole? = null,
    /** Active (not removed) players in the league, and how many are sold -- "Player 12 of 58". */
    val playersTotal: Int = 0,
    val playersSold: Int = 0,
    /** The player just closed, while nobody is up yet ("Last: X sold to Y for ₹Z"); `null` otherwise. */
    val lastResult: AuctionLastResultResponse? = null,
    /** The current player's batting/bowling style (docs/PHASE11.md D1) -- profile attributes, public like [currentPlayerRole]. */
    val currentPlayerBattingStyle: BattingStyle? = null,
    val currentPlayerBowlingStyle: BowlingStyle? = null,
    /** How many players have been opened so far (`leagues.auction_lot_counter`) -- "Lot N" (docs/PHASE11.md D2). `null` before the first `next-player`. */
    val currentLotNumber: Int? = null,
    /** Players still `PENDING` -- the pool `next-player` draws from, unsold players included once they're back in it. */
    val playersPending: Int = 0,
    /** `false` when the auction is running with players waiting but no franchise can bid (squads full / purses spent) -- the organizer's cue to end the auction or allow exceeding the purse. */
    val canAnyoneBid: Boolean = true,
    /** While [canAnyoneBid] is `false`: active franchises, and how many are stopped by a full squad / by a purse below the base price (exceeding off). Zero otherwise. */
    val franchisesTotal: Int = 0,
    val squadsFull: Int = 0,
    val purseBelowBase: Int = 0,
)

/** See [AuctionStateResponse.lastResult]. [franchiseName]/[amount] are `null` when [sold] is false. */
data class AuctionLastResultResponse(
    val playerName: String?,
    val sold: Boolean,
    val franchiseName: String?,
    val amount: BigDecimal?,
)

data class AuctionBidTickerResponse(
    val franchiseId: UUID,
    val franchiseName: String?,
    val amount: BigDecimal,
    val placedAt: Instant,
)

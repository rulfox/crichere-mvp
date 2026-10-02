package com.crichere.app.league

import com.crichere.app.profile.PlayingRole
import kotlinx.serialization.Serializable

/** Mirrors the backend's `com.crichere.backend.league.AuctionStatus`. */
@Serializable
enum class AuctionStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
}

/**
 * Mirrors the backend's `AuctionStateResponse` -- returned by every mutating auction endpoint and
 * broadcast verbatim over the SSE stream (see docs/PHASE5.md), so a client's in-memory state after
 * a direct call and after a stream event are always the exact same shape.
 */
@Serializable
data class AuctionStateDto(
    val auctionStatus: AuctionStatus,
    val currentPlayerId: String? = null,
    val currentPlayerName: String? = null,
    val currentBidAmount: Double? = null,
    val currentLeadingFranchiseId: String? = null,
    val currentLeadingFranchiseName: String? = null,
    val allowExceedPurse: Boolean = false,
    /** Last 8 bids on the current player, newest first (docs/PHASE6.md); empty between players. */
    val recentBids: List<AuctionBidTickerDto> = emptyList(),
    val currentPlayerPhotoUrl: String? = null,
    val currentPlayerRole: PlayingRole? = null,
    val playersTotal: Int = 0,
    val playersSold: Int = 0,
    /** The player just sold / sent back unsold, while nobody is up yet (design L8). */
    val lastResult: AuctionLastResultDto? = null,
)

/** Mirrors the backend's `AuctionBidTickerResponse`. [placedAt] is an ISO-8601 instant. */
@Serializable
data class AuctionBidTickerDto(
    val franchiseId: String,
    val franchiseName: String? = null,
    val amount: Double,
    val placedAt: String,
)

/** Mirrors the backend's `AuctionLastResultResponse`. */
@Serializable
data class AuctionLastResultDto(
    val playerName: String? = null,
    val sold: Boolean,
    val franchiseName: String? = null,
    val amount: Double? = null,
)

/** Mirrors the backend's `AuctionResultsResponse`. */
@Serializable
data class AuctionResultsDto(
    val auctionStatus: AuctionStatus,
    val franchises: List<FranchiseAuctionResultDto> = emptyList(),
)

/** Mirrors the backend's `FranchiseAuctionResultResponse`. */
@Serializable
data class FranchiseAuctionResultDto(
    val franchiseId: String,
    val franchiseName: String,
    val playersWon: List<PlayerAuctionResultDto> = emptyList(),
    val purseSpent: Double,
    val purseRemaining: Double? = null,
    val belowSquadMin: Boolean = false,
)

/** Mirrors the backend's `PlayerAuctionResultResponse`. */
@Serializable
data class PlayerAuctionResultDto(
    val playerId: String,
    val userId: String,
    val playerName: String? = null,
    val soldPrice: Double,
    val photoUrl: String? = null,
)

/** Body of `POST /leagues/{id}/auction/bids`. */
@Serializable
data class PlaceBidRequestDto(
    val franchiseId: String,
    val amount: Double,
)

/** Body of `POST /leagues/{id}/auction/toggle-exceed-purse`. */
@Serializable
data class ToggleExceedPurseRequestDto(
    val allow: Boolean,
)

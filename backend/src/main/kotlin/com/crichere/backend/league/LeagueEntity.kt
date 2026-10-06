package com.crichere.backend.league

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Maps to the `leagues` table (V8__create_leagues_table.sql). [name]/[state]/[district]/[groundId]/
 * [startsOn] are required (`NOT NULL`) -- League Creation has no resumable partial-save (see
 * docs/PHASE2.md's Decisions Made), so there is no "DRAFT" status to derive; [completedAt] set
 * or unset is the only lifecycle signal (see [LeagueStatus]).
 *
 * [organizerUserId]/[groundId] are plain foreign-key columns, not JPA `@ManyToOne`s, matching
 * every other cross-reference in this codebase (`ProfileEntity.userId`, `GroundEntity.
 * registeredByUserId`) -- no lazy-load traversal is needed by anything built so far.
 */
@Entity
@Table(name = "leagues")
class LeagueEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "organizer_user_id", nullable = false, updatable = false)
    var organizerUserId: UUID,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "logo_url", columnDefinition = "TEXT")
    var logoUrl: String? = null,

    @Column(name = "banner_url", columnDefinition = "TEXT")
    var bannerUrl: String? = null,

    @Column(name = "country", nullable = false, length = 2)
    var country: String = "IN",

    @Column(name = "state", nullable = false)
    var state: String,

    @Column(name = "district", nullable = false)
    var district: String,

    /** Mandatory since V21 (design update #6): every league is placed by its ground's map pin. */
    @Column(name = "ground_id", nullable = false)
    var groundId: UUID,

    @Column(name = "starts_on", nullable = false)
    var startsOn: LocalDate,

    @Column(name = "format")
    var format: String? = null,

    @Column(name = "franchises_required")
    var franchisesRequired: Int? = null,

    @Column(name = "players_required")
    var playersRequired: Int? = null,

    @Column(name = "franchise_fee")
    var franchiseFee: BigDecimal? = null,

    @Column(name = "player_fee")
    var playerFee: BigDecimal? = null,

    @Column(name = "organizer_upi_id")
    var organizerUpiId: String? = null,

    @Column(name = "auction_base_price")
    var auctionBasePrice: BigDecimal? = null,

    @Column(name = "auction_purse")
    var auctionPurse: BigDecimal? = null,

    @Column(name = "auction_squad_min")
    var auctionSquadMin: Int? = null,

    @Column(name = "auction_squad_max")
    var auctionSquadMax: Int? = null,

    @Column(name = "auction_bid_increment")
    var auctionBidIncrement: BigDecimal? = null,

    /** `NOT_STARTED` until the organizer calls `start`; see docs/PHASE5.md. */
    @Column(name = "auction_status", nullable = false)
    @Enumerated(EnumType.STRING)
    var auctionStatus: AuctionStatus = AuctionStatus.NOT_STARTED,

    @Column(name = "auction_current_player_id")
    var auctionCurrentPlayerId: UUID? = null,

    @Column(name = "auction_current_bid_amount")
    var auctionCurrentBidAmount: BigDecimal? = null,

    @Column(name = "auction_current_leading_franchise_id")
    var auctionCurrentLeadingFranchiseId: UUID? = null,

    /** Toggled live by the organizer -- see docs/PHASE5.md's Decisions Made (no cap while on). */
    @Column(name = "auction_allow_exceed_purse", nullable = false)
    var auctionAllowExceedPurse: Boolean = false,

    /** One-shot pointer `undo` consumes then clears -- see [com.crichere.backend.auction.AuctionService.undo]. */
    @Column(name = "auction_last_action_type")
    @Enumerated(EnumType.STRING)
    var auctionLastActionType: AuctionLastActionType? = null,

    @Column(name = "auction_last_action_bid_id")
    var auctionLastActionBidId: UUID? = null,

    /** Which player a `SOLD`/`UNSOLD` last-action refers to -- [auctionCurrentPlayerId] itself is cleared the moment that player closes. */
    @Column(name = "auction_last_action_player_id")
    var auctionLastActionPlayerId: UUID? = null,

    /** +1 each time `next-player` opens a player -- the viewer's "Lot N" (docs/PHASE11.md D2). Undo never touches it. */
    @Column(name = "auction_lot_counter", nullable = false)
    var auctionLotCounter: Int = 0,

    /** Optional "bidding opens at" time, informational only (docs/PHASE11.md D3). */
    @Column(name = "auction_scheduled_at")
    var auctionScheduledAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    /** `ANNOUNCED` while [completedAt] is unset, `COMPLETED` once the organizer sets it. No `DRAFT` -- see the class doc. */
    val status: LeagueStatus
        get() = if (completedAt == null) LeagueStatus.ANNOUNCED else LeagueStatus.COMPLETED
}

enum class LeagueStatus {
    ANNOUNCED,
    COMPLETED,
}

/** See docs/PHASE5.md. `COMPLETED` is reached either by the pool selling out or the organizer calling `end`. */
enum class AuctionStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
}

/** What [LeagueEntity.auctionLastActionBidId] (when [LeagueEntity.auctionLastActionType] is [BID]) or the current player (when [SOLD]/[UNSOLD]) refers to -- see [com.crichere.backend.auction.AuctionService.undo]. */
enum class AuctionLastActionType {
    BID,
    SOLD,
    UNSOLD,
}

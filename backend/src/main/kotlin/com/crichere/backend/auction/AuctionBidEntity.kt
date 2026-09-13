package com.crichere.backend.auction

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Maps to the `auction_bids` table (V15__add_auction_engine.sql). Full bid history, not just the
 * current-leading snapshot -- see docs/PHASE5.md's Security section. A bid is never deleted;
 * `undo` marks it [reversed] instead, so a post-auction dispute always has a real trail.
 */
@Entity
@Table(name = "auction_bids")
class AuctionBidEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "league_id", nullable = false, updatable = false)
    var leagueId: UUID,

    @Column(name = "player_id", nullable = false, updatable = false)
    var playerId: UUID,

    @Column(name = "franchise_id", nullable = false, updatable = false)
    var franchiseId: UUID,

    @Column(name = "amount", nullable = false, updatable = false)
    var amount: BigDecimal,

    @Column(name = "placed_at", nullable = false, updatable = false)
    var placedAt: Instant = Instant.now(),

    @Column(name = "reversed", nullable = false)
    var reversed: Boolean = false,
)

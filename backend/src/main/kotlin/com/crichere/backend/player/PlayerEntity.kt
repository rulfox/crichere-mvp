package com.crichere.backend.player

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
import java.util.UUID

/**
 * Maps to the `league_players` table (V11__create_league_players_table.sql). [leagueId]/[userId]
 * are plain foreign-key columns, matching every other cross-reference in this codebase (no JPA
 * `@ManyToOne`). [leaveRequestedAt]/[removedAt] implement the request-and-approve leave flow (see
 * docs/PHASE3.md's Decisions Made) -- an organizer approval and a unilateral Remove both just set
 * [removedAt], since the effect on capacity is identical either way.
 */
@Entity
@Table(name = "league_players")
class PlayerEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "league_id", nullable = false, updatable = false)
    var leagueId: UUID,

    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,

    @Column(name = "joined_at", nullable = false, updatable = false)
    var joinedAt: Instant = Instant.now(),

    @Column(name = "payment_screenshot_url", columnDefinition = "TEXT")
    var paymentScreenshotUrl: String? = null,

    @Column(name = "leave_requested_at")
    var leaveRequestedAt: Instant? = null,

    @Column(name = "removed_at")
    var removedAt: Instant? = null,

    /** See docs/PHASE5.md. `null` unless [auctionOutcome] is `SOLD`. */
    @Column(name = "sold_to_franchise_id")
    var soldToFranchiseId: UUID? = null,

    @Column(name = "sold_price")
    var soldPrice: BigDecimal? = null,

    @Column(name = "auction_outcome", nullable = false)
    @Enumerated(EnumType.STRING)
    var auctionOutcome: AuctionOutcome = AuctionOutcome.PENDING,
)

/**
 * `UNSOLD` is a *terminal* state set only when the organizer ends the auction early with players
 * still unclaimed -- closing a player with no bids (the `unsold` action) sets this back to
 * `PENDING` instead, re-queuing it for a later round (see docs/PHASE5.md's Features and
 * `com.crichere.backend.auction.AuctionService`).
 */
enum class AuctionOutcome {
    PENDING,
    SOLD,
    UNSOLD,
}

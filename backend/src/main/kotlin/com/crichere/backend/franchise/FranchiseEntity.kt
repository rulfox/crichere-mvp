package com.crichere.backend.franchise

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Maps to the `league_franchises` table (V12__create_league_franchises_table.sql). A claimed
 * franchise is a real named entity (see docs/PHASE3.md's Decisions Made) -- [name]/[logoUrl] are
 * the franchise's own identity, distinct from [ownerUserId]'s profile. Same
 * leave-request-and-approve shape as [com.crichere.backend.player.PlayerEntity]; deliberately no
 * uniqueness constraint on (league, owner) at the DB level -- multi-franchise ownership by the
 * same user is allowed.
 */
@Entity
@Table(name = "league_franchises")
class FranchiseEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "league_id", nullable = false, updatable = false)
    var leagueId: UUID,

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    var ownerUserId: UUID,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "logo_url", columnDefinition = "TEXT")
    var logoUrl: String? = null,

    @Column(name = "joined_at", nullable = false, updatable = false)
    var joinedAt: Instant = Instant.now(),

    @Column(name = "payment_screenshot_url", columnDefinition = "TEXT")
    var paymentScreenshotUrl: String? = null,

    @Column(name = "leave_requested_at")
    var leaveRequestedAt: Instant? = null,

    @Column(name = "removed_at")
    var removedAt: Instant? = null,
)

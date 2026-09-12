package com.crichere.backend.league

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Composite-key identifier for [LeagueFollowEntity], per JPA's `@IdClass` contract (a matching
 * no-arg-constructible, `Serializable`, `equals`/`hashCode`-implementing class -- a Kotlin `data
 * class` with default values satisfies all of that). This is the first composite-key entity in
 * this codebase -- everything else uses a surrogate UUID `id` -- because a follow row is a plain
 * many-to-many join with nothing that ever needs to reference one by its own id, only by the
 * (league, user) pair.
 */
data class LeagueFollowId(
    var leagueId: UUID = UUID(0, 0),
    var userId: UUID = UUID(0, 0),
) : Serializable

/** Maps to the `league_follows` table (V13__create_league_follows_table.sql). No fee, no capacity, no proof -- see docs/PHASE3.md's Decisions Made. */
@Entity
@Table(name = "league_follows")
@IdClass(LeagueFollowId::class)
class LeagueFollowEntity(
    @Id
    @Column(name = "league_id", nullable = false, updatable = false)
    var leagueId: UUID,

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,

    @Column(name = "followed_at", nullable = false, updatable = false)
    var followedAt: Instant = Instant.now(),
)

package com.crichere.backend.league

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Maps to the `league_roles` table (V16__add_league_roles.sql) -- see docs/PHASE7.md. A row is
 * never deleted; revoking sets [revokedAt] instead, so a league always has a real audit trail of
 * who was ever delegated authority over it (same reasoning `auction_bids.reversed` already uses).
 * Plain FK columns, no JPA relations, matching every other entity in this codebase.
 */
@Entity
@Table(name = "league_roles")
class LeagueRoleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "league_id", nullable = false, updatable = false)
    var leagueId: UUID,

    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,

    @Column(name = "role", nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    var role: LeagueRole,

    @Column(name = "granted_by_user_id", nullable = false, updatable = false)
    var grantedByUserId: UUID,

    @Column(name = "granted_at", nullable = false, updatable = false)
    var grantedAt: Instant = Instant.now(),

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
)

/** Only `CO_ORGANIZER` ships in Phase 7 -- a real enum (not a bare string) so a future second role adds one case here, not a new column. */
enum class LeagueRole {
    CO_ORGANIZER,
}

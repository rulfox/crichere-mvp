package com.crichere.backend.league

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Maps to the `leagues` table (V8__create_leagues_table.sql). [name]/[state]/[district]/[city]/
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

    @Column(name = "city", nullable = false)
    var city: String,

    @Column(name = "ground_id")
    var groundId: UUID? = null,

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

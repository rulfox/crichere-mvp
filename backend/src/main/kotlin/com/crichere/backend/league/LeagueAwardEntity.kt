package com.crichere.backend.league

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

/**
 * Maps to the `league_awards` table (V9__create_league_awards_table.sql). One generic,
 * repeatable list per league -- "First Prize"/"Man of the Match"/anything else are just
 * conventional [name] values, never a fixed enum (see docs/PHASE2.md's Decisions Made).
 * [leagueId] is a plain foreign-key column, matching every other cross-reference in this
 * codebase.
 */
@Entity
@Table(name = "league_awards")
class LeagueAwardEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "league_id", nullable = false, updatable = false)
    var leagueId: UUID,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "cash_amount")
    var cashAmount: BigDecimal? = null,

    @Column(name = "has_trophy", nullable = false)
    var hasTrophy: Boolean = false,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
)

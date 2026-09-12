package com.crichere.backend.player

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Spring Data repository for [PlayerEntity]. "Active" everywhere below means `removed_at IS NULL`. */
interface PlayerRepository : JpaRepository<PlayerEntity, UUID> {

    fun findByLeagueIdAndRemovedAtIsNull(leagueId: UUID): List<PlayerEntity>

    fun countByLeagueIdAndRemovedAtIsNull(leagueId: UUID): Long

    /** Double-submit guard (see docs/PHASE3.md) -- backed by the `league_players_active_unique` partial index as the race-condition backstop. */
    fun existsByLeagueIdAndUserIdAndRemovedAtIsNull(leagueId: UUID, userId: UUID): Boolean

    /** Every league a user has actively joined as a player -- feeds `GET /api/v1/me/leagues`'s "playing" list. */
    fun findByUserIdAndRemovedAtIsNull(userId: UUID): List<PlayerEntity>
}

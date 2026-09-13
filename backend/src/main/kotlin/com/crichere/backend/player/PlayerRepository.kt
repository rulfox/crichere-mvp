package com.crichere.backend.player

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

/** Spring Data repository for [PlayerEntity]. "Active" everywhere below means `removed_at IS NULL`. */
interface PlayerRepository : JpaRepository<PlayerEntity, UUID> {

    fun findByLeagueIdAndRemovedAtIsNull(leagueId: UUID): List<PlayerEntity>

    fun countByLeagueIdAndRemovedAtIsNull(leagueId: UUID): Long

    /** Double-submit guard (see docs/PHASE3.md) -- backed by the `league_players_active_unique` partial index as the race-condition backstop. */
    fun existsByLeagueIdAndUserIdAndRemovedAtIsNull(leagueId: UUID, userId: UUID): Boolean

    /** Every league a user has actively joined as a player -- feeds `GET /api/v1/me/leagues`'s "playing" list. */
    fun findByUserIdAndRemovedAtIsNull(userId: UUID): List<PlayerEntity>

    /** The auction pool: still-unsold active joins -- see `com.crichere.backend.auction.AuctionService`. */
    fun findByLeagueIdAndAuctionOutcome(leagueId: UUID, outcome: AuctionOutcome): List<PlayerEntity>

    fun countByLeagueIdAndAuctionOutcome(leagueId: UUID, outcome: AuctionOutcome): Long

    /** A franchise's won squad -- feeds the auction results view and live purse-remaining checks. */
    fun findBySoldToFranchiseId(franchiseId: UUID): List<PlayerEntity>

    /** Also rejects a player id that's real but belongs to a *different* league, same guard `FranchiseRepository` callers use. */
    fun findByIdAndLeagueId(id: UUID, leagueId: UUID): Optional<PlayerEntity>
}

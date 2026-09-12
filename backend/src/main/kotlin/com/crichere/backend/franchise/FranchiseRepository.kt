package com.crichere.backend.franchise

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data repository for [FranchiseEntity]. "Active" everywhere below means
 * `removed_at IS NULL`. Deliberately no `existsBy...` uniqueness helper -- multi-franchise
 * ownership by the same user in the same league is allowed (see docs/PHASE3.md's Decisions Made).
 */
interface FranchiseRepository : JpaRepository<FranchiseEntity, UUID> {

    fun findByLeagueIdAndRemovedAtIsNull(leagueId: UUID): List<FranchiseEntity>

    fun countByLeagueIdAndRemovedAtIsNull(leagueId: UUID): Long

    /** Every league a user actively owns a franchise in -- feeds `GET /api/v1/me/leagues`'s "franchiseOwner" list. */
    fun findByOwnerUserIdAndRemovedAtIsNull(ownerUserId: UUID): List<FranchiseEntity>
}

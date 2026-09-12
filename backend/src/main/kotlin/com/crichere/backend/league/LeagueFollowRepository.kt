package com.crichere.backend.league

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Spring Data repository for [LeagueFollowEntity], keyed by [LeagueFollowId]. */
interface LeagueFollowRepository : JpaRepository<LeagueFollowEntity, LeagueFollowId> {

    fun existsByLeagueIdAndUserId(leagueId: UUID, userId: UUID): Boolean

    fun deleteByLeagueIdAndUserId(leagueId: UUID, userId: UUID)

    /** Every league a user follows -- feeds `GET /api/v1/me/leagues`'s "following" list. */
    fun findByUserId(userId: UUID): List<LeagueFollowEntity>
}

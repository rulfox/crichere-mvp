package com.crichere.backend.league

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Spring Data repository for [LeagueAwardEntity]. */
interface LeagueAwardRepository : JpaRepository<LeagueAwardEntity, UUID> {
    fun findByLeagueIdOrderByDisplayOrder(leagueId: UUID): List<LeagueAwardEntity>
    fun countByLeagueId(leagueId: UUID): Long
}

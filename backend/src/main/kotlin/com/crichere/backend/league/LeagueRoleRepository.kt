package com.crichere.backend.league

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

/** Spring Data repository for [LeagueRoleEntity]. "Active" everywhere below means `revoked_at IS NULL`. */
interface LeagueRoleRepository : JpaRepository<LeagueRoleEntity, UUID> {

    /** The hot path -- called on every [LeagueAuthorization.isOrganizer] check for a caller who isn't the plain organizer. Served directly by the `league_roles_active_unique` partial index. */
    fun existsByLeagueIdAndUserIdAndRevokedAtIsNull(leagueId: UUID, userId: UUID): Boolean

    /** A league's current co-organizers -- feeds `LeagueResponse.coOrganizers` and the mobile management screen. */
    fun findByLeagueIdAndRevokedAtIsNull(leagueId: UUID): List<LeagueRoleEntity>

    /** Also rejects a role id that's real but belongs to a *different* league, same guard every other `findByIdAnd<Parent>` lookup in this codebase uses. */
    fun findByIdAndLeagueId(id: UUID, leagueId: UUID): Optional<LeagueRoleEntity>
}

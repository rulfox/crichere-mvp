package com.crichere.backend.league.dto

import java.time.Instant
import java.util.UUID

/** One active co-organizer grant, embedded in [LeagueResponse] and returned by the roles endpoints (docs/PHASE7.md). */
data class LeagueRoleResponse(
    val id: UUID,
    val userId: UUID,
    /** Resolves the delegate's `ProfileEntity.name` -- may be `null` if they never set one, same as every other roster row in this app. */
    val name: String?,
    val grantedAt: Instant,
)

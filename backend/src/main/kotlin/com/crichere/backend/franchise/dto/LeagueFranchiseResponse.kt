package com.crichere.backend.franchise.dto

import java.time.Instant
import java.util.UUID

/**
 * One row of a league's `franchises` list (embedded in `LeagueResponse`), and the response of
 * claim/remove/leave-request/approve/dismiss/logo-upload. [paymentScreenshotUrl]/
 * [leaveRequestedAt] are nulled out by the mapping function unless the caller is the league's
 * organizer or this row's own owner -- same redaction rule as `LeaguePlayerResponse`. [name]/
 * [logoUrl] are the franchise's own identity (always visible); [ownerName] resolves the owning
 * user's `ProfileEntity.name`, same reasoning as `LeaguePlayerResponse.name`.
 */
data class LeagueFranchiseResponse(
    val id: UUID,
    val ownerUserId: UUID,
    val ownerName: String?,
    val name: String,
    val logoUrl: String?,
    val joinedAt: Instant,
    val paymentScreenshotUrl: String?,
    val leaveRequestedAt: Instant?,
)

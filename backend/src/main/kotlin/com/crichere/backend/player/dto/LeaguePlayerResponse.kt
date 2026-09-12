package com.crichere.backend.player.dto

import java.time.Instant
import java.util.UUID

/**
 * One row of a league's `players` list (embedded in `LeagueResponse`), and the response of
 * join/remove/leave-request/approve/dismiss. [paymentScreenshotUrl]/[leaveRequestedAt] are nulled
 * out by the mapping function unless the caller is the league's organizer or this row's own
 * user -- see docs/PHASE3.md's implementation plan, decision 2. [name] resolves the joining
 * user's `ProfileEntity.name` (may be `null` if they never set one) -- a roster of raw ids would
 * otherwise be a real usability gap.
 */
data class LeaguePlayerResponse(
    val id: UUID,
    val userId: UUID,
    val name: String?,
    val joinedAt: Instant,
    val paymentScreenshotUrl: String?,
    val leaveRequestedAt: Instant?,
)

package com.crichere.backend.franchise

import com.crichere.backend.franchise.dto.LeagueFranchiseResponse
import com.crichere.backend.profile.ProfileRepository
import java.util.UUID

/**
 * Shared response mapping for [FranchiseEntity], used by both [FranchiseService] itself and
 * [com.crichere.backend.league.LeagueService] (which embeds a league's `franchises` list in
 * `LeagueResponse`) -- same redaction rule as [com.crichere.backend.player.PlayerEntity]'s own
 * mapping (see docs/PHASE3.md's implementation plan, decision 2): [callerId] only decides whether
 * [FranchiseEntity.paymentScreenshotUrl]/[FranchiseEntity.leaveRequestedAt] are visible, redacted
 * unless the caller is the league's organizer or this row's own owner. [FranchiseEntity.name]/
 * [FranchiseEntity.logoUrl] are the franchise's own identity and always visible.
 */
fun FranchiseEntity.toResponse(callerId: UUID?, organizerUserId: UUID, profileRepository: ProfileRepository): LeagueFranchiseResponse {
    val visible = callerId != null && (callerId == organizerUserId || callerId == ownerUserId)
    return LeagueFranchiseResponse(
        id = requireNotNull(id),
        ownerUserId = ownerUserId,
        ownerName = profileRepository.findById(ownerUserId).orElse(null)?.name,
        name = name,
        logoUrl = logoUrl,
        joinedAt = joinedAt,
        paymentScreenshotUrl = if (visible) paymentScreenshotUrl else null,
        leaveRequestedAt = if (visible) leaveRequestedAt else null,
    )
}

package com.crichere.backend.player

import com.crichere.backend.common.PaymentScreenshotUrlSigner
import com.crichere.backend.player.dto.LeaguePlayerResponse
import com.crichere.backend.profile.ProfileRepository
import java.util.UUID

/**
 * Shared response mapping for [PlayerEntity], used by both [PlayerService] itself and
 * [com.crichere.backend.league.LeagueService] (which embeds a league's `players` list in
 * `LeagueResponse`) -- a top-level function rather than a private method on either service, so
 * neither has to duplicate the redaction rule. [callerId] is only used to decide whether
 * [PlayerEntity.paymentScreenshotUrl]/[PlayerEntity.leaveRequestedAt] are visible -- redacted
 * unless the caller is the league's organizer or this row's own user (see docs/PHASE3.md's
 * implementation plan, decision 2). A visible screenshot goes out as a short-lived signed link, never
 * the stored URL -- see [PaymentScreenshotUrlSigner].
 */
fun PlayerEntity.toResponse(
    callerId: UUID?,
    organizerUserId: UUID,
    profileRepository: ProfileRepository,
    screenshotSigner: PaymentScreenshotUrlSigner,
): LeaguePlayerResponse {
    val visible = callerId != null && (callerId == organizerUserId || callerId == userId)
    val profile = profileRepository.findById(userId).orElse(null)
    return LeaguePlayerResponse(
        id = requireNotNull(id),
        userId = userId,
        name = profile?.name,
        joinedAt = joinedAt,
        paymentScreenshotUrl = if (visible) screenshotSigner.readUrl(paymentScreenshotUrl, leagueId, userId) else null,
        leaveRequestedAt = if (visible) leaveRequestedAt else null,
        playingRole = profile?.playingRole,
    )
}

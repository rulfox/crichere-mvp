package com.crichere.backend.player

import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.common.PaymentScreenshotUrlSigner
import com.crichere.backend.league.LeagueAuthorization
import com.crichere.backend.league.LeagueCapacityFullException
import com.crichere.backend.league.LeagueCompletedException
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueNotFoundException
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.league.LeagueStatus
import com.crichere.backend.league.PaymentScreenshotRequiredException
import com.crichere.backend.league.requireAuctionNotStarted
import com.crichere.backend.notification.FcmSender
import com.crichere.backend.player.dto.LeaguePlayerJoinRequest
import com.crichere.backend.player.dto.LeaguePlayerResponse
import com.crichere.backend.profile.ProfileRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Joining a league as a player, and the request-and-approve leave flow (see docs/PHASE3.md's
 * Decisions Made). [join] mirrors [com.crichere.backend.league.LeagueService.create]'s
 * rate-limit-then-validate shape; [LeagueAuthorization] is the shared component
 * [com.crichere.backend.league.LeagueService] itself now also uses, not a private copy.
 */
@Service
class PlayerService(
    private val playerRepository: PlayerRepository,
    private val leagueRepository: LeagueRepository,
    private val profileRepository: ProfileRepository,
    private val contentRateLimiter: ContentRateLimiter,
    private val leagueAuthorization: LeagueAuthorization,
    private val fcmSender: FcmSender,
    private val screenshotSigner: PaymentScreenshotUrlSigner,
) {

    /**
     * `POST /api/v1/leagues/{leagueId}/players`. A payment screenshot is required only when the
     * league has a `playerFee` set (see docs/PHASE3.md). The `league_players_active_unique`
     * partial index is the race-condition backstop behind the pre-insert [AlreadyJoinedException]
     * check -- two near-simultaneous joins from the same user can both pass the check but only
     * one can win the insert.
     *
     * @throws LeagueNotFoundException [leagueId] doesn't exist.
     * @throws LeagueCompletedException the league is already completed.
     * @throws ContentRateLimitExceededException the caller has joined too many leagues recently.
     * @throws LeagueCapacityFullException `playersRequired` has already been reached.
     * @throws com.crichere.backend.league.PaymentScreenshotRequiredException a fee is set but [request] has no screenshot.
     * @throws AlreadyJoinedException the caller already has an active join row for this league.
     * @throws com.crichere.backend.auction.AuctionAlreadyStartedException the auction has already started (see docs/PHASE5.md).
     */
    @Transactional
    fun join(leagueId: UUID, callerId: UUID, request: LeaguePlayerJoinRequest): LeaguePlayerResponse {
        val league = findLeagueOrThrow(leagueId)
        if (league.status == LeagueStatus.COMPLETED) throw LeagueCompletedException()
        requireAuctionNotStarted(league)
        contentRateLimiter.tryConsumeForPlayerJoin(callerId)?.let { retryAfter ->
            throw ContentRateLimitExceededException(retryAfter)
        }

        val activeCount = playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId)
        val required = league.playersRequired
        if (required != null && activeCount >= required) throw LeagueCapacityFullException("player")

        if (league.playerFee != null && request.paymentScreenshotUrl.isNullOrBlank()) {
            throw PaymentScreenshotRequiredException("player")
        }

        if (playerRepository.existsByLeagueIdAndUserIdAndRemovedAtIsNull(leagueId, callerId)) throw AlreadyJoinedException()

        val player = PlayerEntity(
            leagueId = leagueId,
            userId = callerId,
            paymentScreenshotUrl = request.paymentScreenshotUrl,
        )
        val saved = try {
            playerRepository.save(player)
        } catch (e: DataIntegrityViolationException) {
            throw AlreadyJoinedException()
        }
        return saved.toResponse(callerId, league.organizerUserId)
    }

    /**
     * `DELETE /api/v1/leagues/{leagueId}/players/{playerId}`. Organizer-only -- a unilateral
     * removal, distinct from the self-requested [requestLeave]/organizer-approved [approveLeave]
     * pair, but landing on the same [PlayerEntity.removedAt] field either way.
     *
     * @throws LeagueNotFoundException [leagueId] doesn't exist.
     * @throws com.crichere.backend.league.NotOrganizerException [callerId] is not this league's organizer.
     * @throws LeaguePlayerNotFoundException [playerId] doesn't exist under this league.
     * @throws com.crichere.backend.auction.AuctionAlreadyStartedException the auction has already started -- the roster freezes with it (see docs/PHASE5.md).
     */
    @Transactional
    fun remove(leagueId: UUID, playerId: UUID, callerId: UUID) {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        requireAuctionNotStarted(league)
        val player = findPlayerOrThrow(leagueId, playerId)
        player.removedAt = Instant.now()
        playerRepository.save(player)
    }

    /**
     * `POST /api/v1/leagues/{leagueId}/players/{playerId}/leave-request`. Self-scoped -- only the
     * player row's own user can request to leave. Does not free the slot by itself; the organizer
     * must [approveLeave] first (see docs/PHASE3.md's Decisions Made).
     *
     * @throws LeaguePlayerNotFoundException [playerId] doesn't exist under [leagueId].
     * @throws NotPlayerOwnerException [callerId] is not this player row's own user.
     * @throws com.crichere.backend.auction.AuctionAlreadyStartedException the auction has already started -- the roster freezes with it (see docs/PHASE5.md).
     */
    @Transactional
    fun requestLeave(leagueId: UUID, playerId: UUID, callerId: UUID): LeaguePlayerResponse {
        val league = findLeagueOrThrow(leagueId)
        requireAuctionNotStarted(league)
        val player = findPlayerOrThrow(leagueId, playerId)
        if (player.userId != callerId) throw NotPlayerOwnerException()
        player.leaveRequestedAt = Instant.now()
        val response = playerRepository.save(player).toResponse(callerId, league.organizerUserId)

        val name = profileRepository.findById(callerId).orElse(null)?.name ?: "A player"
        fcmSender.sendToUser(league.organizerUserId, league.name, "$name requested to leave", mapOf("leagueId" to leagueId.toString()))
        return response
    }

    /**
     * `POST /api/v1/leagues/{leagueId}/players/{playerId}/leave-request/approve`. Organizer-only.
     * Frees the slot (sets [PlayerEntity.removedAt]).
     *
     * @throws com.crichere.backend.league.NotOrganizerException [callerId] is not this league's organizer.
     * @throws LeaguePlayerNotFoundException [playerId] doesn't exist under [leagueId].
     * @throws NoLeaveRequestPendingException [playerId] has no pending leave request.
     * @throws com.crichere.backend.auction.AuctionAlreadyStartedException the auction has already started -- the roster freezes with it (see docs/PHASE5.md).
     */
    @Transactional
    fun approveLeave(leagueId: UUID, playerId: UUID, callerId: UUID): LeaguePlayerResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        requireAuctionNotStarted(league)
        val player = findPlayerOrThrow(leagueId, playerId)
        if (player.leaveRequestedAt == null) throw NoLeaveRequestPendingException()
        player.removedAt = Instant.now()
        val response = playerRepository.save(player).toResponse(callerId, league.organizerUserId)

        fcmSender.sendToUser(player.userId, league.name, "Your request to leave was approved", mapOf("leagueId" to leagueId.toString()))
        return response
    }

    /**
     * `POST /api/v1/leagues/{leagueId}/players/{playerId}/leave-request/dismiss`. Organizer-only.
     * Clears the pending request without removing the player.
     *
     * @throws com.crichere.backend.league.NotOrganizerException [callerId] is not this league's organizer.
     * @throws LeaguePlayerNotFoundException [playerId] doesn't exist under [leagueId].
     * @throws NoLeaveRequestPendingException [playerId] has no pending leave request.
     */
    @Transactional
    fun dismissLeave(leagueId: UUID, playerId: UUID, callerId: UUID): LeaguePlayerResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        val player = findPlayerOrThrow(leagueId, playerId)
        if (player.leaveRequestedAt == null) throw NoLeaveRequestPendingException()
        player.leaveRequestedAt = null
        val response = playerRepository.save(player).toResponse(callerId, league.organizerUserId)

        fcmSender.sendToUser(player.userId, league.name, "Your request to leave was dismissed", mapOf("leagueId" to leagueId.toString()))
        return response
    }

    private fun findLeagueOrThrow(leagueId: UUID): LeagueEntity =
        leagueRepository.findById(leagueId).orElseThrow { LeagueNotFoundException() }

    /** Also rejects a player id that's real but belongs to a *different* league -- see [LeaguePlayerNotFoundException]. */
    private fun findPlayerOrThrow(leagueId: UUID, playerId: UUID): PlayerEntity {
        val player = playerRepository.findById(playerId).orElseThrow { LeaguePlayerNotFoundException() }
        if (player.leagueId != leagueId) throw LeaguePlayerNotFoundException()
        return player
    }

    private fun PlayerEntity.toResponse(callerId: UUID?, organizerUserId: UUID): LeaguePlayerResponse =
        toResponse(callerId, organizerUserId, profileRepository, screenshotSigner)
}

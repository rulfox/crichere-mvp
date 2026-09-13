package com.crichere.backend.franchise

import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.common.PhotoUploadService
import com.crichere.backend.common.PhotoUploadUrlResponse
import com.crichere.backend.franchise.dto.LeagueFranchiseClaimRequest
import com.crichere.backend.franchise.dto.LeagueFranchiseResponse
import com.crichere.backend.league.LeagueCapacityFullException
import com.crichere.backend.league.LeagueCompletedException
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueNotFoundException
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.league.LeagueStatus
import com.crichere.backend.league.PaymentScreenshotRequiredException
import com.crichere.backend.league.requireAuctionNotStarted
import com.crichere.backend.league.requireOrganizer
import com.crichere.backend.profile.ProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Claiming a franchise in a league, and its request-and-approve leave flow -- same shape as
 * [com.crichere.backend.player.PlayerService], with two real differences (see docs/PHASE3.md's
 * Decisions Made): a claim carries its own [LeagueFranchiseClaimRequest.name]/`logoUrl`, and
 * multiple claims by the same user in the same league are allowed (no existence check, no unique
 * DB constraint -- dual roles/multi-franchise ownership is intentional).
 */
@Service
class FranchiseService(
    private val franchiseRepository: FranchiseRepository,
    private val leagueRepository: LeagueRepository,
    private val profileRepository: ProfileRepository,
    private val contentRateLimiter: ContentRateLimiter,
    private val photoUploadService: PhotoUploadService,
) {

    /**
     * `POST /api/v1/leagues/{leagueId}/franchises`. A payment screenshot is required only when
     * the league has a `franchiseFee` set. Unlike [com.crichere.backend.player.PlayerService.join],
     * there is no already-claimed check -- a second claim by the same user is a valid state.
     *
     * @throws LeagueNotFoundException [leagueId] doesn't exist.
     * @throws LeagueCompletedException the league is already completed.
     * @throws ContentRateLimitExceededException the caller has claimed too many franchises recently.
     * @throws LeagueCapacityFullException `franchisesRequired` has already been reached.
     * @throws com.crichere.backend.league.PaymentScreenshotRequiredException a fee is set but [request] has no screenshot.
     * @throws com.crichere.backend.auction.AuctionAlreadyStartedException the auction has already started (see docs/PHASE5.md).
     */
    @Transactional
    fun claim(leagueId: UUID, callerId: UUID, request: LeagueFranchiseClaimRequest): LeagueFranchiseResponse {
        val league = findLeagueOrThrow(leagueId)
        if (league.status == LeagueStatus.COMPLETED) throw LeagueCompletedException()
        requireAuctionNotStarted(league)
        contentRateLimiter.tryConsumeForFranchiseClaim(callerId)?.let { retryAfter ->
            throw ContentRateLimitExceededException(retryAfter)
        }

        val activeCount = franchiseRepository.countByLeagueIdAndRemovedAtIsNull(leagueId)
        val required = league.franchisesRequired
        if (required != null && activeCount >= required) throw LeagueCapacityFullException("franchise")

        if (league.franchiseFee != null && request.paymentScreenshotUrl.isNullOrBlank()) {
            throw PaymentScreenshotRequiredException("franchise")
        }

        val franchise = FranchiseEntity(
            leagueId = leagueId,
            ownerUserId = callerId,
            name = request.name,
            logoUrl = request.logoUrl,
            paymentScreenshotUrl = request.paymentScreenshotUrl,
        )
        return franchiseRepository.save(franchise).toResponse(callerId, league.organizerUserId)
    }

    /**
     * `DELETE /api/v1/leagues/{leagueId}/franchises/{franchiseId}`. Organizer-only.
     *
     * @throws LeagueNotFoundException [leagueId] doesn't exist.
     * @throws com.crichere.backend.league.NotOrganizerException [callerId] is not this league's organizer.
     * @throws LeagueFranchiseNotFoundException [franchiseId] doesn't exist under this league.
     * @throws com.crichere.backend.auction.AuctionAlreadyStartedException the auction has already started -- the roster freezes with it (see docs/PHASE5.md).
     */
    @Transactional
    fun remove(leagueId: UUID, franchiseId: UUID, callerId: UUID) {
        val league = findLeagueOrThrow(leagueId)
        requireOrganizer(league, callerId)
        requireAuctionNotStarted(league)
        val franchise = findFranchiseOrThrow(leagueId, franchiseId)
        franchise.removedAt = Instant.now()
        franchiseRepository.save(franchise)
    }

    /**
     * `POST /api/v1/leagues/{leagueId}/franchises/{franchiseId}/leave-request`. Self-scoped.
     *
     * @throws LeagueFranchiseNotFoundException [franchiseId] doesn't exist under [leagueId].
     * @throws NotFranchiseOwnerException [callerId] is not this franchise's own owner.
     * @throws com.crichere.backend.auction.AuctionAlreadyStartedException the auction has already started -- the roster freezes with it (see docs/PHASE5.md).
     */
    @Transactional
    fun requestLeave(leagueId: UUID, franchiseId: UUID, callerId: UUID): LeagueFranchiseResponse {
        val league = findLeagueOrThrow(leagueId)
        requireAuctionNotStarted(league)
        val franchise = findFranchiseOrThrow(leagueId, franchiseId)
        if (franchise.ownerUserId != callerId) throw NotFranchiseOwnerException()
        franchise.leaveRequestedAt = Instant.now()
        return franchiseRepository.save(franchise).toResponse(callerId, league.organizerUserId)
    }

    /**
     * `POST /api/v1/leagues/{leagueId}/franchises/{franchiseId}/leave-request/approve`. Organizer-only.
     *
     * @throws com.crichere.backend.league.NotOrganizerException [callerId] is not this league's organizer.
     * @throws LeagueFranchiseNotFoundException [franchiseId] doesn't exist under [leagueId].
     * @throws NoLeaveRequestPendingException [franchiseId] has no pending leave request.
     * @throws com.crichere.backend.auction.AuctionAlreadyStartedException the auction has already started -- the roster freezes with it (see docs/PHASE5.md).
     */
    @Transactional
    fun approveLeave(leagueId: UUID, franchiseId: UUID, callerId: UUID): LeagueFranchiseResponse {
        val league = findLeagueOrThrow(leagueId)
        requireOrganizer(league, callerId)
        requireAuctionNotStarted(league)
        val franchise = findFranchiseOrThrow(leagueId, franchiseId)
        if (franchise.leaveRequestedAt == null) throw NoLeaveRequestPendingException()
        franchise.removedAt = Instant.now()
        return franchiseRepository.save(franchise).toResponse(callerId, league.organizerUserId)
    }

    /**
     * `POST /api/v1/leagues/{leagueId}/franchises/{franchiseId}/leave-request/dismiss`. Organizer-only.
     *
     * @throws com.crichere.backend.league.NotOrganizerException [callerId] is not this league's organizer.
     * @throws LeagueFranchiseNotFoundException [franchiseId] doesn't exist under [leagueId].
     * @throws NoLeaveRequestPendingException [franchiseId] has no pending leave request.
     */
    @Transactional
    fun dismissLeave(leagueId: UUID, franchiseId: UUID, callerId: UUID): LeagueFranchiseResponse {
        val league = findLeagueOrThrow(leagueId)
        requireOrganizer(league, callerId)
        val franchise = findFranchiseOrThrow(leagueId, franchiseId)
        if (franchise.leaveRequestedAt == null) throw NoLeaveRequestPendingException()
        franchise.leaveRequestedAt = null
        return franchiseRepository.save(franchise).toResponse(callerId, league.organizerUserId)
    }

    /**
     * `POST /api/v1/leagues/{leagueId}/franchises/{franchiseId}/logo-upload-url`. The caller must
     * be either this league's organizer or this franchise's own owner (unlike league logo/banner,
     * which is organizer-only -- the franchise owner is the one who actually sets its identity).
     *
     * @throws com.crichere.backend.league.NotOrganizerException never thrown here -- see [NotFranchiseOwnerException] instead.
     * @throws NotFranchiseOwnerException [callerId] is neither the organizer nor this franchise's owner.
     */
    @Transactional(readOnly = true)
    fun createLogoUploadUrl(leagueId: UUID, franchiseId: UUID, callerId: UUID): PhotoUploadUrlResponse {
        val league = findLeagueOrThrow(leagueId)
        val franchise = findFranchiseOrThrow(leagueId, franchiseId)
        if (callerId != league.organizerUserId && callerId != franchise.ownerUserId) throw NotFranchiseOwnerException()
        return photoUploadService.createFranchiseLogoUploadUrl(franchiseId)
    }

    private fun findLeagueOrThrow(leagueId: UUID): LeagueEntity =
        leagueRepository.findById(leagueId).orElseThrow { LeagueNotFoundException() }

    /** Also rejects a franchise id that's real but belongs to a *different* league -- see [LeagueFranchiseNotFoundException]. */
    private fun findFranchiseOrThrow(leagueId: UUID, franchiseId: UUID): FranchiseEntity {
        val franchise = franchiseRepository.findById(franchiseId).orElseThrow { LeagueFranchiseNotFoundException() }
        if (franchise.leagueId != leagueId) throw LeagueFranchiseNotFoundException()
        return franchise
    }

    private fun FranchiseEntity.toResponse(callerId: UUID?, organizerUserId: UUID): LeagueFranchiseResponse =
        toResponse(callerId, organizerUserId, profileRepository)
}

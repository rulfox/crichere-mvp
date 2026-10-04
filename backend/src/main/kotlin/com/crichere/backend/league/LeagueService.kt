package com.crichere.backend.league

import com.crichere.backend.auction.AuctionInProgressException
import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.common.PhotoUploadService
import com.crichere.backend.common.PhotoUploadUrlResponse
import com.crichere.backend.franchise.FranchiseRepository
import com.crichere.backend.franchise.toResponse
import com.crichere.backend.ground.GroundRepository
import com.crichere.backend.league.dto.AuctionSettingsSaveRequest
import com.crichere.backend.league.dto.LeagueAwardResponse
import com.crichere.backend.league.dto.LeagueAwardSaveRequest
import com.crichere.backend.league.dto.LeagueResponse
import com.crichere.backend.league.dto.LeagueRoleResponse
import com.crichere.backend.league.dto.LeagueSaveRequest
import com.crichere.backend.player.PlayerRepository
import com.crichere.backend.player.toResponse
import com.crichere.backend.profile.ProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class LeagueService(
    private val leagueRepository: LeagueRepository,
    private val leagueAwardRepository: LeagueAwardRepository,
    private val leagueFollowRepository: LeagueFollowRepository,
    private val groundRepository: GroundRepository,
    private val playerRepository: PlayerRepository,
    private val franchiseRepository: FranchiseRepository,
    private val profileRepository: ProfileRepository,
    private val contentRateLimiter: ContentRateLimiter,
    private val photoUploadService: PhotoUploadService,
    private val leagueAuthorization: LeagueAuthorization,
    private val leagueRoleRepository: LeagueRoleRepository,
) {

    /**
     * `GET /api/v1/leagues/{id}`. [callerId] is `null` for an anonymous caller (the endpoint is
     * public) -- only used to decide payment-screenshot/leave-request-timestamp redaction on the
     * embedded `players`/`franchises` rows and to compute `isFollowing` (see
     * docs/PHASE3.md's implementation plan, decision 2).
     */
    @Transactional(readOnly = true)
    fun getLeague(leagueId: UUID, callerId: UUID?): LeagueResponse =
        findLeagueOrThrow(leagueId).toResponse(callerId)

    /**
     * `GET /api/v1/leagues` with the area filters (state/district/city, each optional). Mutually
     * exclusive with [listNearest] in the UI -- see docs/PHASE2.md's Decisions Made.
     */
    @Transactional(readOnly = true)
    fun listByArea(state: String?, district: String?, city: String?, callerId: UUID?): List<LeagueResponse> =
        leagueRepository.findByAreaFilters(state, district, city).map { it.toResponse(callerId) }

    /**
     * `GET /api/v1/leagues?near=lat,lng`. Only leagues with a ground attached participate --
     * no city/district-centroid fallback (see docs/PHASE2.md's Decisions Made).
     */
    @Transactional(readOnly = true)
    fun listNearest(latitude: Double, longitude: Double, callerId: UUID?): List<LeagueResponse> =
        leagueRepository.findNearest(latitude, longitude).map { it.toResponse(callerId) }

    /**
     * `POST /api/v1/leagues`. Rate-limited per caller (see [ContentRateLimiter]) -- an open,
     * unrestricted-volume endpoint (any logged-in user, no approval gate, per docs/PHASE2.md's
     * Decisions Made) with no other abuse control. [LeagueSaveRequest.awards], if present,
     * are created in the same transaction so the mobile client's three pre-suggested rows land
     * in one call, not three follow-ups.
     *
     * @throws ContentRateLimitExceededException the caller has created too many leagues recently.
     * @throws GroundNotFoundException [request]'s `groundId` doesn't reference a real ground.
     * @throws OrganizerUpiRequiredException a fee is set but [request.organizerUpiId] is blank.
     */
    @Transactional
    fun create(organizerUserId: UUID, request: LeagueSaveRequest): LeagueResponse {
        contentRateLimiter.tryConsumeForLeagueCreate(organizerUserId)?.let { retryAfter ->
            throw ContentRateLimitExceededException(retryAfter)
        }
        requireGroundExistsIfReferenced(request.groundId)
        requireOrganizerUpiIdIfFeeSet(request)

        val league = LeagueEntity(
            organizerUserId = organizerUserId,
            name = request.name,
            description = request.description,
            logoUrl = request.logoUrl,
            bannerUrl = request.bannerUrl,
            state = request.state,
            district = request.district,
            city = request.city,
            groundId = request.groundId,
            startsOn = requireNotNull(request.startsOn),
            format = request.format,
            franchisesRequired = request.franchisesRequired,
            playersRequired = request.playersRequired,
            franchiseFee = request.franchiseFee,
            playerFee = request.playerFee,
            organizerUpiId = request.organizerUpiId,
        )
        val saved = leagueRepository.save(league)

        val initialAwards = request.awards.orEmpty().mapIndexed { index, awardRequest ->
            LeagueAwardEntity(
                leagueId = requireNotNull(saved.id),
                name = awardRequest.name,
                cashAmount = awardRequest.cashAmount,
                hasTrophy = awardRequest.hasTrophy,
                displayOrder = index,
            )
        }
        if (initialAwards.isNotEmpty()) leagueAwardRepository.saveAll(initialAwards)

        return saved.toResponse(organizerUserId)
    }

    /**
     * `PUT /api/v1/leagues/{id}`. Full-replace, organizer-only (see [LeagueExceptions]) --
     * mirrors Phase 1's `PUT /profiles/me` full-replace semantics for the same
     * validation-simplicity reason (see docs/PHASE2.md). [request.awards] is ignored here --
     * awards are managed through their own endpoints once a league exists (see
     * [LeagueSaveRequest.awards]'s own doc).
     *
     * Capacity can only be raised, never dropped below the current active player/franchise
     * count; a fee can't be changed once at least one active row exists for that role (see
     * docs/PHASE3.md's Decisions Made) -- both checked here, before [applyFullReplace].
     *
     * @throws NotOrganizerException [callerId] is not this league's organizer.
     * @throws GroundNotFoundException [request]'s `groundId` doesn't reference a real ground.
     * @throws OrganizerUpiRequiredException a fee is set but [request.organizerUpiId] is blank.
     * @throws CapacityBelowActiveCountException [request] would drop capacity below the current active count for a role.
     * @throws FeeLockedException [request] would change a fee while active rows already exist for that role.
     */
    @Transactional
    fun update(leagueId: UUID, callerId: UUID, request: LeagueSaveRequest): LeagueResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        requireGroundExistsIfReferenced(request.groundId)
        requireOrganizerUpiIdIfFeeSet(request)
        requireCapacityNotBelowActiveCount(league, request)
        requireFeeNotLockedByActiveRows(league, request)

        applyFullReplace(league, request)
        league.updatedAt = Instant.now()
        return leagueRepository.save(league).toResponse(callerId)
    }

    /**
     * `PATCH /api/v1/leagues/{id}/complete`. Organizer-only. Does not lock the league -- edits
     * and awards remain fully usable afterward (see docs/PHASE2.md's Decisions Made: some
     * awards, like Man of the Match, are only decided once a league is already complete).
     *
     * Blocked while the auction is `IN_PROGRESS` (see docs/PHASE5.md's Decisions Made) -- unlike
     * [updateAuctionSettings]/roster actions, which freeze forever once the auction has ever
     * started, this only guards the live window: completing a league whose auction never started,
     * or has already finished, is still allowed.
     *
     * @throws NotOrganizerException [callerId] is not this league's organizer.
     * @throws com.crichere.backend.auction.AuctionInProgressException the auction is `IN_PROGRESS`.
     */
    @Transactional
    fun complete(leagueId: UUID, callerId: UUID): LeagueResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        if (league.auctionStatus == AuctionStatus.IN_PROGRESS) throw AuctionInProgressException()

        league.completedAt = Instant.now()
        league.updatedAt = Instant.now()
        return leagueRepository.save(league).toResponse(callerId)
    }

    /**
     * `PUT /api/v1/leagues/{id}/auction-settings`. Organizer-only, full-replace of all 5 fields
     * together (see docs/PHASE4.md's Decisions Made). Editable up until the auction starts --
     * Phase 5's `start` action (`com.crichere.backend.auction.AuctionService.start`) is what locks
     * it, permanently, via [requireAuctionNotStarted].
     *
     * @throws NotOrganizerException [callerId] is not this league's organizer.
     * @throws SquadSizeInvalidException [request.squadMin] is greater than [request.squadMax].
     * @throws com.crichere.backend.auction.AuctionAlreadyStartedException the auction has already started (see docs/PHASE5.md).
     */
    @Transactional
    fun updateAuctionSettings(leagueId: UUID, callerId: UUID, request: AuctionSettingsSaveRequest): LeagueResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        requireAuctionNotStarted(league)
        val squadMin = requireNotNull(request.squadMin)
        val squadMax = requireNotNull(request.squadMax)
        if (squadMin > squadMax) throw SquadSizeInvalidException()

        league.auctionBasePrice = request.basePrice
        league.auctionPurse = request.purse
        league.auctionSquadMin = squadMin
        league.auctionSquadMax = squadMax
        league.auctionBidIncrement = request.bidIncrement
        league.auctionScheduledAt = request.scheduledAt
        league.updatedAt = Instant.now()
        return leagueRepository.save(league).toResponse(callerId)
    }

    /**
     * `POST /api/v1/leagues/{id}/logo-upload-url`. Organizer-only -- unlike
     * [PhotoUploadService.createUploadUrl]'s implicit self-scoping (the key is always the
     * caller's own user id), a league's id is not the caller's own id, so the ownership check
     * happens here, before [PhotoUploadService] is ever called (see [PhotoUploadService]'s
     * class doc on the security boundary).
     *
     * @throws NotOrganizerException [callerId] is not this league's organizer.
     */
    @Transactional(readOnly = true)
    fun createLogoUploadUrl(leagueId: UUID, callerId: UUID): PhotoUploadUrlResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        return photoUploadService.createLeagueLogoUploadUrl(leagueId)
    }

    /** Same as [createLogoUploadUrl], for the league's banner. */
    @Transactional(readOnly = true)
    fun createBannerUploadUrl(leagueId: UUID, callerId: UUID): PhotoUploadUrlResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        return photoUploadService.createLeagueBannerUploadUrl(leagueId)
    }

    /**
     * `POST /api/v1/leagues/{id}/payment-screenshot-upload-url`. Always self-scoped (the caller's
     * own id is the key, see [PhotoUploadService.createPaymentScreenshotUploadUrl]) -- serves
     * both the player-join and franchise-claim flows.
     *
     * @throws LeagueNotFoundException [leagueId] doesn't exist.
     */
    @Transactional(readOnly = true)
    fun createPaymentScreenshotUploadUrl(leagueId: UUID, callerId: UUID): PhotoUploadUrlResponse {
        findLeagueOrThrow(leagueId)
        return photoUploadService.createPaymentScreenshotUploadUrl(leagueId, callerId)
    }

    /**
     * `POST /api/v1/leagues/{id}/franchise-logo-upload-url`. Always self-scoped (the caller's own
     * id is the key, see [PhotoUploadService.createPendingFranchiseLogoUploadUrl]) -- used before
     * a franchise claim exists, so the resulting URL can be included directly in the claim
     * request body rather than needing a separate post-claim update call.
     *
     * @throws LeagueNotFoundException [leagueId] doesn't exist.
     */
    @Transactional(readOnly = true)
    fun createFranchiseLogoUploadUrl(leagueId: UUID, callerId: UUID): PhotoUploadUrlResponse {
        findLeagueOrThrow(leagueId)
        return photoUploadService.createPendingFranchiseLogoUploadUrl(leagueId, callerId)
    }

    /**
     * `POST /api/v1/leagues/{id}/follow`. Self-scoped, no organizer check -- anyone can follow.
     * Idempotent: following twice is a no-op, not an error.
     *
     * @throws LeagueNotFoundException [leagueId] doesn't exist.
     */
    @Transactional
    fun follow(leagueId: UUID, callerId: UUID) {
        findLeagueOrThrow(leagueId)
        if (!leagueFollowRepository.existsByLeagueIdAndUserId(leagueId, callerId)) {
            leagueFollowRepository.save(LeagueFollowEntity(leagueId = leagueId, userId = callerId))
        }
    }

    /**
     * `DELETE /api/v1/leagues/{id}/follow`. Self-scoped. Idempotent: unfollowing when not
     * following is a no-op, not an error.
     *
     * @throws LeagueNotFoundException [leagueId] doesn't exist.
     */
    @Transactional
    fun unfollow(leagueId: UUID, callerId: UUID) {
        findLeagueOrThrow(leagueId)
        leagueFollowRepository.deleteByLeagueIdAndUserId(leagueId, callerId)
    }

    /**
     * `POST /api/v1/leagues/{id}/awards`. Organizer-only, rate-limited per caller. Appended at
     * the end of the list (`displayOrder` = current award count) -- no reordering support in
     * Phase 2.
     *
     * @throws NotOrganizerException [callerId] is not this league's organizer.
     * @throws ContentRateLimitExceededException the caller has added too many awards recently.
     */
    @Transactional
    fun addAward(leagueId: UUID, callerId: UUID, request: LeagueAwardSaveRequest): LeagueAwardResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        contentRateLimiter.tryConsumeForAwardCreate(callerId)?.let { retryAfter ->
            throw ContentRateLimitExceededException(retryAfter)
        }

        val award = LeagueAwardEntity(
            leagueId = leagueId,
            name = request.name,
            cashAmount = request.cashAmount,
            hasTrophy = request.hasTrophy,
            displayOrder = leagueAwardRepository.countByLeagueId(leagueId).toInt(),
        )
        return leagueAwardRepository.save(award).toResponse()
    }

    /**
     * `PUT /api/v1/leagues/{id}/awards/{awardId}`. Organizer-only, full-replace of name/cash
     * amount/trophy flag -- `displayOrder` is untouched (no reordering support in Phase 2).
     *
     * @throws NotOrganizerException [callerId] is not this league's organizer.
     * @throws LeagueAwardNotFoundException [awardId] doesn't exist under this league.
     */
    @Transactional
    fun updateAward(leagueId: UUID, awardId: UUID, callerId: UUID, request: LeagueAwardSaveRequest): LeagueAwardResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        val award = findAwardOrThrow(leagueId, awardId)

        award.name = request.name
        award.cashAmount = request.cashAmount
        award.hasTrophy = request.hasTrophy
        return leagueAwardRepository.save(award).toResponse()
    }

    /**
     * `DELETE /api/v1/leagues/{id}/awards/{awardId}`. Organizer-only.
     *
     * @throws NotOrganizerException [callerId] is not this league's organizer.
     * @throws LeagueAwardNotFoundException [awardId] doesn't exist under this league.
     */
    @Transactional
    fun deleteAward(leagueId: UUID, awardId: UUID, callerId: UUID) {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        val award = findAwardOrThrow(leagueId, awardId)
        leagueAwardRepository.delete(award)
    }

    /** Full-replace: every mutable field on [league] becomes exactly what [request] carries. Used only by [update]. */
    private fun applyFullReplace(league: LeagueEntity, request: LeagueSaveRequest) {
        league.name = request.name
        league.description = request.description
        league.logoUrl = request.logoUrl
        league.bannerUrl = request.bannerUrl
        league.state = request.state
        league.district = request.district
        league.city = request.city
        league.groundId = request.groundId
        league.startsOn = requireNotNull(request.startsOn)
        league.format = request.format
        league.franchisesRequired = request.franchisesRequired
        league.playersRequired = request.playersRequired
        league.franchiseFee = request.franchiseFee
        league.playerFee = request.playerFee
        league.organizerUpiId = request.organizerUpiId
    }

    private fun requireGroundExistsIfReferenced(groundId: UUID?) {
        if (groundId != null && !groundRepository.existsById(groundId)) throw GroundNotFoundException()
    }

    /** See docs/PHASE3.md's Decisions Made: a fee with nowhere to pay it is a dead end. */
    private fun requireOrganizerUpiIdIfFeeSet(request: LeagueSaveRequest) {
        val feeSet = request.franchiseFee != null || request.playerFee != null
        if (feeSet && request.organizerUpiId.isNullOrBlank()) throw OrganizerUpiRequiredException()
    }

    private fun requireCapacityNotBelowActiveCount(league: LeagueEntity, request: LeagueSaveRequest) {
        val activePlayers = playerRepository.countByLeagueIdAndRemovedAtIsNull(requireNotNull(league.id))
        val activeFranchises = franchiseRepository.countByLeagueIdAndRemovedAtIsNull(requireNotNull(league.id))
        if (request.playersRequired != null && request.playersRequired < activePlayers) throw CapacityBelowActiveCountException("player")
        if (request.franchisesRequired != null && request.franchisesRequired < activeFranchises) throw CapacityBelowActiveCountException("franchise")
    }

    private fun requireFeeNotLockedByActiveRows(league: LeagueEntity, request: LeagueSaveRequest) {
        val activePlayers = playerRepository.countByLeagueIdAndRemovedAtIsNull(requireNotNull(league.id))
        val activeFranchises = franchiseRepository.countByLeagueIdAndRemovedAtIsNull(requireNotNull(league.id))
        if (request.playerFee != league.playerFee && activePlayers > 0) throw FeeLockedException("player")
        if (request.franchiseFee != league.franchiseFee && activeFranchises > 0) throw FeeLockedException("franchise")
    }

    private fun findLeagueOrThrow(leagueId: UUID): LeagueEntity =
        leagueRepository.findById(leagueId).orElseThrow { LeagueNotFoundException() }

    /** Also rejects an award id that's real but belongs to a *different* league -- see [LeagueAwardNotFoundException]. */
    private fun findAwardOrThrow(leagueId: UUID, awardId: UUID): LeagueAwardEntity {
        val award = leagueAwardRepository.findById(awardId).orElseThrow { LeagueAwardNotFoundException() }
        if (award.leagueId != leagueId) throw LeagueAwardNotFoundException()
        return award
    }

    private fun LeagueEntity.toResponse(callerId: UUID?): LeagueResponse {
        val leagueId = requireNotNull(id)
        val awards = leagueAwardRepository.findByLeagueIdOrderByDisplayOrder(leagueId).map { it.toResponse() }
        val players = playerRepository.findByLeagueIdAndRemovedAtIsNull(leagueId).map { it.toResponse(callerId, organizerUserId, profileRepository) }
        val franchises = franchiseRepository.findByLeagueIdAndRemovedAtIsNull(leagueId).map { it.toResponse(callerId, organizerUserId, profileRepository) }
        val isFollowing = callerId != null && leagueFollowRepository.existsByLeagueIdAndUserId(leagueId, callerId)
        // Save-time-only warning, never a rejection -- see docs/PHASE4.md's two-stage squad-math
        // check. Both sides must be present, or there's nothing to warn about yet.
        val squadMax = auctionSquadMax
        val franchisesTarget = franchisesRequired
        val playersTarget = playersRequired
        val auctionSquadMaxWarning = squadMax != null && franchisesTarget != null && playersTarget != null &&
            squadMax * franchisesTarget > playersTarget
        // Same per-row lookup shape as the awards fetch just above -- an accepted N+1 for Phase 2's
        // data volume (see docs/PHASE2.md's Decisions Made / the code review that flagged this same
        // tradeoff for awards).
        val groundName = groundId?.let { groundRepository.findById(it).orElse(null)?.name }
        val coOrganizers = leagueRoleRepository.findByLeagueIdAndRevokedAtIsNull(leagueId).map { it.toResponse() }
        return LeagueResponse(
            id = leagueId,
            organizerUserId = organizerUserId,
            name = name,
            description = description,
            logoUrl = logoUrl,
            bannerUrl = bannerUrl,
            country = country,
            state = state,
            district = district,
            city = city,
            groundId = groundId,
            groundName = groundName,
            startsOn = startsOn,
            format = format,
            franchisesRequired = franchisesRequired,
            playersRequired = playersRequired,
            franchiseFee = franchiseFee,
            playerFee = playerFee,
            organizerUpiId = organizerUpiId,
            status = status,
            awards = awards,
            players = players,
            franchises = franchises,
            isFollowing = isFollowing,
            auctionBasePrice = auctionBasePrice,
            auctionPurse = auctionPurse,
            auctionSquadMin = auctionSquadMin,
            auctionSquadMax = auctionSquadMax,
            auctionBidIncrement = auctionBidIncrement,
            auctionSquadMaxWarning = auctionSquadMaxWarning,
            coOrganizers = coOrganizers,
            auctionScheduledAt = auctionScheduledAt,
            auctionStatus = auctionStatus,
        )
    }

    private fun LeagueRoleEntity.toResponse() = LeagueRoleResponse(
        id = requireNotNull(id),
        userId = userId,
        name = profileRepository.findById(userId).orElse(null)?.name,
        grantedAt = grantedAt,
    )

    private fun LeagueAwardEntity.toResponse() = LeagueAwardResponse(
        id = requireNotNull(id),
        name = name,
        cashAmount = cashAmount,
        hasTrophy = hasTrophy,
        displayOrder = displayOrder,
    )
}

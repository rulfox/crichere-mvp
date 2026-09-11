package com.crichere.backend.league

import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.common.PhotoUploadService
import com.crichere.backend.common.PhotoUploadUrlResponse
import com.crichere.backend.ground.GroundRepository
import com.crichere.backend.league.dto.LeagueAwardResponse
import com.crichere.backend.league.dto.LeagueAwardSaveRequest
import com.crichere.backend.league.dto.LeagueResponse
import com.crichere.backend.league.dto.LeagueSaveRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class LeagueService(
    private val leagueRepository: LeagueRepository,
    private val leagueAwardRepository: LeagueAwardRepository,
    private val groundRepository: GroundRepository,
    private val contentRateLimiter: ContentRateLimiter,
    private val photoUploadService: PhotoUploadService,
) {

    /** `GET /api/v1/leagues/{id}`. */
    @Transactional(readOnly = true)
    fun getLeague(leagueId: UUID): LeagueResponse =
        findLeagueOrThrow(leagueId).toResponse()

    /**
     * `GET /api/v1/leagues` with the area filters (state/district/city, each optional). Mutually
     * exclusive with [listNearest] in the UI -- see docs/PHASE2.md's Decisions Made.
     */
    @Transactional(readOnly = true)
    fun listByArea(state: String?, district: String?, city: String?): List<LeagueResponse> =
        leagueRepository.findByAreaFilters(state, district, city).map { it.toResponse() }

    /**
     * `GET /api/v1/leagues?near=lat,lng`. Only leagues with a ground attached participate --
     * no city/district-centroid fallback (see docs/PHASE2.md's Decisions Made).
     */
    @Transactional(readOnly = true)
    fun listNearest(latitude: Double, longitude: Double): List<LeagueResponse> =
        leagueRepository.findNearest(latitude, longitude).map { it.toResponse() }

    /**
     * `POST /api/v1/leagues`. Rate-limited per caller (see [ContentRateLimiter]) -- an open,
     * unrestricted-volume endpoint (any logged-in user, no approval gate, per docs/PHASE2.md's
     * Decisions Made) with no other abuse control. [LeagueSaveRequest.awards], if present,
     * are created in the same transaction so the mobile client's three pre-suggested rows land
     * in one call, not three follow-ups.
     *
     * @throws ContentRateLimitExceededException the caller has created too many leagues recently.
     * @throws GroundNotFoundException [request]'s `groundId` doesn't reference a real ground.
     */
    @Transactional
    fun create(organizerUserId: UUID, request: LeagueSaveRequest): LeagueResponse {
        contentRateLimiter.tryConsumeForLeagueCreate(organizerUserId)?.let { retryAfter ->
            throw ContentRateLimitExceededException(retryAfter)
        }
        requireGroundExistsIfReferenced(request.groundId)

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

        return saved.toResponse()
    }

    /**
     * `PUT /api/v1/leagues/{id}`. Full-replace, organizer-only (see [LeagueExceptions]) --
     * mirrors Phase 1's `PUT /profiles/me` full-replace semantics for the same
     * validation-simplicity reason (see docs/PHASE2.md). [request.awards] is ignored here --
     * awards are managed through their own endpoints once a league exists (see
     * [LeagueSaveRequest.awards]'s own doc).
     *
     * @throws NotOrganizerException [callerId] is not this league's organizer.
     * @throws GroundNotFoundException [request]'s `groundId` doesn't reference a real ground.
     */
    @Transactional
    fun update(leagueId: UUID, callerId: UUID, request: LeagueSaveRequest): LeagueResponse {
        val league = findLeagueOrThrow(leagueId)
        requireOrganizer(league, callerId)
        requireGroundExistsIfReferenced(request.groundId)

        applyFullReplace(league, request)
        league.updatedAt = Instant.now()
        return leagueRepository.save(league).toResponse()
    }

    /**
     * `PATCH /api/v1/leagues/{id}/complete`. Organizer-only. Does not lock the league -- edits
     * and awards remain fully usable afterward (see docs/PHASE2.md's Decisions Made: some
     * awards, like Man of the Match, are only decided once a league is already complete).
     *
     * @throws NotOrganizerException [callerId] is not this league's organizer.
     */
    @Transactional
    fun complete(leagueId: UUID, callerId: UUID): LeagueResponse {
        val league = findLeagueOrThrow(leagueId)
        requireOrganizer(league, callerId)

        league.completedAt = Instant.now()
        league.updatedAt = Instant.now()
        return leagueRepository.save(league).toResponse()
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
        requireOrganizer(league, callerId)
        return photoUploadService.createLeagueLogoUploadUrl(leagueId)
    }

    /** Same as [createLogoUploadUrl], for the league's banner. */
    @Transactional(readOnly = true)
    fun createBannerUploadUrl(leagueId: UUID, callerId: UUID): PhotoUploadUrlResponse {
        val league = findLeagueOrThrow(leagueId)
        requireOrganizer(league, callerId)
        return photoUploadService.createLeagueBannerUploadUrl(leagueId)
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
        requireOrganizer(league, callerId)
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
        requireOrganizer(league, callerId)
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
        requireOrganizer(league, callerId)
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
    }

    private fun requireGroundExistsIfReferenced(groundId: UUID?) {
        if (groundId != null && !groundRepository.existsById(groundId)) throw GroundNotFoundException()
    }

    private fun requireOrganizer(league: LeagueEntity, callerId: UUID) {
        if (league.organizerUserId != callerId) throw NotOrganizerException()
    }

    private fun findLeagueOrThrow(leagueId: UUID): LeagueEntity =
        leagueRepository.findById(leagueId).orElseThrow { LeagueNotFoundException() }

    /** Also rejects an award id that's real but belongs to a *different* league -- see [LeagueAwardNotFoundException]. */
    private fun findAwardOrThrow(leagueId: UUID, awardId: UUID): LeagueAwardEntity {
        val award = leagueAwardRepository.findById(awardId).orElseThrow { LeagueAwardNotFoundException() }
        if (award.leagueId != leagueId) throw LeagueAwardNotFoundException()
        return award
    }

    private fun LeagueEntity.toResponse(): LeagueResponse {
        val awards = leagueAwardRepository.findByLeagueIdOrderByDisplayOrder(requireNotNull(id)).map { it.toResponse() }
        // Same per-row lookup shape as the awards fetch just above -- an accepted N+1 for Phase 2's
        // data volume (see docs/PHASE2.md's Decisions Made / the code review that flagged this same
        // tradeoff for awards).
        val groundName = groundId?.let { groundRepository.findById(it).orElse(null)?.name }
        return LeagueResponse(
            id = requireNotNull(id),
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
            status = status,
            awards = awards,
        )
    }

    private fun LeagueAwardEntity.toResponse() = LeagueAwardResponse(
        id = requireNotNull(id),
        name = name,
        cashAmount = cashAmount,
        hasTrophy = hasTrophy,
        displayOrder = displayOrder,
    )
}

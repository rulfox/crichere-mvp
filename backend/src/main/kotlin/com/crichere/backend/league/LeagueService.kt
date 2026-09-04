package com.crichere.backend.league

import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.common.PhotoUploadService
import com.crichere.backend.common.PhotoUploadUrlResponse
import com.crichere.backend.ground.GroundRepository
import com.crichere.backend.league.dto.LeagueResponse
import com.crichere.backend.league.dto.LeagueSaveRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class LeagueService(
    private val leagueRepository: LeagueRepository,
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
     * Decisions Made) with no other abuse control.
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
        return leagueRepository.save(league).toResponse()
    }

    /**
     * `PUT /api/v1/leagues/{id}`. Full-replace, organizer-only (see [LeagueExceptions]) --
     * mirrors Phase 1's `PUT /profiles/me` full-replace semantics for the same
     * validation-simplicity reason (see docs/PHASE2.md).
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

    private fun LeagueEntity.toResponse() = LeagueResponse(
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
        startsOn = startsOn,
        format = format,
        franchisesRequired = franchisesRequired,
        playersRequired = playersRequired,
        franchiseFee = franchiseFee,
        playerFee = playerFee,
        status = status,
    )
}

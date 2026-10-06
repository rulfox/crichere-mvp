package com.crichere.backend.ground

import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.ground.dto.GroundCreateRequest
import com.crichere.backend.ground.dto.GroundResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GroundService(
    private val groundRepository: GroundRepository,
    private val contentRateLimiter: ContentRateLimiter,
) {

    /**
     * `GET /api/v1/grounds` -- every filter optional. [search] becomes an always-non-null,
     * pre-lowercased `%pattern%` (`%%`, matching everything, when blank/absent) before it ever
     * reaches the query -- see [GroundRepository.searchByPattern] for why a null search
     * parameter can't be handed to Postgres directly here.
     */
    @Transactional(readOnly = true)
    fun search(search: String?, state: String?, district: String?): List<GroundResponse> {
        val pattern = "%${search.orEmpty().lowercase()}%"
        return groundRepository.searchByPattern(pattern, state, district).map { it.toResponse() }
    }

    /**
     * `POST /api/v1/grounds`. Rate-limited per caller (see [ContentRateLimiter]) -- this is an
     * open, unrestricted-volume endpoint (any logged-in user, no approval gate, per
     * docs/PHASE2.md's Decisions Made) with no other abuse control.
     *
     * @throws ContentRateLimitExceededException the caller has registered too many grounds recently.
     */
    @Transactional
    fun create(userId: UUID, request: GroundCreateRequest): GroundResponse {
        contentRateLimiter.tryConsumeForGroundCreate(userId)?.let { retryAfter ->
            throw ContentRateLimitExceededException(retryAfter)
        }

        val ground = GroundEntity(
            name = request.name,
            state = request.state,
            district = request.district,
            latitude = requireNotNull(request.latitude),
            longitude = requireNotNull(request.longitude),
            registeredByUserId = userId,
        )
        return groundRepository.save(ground).toResponse()
    }

    private fun GroundEntity.toResponse() = GroundResponse(
        id = requireNotNull(id),
        name = name,
        state = state,
        district = district,
        latitude = latitude,
        longitude = longitude,
    )
}

package com.crichere.backend.league

import com.crichere.backend.auth.PhoneCryptoService
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.league.dto.LeagueResponse
import com.crichere.backend.league.dto.RoleLookupResponse
import com.crichere.backend.profile.ProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Co-organizer role delegation (docs/PHASE7.md). A separate service from [LeagueService] for the
 * same reason auction logic lives in its own [com.crichere.backend.auction.AuctionService] rather
 * than being folded into it -- a distinct feature with its own endpoints, not an extension of
 * league CRUD. Returns [LeagueResponse] from every mutation by delegating back to
 * [LeagueService.getLeague] rather than duplicating its response-mapping logic.
 */
@Service
class RoleService(
    private val leagueRepository: LeagueRepository,
    private val leagueRoleRepository: LeagueRoleRepository,
    private val leagueAuthorization: LeagueAuthorization,
    private val leagueService: LeagueService,
    private val userRepository: UserRepository,
    private val profileRepository: ProfileRepository,
    private val phoneCryptoService: PhoneCryptoService,
    private val contentRateLimiter: ContentRateLimiter,
) {

    /**
     * `POST /leagues/{id}/roles/lookup`. Organizer-only and tightly rate-limited -- see
     * docs/PHASE7.md's Security section for why this specific endpoint gets more scrutiny than
     * everything else in this service. Reuses the exact-match phone-hash mechanism auth/OTP
     * already relies on ([PhoneCryptoService.hmacLookupHash] /
     * [UserRepository.findByPhoneLookupHash]) -- no new phone-matching logic.
     *
     * @throws com.crichere.backend.common.ContentRateLimitExceededException the caller has looked up too many numbers recently.
     * @throws NotOrganizerException [callerId] is not [leagueId]'s organizer or an active co-organizer.
     * @throws UserNotFoundException no registered user matches [phoneNumber].
     */
    @Transactional(readOnly = true)
    fun lookup(leagueId: UUID, callerId: UUID, phoneNumber: String): RoleLookupResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)

        contentRateLimiter.tryConsumeForRoleLookup(callerId)?.let { retryAfter ->
            throw ContentRateLimitExceededException(retryAfter)
        }

        val hash = phoneCryptoService.hmacLookupHash(phoneNumber)
        val user = userRepository.findByPhoneLookupHash(hash) ?: throw UserNotFoundException()
        val name = profileRepository.findById(requireNotNull(user.id)).orElse(null)?.name
        return RoleLookupResponse(userId = requireNotNull(user.id), name = name)
    }

    /**
     * `POST /leagues/{id}/roles`. Always grants [LeagueRole.CO_ORGANIZER] -- see
     * `dto/RoleRequests.kt`'s note on why the request has no `role` field yet.
     *
     * @throws NotOrganizerException [callerId] is not [leagueId]'s organizer or an active co-organizer.
     * @throws CannotGrantRoleToOrganizerException [targetUserId] is already this league's organizer.
     * @throws RoleAlreadyGrantedException [targetUserId] already has an active grant of this role.
     */
    @Transactional
    fun grant(leagueId: UUID, callerId: UUID, targetUserId: UUID): LeagueResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)

        if (targetUserId == league.organizerUserId) throw CannotGrantRoleToOrganizerException()
        if (leagueRoleRepository.existsByLeagueIdAndUserIdAndRevokedAtIsNull(leagueId, targetUserId)) {
            throw RoleAlreadyGrantedException()
        }

        leagueRoleRepository.save(
            LeagueRoleEntity(
                leagueId = leagueId,
                userId = targetUserId,
                role = LeagueRole.CO_ORGANIZER,
                grantedByUserId = callerId,
            ),
        )
        return leagueService.getLeague(leagueId, callerId)
    }

    /**
     * `DELETE /leagues/{id}/roles/{roleId}`. Any active organizer or co-organizer can revoke any
     * grant -- including their own (stepping down) -- per docs/PHASE7.md's "full delegate,
     * symmetric" decision. Takes effect on the revoked user's very next request; no token
     * invalidation needed, since [LeagueAuthorization.isOrganizer] re-queries this table fresh
     * every time.
     *
     * @throws NotOrganizerException [callerId] is not [leagueId]'s organizer or an active co-organizer.
     * @throws RoleNotFoundException [roleId] doesn't exist under this league, or is already revoked.
     */
    @Transactional
    fun revoke(leagueId: UUID, callerId: UUID, roleId: UUID): LeagueResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)

        val role = leagueRoleRepository.findByIdAndLeagueId(roleId, leagueId).orElseThrow { RoleNotFoundException() }
        if (role.revokedAt != null) throw RoleNotFoundException()
        role.revokedAt = Instant.now()
        leagueRoleRepository.save(role)
        return leagueService.getLeague(leagueId, callerId)
    }

    private fun findLeagueOrThrow(leagueId: UUID): LeagueEntity =
        leagueRepository.findById(leagueId).orElseThrow { LeagueNotFoundException() }
}

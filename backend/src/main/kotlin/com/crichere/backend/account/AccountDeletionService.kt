package com.crichere.backend.account

import com.crichere.backend.auth.RefreshTokenRepository
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.league.LeagueRoleEntity
import com.crichere.backend.league.LeagueRoleRepository
import com.crichere.backend.notification.DeviceTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Requesting an account deletion (docs/PHASE16.md). The request only starts the grace period:
 * every session ends and push stops, but nothing is erased until [AccountPurgeService] runs after
 * [AccountDeletionProperties.gracePeriod]. Signing in again before then cancels it
 * ([com.crichere.backend.auth.AuthService.signInVerifiedPhone]).
 *
 * The blocker rules live here so the request, the purge and [com.crichere.backend.league.RoleService]
 * all apply the same ones.
 */
@Service
class AccountDeletionService(
    private val userRepository: UserRepository,
    private val leagueRepository: LeagueRepository,
    private val leagueRoleRepository: LeagueRoleRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val contentRateLimiter: ContentRateLimiter,
    private val properties: AccountDeletionProperties,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** `GET /api/v1/me/deletion`. */
    @Transactional(readOnly = true)
    fun status(userId: UUID): AccountDeletionStatusResponse =
        AccountDeletionStatusResponse(blockers = blockers(userId), gracePeriodDays = properties.gracePeriod.toDays())

    /**
     * `POST /api/v1/me/deletion`. Idempotent: asking again while already pending returns the
     * original purge time rather than restarting the clock.
     *
     * @throws AccountDeletionNotConfirmedException [confirm] isn't exactly `DELETE`.
     * @throws AccountDeletionBlockedException see [blockers].
     */
    @Transactional
    fun request(userId: UUID, confirm: String?): AccountDeletionScheduledResponse {
        contentRateLimiter.tryConsumeForAccountDeletion(userId)?.let { throw ContentRateLimitExceededException(it) }
        if (confirm != CONFIRMATION) throw AccountDeletionNotConfirmedException()

        val user = userRepository.findById(userId).orElseThrow()
        user.deletionRequestedAt?.let { return AccountDeletionScheduledResponse(purgeAfter = it.plus(properties.gracePeriod)) }

        val blockers = blockers(userId)
        if (blockers.isNotEmpty()) throw AccountDeletionBlockedException(blockers)

        val now = clock.instant()
        user.deletionRequestedAt = now
        userRepository.save(user)
        refreshTokenRepository.revokeAllForUser(userId, now)
        deviceTokenRepository.deleteByUserId(userId)
        log.info("Account deletion requested for user {}", userId)
        return AccountDeletionScheduledResponse(purgeAfter = now.plus(properties.gracePeriod))
    }

    /**
     * Every league that stops [userId]'s account from being deleted now:
     * - [DeletionBlockReason.AUCTION_LIVE]: a running auction they organize, play in or own a franchise in;
     * - [DeletionBlockReason.NO_CO_ORGANIZER]: a league they organize that isn't completed and has no [successorFor].
     * A league with a live auction is reported once, as `AUCTION_LIVE`.
     */
    fun blockers(userId: UUID): List<DeletionBlocker> {
        val live = leagueRepository.findLiveAuctionsInvolving(userId)
        val liveIds = live.mapNotNull { it.id }.toSet()
        val noSuccessor = leagueRepository.findByOrganizerUserIdAndCompletedAtIsNull(userId)
            .filter { it.id !in liveIds && successorFor(requireNotNull(it.id), userId) == null }
        return live.map { it.toBlocker(DeletionBlockReason.AUCTION_LIVE) } +
            noSuccessor.map { it.toBlocker(DeletionBlockReason.NO_CO_ORGANIZER) }
    }

    /**
     * Who takes [leagueId] over when [leavingOrganizerId] is purged: the earliest-granted active
     * co-organizer whose own account isn't being deleted. `null` means nobody can.
     *
     * @param excludingRoleId leave this grant out -- [wouldStrandLeague] asks "and without this one?".
     */
    fun successorFor(leagueId: UUID, leavingOrganizerId: UUID, excludingRoleId: UUID? = null): LeagueRoleEntity? =
        leagueRoleRepository.findByLeagueIdAndRevokedAtIsNullOrderByGrantedAtAsc(leagueId)
            .asSequence()
            .filter { it.userId != leavingOrganizerId && it.id != excludingRoleId }
            .firstOrNull { role ->
                userRepository.findById(role.userId).map { it.deletionRequestedAt == null && it.deletedAt == null }.orElse(false)
            }

    /**
     * `true` when [league]'s organizer is pending deletion and revoking [roleId] would leave nobody
     * to take the league over -- [com.crichere.backend.league.RoleService.revoke] refuses that,
     * because the purge could never finish.
     */
    fun wouldStrandLeague(league: LeagueEntity, roleId: UUID): Boolean {
        if (league.completedAt != null) return false
        val organizer = userRepository.findById(league.organizerUserId).orElse(null) ?: return false
        if (organizer.deletionRequestedAt == null || organizer.deletedAt != null) return false
        return successorFor(requireNotNull(league.id), league.organizerUserId, excludingRoleId = roleId) == null
    }

    private fun LeagueEntity.toBlocker(reason: DeletionBlockReason) =
        DeletionBlocker(leagueId = requireNotNull(id), leagueName = name, reason = reason)

    companion object {
        const val CONFIRMATION = "DELETE"

        /** What a purged account's name becomes everywhere it still appears (rosters, results, co-organizer lists). */
        const val DELETED_USER_NAME = "Deleted user"
    }
}

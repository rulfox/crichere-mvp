package com.crichere.backend.account

import com.crichere.backend.auth.OtpChallengeRepository
import com.crichere.backend.auth.RefreshTokenRepository
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.common.ObjectDeleter
import com.crichere.backend.common.PhotoUploadService
import com.crichere.backend.franchise.FranchiseRepository
import com.crichere.backend.league.AuctionStatus
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueFollowRepository
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.league.LeagueRoleRepository
import com.crichere.backend.notification.DeviceTokenRepository
import com.crichere.backend.notification.FcmSender
import com.crichere.backend.player.PlayerRepository
import com.crichere.backend.profile.ProfileEntity
import com.crichere.backend.profile.ProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** What happened to one due account on a purge run. */
enum class PurgeOutcome {
    PURGED,

    /** A blocker appeared during the grace period (an auction started, the last co-organizer left); retried next run. */
    POSTPONED,

    /** Cancelled by a sign-in, already purged, or locked by another instance. */
    SKIPPED,
}

/**
 * Erases accounts whose deletion grace period has run out (docs/PHASE16.md).
 *
 * The `users` row stays as a tombstone: every FK to it cascades, so deleting it would take other
 * people's leagues, bids and results with it. Everything personal goes instead -- phone, profile
 * details, photo, payment screenshots, sessions, push tokens, follows -- and the profile keeps only
 * the name [AccountDeletionService.DELETED_USER_NAME], so every existing response mapping shows
 * "Deleted user" with no change of its own.
 *
 * Order per account: blocker pre-check, then the S3 deletes, then one database transaction. S3 goes
 * first because it can't roll back with the database; deleting a missing key succeeds, so a
 * transaction that fails afterwards just retries the whole account next run. Pushes to promoted
 * co-organizers go out only after the commit.
 */
@Service
class AccountPurgeService(
    private val userRepository: UserRepository,
    private val profileRepository: ProfileRepository,
    private val leagueRepository: LeagueRepository,
    private val leagueRoleRepository: LeagueRoleRepository,
    private val leagueFollowRepository: LeagueFollowRepository,
    private val playerRepository: PlayerRepository,
    private val franchiseRepository: FranchiseRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val otpChallengeRepository: OtpChallengeRepository,
    private val accountDeletionService: AccountDeletionService,
    private val objectDeleter: ObjectDeleter,
    private val fcmSender: FcmSender,
    private val transactionTemplate: TransactionTemplate,
    private val properties: AccountDeletionProperties,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Purges every account due at [now]. One account failing never stops the others. */
    fun purgeDue(now: Instant = clock.instant()): Map<UUID, PurgeOutcome> {
        val cutoff = now.minus(properties.gracePeriod)
        return userRepository.findDueForPurge(cutoff).associateWith { userId ->
            try {
                purge(userId, cutoff, now)
            } catch (e: Exception) {
                log.error("Account purge failed for user {}; retrying next run: {}", userId, e.javaClass.simpleName)
                PurgeOutcome.POSTPONED
            }
        }
    }

    private fun purge(userId: UUID, cutoff: Instant, now: Instant): PurgeOutcome {
        // Cheap check before touching S3, so a blocked account keeps its photo until it can go.
        if (accountDeletionService.blockers(userId).isNotEmpty()) {
            log.warn("Account purge postponed for user {}: blocked", userId)
            return PurgeOutcome.POSTPONED
        }

        objectDeleter.delete(objectKeys(userId))

        val result = transactionTemplate.execute { purgeRows(userId, cutoff, now) } ?: PurgeResult(PurgeOutcome.SKIPPED)
        result.handovers.forEach { (leagueId, leagueName, newOrganizerId) ->
            fcmSender.sendToUser(
                newOrganizerId,
                leagueName,
                "The organizer deleted their account. You're now the organizer of $leagueName.",
                mapOf("leagueId" to leagueId.toString()),
            )
        }
        if (result.outcome == PurgeOutcome.PURGED) log.info("Account purged for user {}", userId)
        return result.outcome
    }

    /** Profile photo plus the one payment-screenshot key per league the user ever paid into (`PhotoUploadService.paymentScreenshotKey`). */
    private fun objectKeys(userId: UUID): List<String> {
        val leagueIds = playerRepository.findByUserId(userId).map { it.leagueId } +
            franchiseRepository.findByOwnerUserId(userId).map { it.leagueId }
        return listOf("users/$userId/profile.jpg") +
            leagueIds.distinct().map { PhotoUploadService.paymentScreenshotKey(it, userId) }
    }

    private fun purgeRows(userId: UUID, cutoff: Instant, now: Instant): PurgeResult {
        val user = userRepository.lockDueForPurge(userId, cutoff) ?: return PurgeResult(PurgeOutcome.SKIPPED)

        // Re-checked under the lock: the state may have moved since the pre-check.
        if (leagueRepository.findLiveAuctionsInvolving(userId).isNotEmpty()) return PurgeResult(PurgeOutcome.POSTPONED)
        // Every successor is found before anything is written, so a postponement changes nothing.
        val successors = leagueRepository.findByOrganizerUserIdAndCompletedAtIsNull(userId).map { league ->
            league to (accountDeletionService.successorFor(requireNotNull(league.id), userId) ?: return PurgeResult(PurgeOutcome.POSTPONED))
        }
        val handovers = successors.map { (league, successor) ->
            successor.revokedAt = now
            leagueRoleRepository.save(successor)
            Handover(requireNotNull(league.id), league.name, successor.userId)
        }

        leagueRoleRepository.findByUserIdAndRevokedAtIsNull(userId).forEach {
            it.revokedAt = now
            leagueRoleRepository.save(it)
        }

        // Rows in leagues still taking sign-ups are withdrawn (a ghost player or franchise owner
        // would hold a slot nobody can use); rows in auctioned or completed leagues stay as history.
        val leagues = mutableMapOf<UUID, LeagueEntity?>()
        fun stillOpen(leagueId: UUID): Boolean {
            val league = leagues.getOrPut(leagueId) { leagueRepository.findById(leagueId).orElse(null) } ?: return false
            return league.completedAt == null && league.auctionStatus == AuctionStatus.NOT_STARTED
        }
        playerRepository.findByUserId(userId).forEach { player ->
            player.paymentScreenshotUrl = null
            if (player.removedAt == null && stillOpen(player.leagueId)) player.removedAt = now
            playerRepository.save(player)
        }
        franchiseRepository.findByOwnerUserId(userId).forEach { franchise ->
            franchise.paymentScreenshotUrl = null
            if (franchise.removedAt == null && stillOpen(franchise.leagueId)) franchise.removedAt = now
            franchiseRepository.save(franchise)
        }

        val profile = profileRepository.findById(userId).orElse(null) ?: ProfileEntity(userId = userId)
        profile.name = AccountDeletionService.DELETED_USER_NAME
        profile.accountDeleted = true
        profile.photoUrl = null
        profile.state = null
        profile.district = null
        profile.playingRole = null
        profile.battingStyle = null
        profile.bowlingStyle = null
        profileRepository.save(profile)

        leagueFollowRepository.deleteByUserId(userId)
        deviceTokenRepository.deleteByUserId(userId)
        refreshTokenRepository.deleteByUserId(userId)
        user.phoneLookupHash?.let { otpChallengeRepository.deleteByPhoneLookupHash(it) }

        user.phoneLookupHash = null
        user.phoneEncrypted = null
        user.deletedAt = now
        userRepository.saveAndFlush(user)

        // Last: these native updates clear the persistence context.
        handovers.forEach { leagueRepository.handOverOrganizer(it.leagueId, it.newOrganizerId, now) }
        return PurgeResult(PurgeOutcome.PURGED, handovers)
    }

    private data class Handover(val leagueId: UUID, val leagueName: String, val newOrganizerId: UUID)

    private data class PurgeResult(val outcome: PurgeOutcome, val handovers: List<Handover> = emptyList())
}

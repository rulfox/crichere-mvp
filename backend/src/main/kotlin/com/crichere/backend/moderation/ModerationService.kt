package com.crichere.backend.moderation

import com.crichere.backend.auth.UserRepository
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.franchise.FranchiseRepository
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.league.LeagueRoleRepository
import com.crichere.backend.player.PlayerRepository
import com.crichere.backend.profile.ProfileRepository
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** A self-block, a self-report, or a target that doesn't exist or was deleted: one error, so ids can't be probed. */
class ModerationTargetNotFoundException : RuntimeException("Target not found")

/** This reporter already has an open report on this target (board R6b). */
class ReportAlreadyOpenException : RuntimeException("Report already open")

/** The reason is "Something else", or the body doesn't match the target type. */
class InvalidReportException : RuntimeException("Invalid report")

data class BlockedPersonResponse(val userId: UUID, val name: String?, val blockedAt: Instant)

data class ModerationNoticeResponse(val id: UUID, val kind: ModerationNoticeKind, val subjectName: String?)

/** Ids, a reason and text only: the server builds the evidence, so no URL is ever taken from a client. */
data class ReportRequest(
    @field:NotNull(message = "targetType is required")
    val targetType: ReportTargetType?,
    val targetUserId: UUID? = null,
    val targetLeagueId: UUID? = null,
    /** The league the person was reported from, for the team's context. */
    val contextLeagueId: UUID? = null,
    @field:NotNull(message = "reason is required")
    val reason: ReportReason?,
    @field:Size(max = 500, message = "details must be at most 500 characters")
    val details: String? = null,
)

/**
 * Blocking (docs/PHASE18.md). One-way and private: the only read is the caller's own list, and no
 * other response says who blocked whom. Hiding happens on the client for shared rosters and the
 * public auction stream; [leaguesHiddenFrom] covers the dashboard lists.
 */
@Service
class BlockService(
    private val blockRepository: UserBlockRepository,
    private val userRepository: UserRepository,
    private val profileRepository: ProfileRepository,
    private val playerRepository: PlayerRepository,
    private val franchiseRepository: FranchiseRepository,
    private val leagueRoleRepository: LeagueRoleRepository,
    private val rateLimiter: ContentRateLimiter,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun list(callerId: UUID): List<BlockedPersonResponse> {
        val rows = blockRepository.findByBlockerIdOrderByCreatedAtDesc(callerId)
        val names = profileRepository.findAllById(rows.map { it.blockedId }).associate { it.userId to it.name }
        return rows.map { BlockedPersonResponse(it.blockedId, names[it.blockedId], it.createdAt) }
    }

    /** Idempotent: blocking twice leaves the original date. */
    @Transactional
    fun block(callerId: UUID, targetId: UUID) {
        rateLimiter.requireBlockAllowed(callerId)
        val target = userRepository.findById(targetId).orElse(null)
        if (targetId == callerId || target == null || target.deletedAt != null) throw ModerationTargetNotFoundException()
        if (blockRepository.existsByBlockerIdAndBlockedId(callerId, targetId)) return
        blockRepository.save(UserBlockEntity(blockerId = callerId, blockedId = targetId, createdAt = clock.instant()))
    }

    /** Idempotent, so Undo can't fail on a double tap. */
    @Transactional
    fun unblock(callerId: UUID, targetId: UUID) {
        blockRepository.deleteByBlockerIdAndBlockedId(callerId, targetId)
    }

    /**
     * Drops leagues whose main organizer [callerId] blocked, unless the caller is in the league (decisions
     * in docs/PHASE18.md: co-organizers don't count, and joined leagues stay).
     */
    fun visibleTo(callerId: UUID?, leagues: List<LeagueEntity>): List<LeagueEntity> {
        if (callerId == null) return leagues
        val blocked = blockRepository.findByBlockerIdOrderByCreatedAtDesc(callerId).map { it.blockedId }.toSet()
        if (blocked.isEmpty()) return leagues
        val joined = playerRepository.findByUserIdAndRemovedAtIsNull(callerId).map { it.leagueId }.toSet() +
            franchiseRepository.findByOwnerUserIdAndRemovedAtIsNull(callerId).map { it.leagueId } +
            leagueRoleRepository.findByUserIdAndRevokedAtIsNull(callerId).map { it.leagueId }
        return leagues.filter { it.organizerUserId !in blocked || it.id in joined }
    }
}

/**
 * Reports (docs/PHASE18.md). Stored for the team, who read them with `backend/tools/moderation`; there
 * is no read endpoint, so a reporter can't be exposed by any response. Nothing is ever actioned
 * automatically. Never log [ReportRequest.details].
 */
@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val userRepository: UserRepository,
    private val profileRepository: ProfileRepository,
    private val leagueRepository: LeagueRepository,
    private val leagueRoleRepository: LeagueRoleRepository,
    private val franchiseRepository: FranchiseRepository,
    private val rateLimiter: ContentRateLimiter,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {

    fun report(callerId: UUID, request: ReportRequest) {
        rateLimiter.requireReportAllowed(callerId)
        val reason = requireNotNull(request.reason)
        val details = request.details?.let(::clean)?.ifBlank { null }
        if (reason == ReportReason.SOMETHING_ELSE && details == null) throw InvalidReportException()

        val report = when (requireNotNull(request.targetType)) {
            ReportTargetType.USER -> userReport(callerId, request, reason, details)
            ReportTargetType.LEAGUE -> leagueReport(callerId, request, reason, details)
        }
        try {
            reportRepository.saveAndFlush(report)
        } catch (_: DataIntegrityViolationException) {
            // Two sends at once: the unique index decides, and the loser gets the same answer as a later one.
            throw ReportAlreadyOpenException()
        }
    }

    private fun userReport(callerId: UUID, request: ReportRequest, reason: ReportReason, details: String?): ReportEntity {
        if (reason !in PERSON_REASONS || request.targetLeagueId != null) throw InvalidReportException()
        val targetId = request.targetUserId ?: throw InvalidReportException()
        val target = userRepository.findById(targetId).orElse(null)
        if (targetId == callerId || target == null || target.deletedAt != null) throw ModerationTargetNotFoundException()
        if (reportRepository.existsByReporterIdAndTargetUserIdAndStatus(callerId, targetId, ReportStatus.OPEN)) {
            throw ReportAlreadyOpenException()
        }
        val profile = profileRepository.findById(targetId).orElse(null)
        val franchises = franchiseRepository.findByOwnerUserIdAndRemovedAtIsNull(targetId)
            .filter { request.contextLeagueId == null || it.leagueId == request.contextLeagueId }
            .map { mapOf("name" to it.name, "logoUrl" to it.logoUrl) }
        val snapshot = mapOf("name" to profile?.name, "photoUrl" to profile?.photoUrl, "franchises" to franchises)
        return ReportEntity(
            reporterId = callerId,
            targetType = ReportTargetType.USER,
            targetUserId = targetId,
            contextLeagueId = request.contextLeagueId?.takeIf { leagueRepository.existsById(it) },
            reason = reason,
            details = details,
            snapshot = objectMapper.writeValueAsString(snapshot),
            createdAt = clock.instant(),
        )
    }

    private fun leagueReport(callerId: UUID, request: ReportRequest, reason: ReportReason, details: String?): ReportEntity {
        if (reason !in LEAGUE_REASONS || request.targetUserId != null) throw InvalidReportException()
        val leagueId = request.targetLeagueId ?: throw InvalidReportException()
        val league = leagueRepository.findById(leagueId).orElseThrow { ModerationTargetNotFoundException() }
        // Board R3: the link is hidden for the organizer and co-organizers.
        if (league.organizerUserId == callerId ||
            leagueRoleRepository.existsByLeagueIdAndUserIdAndRevokedAtIsNull(leagueId, callerId)
        ) {
            throw ModerationTargetNotFoundException()
        }
        if (reportRepository.existsByReporterIdAndTargetLeagueIdAndStatus(callerId, leagueId, ReportStatus.OPEN)) {
            throw ReportAlreadyOpenException()
        }
        val snapshot = mapOf(
            "name" to league.name,
            "description" to league.description,
            "logoUrl" to league.logoUrl,
            "bannerUrl" to league.bannerUrl,
        )
        return ReportEntity(
            reporterId = callerId,
            targetType = ReportTargetType.LEAGUE,
            targetLeagueId = leagueId,
            reason = reason,
            details = details,
            snapshot = objectMapper.writeValueAsString(snapshot),
            createdAt = clock.instant(),
        )
    }

    /** Control characters out, line breaks kept: the text ends up in a terminal. */
    private fun clean(text: String): String =
        text.filter { it == '\n' || !it.isISOControl() }.trim()

    private companion object {
        val PERSON_REASONS = setOf(
            ReportReason.INAPPROPRIATE_IMAGE,
            ReportReason.HATE_OR_HARASSMENT,
            ReportReason.IMPERSONATION,
            ReportReason.UNDER_18,
            ReportReason.SPAM_OR_MISLEADING,
            ReportReason.SOMETHING_ELSE,
        )
        val LEAGUE_REASONS = setOf(
            ReportReason.INAPPROPRIATE_IMAGE,
            ReportReason.HATE_OR_HARASSMENT,
            ReportReason.BETTING_OR_GAMBLING,
            ReportReason.FAKE_OR_SCAM_LEAGUE,
            ReportReason.SOMETHING_ELSE,
        )
    }
}

/** One-time notices about a removed image (boards R12b to R12e). Dismissal is stored here, so it shows once across devices. */
@Service
class ModerationNoticeService(
    private val noticeRepository: ModerationNoticeRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun pending(userId: UUID): List<ModerationNoticeResponse> =
        noticeRepository.findByUserIdAndDismissedAtIsNullOrderByCreatedAt(userId)
            .map { ModerationNoticeResponse(it.id, it.kind, it.subjectName) }

    /** A no-op for someone else's notice or an unknown id, so ids can't be probed. */
    @Transactional
    fun dismiss(userId: UUID, noticeId: UUID) {
        val notice = noticeRepository.findById(noticeId).orElse(null) ?: return
        if (notice.userId == userId && notice.dismissedAt == null) notice.dismissedAt = clock.instant()
    }
}

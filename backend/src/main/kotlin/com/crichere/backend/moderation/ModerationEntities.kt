package com.crichere.backend.moderation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/** Composite key for [UserBlockEntity], the same `@IdClass` shape as `LeagueFollowId`. */
data class UserBlockId(
    var blockerId: UUID = UUID(0, 0),
    var blockedId: UUID = UUID(0, 0),
) : Serializable

/** Maps to `user_blocks` (V25__moderation.sql). One-way, and never shown to the blocked person (docs/PHASE18.md). */
@Entity
@Table(name = "user_blocks")
@IdClass(UserBlockId::class)
class UserBlockEntity(
    @Id
    @Column(name = "blocker_id", nullable = false, updatable = false)
    var blockerId: UUID,

    @Id
    @Column(name = "blocked_id", nullable = false, updatable = false)
    var blockedId: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)

enum class ReportTargetType { USER, LEAGUE }

enum class ReportStatus { OPEN, RESOLVED, DISMISSED }

/** The reasons on boards R4a and R4b. */
enum class ReportReason {
    INAPPROPRIATE_IMAGE,
    HATE_OR_HARASSMENT,
    IMPERSONATION,
    UNDER_18,
    SPAM_OR_MISLEADING,
    BETTING_OR_GAMBLING,
    FAKE_OR_SCAM_LEAGUE,
    SOMETHING_ELSE,
}

/**
 * Maps to `reports` (V25__moderation.sql). No API ever reads these back: the team uses
 * `backend/tools/moderation`, so a reporter's identity can't leak through a response.
 */
@Entity
@Table(name = "reports")
class ReportEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "reporter_id")
    var reporterId: UUID?,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false)
    var targetType: ReportTargetType,

    @Column(name = "target_user_id")
    var targetUserId: UUID? = null,

    @Column(name = "target_league_id")
    var targetLeagueId: UUID? = null,

    @Column(name = "context_league_id")
    var contextLeagueId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false)
    var reason: ReportReason,

    @Column(name = "details", length = 500)
    var details: String? = null,

    @Column(name = "snapshot", nullable = false, columnDefinition = "TEXT")
    var snapshot: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ReportStatus = ReportStatus.OPEN,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,
)

enum class ModerationNoticeKind { PHOTO, LEAGUE_LOGO, LEAGUE_BANNER, FRANCHISE_LOGO }

/** Maps to `moderation_notices` (V25__moderation.sql): boards R12b to R12e. Rows are written by `backend/tools/moderation`. */
@Entity
@Table(name = "moderation_notices")
class ModerationNoticeEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    var kind: ModerationNoticeKind,

    @Column(name = "subject_name", columnDefinition = "TEXT")
    var subjectName: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "dismissed_at")
    var dismissedAt: Instant? = null,
)

/** Maps to `suspended_phone_hashes` (V25__moderation.sql). Keeps a suspended number out after its account is purged. */
@Entity
@Table(name = "suspended_phone_hashes")
class SuspendedPhoneHashEntity(
    @Id
    @Column(name = "phone_lookup_hash", nullable = false, updatable = false, columnDefinition = "TEXT")
    var phoneLookupHash: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)

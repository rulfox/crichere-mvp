package com.crichere.backend.moderation

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserBlockRepository : JpaRepository<UserBlockEntity, UserBlockId> {

    fun findByBlockerIdOrderByCreatedAtDesc(blockerId: UUID): List<UserBlockEntity>

    fun existsByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID): Boolean

    fun deleteByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID)

    /** Which of [blockerIds] have blocked [blockedId]: the follower push check. */
    fun findByBlockerIdInAndBlockedId(blockerIds: Collection<UUID>, blockedId: UUID): List<UserBlockEntity>

    /** Account purge (docs/PHASE18.md): both directions. */
    @Modifying
    @Query("DELETE FROM UserBlockEntity b WHERE b.blockerId = :userId OR b.blockedId = :userId")
    fun deleteAllInvolving(@Param("userId") userId: UUID)
}

interface ReportRepository : JpaRepository<ReportEntity, UUID> {

    fun existsByReporterIdAndTargetUserIdAndStatus(reporterId: UUID, targetUserId: UUID, status: ReportStatus): Boolean

    fun existsByReporterIdAndTargetLeagueIdAndStatus(reporterId: UUID, targetLeagueId: UUID, status: ReportStatus): Boolean

    fun findByStatus(status: ReportStatus): List<ReportEntity>

    /** Account purge: the evidence stays for its retention period, the reporter doesn't. */
    @Modifying
    @Query("UPDATE ReportEntity r SET r.reporterId = NULL WHERE r.reporterId = :userId")
    fun clearReporter(@Param("userId") userId: UUID)
}

interface ModerationNoticeRepository : JpaRepository<ModerationNoticeEntity, UUID> {

    fun findByUserIdAndDismissedAtIsNullOrderByCreatedAt(userId: UUID): List<ModerationNoticeEntity>
}

interface SuspendedPhoneHashRepository : JpaRepository<SuspendedPhoneHashEntity, String>

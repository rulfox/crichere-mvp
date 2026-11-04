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

/**
 * Image URLs held as evidence by open reports. Reads the snapshot's image fields only, never free text
 * like a league description, so a user can't pin someone's object by typing its URL.
 */
const val OPEN_REPORT_IMAGE_URLS = """
    select image.url from reports r
    cross join lateral (
        select cast(r.snapshot as jsonb) ->> 'photoUrl' as url
        union all select cast(r.snapshot as jsonb) ->> 'logoUrl'
        union all select cast(r.snapshot as jsonb) ->> 'bannerUrl'
        union all select franchise ->> 'logoUrl'
            from jsonb_array_elements(coalesce(cast(r.snapshot as jsonb) -> 'franchises', cast('[]' as jsonb))) franchise
    ) image
    where r.status = 'OPEN' and image.url is not null
"""

interface ReportRepository : JpaRepository<ReportEntity, UUID> {

    fun existsByReporterIdAndTargetUserIdAndStatus(reporterId: UUID, targetUserId: UUID, status: ReportStatus): Boolean

    fun existsByReporterIdAndTargetLeagueIdAndStatus(reporterId: UUID, targetLeagueId: UUID, status: ReportStatus): Boolean

    fun findByStatus(status: ReportStatus): List<ReportEntity>

    @Query(value = OPEN_REPORT_IMAGE_URLS, nativeQuery = true)
    fun findOpenEvidenceUrls(): List<String>

    /** Account purge: the evidence stays for its retention period, the reporter doesn't. */
    @Modifying
    @Query("UPDATE ReportEntity r SET r.reporterId = NULL WHERE r.reporterId = :userId")
    fun clearReporter(@Param("userId") userId: UUID)
}

interface ModerationNoticeRepository : JpaRepository<ModerationNoticeEntity, UUID> {

    fun findByUserIdAndDismissedAtIsNullOrderByCreatedAt(userId: UUID): List<ModerationNoticeEntity>

    /** Account purge. */
    fun deleteByUserId(userId: UUID)
}

interface SuspendedPhoneHashRepository : JpaRepository<SuspendedPhoneHashEntity, String>

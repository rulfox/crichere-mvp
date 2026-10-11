package com.crichere.backend.league

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * Spring Data repository for [LeagueEntity]. Two distinct list queries, per docs/PHASE2.md's
 * Decisions Made -- `near` is mutually exclusive with the state/district area filters in
 * the UI, so they're modeled as two separate repository methods rather than one query trying to
 * express both:
 *
 * - [findByAreaFilters]: state/district, each optional, combined via a single
 *   `:param IS NULL OR ...` query rather than the Specification API, matching [GroundRepository
 *   com.crichere.backend.ground.GroundRepository]'s equivalent.
 * - [findNearest]: Haversine distance against the attached ground's lat/long, native SQL (no
 *   portable JPQL trig functions) -- every league has a ground since V21, so every
 *   active league participates.
 */
interface LeagueRepository : JpaRepository<LeagueEntity, UUID> {

    @Query(
        """
        SELECT l FROM LeagueEntity l
        WHERE l.completedAt IS NULL
          AND (:state IS NULL OR l.state = :state)
          AND (:district IS NULL OR l.district = :district)
        ORDER BY l.startsOn
        """,
    )
    fun findByAreaFilters(
        @Param("state") state: String?,
        @Param("district") district: String?,
    ): List<LeagueEntity>

    @Query(
        value = """
            SELECT l.* FROM leagues l
            JOIN grounds g ON g.id = l.ground_id
            WHERE l.completed_at IS NULL
            ORDER BY (
                6371 * acos(
                    cos(radians(:latitude)) * cos(radians(g.latitude)) * cos(radians(g.longitude) - radians(:longitude))
                    + sin(radians(:latitude)) * sin(radians(g.latitude))
                )
            )
        """,
        nativeQuery = true,
    )
    fun findNearest(
        @Param("latitude") latitude: Double,
        @Param("longitude") longitude: Double,
    ): List<LeagueEntity>

    /** Every league a user organizes -- feeds `GET /api/v1/me/leagues`'s "organizing" list. */
    fun findByOrganizerUserId(organizerUserId: UUID): List<LeagueEntity>

    /** Leagues a user organizes that aren't completed yet -- the ones an account deletion must hand over (docs/PHASE16.md). */
    fun findByOrganizerUserIdAndCompletedAtIsNull(organizerUserId: UUID): List<LeagueEntity>

    /**
     * Leagues with a running auction that [userId] organizes, plays in or owns a franchise in.
     * Account deletion is refused (and a due purge postponed) while any exist (docs/PHASE16.md).
     */
    @Query(
        value = """
            SELECT l.* FROM leagues l
            WHERE l.auction_status = 'IN_PROGRESS'
              AND (
                l.organizer_user_id = :userId
                OR EXISTS (SELECT 1 FROM league_players p WHERE p.league_id = l.id AND p.user_id = :userId AND p.removed_at IS NULL)
                OR EXISTS (SELECT 1 FROM league_franchises f WHERE f.league_id = l.id AND f.owner_user_id = :userId AND f.removed_at IS NULL)
              )
        """,
        nativeQuery = true,
    )
    fun findLiveAuctionsInvolving(@Param("userId") userId: UUID): List<LeagueEntity>

    /**
     * Makes [newOrganizerUserId] the organizer. Native because `organizer_user_id` is mapped
     * `updatable = false` -- an account purge is the only thing allowed to change it.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "UPDATE leagues SET organizer_user_id = :newOrganizerUserId, organizer_handover_at = :now, organizer_handover_seen_at = NULL, updated_at = :now WHERE id = :leagueId",
        nativeQuery = true,
    )
    fun handOverOrganizer(
        @Param("leagueId") leagueId: UUID,
        @Param("newOrganizerUserId") newOrganizerUserId: UUID,
        @Param("now") now: Instant,
    ): Int

    /** The new organizer dismissed the handover notice (design D9). Idempotent: keeps the first time. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "UPDATE leagues SET organizer_handover_seen_at = :now WHERE id = :leagueId AND organizer_handover_seen_at IS NULL",
        nativeQuery = true,
    )
    fun markHandoverNoticeSeen(@Param("leagueId") leagueId: UUID, @Param("now") now: Instant): Int

    /**
     * Row-level lock for `com.crichere.backend.auction.AuctionService.placeBid` -- two bids
     * arriving the same instant must serialize against each other rather than both reading the
     * same stale current-bid value (see docs/PHASE5.md's Security section). Only ever called
     * inside a `@Transactional` method; the lock releases at commit.
     */
    /**
     * The `IN_PROGRESS` auction with the most recent (unreversed) bid, falling back to the most
     * recently updated one when none has a bid yet -- feeds the public "live now" lookup
     * (docs/PHASE11.md D5). Native SQL for `NULLS LAST` on the aggregated bid time.
     */
    @Query(
        value = """
            SELECT l.* FROM leagues l
            LEFT JOIN auction_bids b ON b.league_id = l.id AND b.reversed = FALSE
            WHERE l.auction_status = 'IN_PROGRESS'
            GROUP BY l.id
            ORDER BY MAX(b.placed_at) DESC NULLS LAST, l.updated_at DESC
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findLiveNow(): LeagueEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LeagueEntity l WHERE l.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): LeagueEntity?
}

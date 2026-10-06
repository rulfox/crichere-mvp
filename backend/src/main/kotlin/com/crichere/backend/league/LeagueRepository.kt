package com.crichere.backend.league

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

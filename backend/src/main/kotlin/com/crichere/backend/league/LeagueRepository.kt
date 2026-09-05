package com.crichere.backend.league

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Spring Data repository for [LeagueEntity]. Two distinct list queries, per docs/PHASE2.md's
 * Decisions Made -- `near` is mutually exclusive with the state/district/city area filters in
 * the UI, so they're modeled as two separate repository methods rather than one query trying to
 * express both:
 *
 * - [findByAreaFilters]: state/district/city, each optional, combined via a single
 *   `:param IS NULL OR ...` query rather than the Specification API, matching [GroundRepository
 *   com.crichere.backend.ground.GroundRepository]'s equivalent.
 * - [findNearest]: Haversine distance against the attached ground's lat/long, native SQL (no
 *   portable JPQL trig functions) -- only leagues with a `ground_id` participate (see
 *   docs/PHASE2.md's Decisions Made on why there's no city/district-centroid fallback).
 */
interface LeagueRepository : JpaRepository<LeagueEntity, UUID> {

    @Query(
        """
        SELECT l FROM LeagueEntity l
        WHERE l.completedAt IS NULL
          AND (:state IS NULL OR l.state = :state)
          AND (:district IS NULL OR l.district = :district)
          AND (:city IS NULL OR l.city = :city)
        ORDER BY l.startsOn
        """,
    )
    fun findByAreaFilters(
        @Param("state") state: String?,
        @Param("district") district: String?,
        @Param("city") city: String?,
    ): List<LeagueEntity>

    @Query(
        value = """
            SELECT l.* FROM leagues l
            JOIN grounds g ON g.id = l.ground_id
            WHERE l.ground_id IS NOT NULL AND l.completed_at IS NULL
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
}

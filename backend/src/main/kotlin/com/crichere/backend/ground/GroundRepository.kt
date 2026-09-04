package com.crichere.backend.ground

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Spring Data repository for [GroundEntity]. [searchByPattern] backs the Ground-picker's
 * search-or-register flow (`GET /api/v1/grounds`) -- state/district/city are combined via a
 * single `:param IS NULL OR ...` query rather than the Specification API, matching this
 * codebase's general preference for the simplest thing that works at MVP data volume.
 *
 * [searchPattern] is deliberately never null -- see [GroundService.search] for why: a nullable
 * `String` bound directly into a `LOWER()`/`LIKE` expression made Hibernate bind it as `bytea`
 * on Postgres when the value was actually null at runtime (`function lower(bytea) does not
 * exist`), a real, reproduced Hibernate/Postgres type-inference gap on this stack -- passing an
 * always-non-null, already-formatted `%pattern%` string (`%%` matching everything when there's
 * no search term) sidesteps it entirely rather than working around it with casts.
 */
interface GroundRepository : JpaRepository<GroundEntity, UUID> {

    @Query(
        """
        SELECT g FROM GroundEntity g
        WHERE LOWER(g.name) LIKE :searchPattern
          AND (:state IS NULL OR g.state = :state)
          AND (:district IS NULL OR g.district = :district)
          AND (:city IS NULL OR g.city = :city)
        ORDER BY g.name
        """,
    )
    fun searchByPattern(
        @Param("searchPattern") searchPattern: String,
        @Param("state") state: String?,
        @Param("district") district: String?,
        @Param("city") city: String?,
    ): List<GroundEntity>
}

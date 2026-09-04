package com.crichere.backend.reference

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data repository for [CityEntity]. [findByDistrictId] is the natural lookup for a
 * "pick your state, then your district, then your city" location UI -- consumed by the
 * reference feature's endpoints.
 */
interface CityRepository : JpaRepository<CityEntity, UUID> {
    fun findByDistrictId(districtId: UUID): List<CityEntity>
}

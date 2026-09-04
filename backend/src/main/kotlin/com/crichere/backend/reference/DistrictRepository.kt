package com.crichere.backend.reference

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Spring Data repository for [DistrictEntity]. */
interface DistrictRepository : JpaRepository<DistrictEntity, UUID> {
    fun findByStateCode(stateCode: String): List<DistrictEntity>
}

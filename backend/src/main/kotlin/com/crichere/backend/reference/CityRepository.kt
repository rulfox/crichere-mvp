package com.crichere.backend.reference

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data repository for [CityEntity]. [findByStateCode] is the natural lookup for a
 * "pick your state, then pick your city" profile UI -- consumed by the reference feature's
 * endpoints in a later task.
 */
interface CityRepository : JpaRepository<CityEntity, UUID> {
    fun findByStateCode(stateCode: String): List<CityEntity>
}

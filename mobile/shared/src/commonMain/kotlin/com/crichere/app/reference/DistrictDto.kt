package com.crichere.app.reference

import kotlinx.serialization.Serializable

/**
 * Mirrors the backend's `DistrictResponse`
 * (`backend/src/main/kotlin/com/crichere/backend/reference/dto/DistrictResponse.kt`): one row
 * of `GET /api/v1/reference/states/{stateCode}/districts`. [id] is a UUID string -- it's what
 * `getCitiesForDistrict` is keyed on, not the district's name.
 */
@Serializable
data class DistrictDto(
    val id: String,
    val name: String,
)

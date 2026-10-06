package com.crichere.app.reference

import kotlinx.serialization.Serializable

/**
 * Mirrors the backend's `DistrictResponse`
 * (`backend/src/main/kotlin/com/crichere/backend/reference/dto/DistrictResponse.kt`): one row
 * of `GET /api/v1/reference/states/{stateCode}/districts`. [id] is a UUID string; screens select
 * and store the district by [name].
 */
@Serializable
data class DistrictDto(
    val id: String,
    val name: String,
)

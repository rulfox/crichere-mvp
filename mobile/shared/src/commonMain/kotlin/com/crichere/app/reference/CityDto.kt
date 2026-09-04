package com.crichere.app.reference

import kotlinx.serialization.Serializable

/**
 * Mirrors the backend's `CityResponse`
 * (`backend/src/main/kotlin/com/crichere/backend/reference/dto/CityResponse.kt`): one row of
 * `GET /api/v1/reference/states/{stateCode}/cities`.
 */
@Serializable
data class CityDto(
    val name: String,
)

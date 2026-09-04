package com.crichere.app.reference

import kotlinx.serialization.Serializable

/**
 * Mirrors the backend's `StateResponse` (`backend/src/main/kotlin/com/crichere/backend/reference/dto/StateResponse.kt`):
 * `GET /api/v1/reference/states` returns a JSON array of these, sorted by name, seeded by
 * `V4__seed_states_cities.sql`.
 */
@Serializable
data class StateDto(
    val code: String,
    val name: String,
)

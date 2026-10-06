package com.crichere.app.ground

import kotlinx.serialization.Serializable

/** Mirrors the backend's `GroundResponse`. */
@Serializable
data class GroundDto(
    val id: String,
    val name: String,
    val state: String,
    val district: String,
    val latitude: Double,
    val longitude: Double,
)

/** Mirrors the backend's `GroundCreateRequest` -- body of `POST /api/v1/grounds`. Every field required, no resumable partial-save. */
@Serializable
data class GroundCreateRequestDto(
    val name: String,
    val state: String,
    val district: String,
    val latitude: Double,
    val longitude: Double,
)

package com.crichere.backend.ground.dto

import java.util.UUID

/** One row of `GET /api/v1/grounds`, and the response of `POST /api/v1/grounds`. */
data class GroundResponse(
    val id: UUID,
    val name: String,
    val state: String,
    val district: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
)

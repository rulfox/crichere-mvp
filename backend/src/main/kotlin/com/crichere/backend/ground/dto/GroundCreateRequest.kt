package com.crichere.backend.ground.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * Body of `POST /api/v1/grounds`. Unlike [com.crichere.backend.profile.dto.ProfileUpdateRequest],
 * this is a one-shot create, not a resumable upsert -- every field is required, since a ground
 * with a missing name or pin isn't a valid registration to begin with.
 */
data class GroundCreateRequest(
    @field:NotBlank(message = "name is required")
    @field:Size(max = 200, message = "name must be at most 200 characters")
    val name: String,

    @field:NotBlank(message = "state is required")
    val state: String,

    @field:NotBlank(message = "district is required")
    val district: String,

    @field:NotBlank(message = "city is required")
    val city: String,

    @field:NotNull(message = "latitude is required")
    @field:DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
    @field:DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
    val latitude: Double?,

    @field:NotNull(message = "longitude is required")
    @field:DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
    @field:DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
    val longitude: Double?,
)

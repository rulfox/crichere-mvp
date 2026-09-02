package com.crichere.backend.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Body of `POST /api/v1/auth/refresh`. [refreshToken] is the raw opaque token from the
 * previous `/session` or `/refresh` response; it is single-use and is rotated out on success.
 */
data class RefreshRequest(
    @field:NotBlank(message = "refreshToken is required")
    @field:Size(max = 512, message = "refreshToken is too long")
    val refreshToken: String,
)

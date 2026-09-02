package com.crichere.backend.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Body of `POST /api/v1/auth/logout`. Revokes [refreshToken] so it can no longer be
 * exchanged. The already-issued access token keeps working until it expires (at most fifteen
 * minutes) -- that is the accepted trade-off of stateless access tokens.
 */
data class LogoutRequest(
    @field:NotBlank(message = "refreshToken is required")
    @field:Size(max = 512, message = "refreshToken is too long")
    val refreshToken: String,
)

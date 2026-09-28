package com.crichere.backend.me.dto

import jakarta.validation.constraints.NotBlank

/** Body of `POST /me/device-tokens` (docs/PHASE8.md). */
data class RegisterDeviceTokenRequest(
    @field:NotBlank
    val token: String?,
    @field:NotBlank
    val platform: String?,
)

/** Body of `POST /me/device-tokens/unregister`. */
data class UnregisterDeviceTokenRequest(
    @field:NotBlank
    val token: String?,
)

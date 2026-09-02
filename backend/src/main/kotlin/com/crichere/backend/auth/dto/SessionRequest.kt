package com.crichere.backend.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Body of `POST /api/v1/auth/session`.
 *
 * [idToken] is the Firebase ID token the mobile client already obtained by completing the
 * phone-OTP flow client-side. There is no phone number and no OTP code in this request --
 * the backend neither sends nor checks OTPs.
 */
data class SessionRequest(
    @field:NotBlank(message = "idToken is required")
    // A Firebase ID token is a signed JWT of a few hundred bytes. The ceiling is a cheap
    // guard so an oversized body is rejected before any verification work is attempted.
    @field:Size(max = 4096, message = "idToken is too long")
    val idToken: String,
)

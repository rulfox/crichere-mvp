package com.crichere.app.auth

import kotlinx.serialization.Serializable

/**
 * Mirrors the backend's `AuthResponse`
 * (`backend/src/main/kotlin/com/crichere/backend/auth/dto/AuthResponse.kt`) -- the identical
 * response shape returned by both `POST /api/v1/auth/session` and `POST /api/v1/auth/refresh`.
 * Used directly as both the wire DTO and the domain return type, same pattern `StateDto` already
 * established in `reference/StateDto.kt`.
 *
 * [userId] and [accessTokenExpiresAt] stay plain `String` (the backend serializes a `UUID` and an
 * `Instant` respectively as ISO-8601-ish strings) rather than parsed types: nothing in this task
 * needs to do arithmetic on either of them client-side (refresh is triggered reactively by a 401,
 * not by checking expiry), so parsing them would only add a `kotlinx-datetime` dependency this
 * task doesn't otherwise need. A future task that wants proactive expiry-based refresh can add
 * that parsing then.
 *
 * @property profileComplete Whether the user still has onboarding to finish. Both
 *   [AuthRepository.exchangeSession] and [AuthRepository.refresh] surface this so callers can
 *   route ([AuthNavigationEvent]) without a second request.
 */
@Serializable
data class AuthResult(
    val userId: String,
    val accessToken: String,
    val tokenType: String = "Bearer",
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val profileComplete: Boolean,
)

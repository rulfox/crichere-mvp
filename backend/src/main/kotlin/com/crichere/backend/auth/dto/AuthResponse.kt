package com.crichere.backend.auth.dto

import com.crichere.backend.auth.AuthResult
import java.time.Instant
import java.util.UUID

/**
 * Response body of both `POST /api/v1/auth/session` and `POST /api/v1/auth/refresh`.
 *
 * Both endpoints return the identical shape on purpose: the mobile client has one code path
 * for "I now hold a session", whether it just signed in or just rotated.
 *
 * @property profileComplete Whether the user still has onboarding to finish. The app routes
 *   on this, which is why it rides along with the tokens instead of costing a second request.
 */
data class AuthResponse(
    val userId: UUID,
    val accessToken: String,
    /** Always `Bearer`; present so clients can build the header without hard-coding it. */
    val tokenType: String = "Bearer",
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val profileComplete: Boolean,
) {
    companion object {
        fun from(result: AuthResult) =
            AuthResponse(
                userId = result.userId,
                accessToken = result.accessToken,
                accessTokenExpiresAt = result.accessTokenExpiresAt,
                refreshToken = result.refreshToken,
                profileComplete = result.profileComplete,
            )
    }
}

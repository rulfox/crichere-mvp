package com.crichere.backend.auth

import com.crichere.backend.auth.dto.AuthResponse
import com.crichere.backend.auth.dto.LogoutRequest
import com.crichere.backend.auth.dto.RefreshRequest
import com.crichere.backend.auth.dto.SessionRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The three auth endpoints. All `POST`: each one changes server state (creates an account,
 * rotates a token, revokes a token) and each one carries a credential in the body, which has
 * no business sitting in a URL or a proxy's access log.
 *
 * There is no send-OTP endpoint. The client gets its OTP from Firebase directly.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    /**
     * Exchanges a Firebase ID token for a Crichere session, registering the account on first
     * sign-in. Idempotent with respect to the phone number: the same number always resolves
     * to the same account.
     */
    @PostMapping("/session")
    fun createSession(@Valid @RequestBody request: SessionRequest): AuthResponse =
        AuthResponse.from(authService.verifySession(request.idToken))

    /**
     * Exchanges a refresh token for a fresh pair. The presented token is revoked as part of
     * the exchange, so it works exactly once.
     */
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): AuthResponse =
        AuthResponse.from(authService.refresh(request.refreshToken))

    /**
     * Revokes a refresh token. Answers `204 No Content` regardless of whether the token
     * existed, so it cannot be used to probe for valid tokens.
     */
    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: LogoutRequest): ResponseEntity<Void> {
        authService.logout(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}

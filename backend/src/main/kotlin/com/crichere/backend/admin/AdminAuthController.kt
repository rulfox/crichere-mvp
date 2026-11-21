package com.crichere.backend.admin

import com.crichere.backend.common.ProblemDetails
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Duration
import java.time.Instant

data class AdminSignInRequest(
    /** The ID token from Google Identity Services. */
    @field:NotBlank @field:Size(max = 4096) val credential: String,
)

data class AdminSessionResponse(val email: String, val expiresAt: Instant, val liveSessions: Long? = null)

/** Admin sign-in and sessions (docs/PHASE20.md Step 2). */
@RestController
@RequestMapping("/api/v1/admin/session")
class AdminAuthController(
    private val sessions: AdminSessionService,
    private val rateLimiter: AdminSignInRateLimiter,
    private val properties: AdminProperties,
) {
    @PostMapping
    fun signIn(@Valid @RequestBody body: AdminSignInRequest, request: HttpServletRequest): ResponseEntity<AdminSessionResponse> {
        val clientIp = request.remoteAddr ?: "unknown"
        if (!rateLimiter.tryConsume(clientIp)) throw AdminSignInRateLimitedException()
        val issued = sessions.signIn(body.credential, request.getHeader(HttpHeaders.USER_AGENT), clientIp)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, adminCookie(properties, issued.rawToken, properties.sessionTtl).toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(AdminSessionResponse(issued.email, issued.expiresAt))
    }

    @GetMapping
    fun current(@AuthenticationPrincipal principal: AdminPrincipal): ResponseEntity<AdminSessionResponse> =
        ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(AdminSessionResponse(principal.email, principal.expiresAt, sessions.liveSessionCount(principal)))

    @DeleteMapping
    fun signOut(@AuthenticationPrincipal principal: AdminPrincipal): ResponseEntity<Void> {
        sessions.signOut(principal)
        return clearCookie()
    }

    @PostMapping("/sign-out-everywhere")
    fun signOutEverywhere(@AuthenticationPrincipal principal: AdminPrincipal): ResponseEntity<Void> {
        sessions.signOutEverywhere(principal)
        return clearCookie()
    }

    private fun clearCookie(): ResponseEntity<Void> =
        ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, adminCookie(properties, "", Duration.ZERO).toString())
            .build()
}

@RestControllerAdvice(assignableTypes = [AdminAuthController::class])
class AdminAuthExceptionHandler {
    @ExceptionHandler(AdminSignInFailedException::class)
    fun signInFailed(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ProblemDetails.of(
                status = HttpStatus.UNAUTHORIZED,
                slug = "admin-sign-in-failed",
                title = "Sign-in failed",
                code = "ADMIN_SIGN_IN_FAILED",
                detail = "This Google account can't sign in to the admin site.",
                instance = request.requestURI,
            ),
        )

    @ExceptionHandler(AdminSignInRateLimitedException::class)
    fun rateLimited(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "900").body(
            ProblemDetails.of(
                status = HttpStatus.TOO_MANY_REQUESTS,
                slug = "rate-limit-exceeded",
                title = "Too many requests",
                code = "RATE_LIMIT_EXCEEDED",
                detail = "Too many sign-in attempts. Please try again later.",
                instance = request.requestURI,
            ),
        )
}

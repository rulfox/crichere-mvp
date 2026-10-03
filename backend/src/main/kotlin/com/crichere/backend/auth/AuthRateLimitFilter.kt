package com.crichere.backend.auth

import com.crichere.backend.common.ProblemDetails
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import kotlin.math.max

/**
 * Caps how often one client IP may attempt to start a session.
 *
 * Only `POST /api/v1/auth/session` is limited here. That is the abuse-prone entry point: it is
 * unauthenticated, it triggers a Firebase round-trip, and it is the endpoint that creates
 * accounts. `/refresh` and `/logout` are left alone deliberately -- both require a 256-bit
 * random token, so there is nothing to guess, and an IP-wide limit on refresh would be the
 * most likely way to lock a whole CGNAT'd carrier out of an app that refreshes every fifteen
 * minutes. See [AuthRateLimiter] for the per-phone dimension and the chosen numbers.
 *
 * ## Client IP
 *
 * The key is `ServletRequest.getRemoteAddr()`, never an `X-Forwarded-For` header. A header is
 * caller-supplied: honouring it unconditionally would let anyone bypass the limit entirely by
 * varying one string. If this service is ever put behind a reverse proxy, the correct fix is
 * to configure Spring Boot's `server.forward-headers-strategy` so the container itself
 * resolves `remoteAddr` from the proxy's trusted headers -- not to read the header here.
 *
 * ## Why it responds directly
 *
 * A servlet filter runs outside the dispatcher servlet, so `@RestControllerAdvice` cannot see
 * anything thrown here. The 429 body is therefore written by hand, through the same
 * [ProblemDetails] builder the advice uses, so clients see one error format everywhere.
 */
class AuthRateLimitFilter(
    private val rateLimiter: AuthRateLimiter,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val ip = request.remoteAddr ?: UNKNOWN_IP
        val retryAfter = when (request.requestURI) {
            OTP_SEND_PATH, OTP_RESEND_PATH -> rateLimiter.tryConsumeOtpSendForIp(ip)
            OTP_VERIFY_PATH -> rateLimiter.tryConsumeOtpVerifyForIp(ip)
            else -> rateLimiter.tryConsumeForIp(ip)
        }
        if (retryAfter != null) {
            writeTooManyRequests(request, response, retryAfter)
            return
        }
        filterChain.doFilter(request, response)
    }

    /** Restricts this filter to the one endpoint it guards. */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !(request.method.equals("POST", ignoreCase = true) && request.requestURI in GUARDED_PATHS)

    private fun writeTooManyRequests(
        request: HttpServletRequest,
        response: HttpServletResponse,
        retryAfter: Duration,
    ) {
        val retryAfterSeconds = max(1L, retryAfter.seconds)
        val problem = ProblemDetails.of(
            status = HttpStatus.TOO_MANY_REQUESTS,
            slug = "rate-limit-exceeded",
            title = "Too many requests",
            code = "RATE_LIMIT_EXCEEDED",
            detail = "Too many authentication attempts. Please try again later.",
            instance = request.requestURI,
            extensions = mapOf("retryAfterSeconds" to retryAfterSeconds),
        )

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
        objectMapper.writeValue(response.outputStream, problem)
    }

    private companion object {
        const val SESSION_PATH = "/api/v1/auth/session"
        const val OTP_SEND_PATH = "/api/v1/auth/otp/send"
        const val OTP_RESEND_PATH = "/api/v1/auth/otp/resend"
        const val OTP_VERIFY_PATH = "/api/v1/auth/otp/verify"
        val GUARDED_PATHS = setOf(SESSION_PATH, OTP_SEND_PATH, OTP_RESEND_PATH, OTP_VERIFY_PATH)
        const val UNKNOWN_IP = "unknown"
    }
}

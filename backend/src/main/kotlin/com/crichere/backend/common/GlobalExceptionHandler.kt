package com.crichere.backend.common

import com.crichere.backend.auth.AuthenticationFailedException
import com.crichere.backend.auth.RateLimitExceededException
import com.crichere.backend.profile.BowlingStyleNotAllowedException
import com.crichere.backend.profile.BowlingStyleRequiredException
import com.crichere.backend.profile.PhotoUploadUnavailableException
import com.crichere.backend.reference.MalformedDistrictIdException
import com.crichere.backend.reference.MalformedStateCodeException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import kotlin.math.max

/**
 * Turns exceptions escaping the controllers into RFC 7807 responses.
 *
 * Only exceptions that something in the current codebase can actually throw are handled here.
 * Speculative handlers for a not-found entity or a conflict would be dead code today; they
 * belong to the task that first throws them.
 *
 * Note that this advice never sees a Spring Security rejection: those are raised inside the
 * filter chain, before the dispatcher servlet picks a handler. Those are wired separately in
 * `SecurityConfig`, using the same [ProblemDetails] builder so the shapes match.
 *
 * The governing rule for every handler below: the exception's own message never reaches the
 * response body. Framework and JPA exception messages routinely embed SQL fragments, entity
 * class names, and constraint names.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Bean-validation failure on a `@Valid @RequestBody`.
     *
     * The per-field messages *are* echoed, because they come from our own DTO annotations
     * (`"idToken is required"`), not from a framework internal -- and a client genuinely
     * cannot fix a 400 it cannot localise to a field.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val errors = exception.bindingResult.fieldErrors.associate { fieldError ->
            fieldError.field to (fieldError.defaultMessage ?: "is invalid")
        }
        return ProblemDetails.of(
            status = HttpStatus.BAD_REQUEST,
            slug = "validation-failed",
            title = "Validation failed",
            code = "VALIDATION_FAILED",
            detail = "One or more fields in the request body are missing or invalid.",
            instance = request.requestURI,
            extensions = mapOf("errors" to errors),
        )
    }

    /**
     * Body absent, truncated, or not parseable as JSON. The parser's message points at byte
     * offsets and Jackson class names, so it is dropped entirely.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.BAD_REQUEST,
            slug = "malformed-request",
            title = "Malformed request",
            code = "MALFORMED_REQUEST",
            detail = "The request body could not be read as JSON.",
            instance = request.requestURI,
        )

    /** e.g. `GET /api/v1/auth/session`, which exists only as a `POST`. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.METHOD_NOT_ALLOWED,
            slug = "method-not-allowed",
            title = "Method not allowed",
            code = "METHOD_NOT_ALLOWED",
            detail = "This endpoint does not support the requested HTTP method.",
            instance = request.requestURI,
        )

    /**
     * Every authentication failure -- bad Firebase token, unknown/expired/revoked refresh
     * token -- collapses into one indistinguishable 401. Which credential failed, and why, is
     * exactly the information an attacker probing for valid accounts wants.
     */
    @ExceptionHandler(AuthenticationFailedException::class)
    fun handleAuthenticationFailed(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.UNAUTHORIZED,
            slug = "invalid-credentials",
            title = "Invalid credentials",
            code = "INVALID_CREDENTIALS",
            detail = "The supplied credentials are not valid.",
            instance = request.requestURI,
        )

    /**
     * Rate limit tripped. `Retry-After` is set (in whole seconds, as RFC 9110 requires) so a
     * well-behaved client backs off instead of retrying immediately.
     */
    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimitExceeded(
        exception: RateLimitExceededException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val retryAfterSeconds = max(1L, exception.retryAfter.seconds)
        val body = ProblemDetails.of(
            status = HttpStatus.TOO_MANY_REQUESTS,
            slug = "rate-limit-exceeded",
            title = "Too many requests",
            code = "RATE_LIMIT_EXCEEDED",
            detail = "Too many authentication attempts. Please try again later.",
            instance = request.requestURI,
            extensions = mapOf("retryAfterSeconds" to retryAfterSeconds),
        )
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
            .body(body)
    }

    /** An unknown path under a public prefix, e.g. `POST /api/v1/auth/nope`. */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.NOT_FOUND,
            slug = "not-found",
            title = "Not found",
            code = "NOT_FOUND",
            detail = "No endpoint matches this request.",
            instance = request.requestURI,
        )

    /**
     * A `{state}` path segment that cannot possibly be a state code (see
     * [com.crichere.backend.reference.ReferenceController]). Same shape as
     * [handleNoResourceFound] -- from the caller's point of view this is exactly that: nothing
     * matches this request.
     */
    @ExceptionHandler(MalformedStateCodeException::class)
    fun handleMalformedStateCode(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.NOT_FOUND,
            slug = "not-found",
            title = "Not found",
            code = "NOT_FOUND",
            detail = "No state matches this code.",
            instance = request.requestURI,
        )

    /** Same shape as [handleMalformedStateCode], for the District-retrofit `districts/{id}/cities` endpoint. */
    @ExceptionHandler(MalformedDistrictIdException::class)
    fun handleMalformedDistrictId(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.NOT_FOUND,
            slug = "not-found",
            title = "Not found",
            code = "NOT_FOUND",
            detail = "No district matches this id.",
            instance = request.requestURI,
        )

    /**
     * `PUT /profiles/me` supplied a `playingRole`/`bowlingStyle` combination that violates the
     * role-conditional rule (see `com.crichere.backend.profile.ProfileService`). Both
     * directions share one `type`/`code` -- a client branching on `code` doesn't need to tell
     * them apart -- but get their own fixed `detail` text rather than the exception's own
     * message, for the same reason every other handler in this file does that.
     */
    @ExceptionHandler(BowlingStyleRequiredException::class)
    fun handleBowlingStyleRequired(request: HttpServletRequest): ProblemDetail =
        profileValidationProblem(
            detail = "bowlingStyle is required when playingRole is BOWLER or ALL_ROUNDER.",
            request = request,
        )

    @ExceptionHandler(BowlingStyleNotAllowedException::class)
    fun handleBowlingStyleNotAllowed(request: HttpServletRequest): ProblemDetail =
        profileValidationProblem(
            detail = "bowlingStyle is only allowed when playingRole is BOWLER or ALL_ROUNDER.",
            request = request,
        )

    private fun profileValidationProblem(detail: String, request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.BAD_REQUEST,
            slug = "invalid-bowling-style",
            title = "Invalid bowling style",
            code = "INVALID_BOWLING_STYLE",
            detail = detail,
            instance = request.requestURI,
        )

    /**
     * `POST /profiles/me/photo-upload-url` was called but S3 is not usable in this environment
     * yet (see `com.crichere.backend.profile.PhotoUploadService`). `503`, not `500`: the
     * request itself was fine, an external dependency is the one that is not ready.
     */
    @ExceptionHandler(PhotoUploadUnavailableException::class)
    fun handlePhotoUploadUnavailable(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.SERVICE_UNAVAILABLE,
            slug = "photo-upload-unavailable",
            title = "Photo upload unavailable",
            code = "PHOTO_UPLOAD_UNAVAILABLE",
            detail = "Photo upload is temporarily unavailable. Please try again later.",
            instance = request.requestURI,
        )

    /**
     * Anything unanticipated. The stack trace goes to the server log -- where it is useful and
     * private -- and the caller gets a bare sentence with no class names, no SQL, and no
     * indication of what broke.
     *
     * Spring Security's own exceptions are re-thrown untouched. They are raised *inside* the
     * dispatcher (method security) but are meant to be translated by `ExceptionTranslationFilter`
     * further out in the chain; swallowing them here would silently turn every 401/403 into a
     * 500. Re-throwing the identical instance lets the resolver fall through without logging a
     * spurious warning.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception, request: HttpServletRequest): ProblemDetail {
        if (exception is AccessDeniedException || exception is AuthenticationException) throw exception
        log.error("Unhandled exception while handling {} {}", request.method, request.requestURI, exception)
        return ProblemDetails.of(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            slug = "internal-error",
            title = "Internal server error",
            code = "INTERNAL_ERROR",
            detail = "An unexpected error occurred.",
            instance = request.requestURI,
        )
    }
}

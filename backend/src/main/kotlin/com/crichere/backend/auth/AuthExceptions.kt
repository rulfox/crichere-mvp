package com.crichere.backend.auth

import java.time.Duration

/**
 * Auth failures that the API turns into a deliberate, non-descriptive error response.
 *
 * These carry no context about *why* a credential was rejected. That is intentional: telling
 * a caller "this refresh token exists but is revoked" versus "no such token" hands them an
 * oracle. Every one of these becomes the same flat 401 body.
 */
sealed class AuthenticationFailedException(message: String) : RuntimeException(message) {
    /** The Firebase ID token was missing, malformed, expired, or carried no phone number. */
    class InvalidFirebaseIdTokenException : AuthenticationFailedException("Invalid Firebase ID token")

    /** The refresh token was unknown, already revoked, or past its expiry. */
    class InvalidRefreshTokenException : AuthenticationFailedException("Invalid refresh token")

    /** The bearer access token was missing, malformed, expired, or not signed by us. */
    class InvalidAccessTokenException : AuthenticationFailedException("Invalid access token")
}

typealias InvalidFirebaseIdTokenException = AuthenticationFailedException.InvalidFirebaseIdTokenException
typealias InvalidRefreshTokenException = AuthenticationFailedException.InvalidRefreshTokenException
typealias InvalidAccessTokenException = AuthenticationFailedException.InvalidAccessTokenException

/**
 * Too many authentication attempts for one rate-limit key. [retryAfter] is how long the caller
 * should wait before the bucket has a token again; it is surfaced as the `Retry-After` header.
 *
 * The message deliberately does not name the key (phone hash or IP) that tripped.
 */
class RateLimitExceededException(val retryAfter: Duration) :
    RuntimeException("Too many authentication attempts")

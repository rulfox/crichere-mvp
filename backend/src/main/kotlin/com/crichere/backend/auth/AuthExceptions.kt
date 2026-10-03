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

/** The backend-driven OTP flow is not the active provider (`crichere.otp.provider`), so the `/auth/otp` endpoints do not exist. */
class OtpDisabledException : RuntimeException("OTP provider is not enabled")

/** Not an Indian mobile number (see [PhoneNumberNormalizer]). Echoes nothing about the input. */
class InvalidPhoneNumberException : RuntimeException("Invalid phone number")

/** The code was wrong. [attemptsRemaining] reaches 0 on the attempt that invalidated the challenge. */
class OtpInvalidCodeException(val attemptsRemaining: Int) : RuntimeException("Incorrect code")

/**
 * The challenge is unknown, expired, already used, or out of attempts. One exception for all of
 * them so a caller cannot tell a guessed id from a spent one.
 */
class OtpChallengeExpiredException : RuntimeException("Code expired")

/** Resends for this challenge are used up; the client must start over from phone entry. */
class OtpResendLimitReachedException : RuntimeException("Resend limit reached")

/** Provider failure, missing credentials, or the global send cap. Surfaces as a generic 503. */
class OtpUnavailableException(message: String = "OTP delivery unavailable", cause: Throwable? = null) :
    RuntimeException(message, cause)

/** `crichere.otp.app-check-required` is on and the App Check token was absent or invalid. */
class AppCheckFailedException : RuntimeException("App Check verification failed")

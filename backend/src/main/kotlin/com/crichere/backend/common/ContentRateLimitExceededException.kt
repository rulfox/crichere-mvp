package com.crichere.backend.common

import java.time.Duration

/**
 * Too many creation attempts for one rate-limit key on a content-creation endpoint (league,
 * ground, or award). Distinct from [com.crichere.backend.auth.RateLimitExceededException] --
 * that one is auth-attempt-specific wording ("Too many authentication attempts"), which would
 * be a misleading message on a `POST /leagues` rejection. [retryAfter] is how long the caller
 * should wait before the bucket has a token again; surfaced as the `Retry-After` header.
 */
class ContentRateLimitExceededException(val retryAfter: Duration) :
    RuntimeException("Too many requests")

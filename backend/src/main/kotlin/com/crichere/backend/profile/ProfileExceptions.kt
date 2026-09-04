package com.crichere.backend.profile

/**
 * The role/bowling-style cross-field rule (see [ProfileService]) was violated by a
 * `PUT /api/v1/profiles/me` request. Turned into a `400` `ProblemDetail` by
 * `com.crichere.backend.common.GlobalExceptionHandler`, following the same pattern Task 3
 * established for [com.crichere.backend.auth.AuthenticationFailedException].
 *
 * Two subtypes rather than one generic exception because the two directions warrant different
 * `detail` text -- "you forgot a field" and "you supplied a field you shouldn't have" are not
 * the same instruction to a client trying to fix its request.
 */
sealed class ProfileValidationException(message: String) : RuntimeException(message) {
    /** `playingRole` is `BOWLER`/`ALL_ROUNDER` but `bowlingStyle` was not supplied. */
    class BowlingStyleRequiredException :
        ProfileValidationException("bowlingStyle is required when playingRole is BOWLER or ALL_ROUNDER")

    /** `playingRole` is not `BOWLER`/`ALL_ROUNDER` (including absent) but `bowlingStyle` was supplied. */
    class BowlingStyleNotAllowedException :
        ProfileValidationException("bowlingStyle is only allowed when playingRole is BOWLER or ALL_ROUNDER")
}

typealias BowlingStyleRequiredException = ProfileValidationException.BowlingStyleRequiredException
typealias BowlingStyleNotAllowedException = ProfileValidationException.BowlingStyleNotAllowedException

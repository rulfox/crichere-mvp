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

/**
 * `POST /api/v1/profiles/me/photo-upload-url` was called but S3 is not usable: the bucket/
 * region configuration is blank, or no AWS credentials could be resolved. Both are expected,
 * temporary states of this environment before AWS setup happens (mirrors
 * [com.crichere.backend.auth.FirebaseAdminTokenVerifier]'s relationship with Firebase
 * credentials) -- turned into a `503`, not a `500`, since the server itself is fine and the
 * caller's request was well-formed.
 */
class PhotoUploadUnavailableException : RuntimeException("Photo upload is not configured")

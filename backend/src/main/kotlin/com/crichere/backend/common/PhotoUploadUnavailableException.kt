package com.crichere.backend.common

/**
 * A presigned-upload endpoint was called but S3 is not usable: the bucket/region configuration
 * is blank, or no AWS credentials could be resolved. Both are expected, temporary states of this
 * environment before AWS setup happens (mirrors
 * [com.crichere.backend.auth.FirebaseAdminTokenVerifier]'s relationship with Firebase
 * credentials) -- turned into a `503`, not a `500`, since the server itself is fine and the
 * caller's request was well-formed. Shared across every feature that uploads to S3 (profile
 * photo, league logo/banner) -- see [PhotoUploadService].
 */
class PhotoUploadUnavailableException : RuntimeException("Photo upload is not configured")

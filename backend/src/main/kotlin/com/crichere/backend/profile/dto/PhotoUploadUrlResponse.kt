package com.crichere.backend.profile.dto

import java.time.Instant

/**
 * Body of `POST /api/v1/profiles/me/photo-upload-url`.
 *
 * Shaped after S3's presigned-POST contract: the client issues a multipart `POST` straight to
 * [uploadUrl] with every entry of [fields] included as a form field (in the order they are
 * given -- S3 requires the file itself to be the last part), plus the file under a `file`
 * field. There is no separate "confirm upload" step; the object lands at [key] the moment S3
 * accepts the POST, and the client is expected to `PUT /profiles/me` with `photoUrl` set to the
 * resulting public/CDN URL for that key once the upload succeeds.
 */
data class PhotoUploadUrlResponse(
    val uploadUrl: String,
    val fields: Map<String, String>,
    val key: String,
    val expiresAt: Instant,
)

package com.crichere.backend.common

import java.time.Instant

/**
 * An S3 presigned-POST contract, returned by every presign endpoint (profile photo, league
 * logo/banner) -- see [PhotoUploadService].
 */
data class PhotoUploadUrlResponse(
    val uploadUrl: String,
    val fields: Map<String, String>,
    val key: String,
    val expiresAt: Instant,
)

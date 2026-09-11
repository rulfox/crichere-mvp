package com.crichere.app.upload

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * Mirrors the backend's `PhotoUploadUrlResponse`
 * (`backend/src/main/kotlin/com/crichere/backend/common/PhotoUploadUrlResponse.kt`) -- an S3
 * presigned-POST contract, shared by every presign endpoint (profile photo, league logo/banner).
 * [expiresAt] stays a plain `String` (same reasoning `AuthResult` documents for its own
 * `Instant`-shaped fields: nothing here needs to do arithmetic on it).
 */
@Serializable
data class PhotoUploadInfoDto(
    val uploadUrl: String,
    val fields: Map<String, String>,
    val key: String,
    val expiresAt: String,
)

/** Thrown by [uploadPhotoViaPresignedPost] when the S3 POST itself doesn't return 2xx. */
class PhotoUploadFailedException(message: String) : Exception(message)

/**
 * Performs the multipart `POST` straight to [uploadInfo]'s `uploadUrl`: every entry of
 * [PhotoUploadInfoDto.fields] as a form field, in order, followed by [bytes] under a `file` field
 * (S3 requires the file part last). Returns the computed public URL (`uploadUrl + key`) to save
 * into whatever record is being updated (profile/league) -- the backend does not hand this
 * concatenated form back.
 *
 * Shared by [com.crichere.app.profile.ProfileRepository] and
 * [com.crichere.app.league.LeagueRepository] -- both presign against the same backend mechanism
 * (`PhotoUploadService`), so this is the one place that S3 POST shape is implemented, not
 * duplicated per feature.
 *
 * [uploadClient] must be the separate, unauthenticated, no-base-URL client
 * (`HttpClientFactory.createUploadClient()`) -- see that factory method's doc for why it must
 * stay separate from the default authenticated client.
 */
suspend fun uploadPhotoViaPresignedPost(
    uploadClient: HttpClient,
    uploadInfo: PhotoUploadInfoDto,
    bytes: ByteArray,
    contentType: String,
    filename: String,
): String {
    val response = uploadClient.submitFormWithBinaryData(
        url = uploadInfo.uploadUrl,
        formData = formData {
            uploadInfo.fields.forEach { (key, value) -> append(key, value) }
            // S3's presigned-POST policy condition `["starts-with", "$Content-Type", ...]`
            // (see backend PhotoUploadService) is checked against a plain form field named
            // "Content-Type", not the "file" part's own Content-Type header below -- both are
            // required, and this field must come before "file" like every other field.
            append("Content-Type", contentType)
            append(
                key = "file",
                value = bytes,
                headers = Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                },
            )
        },
    )
    if (!response.status.isSuccess()) {
        throw PhotoUploadFailedException("S3 upload failed with status ${response.status}")
    }
    return uploadInfo.uploadUrl + uploadInfo.key
}

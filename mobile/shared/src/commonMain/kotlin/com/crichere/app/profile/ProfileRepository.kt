package com.crichere.app.profile

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Headers
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Thrown by [ProfileRepository.saveProfile] for anything other than a clean 2xx. */
class ProfileSaveFailedException(message: String) : Exception(message)

/**
 * Thrown by [ProfileRepository.requestPhotoUploadUrl] on the real `503` this environment's
 * unconfigured AWS S3 setup returns (`crichere.aws.s3.bucket`/`region` blank -- see
 * `PhotoUploadService.kt`'s `PhotoUploadUnavailableException`). Distinct from
 * [ProfileSaveFailedException] so `ProfileSetupViewModel` can show a specific, expected message
 * ("photo upload unavailable, try again later") rather than a generic failure.
 */
class PhotoUploadUnavailableException(
    message: String = "Photo upload is unavailable right now. Please try again later.",
) : Exception(message)

/** Thrown by [ProfileRepository.uploadPhoto] when the S3 POST itself doesn't return 2xx. */
class PhotoUploadFailedException(message: String) : Exception(message)

/**
 * `commonMain` profile use cases against the real backend (`GET`/`PUT /api/v1/profiles/me`,
 * `POST /api/v1/profiles/me/photo-upload-url`) plus the S3 presigned-POST leg itself
 * ([uploadPhoto]). Follows [com.crichere.app.reference.ReferenceRepository]'s established
 * interface+`Ktor*Impl` pattern.
 */
interface ProfileRepository {
    /** `GET /api/v1/profiles/me`. Always `200`; every field `null`/`profileComplete == false` for a brand-new user. */
    suspend fun getProfile(): ProfileDto

    /** `PUT /api/v1/profiles/me`. [snapshot] must be the full accumulated onboarding state, not a partial diff. */
    suspend fun saveProfile(snapshot: ProfileUpdateRequestDto): ProfileDto

    /** `POST /api/v1/profiles/me/photo-upload-url`. Throws [PhotoUploadUnavailableException] on a `503`. */
    suspend fun requestPhotoUploadUrl(): PhotoUploadInfoDto

    /**
     * Performs the multipart `POST` straight to [PhotoUploadInfoDto.uploadUrl]: every entry of
     * [PhotoUploadInfoDto.fields] as a form field, in order, followed by [bytes] under a `file`
     * field (S3 requires the file part last). Returns the computed public URL
     * (`uploadUrl + key`) to save into the profile's `photoUrl` -- the backend does not hand this
     * concatenated form back, per this task's brief.
     */
    suspend fun uploadPhoto(uploadInfo: PhotoUploadInfoDto, bytes: ByteArray, contentType: String): String
}

/**
 * Real implementation. [httpClient] must be the default, *authenticated* Koin-registered client
 * (`HttpClientFactory.create(...)`, with the `Auth` bearer plugin) -- **not**
 * `AuthRepository`'s `authHttpClient` -- since every endpoint here is authenticated. [uploadClient]
 * is the separate, unauthenticated, no-base-URL client (`HttpClientFactory.createUploadClient()`)
 * used only for the absolute-URL S3 POST -- see that factory method's doc for why it must stay
 * separate from [httpClient].
 */
internal class KtorProfileRepository(
    private val httpClient: HttpClient,
    private val uploadClient: HttpClient,
) : ProfileRepository {

    override suspend fun getProfile(): ProfileDto =
        httpClient.get("/api/v1/profiles/me").body()

    override suspend fun saveProfile(snapshot: ProfileUpdateRequestDto): ProfileDto {
        val response = httpClient.put("/api/v1/profiles/me") {
            contentType(ContentType.Application.Json)
            setBody(snapshot)
        }
        if (!response.status.isSuccess()) {
            throw ProfileSaveFailedException("Profile save failed with status ${response.status}")
        }
        return response.body()
    }

    override suspend fun requestPhotoUploadUrl(): PhotoUploadInfoDto {
        val response: HttpResponse = httpClient.post("/api/v1/profiles/me/photo-upload-url")
        if (response.status == HttpStatusCode.ServiceUnavailable) {
            throw PhotoUploadUnavailableException()
        }
        if (!response.status.isSuccess()) {
            throw ProfileSaveFailedException("Photo upload URL request failed with status ${response.status}")
        }
        return response.body()
    }

    override suspend fun uploadPhoto(uploadInfo: PhotoUploadInfoDto, bytes: ByteArray, contentType: String): String {
        val response = uploadClient.submitFormWithBinaryData(
            url = uploadInfo.uploadUrl,
            formData = formData {
                // Preserve the backend's field order: S3's presigned-POST policy requires the
                // `file` part to be the last one in the multipart body.
                uploadInfo.fields.forEach { (key, value) -> append(key, value) }
                append(
                    key = "file",
                    value = bytes,
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, contentType)
                        append(HttpHeaders.ContentDisposition, "filename=\"profile.jpg\"")
                    },
                )
            },
        )
        if (!response.status.isSuccess()) {
            throw PhotoUploadFailedException("S3 upload failed with status ${response.status}")
        }
        return uploadInfo.uploadUrl + uploadInfo.key
    }
}

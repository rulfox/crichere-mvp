package com.crichere.app.profile

import com.crichere.app.upload.PhotoUploadInfoDto
import kotlinx.coroutines.delay

/** In-memory [ProfileRepository] test double -- no real backend/S3 to hit in tests. */
class FakeProfileRepository(
    var profile: ProfileDto = ProfileDto(userId = "11111111-1111-1111-1111-111111111111"),
) : ProfileRepository {

    var getProfileCallCount = 0
        private set
    var saveProfileCallCount = 0
        private set
    val savedSnapshots = mutableListOf<ProfileUpdateRequestDto>()

    var getProfileError: Throwable? = null
    var saveProfileError: Throwable? = null
    var nextProfileComplete: Boolean = false

    var photoUploadInfo: PhotoUploadInfoDto = PhotoUploadInfoDto(
        uploadUrl = "https://crichere-media-dev.s3.ap-south-1.amazonaws.com/",
        fields = mapOf("key" to "users/u1/profile.jpg"),
        key = "users/u1/profile.jpg",
        expiresAt = "2026-09-03T12:05:00.000Z",
    )
    var requestPhotoUploadUrlError: Throwable? = null
    var uploadPhotoError: Throwable? = null
    var uploadedPhotoUrl: String = "https://crichere-media-dev.s3.ap-south-1.amazonaws.com/users/u1/profile.jpg"

    /** Simulates a slow response -- lets tests prove a stale, still in-flight [getProfile] call gets cancelled rather than overwriting a newer one's state. */
    var getProfileDelayMillis: Long = 0

    override suspend fun getProfile(): ProfileDto {
        getProfileCallCount++
        if (getProfileDelayMillis > 0) delay(getProfileDelayMillis)
        getProfileError?.let { throw it }
        return profile
    }

    override suspend fun saveProfile(snapshot: ProfileUpdateRequestDto): ProfileDto {
        saveProfileCallCount++
        savedSnapshots += snapshot
        saveProfileError?.let { throw it }
        return ProfileDto(
            userId = profile.userId,
            name = snapshot.name,
            photoUrl = snapshot.photoUrl,
            country = profile.country,
            state = snapshot.state,
            district = snapshot.district,
            city = snapshot.city,
            playingRole = snapshot.playingRole,
            battingStyle = snapshot.battingStyle,
            bowlingStyle = snapshot.bowlingStyle,
            profileComplete = nextProfileComplete,
        )
    }

    override suspend fun requestPhotoUploadUrl(): PhotoUploadInfoDto {
        requestPhotoUploadUrlError?.let { throw it }
        return photoUploadInfo
    }

    override suspend fun uploadPhoto(uploadInfo: PhotoUploadInfoDto, bytes: ByteArray, contentType: String): String {
        uploadPhotoError?.let { throw it }
        return uploadedPhotoUrl
    }
}

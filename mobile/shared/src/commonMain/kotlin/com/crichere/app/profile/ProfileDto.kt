package com.crichere.app.profile

import kotlinx.serialization.Serializable

/**
 * Mirrors the backend's `PlayingRole`
 * (`backend/src/main/kotlin/com/crichere/backend/profile/PlayingRole.kt`) -- default
 * kotlinx.serialization enum-by-name encoding, values must match the backend's exactly.
 */
@Serializable
enum class PlayingRole {
    BATSMAN,
    BOWLER,
    ALL_ROUNDER,
    WICKETKEEPER,
}

/** Mirrors the backend's `BattingStyle` (`backend/src/main/kotlin/com/crichere/backend/profile/BattingStyle.kt`). */
@Serializable
enum class BattingStyle {
    RIGHT_HAND,
    LEFT_HAND,
}

/** Mirrors the backend's `BowlingStyle` (`backend/src/main/kotlin/com/crichere/backend/profile/BowlingStyle.kt`). */
@Serializable
enum class BowlingStyle {
    RIGHT_ARM_FAST,
    RIGHT_ARM_MEDIUM,
    RIGHT_ARM_OFFBREAK,
    RIGHT_ARM_LEGBREAK,
    LEFT_ARM_FAST,
    LEFT_ARM_MEDIUM,
    LEFT_ARM_ORTHODOX,
    LEFT_ARM_CHINAMAN,
}

/**
 * Mirrors the backend's `ProfileResponse`
 * (`backend/src/main/kotlin/com/crichere/backend/profile/dto/ProfileResponse.kt`) -- body of
 * `GET /api/v1/profiles/me` and the response of `PUT /api/v1/profiles/me`. Every field except
 * [userId] and [profileComplete] is `null` for a user with no profile row yet; that is a `200`,
 * not a `404` (see the backend doc), which is exactly what makes this the resumability pre-fill
 * source this task's `ProfileSetupViewModel` calls on entry.
 *
 * [state] holds the state's **display name** (e.g. `"Karnataka"`), never its code -- per this
 * task's ruling (nothing backend-side ties this free-form field to the reference table).
 */
@Serializable
data class ProfileDto(
    val userId: String,
    val name: String? = null,
    val photoUrl: String? = null,
    val country: String? = null,
    val state: String? = null,
    val district: String? = null,
    val city: String? = null,
    val playingRole: PlayingRole? = null,
    val battingStyle: BattingStyle? = null,
    val bowlingStyle: BowlingStyle? = null,
    val profileComplete: Boolean = false,
)

/**
 * Mirrors the backend's `ProfileUpdateRequest`
 * (`backend/src/main/kotlin/com/crichere/backend/profile/dto/ProfileUpdateRequest.kt`) -- body
 * of `PUT /api/v1/profiles/me`. Same shape as [ProfileDto] minus `userId`/`profileComplete`/
 * `country` (the backend doesn't accept any of the three in this request).
 *
 * This is a **full-replace upsert, not a merge-patch**: every call site must send the complete
 * accumulated onboarding snapshot (not-yet-collected fields as `null`), never just the one field
 * the user last touched -- see `ProfileSetupViewModel`.
 */
@Serializable
data class ProfileUpdateRequestDto(
    val name: String? = null,
    val photoUrl: String? = null,
    val state: String? = null,
    val district: String? = null,
    val city: String? = null,
    val playingRole: PlayingRole? = null,
    val battingStyle: BattingStyle? = null,
    val bowlingStyle: BowlingStyle? = null,
)

/**
 * Mirrors the backend's `PhotoUploadUrlResponse`
 * (`backend/src/main/kotlin/com/crichere/backend/profile/dto/PhotoUploadUrlResponse.kt`) -- an
 * S3 presigned-POST contract. [expiresAt] stays a plain `String` (same reasoning `AuthResult`
 * documents for its own `Instant`-shaped fields: nothing here needs to do arithmetic on it).
 */
@Serializable
data class PhotoUploadInfoDto(
    val uploadUrl: String,
    val fields: Map<String, String>,
    val key: String,
    val expiresAt: String,
)

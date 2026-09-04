package com.crichere.app.league

import kotlinx.serialization.Serializable

/** Mirrors the backend's `LeagueStatus` (`backend/.../league/LeagueEntity.kt`). No `DRAFT` -- see that file's doc. */
@Serializable
enum class LeagueStatus {
    ANNOUNCED,
    COMPLETED,
}

/** Mirrors the backend's `LeagueAwardResponse`. */
@Serializable
data class LeagueAwardDto(
    val id: String,
    val name: String,
    val cashAmount: Double? = null,
    val hasTrophy: Boolean = false,
    val displayOrder: Int = 0,
)

/** Mirrors the backend's `LeagueAwardSaveRequest` -- body of award create/edit, and one entry of [LeagueSaveRequestDto.awards]. */
@Serializable
data class LeagueAwardSaveRequestDto(
    val name: String,
    val cashAmount: Double? = null,
    val hasTrophy: Boolean = false,
)

/**
 * Mirrors the backend's `LeagueResponse`. [startsOn] stays a plain ISO-8601 `String`
 * ("2026-10-12") -- same reasoning `AuthResult`/`PhotoUploadInfoDto` document for their own
 * date/instant fields: nothing here needs real date arithmetic, only display and round-tripping
 * through a date picker.
 */
@Serializable
data class LeagueDto(
    val id: String,
    val organizerUserId: String,
    val name: String,
    val description: String? = null,
    val logoUrl: String? = null,
    val bannerUrl: String? = null,
    val country: String = "IN",
    val state: String,
    val district: String,
    val city: String,
    val groundId: String? = null,
    val startsOn: String,
    val format: String? = null,
    val franchisesRequired: Int? = null,
    val playersRequired: Int? = null,
    val franchiseFee: Double? = null,
    val playerFee: Double? = null,
    val status: LeagueStatus,
    val awards: List<LeagueAwardDto> = emptyList(),
)

/** Mirrors the backend's `LeagueSaveRequest` -- body of both create and edit. */
@Serializable
data class LeagueSaveRequestDto(
    val name: String,
    val description: String? = null,
    val logoUrl: String? = null,
    val bannerUrl: String? = null,
    val state: String,
    val district: String,
    val city: String,
    val groundId: String? = null,
    val startsOn: String,
    val format: String? = null,
    val franchisesRequired: Int? = null,
    val playersRequired: Int? = null,
    val franchiseFee: Double? = null,
    val playerFee: Double? = null,
    val awards: List<LeagueAwardSaveRequestDto>? = null,
)

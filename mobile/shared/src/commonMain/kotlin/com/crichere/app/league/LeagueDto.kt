package com.crichere.app.league

import com.crichere.app.profile.PlayingRole
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

/** Mirrors the backend's `LeaguePlayerResponse`. `paymentScreenshotUrl`/`leaveRequestedAt` are `null` unless the caller is the organizer or this row's own user (see docs/PHASE3.md). */
@Serializable
data class LeaguePlayerDto(
    val id: String,
    val userId: String,
    val name: String? = null,
    val joinedAt: String,
    val paymentScreenshotUrl: String? = null,
    val leaveRequestedAt: String? = null,
    /** From the player's profile; `null` if unset (or from a backend older than this field). */
    val playingRole: PlayingRole? = null,
)

/** Body of `POST /leagues/{id}/players`. */
@Serializable
data class LeaguePlayerJoinRequestDto(
    val paymentScreenshotUrl: String? = null,
)

/** Mirrors the backend's `LeagueFranchiseResponse`. Same redaction rule as [LeaguePlayerDto]. */
@Serializable
data class LeagueFranchiseDto(
    val id: String,
    val ownerUserId: String,
    val ownerName: String? = null,
    val name: String,
    val logoUrl: String? = null,
    val joinedAt: String,
    val paymentScreenshotUrl: String? = null,
    val leaveRequestedAt: String? = null,
)

/** Mirrors the backend's `LeagueRoleResponse` (docs/PHASE7.md) -- one active co-organizer grant. */
@Serializable
data class LeagueRoleDto(
    val id: String,
    val userId: String,
    val name: String? = null,
    val grantedAt: String,
)

/** Mirrors the backend's `RoleLookupResponse` -- who a phone number resolved to, not yet a grant. */
@Serializable
data class RoleLookupResultDto(
    val userId: String,
    val name: String? = null,
)

/** Body of `POST /leagues/{id}/franchises`. */
@Serializable
data class LeagueFranchiseClaimRequestDto(
    val name: String,
    val logoUrl: String? = null,
    val paymentScreenshotUrl: String? = null,
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
    val groundName: String? = null,
    val startsOn: String,
    val format: String? = null,
    val franchisesRequired: Int? = null,
    val playersRequired: Int? = null,
    val franchiseFee: Double? = null,
    val playerFee: Double? = null,
    val organizerUpiId: String? = null,
    val status: LeagueStatus,
    val awards: List<LeagueAwardDto> = emptyList(),
    val players: List<LeaguePlayerDto> = emptyList(),
    val franchises: List<LeagueFranchiseDto> = emptyList(),
    val isFollowing: Boolean = false,
    val auctionBasePrice: Double? = null,
    val auctionPurse: Double? = null,
    val auctionSquadMin: Int? = null,
    val auctionSquadMax: Int? = null,
    val auctionBidIncrement: Double? = null,
    val auctionSquadMaxWarning: Boolean = false,
    val coOrganizers: List<LeagueRoleDto> = emptyList(),
)

/**
 * "Can this caller act as this league's organizer" (docs/PHASE7.md) -- the plain
 * `organizerUserId` comparison every screen used before this phase, widened to also accept an
 * active co-organizer grant. Client-side gating only, same as every other `isOrganizer`-shaped
 * check in this app -- the real enforcement is always server-side (`LeagueAuthorization`).
 */
fun LeagueDto.isOrganizerOrCoOrganizer(userId: String?): Boolean =
    userId != null && (userId == organizerUserId || coOrganizers.any { it.userId == userId })

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
    val organizerUpiId: String? = null,
    val awards: List<LeagueAwardSaveRequestDto>? = null,
)

/** Mirrors the backend's `AuctionSettingsSaveRequest` -- body of `PUT /leagues/{id}/auction-settings` (see docs/PHASE4.md). All fields required together, full-replace, same posture as [LeagueSaveRequestDto]. */
@Serializable
data class AuctionSettingsSaveRequestDto(
    val basePrice: Double,
    val purse: Double,
    val squadMin: Int,
    val squadMax: Int,
    val bidIncrement: Double,
)

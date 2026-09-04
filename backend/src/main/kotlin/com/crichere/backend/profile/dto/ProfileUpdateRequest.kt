package com.crichere.backend.profile.dto

import com.crichere.backend.profile.BattingStyle
import com.crichere.backend.profile.BowlingStyle
import com.crichere.backend.profile.PlayingRole
import jakarta.validation.constraints.Size

/**
 * Body of `PUT /api/v1/profiles/me`.
 *
 * Every field is optional. This is a **full-replace upsert, not a merge-patch**: whatever this
 * request carries for a field -- including a field left out (`null`) -- becomes that field's
 * new stored value. The reason every field is nullable rather than required is the mobile
 * app's resumable onboarding flow (see [com.crichere.backend.profile.ProfileController]):
 * `GET /profiles/me` pre-fills the client's local form state from whatever is already saved,
 * the client fills in more fields as the user progresses through the setup screens, and each
 * `PUT` re-submits that whole accumulated snapshot -- which is why the client, not the server,
 * is responsible for carrying forward fields from a previous save rather than the server
 * merging partial updates on the client's behalf.
 *
 * Only length constraints are expressed here as Bean Validation; nothing is `@NotBlank`/
 * `@NotNull` because nothing is required at the single-request level -- a first PUT with only
 * `name` set is exactly how onboarding starts. The role-conditional requirement on
 * [bowlingStyle] is genuinely cross-field (it depends on [playingRole] within this same
 * request) and cannot be expressed as a per-field annotation, so
 * [com.crichere.backend.profile.ProfileService] checks it explicitly.
 */
data class ProfileUpdateRequest(
    @field:Size(min = 1, max = 100, message = "name must be between 1 and 100 characters")
    val name: String? = null,

    // A signed S3 object URL is comfortably under this; the ceiling exists only to reject an
    // absurdly oversized value before it is persisted.
    @field:Size(min = 1, max = 2048, message = "photoUrl must be between 1 and 2048 characters")
    val photoUrl: String? = null,

    @field:Size(min = 1, max = 100, message = "state must be between 1 and 100 characters")
    val state: String? = null,

    @field:Size(min = 1, max = 100, message = "district must be between 1 and 100 characters")
    val district: String? = null,

    @field:Size(min = 1, max = 100, message = "city must be between 1 and 100 characters")
    val city: String? = null,

    val playingRole: PlayingRole? = null,

    val battingStyle: BattingStyle? = null,

    val bowlingStyle: BowlingStyle? = null,
)

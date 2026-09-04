package com.crichere.backend.profile.dto

import com.crichere.backend.profile.BattingStyle
import com.crichere.backend.profile.BowlingStyle
import com.crichere.backend.profile.PlayingRole
import java.util.UUID

/**
 * Body of `GET /api/v1/profiles/me` and the response of `PUT /api/v1/profiles/me`.
 *
 * Every field except [userId] and [profileComplete] is nullable. For a user with no `profiles`
 * row yet, every one of them is `null` and [profileComplete] is `false` -- this is a `200`, not
 * a `404` (see [com.crichere.backend.profile.ProfileController]), matching the mobile app's
 * resumable-onboarding flow: it calls this endpoint on entry to pre-fill whatever has been
 * saved and compute which setup screen to show next.
 *
 * [profileComplete] is never computed here -- it always comes from
 * [com.crichere.backend.profile.ProfileCompletionLookup], the single source of truth for the
 * completeness rule shared with `/auth/session` and `/auth/refresh`.
 */
data class ProfileResponse(
    val userId: UUID,
    val name: String?,
    val photoUrl: String?,
    val country: String?,
    val state: String?,
    val district: String?,
    val city: String?,
    val playingRole: PlayingRole?,
    val battingStyle: BattingStyle?,
    val bowlingStyle: BowlingStyle?,
    val profileComplete: Boolean,
)

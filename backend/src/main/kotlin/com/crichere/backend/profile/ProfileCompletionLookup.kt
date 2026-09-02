package com.crichere.backend.profile

import java.util.UUID

/**
 * The one thing the auth feature is allowed to ask the profile feature.
 *
 * `/auth/session` and `/auth/refresh` both have to tell the mobile app whether the user still
 * needs to finish onboarding, which is a profile question. Rather than let `auth` reach into
 * `ProfileRepository`/`ProfileEntity` -- the two features are not permitted to touch each
 * other's internals -- it depends on this single-method interface, and the profile feature
 * owns the implementation and the definition of "complete".
 */
interface ProfileCompletionLookup {
    /**
     * @return `true` when [userId] has a profile row with every required field filled in.
     *   `false` when the profile is partially filled, or when no profile row exists at all --
     *   a user with no profile yet is *incomplete*, not an error.
     */
    fun isComplete(userId: UUID): Boolean
}

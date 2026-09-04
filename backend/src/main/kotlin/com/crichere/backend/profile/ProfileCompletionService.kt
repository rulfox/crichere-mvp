package com.crichere.backend.profile

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The single source of truth for "is this player's profile finished?".
 *
 * The mobile app routes on this: a user whose profile is complete goes to the home screen,
 * one whose profile is not goes to the profile-setup flow. Both `/auth/session` and
 * `/auth/refresh` return it, and the profile feature's own `GET /profiles/me` must return the
 * same answer -- so the rule lives here and only here. Nothing else in the codebase should
 * re-derive it, and it is deliberately *not* a stored column: a stored flag can drift out of
 * sync with the fields it summarises.
 *
 * ## The rule
 *
 * Always required: `name`, `photoUrl`, `state`, `district`, `city`, `playingRole`, `battingStyle`.
 *
 * `bowlingStyle` is required only when the player's role means they actually bowl --
 * [PlayingRole.BOWLER] and [PlayingRole.ALL_ROUNDER]. A pure batsman or a wicketkeeper is
 * complete without one, and demanding it would leave those players permanently stuck in
 * onboarding.
 *
 * Text fields must be non-blank, not merely non-null: a name of `"   "` is not a name, and
 * the database columns are plain nullable `VARCHAR`/`TEXT` with no such constraint.
 */
@Service
class ProfileCompletionService(
    private val profileRepository: ProfileRepository,
) : ProfileCompletionLookup {

    @Transactional(readOnly = true)
    override fun isComplete(userId: UUID): Boolean =
        profileRepository.findById(userId)
            .map { isComplete(it) }
            .orElse(false)

    /**
     * The rule itself, applied to an already-loaded profile.
     *
     * Exposed as an overload so a caller that has just read (or written) the entity -- the
     * profile feature's own endpoints -- can get the answer without a second query, while
     * still going through this one implementation.
     */
    fun isComplete(profile: ProfileEntity): Boolean {
        if (profile.name.isNullOrBlank()) return false
        if (profile.photoUrl.isNullOrBlank()) return false
        if (profile.state.isNullOrBlank()) return false
        if (profile.district.isNullOrBlank()) return false
        if (profile.city.isNullOrBlank()) return false
        if (profile.battingStyle == null) return false

        val role = profile.playingRole ?: return false
        if (requiresBowlingStyle(role) && profile.bowlingStyle == null) return false

        return true
    }

    /** Whether [role] is one that bowls, and therefore must declare a bowling style. */
    private fun requiresBowlingStyle(role: PlayingRole): Boolean =
        when (role) {
            PlayingRole.BOWLER, PlayingRole.ALL_ROUNDER -> true
            // Exhaustive on purpose: adding a role to the enum should force a decision here
            // rather than silently defaulting to "does not bowl".
            PlayingRole.BATSMAN, PlayingRole.WICKETKEEPER -> false
        }
}

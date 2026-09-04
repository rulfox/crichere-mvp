package com.crichere.backend.profile

import com.crichere.backend.profile.dto.ProfileResponse
import com.crichere.backend.profile.dto.ProfileUpdateRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The profile feature's read and upsert logic.
 *
 * Composes with [ProfileCompletionLookup] for the one question it never answers itself --
 * "is this profile complete?" -- rather than re-deriving that rule. See
 * [ProfileCompletionService] for why that rule lives in exactly one place.
 */
@Service
class ProfileService(
    private val profileRepository: ProfileRepository,
    private val profileCompletionLookup: ProfileCompletionLookup,
) {

    /**
     * `GET /profiles/me`. Returns a fully-`null` response for a user with no `profiles` row
     * yet rather than failing -- see the class doc on [ProfileResponse] for why that is a
     * deliberate `200`, not a `404`.
     */
    @Transactional(readOnly = true)
    fun getMyProfile(userId: UUID): ProfileResponse {
        val profile = profileRepository.findById(userId).orElse(null)
        return toResponse(userId, profile)
    }

    /**
     * `PUT /profiles/me`: a full-replace upsert (see [ProfileUpdateRequest] for why every
     * field, present or absent, becomes the new stored value) plus the one cross-field rule
     * Bean Validation cannot express.
     *
     * @throws BowlingStyleRequiredException [ProfileUpdateRequest.playingRole] is a bowling
     *   role but [ProfileUpdateRequest.bowlingStyle] was not supplied.
     * @throws BowlingStyleNotAllowedException [ProfileUpdateRequest.playingRole] is not a
     *   bowling role (including absent) but [ProfileUpdateRequest.bowlingStyle] was supplied.
     */
    @Transactional
    fun upsert(userId: UUID, request: ProfileUpdateRequest): ProfileResponse {
        validateBowlingStyle(request.playingRole, request.bowlingStyle)

        val profile = profileRepository.findById(userId).orElseGet { ProfileEntity(userId = userId) }
        profile.name = request.name
        profile.photoUrl = request.photoUrl
        profile.state = request.state
        profile.city = request.city
        profile.playingRole = request.playingRole
        profile.battingStyle = request.battingStyle
        profile.bowlingStyle = request.bowlingStyle

        val saved = profileRepository.save(profile)
        return toResponse(userId, saved)
    }

    /**
     * The rule this task's brief calls out as "not annotation-expressible": whether
     * [bowlingStyle] may/must be present depends on [role], which is a different field on the
     * same request. Evaluated purely against the request's own two fields -- not merged with
     * whatever the stored profile already has -- because a full-replace PUT makes every
     * request self-contained; see [ProfileUpdateRequest].
     */
    private fun validateBowlingStyle(role: PlayingRole?, bowlingStyle: BowlingStyle?) {
        val requiresBowlingStyle = role == PlayingRole.BOWLER || role == PlayingRole.ALL_ROUNDER
        if (requiresBowlingStyle && bowlingStyle == null) throw BowlingStyleRequiredException()
        if (!requiresBowlingStyle && bowlingStyle != null) throw BowlingStyleNotAllowedException()
    }

    private fun toResponse(userId: UUID, profile: ProfileEntity?): ProfileResponse =
        ProfileResponse(
            userId = userId,
            name = profile?.name,
            photoUrl = profile?.photoUrl,
            country = profile?.country,
            state = profile?.state,
            city = profile?.city,
            playingRole = profile?.playingRole,
            battingStyle = profile?.battingStyle,
            bowlingStyle = profile?.bowlingStyle,
            // Always the same call whether or not a row exists -- ProfileCompletionLookup
            // already defines "no row" as incomplete, so there is no second, parallel
            // completeness decision made here.
            profileComplete = profileCompletionLookup.isComplete(userId),
        )
}

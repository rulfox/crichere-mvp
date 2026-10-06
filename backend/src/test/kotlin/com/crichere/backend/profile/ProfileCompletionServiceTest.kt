package com.crichere.backend.profile

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule that decides where the mobile app sends a user after login, so it is worth being
 * exhaustive: every playing role, and every required field individually removed.
 */
class ProfileCompletionServiceTest {

    private val profileRepository = mockk<ProfileRepository>()
    private val service = ProfileCompletionService(profileRepository)

    // ---------------------------------------------------------------- lookup behaviour

    @Test
    fun `a user with no profile row yet is incomplete, not an error`() {
        // The state of every user between finishing OTP and finishing onboarding.
        every { profileRepository.findById(USER_ID) } returns Optional.empty()

        assertFalse(service.isComplete(USER_ID))
    }

    @Test
    fun `a user with a fully filled profile is complete`() {
        every { profileRepository.findById(USER_ID) } returns Optional.of(complete(PlayingRole.BATSMAN))

        assertTrue(service.isComplete(USER_ID))
    }

    @Test
    fun `a user with a half-filled profile is incomplete`() {
        every { profileRepository.findById(USER_ID) } returns
            Optional.of(complete(PlayingRole.BATSMAN).apply { district = null })

        assertFalse(service.isComplete(USER_ID))
    }

    // ---------------------------------------------------------------- role branches

    @Nested
    @DisplayName("bowling style requirement by role")
    inner class BowlingStyleByRole {

        @Test
        fun `a bowler without a bowling style is incomplete`() {
            assertFalse(service.isComplete(complete(PlayingRole.BOWLER).apply { bowlingStyle = null }))
        }

        @Test
        fun `a bowler with a bowling style is complete`() {
            assertTrue(service.isComplete(complete(PlayingRole.BOWLER)))
        }

        @Test
        fun `an all-rounder without a bowling style is incomplete`() {
            assertFalse(service.isComplete(complete(PlayingRole.ALL_ROUNDER).apply { bowlingStyle = null }))
        }

        @Test
        fun `an all-rounder with a bowling style is complete`() {
            assertTrue(service.isComplete(complete(PlayingRole.ALL_ROUNDER)))
        }

        @Test
        fun `a batsman without a bowling style is complete`() {
            // Requiring one would leave every specialist batsman stuck in onboarding forever.
            assertTrue(service.isComplete(complete(PlayingRole.BATSMAN).apply { bowlingStyle = null }))
        }

        @Test
        fun `a wicketkeeper without a bowling style is complete`() {
            assertTrue(service.isComplete(complete(PlayingRole.WICKETKEEPER).apply { bowlingStyle = null }))
        }

        @Test
        fun `a batsman who happens to have declared a bowling style is still complete`() {
            // An optional field being present must never make a profile *less* complete.
            assertTrue(service.isComplete(complete(PlayingRole.BATSMAN)))
        }

        @Test
        fun `a wicketkeeper who happens to have declared a bowling style is still complete`() {
            assertTrue(service.isComplete(complete(PlayingRole.WICKETKEEPER)))
        }
    }

    @ParameterizedTest(name = "a fully filled {0} profile is complete")
    @EnumSource(PlayingRole::class)
    fun `every role can reach a complete profile`(role: PlayingRole) {
        // Guards against a role that can never finish onboarding, which would be invisible
        // from the backend and look like a broken app.
        assertTrue(service.isComplete(complete(role)))
    }

    // ---------------------------------------------------------------- missing fields

    @ParameterizedTest(name = "{1} role: a profile missing {0} is incomplete")
    @MethodSource("missingRequiredFieldCases")
    fun `every required field is genuinely required`(
        fieldName: String,
        role: PlayingRole,
        remove: (ProfileEntity) -> Unit,
    ) {
        val profile = complete(role).also(remove)

        assertFalse(service.isComplete(profile), "$fieldName is missing, so the profile is not complete")
    }

    @ParameterizedTest(name = "{1} role: a blank {0} does not count as filled in")
    @MethodSource("blankRequiredFieldCases")
    fun `whitespace does not satisfy a required text field`(
        fieldName: String,
        role: PlayingRole,
        blank: (ProfileEntity) -> Unit,
    ) {
        // The columns are plain nullable VARCHAR/TEXT with no CHECK, so "   " reaches us
        // intact; treating it as filled in would let a user skip the whole setup flow.
        val profile = complete(role).also(blank)

        assertFalse(service.isComplete(profile), "$fieldName is blank, so the profile is not complete")
    }

    @Test
    fun `country is not part of the completeness rule`() {
        // It is NOT NULL with a default of 'IN' in the schema, so it is always present and
        // asking about it would be noise.
        assertTrue(service.isComplete(complete(PlayingRole.BATSMAN).apply { country = "IN" }))
    }

    private companion object {
        val USER_ID: UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        /** A profile with every field populated, including the optional bowling style. */
        fun complete(role: PlayingRole) = ProfileEntity(
            userId = USER_ID,
            name = "Rohit Sharma",
            photoUrl = "https://cdn.crichere.app/photos/rohit.jpg",
            country = "IN",
            state = "Maharashtra",
            district = "Mumbai City",
            playingRole = role,
            battingStyle = BattingStyle.RIGHT_HAND,
            bowlingStyle = BowlingStyle.RIGHT_ARM_OFFBREAK,
        )

        /**
         * Each always-required field, nulled out, crossed with every role -- so a field that is
         * only checked on some branch of the role logic cannot slip through.
         */
        @JvmStatic
        fun missingRequiredFieldCases(): List<Arguments> {
            val removals: List<Pair<String, (ProfileEntity) -> Unit>> = listOf(
                "name" to { p -> p.name = null },
                "photoUrl" to { p -> p.photoUrl = null },
                "state" to { p -> p.state = null },
                "district" to { p -> p.district = null },
                "playingRole" to { p -> p.playingRole = null },
                "battingStyle" to { p -> p.battingStyle = null },
            )
            return PlayingRole.entries.flatMap { role ->
                removals.map { (field, removal) ->
                    Arguments.of(field, role, removal)
                }
            }
        }

        /** The same sweep for the text fields, using whitespace instead of null. */
        @JvmStatic
        fun blankRequiredFieldCases(): List<Arguments> {
            val blanks: List<Pair<String, (ProfileEntity) -> Unit>> = listOf(
                "name" to { p -> p.name = "   " },
                "photoUrl" to { p -> p.photoUrl = "" },
                "state" to { p -> p.state = " " },
                "district" to { p -> p.district = " " },
            )
            return PlayingRole.entries.flatMap { role ->
                blanks.map { (field, blank) ->
                    Arguments.of(field, role, blank)
                }
            }
        }
    }
}

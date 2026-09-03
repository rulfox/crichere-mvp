package com.crichere.app.profile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ProfileSetupState.isSaveEnabled] mirrors the backend's own completeness rule
 * (`ProfileCompletionService.isComplete`) client-side, for the save button -- this task's brief
 * calls the backend's own test matrix for that rule "the highest-value test in the phase" and
 * asks for the same rigor here: all 4 [PlayingRole] values crossed with every required-field
 * missing permutation. Pure state, no coroutines needed.
 */
class ProfileSetupStateIsSaveEnabledTest {

    private fun completeState(role: PlayingRole, bowlingStyle: BowlingStyle? = null) = ProfileSetupState(
        isLoading = false,
        name = "Rahul Sharma",
        photoUrl = "https://bucket.s3.ap-south-1.amazonaws.com/users/u1/profile.jpg",
        state = "Karnataka",
        city = "Bengaluru",
        playingRole = role,
        battingStyle = BattingStyle.RIGHT_HAND,
        bowlingStyle = bowlingStyle,
    )

    @Test
    fun `BATSMAN -- complete without a bowling style is enabled`() {
        assertTrue(completeState(PlayingRole.BATSMAN).isSaveEnabled)
    }

    @Test
    fun `WICKETKEEPER -- complete without a bowling style is enabled`() {
        assertTrue(completeState(PlayingRole.WICKETKEEPER).isSaveEnabled)
    }

    @Test
    fun `BOWLER -- complete WITH a bowling style is enabled`() {
        assertTrue(completeState(PlayingRole.BOWLER, BowlingStyle.RIGHT_ARM_FAST).isSaveEnabled)
    }

    @Test
    fun `BOWLER -- missing bowling style is disabled`() {
        assertFalse(completeState(PlayingRole.BOWLER).isSaveEnabled)
    }

    @Test
    fun `ALL_ROUNDER -- complete WITH a bowling style is enabled`() {
        assertTrue(completeState(PlayingRole.ALL_ROUNDER, BowlingStyle.LEFT_ARM_CHINAMAN).isSaveEnabled)
    }

    @Test
    fun `ALL_ROUNDER -- missing bowling style is disabled`() {
        assertFalse(completeState(PlayingRole.ALL_ROUNDER).isSaveEnabled)
    }

    @Test
    fun `BATSMAN -- a bowling style present when not applicable is disabled, mirrors the backend's reject direction`() {
        assertFalse(completeState(PlayingRole.BATSMAN, BowlingStyle.RIGHT_ARM_FAST).isSaveEnabled)
    }

    @Test
    fun `WICKETKEEPER -- a bowling style present when not applicable is disabled`() {
        assertFalse(completeState(PlayingRole.WICKETKEEPER, BowlingStyle.LEFT_ARM_FAST).isSaveEnabled)
    }

    @Test
    fun `no role selected is disabled regardless of everything else`() {
        assertFalse(completeState(PlayingRole.BATSMAN).copy(playingRole = null).isSaveEnabled)
    }

    @Test
    fun `saving is disabled while a save is already in flight`() {
        assertFalse(completeState(PlayingRole.BATSMAN).copy(isSaving = true).isSaveEnabled)
    }

    private val allRoles = listOf(PlayingRole.BATSMAN, PlayingRole.BOWLER, PlayingRole.ALL_ROUNDER, PlayingRole.WICKETKEEPER)

    @Test
    fun `missing name disables save for every role`() {
        for (role in allRoles) {
            val bowling = if (role == PlayingRole.BOWLER || role == PlayingRole.ALL_ROUNDER) BowlingStyle.RIGHT_ARM_FAST else null
            assertFalse(completeState(role, bowling).copy(name = "").isSaveEnabled, "role=$role")
            assertFalse(completeState(role, bowling).copy(name = "   ").isSaveEnabled, "role=$role blank name")
        }
    }

    @Test
    fun `missing photo disables save for every role`() {
        for (role in allRoles) {
            val bowling = if (role == PlayingRole.BOWLER || role == PlayingRole.ALL_ROUNDER) BowlingStyle.RIGHT_ARM_FAST else null
            assertFalse(completeState(role, bowling).copy(photoUrl = null).isSaveEnabled, "role=$role")
        }
    }

    @Test
    fun `missing state disables save for every role`() {
        for (role in allRoles) {
            val bowling = if (role == PlayingRole.BOWLER || role == PlayingRole.ALL_ROUNDER) BowlingStyle.RIGHT_ARM_FAST else null
            assertFalse(completeState(role, bowling).copy(state = null).isSaveEnabled, "role=$role")
        }
    }

    @Test
    fun `missing city disables save for every role`() {
        for (role in allRoles) {
            val bowling = if (role == PlayingRole.BOWLER || role == PlayingRole.ALL_ROUNDER) BowlingStyle.RIGHT_ARM_FAST else null
            assertFalse(completeState(role, bowling).copy(city = null).isSaveEnabled, "role=$role")
        }
    }

    @Test
    fun `missing batting style disables save for every role`() {
        for (role in allRoles) {
            val bowling = if (role == PlayingRole.BOWLER || role == PlayingRole.ALL_ROUNDER) BowlingStyle.RIGHT_ARM_FAST else null
            assertFalse(completeState(role, bowling).copy(battingStyle = null).isSaveEnabled, "role=$role")
        }
    }
}

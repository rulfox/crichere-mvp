@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.profile

import com.crichere.app.auth.AuthRepository
import com.crichere.app.auth.AuthResult
import com.crichere.app.auth.viewModelTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [OwnProfileViewModel] coverage per this task's Testing Strategy: loads and displays correctly,
 * the edit signal fires, and logout calls [AuthRepository.logout] then signals navigation to
 * Phone Entry.
 */
class OwnProfileViewModelTest {

    private class StubAuthRepository : AuthRepository {
        var logoutCallCount = 0
            private set

        override suspend fun sendOtp(phoneNumber: String, resendToken: Any?) = error("not used in this test")
        override suspend fun verifyOtp(verificationId: String, code: String) = error("not used in this test")
        override suspend fun exchangeSession(idToken: String) = error("not used in this test")
        override suspend fun refresh(): AuthResult? = error("not used in this test")

        override suspend fun logout() {
            logoutCallCount++
        }
    }

    @Test
    fun `loads and displays the profile on init`() = viewModelTest {
        val profile = ProfileDto(
            userId = "u1", name = "Rahul Sharma", photoUrl = "https://x/y.jpg", country = "India",
            state = "Karnataka", district = "Bengaluru Urban", city = "Bengaluru", playingRole = PlayingRole.ALL_ROUNDER,
            battingStyle = BattingStyle.RIGHT_HAND, bowlingStyle = BowlingStyle.RIGHT_ARM_OFFBREAK,
            profileComplete = true,
        )
        val profileRepository = FakeProfileRepository(profile)
        val viewModel = OwnProfileViewModel(profileRepository, StubAuthRepository())

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(false, state.isLoading)
        assertEquals("Rahul Sharma", state.name)
        assertEquals("Karnataka", state.state)
        assertEquals("Bengaluru Urban", state.district)
        assertEquals("Bengaluru", state.city)
        assertEquals(PlayingRole.ALL_ROUNDER, state.playingRole)
        assertEquals(BowlingStyle.RIGHT_ARM_OFFBREAK, state.bowlingStyle)
        assertEquals(1, profileRepository.getProfileCallCount)
    }

    @Test
    fun `edit fires the navigate-to-edit-profile signal`() = viewModelTest {
        val viewModel = OwnProfileViewModel(FakeProfileRepository(), StubAuthRepository())
        advanceUntilIdle()

        val observedEvents = mutableListOf<OwnProfileNavigationEvent>()
        val collectorJob = launch { viewModel.navigationEvents.toList(observedEvents) }

        viewModel.editProfile()
        advanceUntilIdle()

        assertEquals(1, observedEvents.size)
        assertEquals(OwnProfileNavigationEvent.NavigateToEditProfile, observedEvents.single())
        collectorJob.cancel()
    }

    @Test
    fun `logout calls AuthRepository logout and signals navigation to Phone Entry`() = viewModelTest {
        val authRepository = StubAuthRepository()
        val viewModel = OwnProfileViewModel(FakeProfileRepository(), authRepository)
        advanceUntilIdle()

        val observedEvents = mutableListOf<OwnProfileNavigationEvent>()
        val collectorJob = launch { viewModel.navigationEvents.toList(observedEvents) }

        viewModel.logout()
        advanceUntilIdle()

        assertEquals(1, authRepository.logoutCallCount)
        assertEquals(1, observedEvents.size)
        assertEquals(OwnProfileNavigationEvent.NavigateToPhoneEntry, observedEvents.single())
        collectorJob.cancel()
    }

    @Test
    fun `a load failure surfaces an error message instead of crashing`() = viewModelTest {
        val profileRepository = FakeProfileRepository().apply {
            getProfileError = RuntimeException("network blip")
        }
        val viewModel = OwnProfileViewModel(profileRepository, StubAuthRepository())

        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.errorMessage != null)
    }
}

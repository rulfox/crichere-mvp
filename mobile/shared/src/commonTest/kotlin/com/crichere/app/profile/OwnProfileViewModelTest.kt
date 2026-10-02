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
        override suspend fun getCurrentUserId(): String? = error("not used in this test")

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
        viewModel.retry()

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
    fun `a first load failure is flagged instead of crashing`() = viewModelTest {
        val profileRepository = FakeProfileRepository().apply {
            getProfileError = RuntimeException("network blip")
        }
        val viewModel = OwnProfileViewModel(profileRepository, StubAuthRepository())
        viewModel.retry()

        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.loadFailed)
    }

    @Test
    fun `a stale in-flight retry from an earlier user is cancelled, not left free to overwrite a newer user's state`() = viewModelTest {
        // Reproduces a real on-device bug: this ViewModel instance outlives any single visit
        // (Koin's koinViewModel() with no key returns the same instance for the whole process
        // lifetime), so viewing this tab as one user, logging out, and signing in as someone else
        // can leave an earlier retry() still in flight when a newer one starts. Without cancelling
        // the older one, its slower response could land after the newer one's and silently show
        // the previous user's profile again.
        val profileRepository = FakeProfileRepository(ProfileDto(userId = "a", name = "User A"))
        val viewModel = OwnProfileViewModel(profileRepository, StubAuthRepository())
        viewModel.retry()
        advanceUntilIdle() // let the first, real visit settle first

        profileRepository.getProfileDelayMillis = 1000
        viewModel.retry() // stale visit, still in flight, never advanced

        profileRepository.profile = ProfileDto(userId = "b", name = "User B")
        profileRepository.getProfileDelayMillis = 0
        viewModel.retry() // fresh visit -- must win

        advanceUntilIdle()

        assertEquals("User B", viewModel.state.value.name)
    }

    // ---------------------------------------------------------------- board N (2026-10-02)

    private val complete = ProfileDto(
        userId = "u1", name = "Aarav Pawar", photoUrl = "https://cdn/old.jpg", state = "Maharashtra", district = "Kolhapur",
        city = "Kolhapur", playingRole = PlayingRole.ALL_ROUNDER, battingStyle = BattingStyle.RIGHT_HAND, bowlingStyle = BowlingStyle.RIGHT_ARM_OFFBREAK,
    )

    @Test
    fun `changing the photo uploads it, then saves the whole profile pointing at it`() = viewModelTest {
        val repository = FakeProfileRepository(complete).apply { uploadedPhotoUrl = "https://cdn/new.jpg?v=2" }
        val viewModel = OwnProfileViewModel(repository, StubAuthRepository())
        viewModel.retry()
        advanceUntilIdle()

        viewModel.changePhoto(ByteArray(10), "image/jpeg")
        advanceUntilIdle()

        val saved = repository.savedSnapshots.single()
        assertEquals("https://cdn/new.jpg?v=2", saved.photoUrl)
        assertEquals("Aarav Pawar", saved.name)
        assertEquals(BowlingStyle.RIGHT_ARM_OFFBREAK, saved.bowlingStyle)
        assertEquals(false, viewModel.state.value.isUploadingPhoto)
        assertEquals(null, viewModel.state.value.photoError)
    }

    @Test
    fun `a failed photo change keeps the old photo and says so`() = viewModelTest {
        val repository = FakeProfileRepository(complete).apply { uploadPhotoError = RuntimeException("timeout") }
        val viewModel = OwnProfileViewModel(repository, StubAuthRepository())
        viewModel.retry()
        advanceUntilIdle()

        viewModel.changePhoto(ByteArray(10), "image/jpeg")
        advanceUntilIdle()

        assertTrue(repository.savedSnapshots.isEmpty())
        assertEquals("https://cdn/old.jpg", viewModel.state.value.photoUrl)
        assertEquals("Couldn't change your photo. Check your connection and try again.", viewModel.state.value.photoError)
        assertEquals(false, viewModel.state.value.isUploadingPhoto)
    }

    @Test
    fun `a failed refresh keeps the profile on screen`() = viewModelTest {
        val repository = FakeProfileRepository(complete)
        val viewModel = OwnProfileViewModel(repository, StubAuthRepository())
        viewModel.retry()
        advanceUntilIdle()

        repository.getProfileError = RuntimeException("network blip")
        viewModel.retry()
        advanceUntilIdle()

        assertEquals("Aarav Pawar", viewModel.state.value.name)
        assertEquals(false, viewModel.state.value.loadFailed)
    }
}

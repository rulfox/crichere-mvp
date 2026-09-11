@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.league

import com.crichere.app.auth.AuthRepository
import com.crichere.app.auth.AuthResult
import com.crichere.app.auth.viewModelTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [LeagueDetailViewModel] coverage: loads the league, gates organizer-only actions on
 * [AuthRepository.getCurrentUserId] matching the league's `organizerUserId`, and marking a league
 * completed only proceeds for the organizer.
 */
class LeagueDetailViewModelTest {

    private class StubAuthRepository(private val currentUserId: String?) : AuthRepository {
        override suspend fun sendOtp(phoneNumber: String, resendToken: Any?) = error("not used in this test")
        override suspend fun verifyOtp(verificationId: String, code: String) = error("not used in this test")
        override suspend fun exchangeSession(idToken: String) = error("not used in this test")
        override suspend fun refresh(): AuthResult? = error("not used in this test")
        override suspend fun logout() = error("not used in this test")
        override suspend fun getCurrentUserId(): String? = currentUserId
    }

    private fun sampleLeague(id: String = "l1", organizerUserId: String = "organizer-1") = LeagueDto(
        id = id,
        organizerUserId = organizerUserId,
        name = "Weekend League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = "2026-10-12",
        status = LeagueStatus.ANNOUNCED,
    )

    @Test
    fun `loads the league and marks the viewer as organizer when ids match`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val viewModel = LeagueDetailViewModel("l1", leagueRepository, StubAuthRepository("organizer-1"))
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Weekend League", state.league?.name)
        assertTrue(state.isOrganizer)
    }

    @Test
    fun `a non-organizer viewer does not get organizer actions`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val viewModel = LeagueDetailViewModel("l1", leagueRepository, StubAuthRepository("someone-else"))
        viewModel.retry()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isOrganizer)
    }

    @Test
    fun `a signed-out viewer -- null user id -- is never treated as organizer`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val viewModel = LeagueDetailViewModel("l1", leagueRepository, StubAuthRepository(null))
        viewModel.retry()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isOrganizer)
    }

    @Test
    fun `a load failure surfaces an error message instead of crashing`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = emptyList())
        val viewModel = LeagueDetailViewModel("missing-id", leagueRepository, StubAuthRepository("organizer-1"))
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.errorMessage != null)
    }

    @Test
    fun `mark completed calls the repository and replaces the league with the completed result`() = viewModelTest {
        val league = sampleLeague()
        val completed = league.copy(status = LeagueStatus.COMPLETED)
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(league)).apply {
            nextCompleted = completed
        }
        val viewModel = LeagueDetailViewModel("l1", leagueRepository, StubAuthRepository("organizer-1"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.markCompleted()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isCompleting)
        assertEquals(LeagueStatus.COMPLETED, state.league?.status)
    }

    @Test
    fun `mark completed is a no-op for a non-organizer viewer`() = viewModelTest {
        val league = sampleLeague()
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(league)).apply {
            nextCompleted = league.copy(status = LeagueStatus.COMPLETED)
        }
        val viewModel = LeagueDetailViewModel("l1", leagueRepository, StubAuthRepository("someone-else"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.markCompleted()
        advanceUntilIdle()

        assertEquals(LeagueStatus.ANNOUNCED, viewModel.state.value.league?.status)
    }
}

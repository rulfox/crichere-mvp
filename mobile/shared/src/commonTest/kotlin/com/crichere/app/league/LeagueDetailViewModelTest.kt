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

    private class StubAuthRepository(var currentUserId: String?) : AuthRepository {
        override suspend fun sendOtp(phoneNumber: String, resendToken: Any?) = error("not used in this test")
        override suspend fun verifyOtp(verificationId: String, code: String) = error("not used in this test")
        override suspend fun exchangeSession(idToken: String) = error("not used in this test")
        override suspend fun refresh(): AuthResult? = error("not used in this test")
        override suspend fun logout() = error("not used in this test")
        override suspend fun getCurrentUserId(): String? = currentUserId
    }

    private fun sampleLeague(
        id: String = "l1",
        organizerUserId: String = "organizer-1",
        coOrganizers: List<LeagueRoleDto> = emptyList(),
    ) = LeagueDto(
        id = id,
        organizerUserId = organizerUserId,
        name = "Weekend League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = "2026-10-12",
        status = LeagueStatus.ANNOUNCED,
        coOrganizers = coOrganizers,
    )

    private fun viewModel(
        leagueRepository: FakeLeagueRepository,
        currentUserId: String? = "organizer-1",
        playerRepository: FakePlayerRepository = FakePlayerRepository(),
        franchiseRepository: FakeFranchiseRepository = FakeFranchiseRepository(),
    ) = LeagueDetailViewModel("l1", leagueRepository, StubAuthRepository(currentUserId), playerRepository, franchiseRepository)

    @Test
    fun `loads the league and marks the viewer as organizer when ids match`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val viewModel = viewModel(leagueRepository, currentUserId = "organizer-1")
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Weekend League", state.league?.name)
        assertTrue(state.isOrganizer)
        assertEquals("organizer-1", state.currentUserId)
    }

    @Test
    fun `a co-organizer, not just the plain organizer, also gets isOrganizer`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(
            leaguesByArea = listOf(sampleLeague(coOrganizers = listOf(LeagueRoleDto(id = "r1", userId = "delegate-1", grantedAt = "2026-09-13T00:00:00Z")))),
        )
        val viewModel = viewModel(leagueRepository, currentUserId = "delegate-1")
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isOrganizer)
    }

    @Test
    fun `a non-organizer viewer does not get organizer actions`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val viewModel = viewModel(leagueRepository, currentUserId = "someone-else")
        viewModel.retry()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isOrganizer)
    }

    @Test
    fun `a signed-out viewer -- null user id -- is never treated as organizer`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val viewModel = viewModel(leagueRepository, currentUserId = null)
        viewModel.retry()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isOrganizer)
    }

    @Test
    fun `a stale in-flight retry from an earlier viewer is cancelled, not left free to overwrite a newer one's state`() = viewModelTest {
        // Reproduces a real on-device bug: this ViewModel instance outlives any single visit
        // (Koin's koinViewModel(key = "league-detail:$leagueId") returns the same instance every
        // time), so logging out and back in as a different user and revisiting this screen can
        // leave an earlier retry() still in flight when a newer one starts. Without cancelling the
        // older one, its slower response could land after the newer one's and silently overwrite
        // the correct isOrganizer with stale data.
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val authRepository = StubAuthRepository(currentUserId = "someone-else")
        val viewModel = LeagueDetailViewModel("l1", leagueRepository, authRepository, FakePlayerRepository(), FakeFranchiseRepository())

        leagueRepository.getLeagueDelayMillis = 1000
        viewModel.retry() // stale visit, as a non-organizer -- still in flight, never advanced

        authRepository.currentUserId = "organizer-1"
        leagueRepository.getLeagueDelayMillis = 0
        viewModel.retry() // fresh visit, as the organizer -- must win

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isOrganizer)
        assertEquals("organizer-1", state.currentUserId)
    }

    @Test
    fun `a load failure surfaces an error message instead of crashing`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = emptyList())
        val viewModel = LeagueDetailViewModel("missing-id", leagueRepository, StubAuthRepository("organizer-1"), FakePlayerRepository(), FakeFranchiseRepository())
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
        val viewModel = viewModel(leagueRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.markCompleted()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isCompleting)
        assertEquals(LeagueStatus.COMPLETED, state.league?.status)
        assertEquals(CompletionNotice.COMPLETED, state.completionNotice)

        viewModel.clearCompletionNotice()
        assertEquals(null, viewModel.state.value.completionNotice)
    }

    @Test
    fun `a network failure on mark completed leaves the league as it was and reports FAILED for the snackbar`() = viewModelTest {
        val league = sampleLeague()
        val viewModel = viewModel(FakeLeagueRepository(leaguesByArea = listOf(league))) // nextCompleted unstubbed: the call fails
        viewModel.retry()
        advanceUntilIdle()

        viewModel.markCompleted()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isCompleting)
        assertEquals(LeagueStatus.ANNOUNCED, state.league?.status)
        assertEquals(CompletionNotice.FAILED, state.completionNotice)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `the server's auction-in-progress refusal gets its own notice and reloads the league`() = viewModelTest {
        val league = sampleLeague()
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(league)).apply {
            completeError = LeagueSaveFailedException("409", code = "AUCTION_IN_PROGRESS")
        }
        val viewModel = viewModel(leagueRepository)
        viewModel.retry()
        advanceUntilIdle()

        // The auction started on another phone after this page loaded.
        leagueRepository.leaguesByArea = listOf(league.copy(auctionStatus = AuctionStatus.IN_PROGRESS))
        viewModel.markCompleted()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(CompletionNotice.AUCTION_IN_PROGRESS, state.completionNotice)
        assertTrue(state.isAuctionLive)
    }

    @Test
    fun `any other server refusal is REFUSED, not a connection failure`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())).apply {
            completeError = LeagueSaveFailedException("403", code = "NOT_ORGANIZER")
        }
        val viewModel = viewModel(leagueRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.markCompleted()
        advanceUntilIdle()

        assertEquals(CompletionNotice.REFUSED, viewModel.state.value.completionNotice)
    }

    @Test
    fun `mark completed does nothing while the auction is live`() = viewModelTest {
        val league = sampleLeague().copy(auctionStatus = AuctionStatus.IN_PROGRESS)
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(league)).apply {
            nextCompleted = league.copy(status = LeagueStatus.COMPLETED)
        }
        val viewModel = viewModel(leagueRepository)
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isAuctionLive)
        viewModel.markCompleted()
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.completionNotice)
        assertEquals(LeagueStatus.ANNOUNCED, viewModel.state.value.league?.status)
    }

    @Test
    fun `mark completed is a no-op for a non-organizer viewer`() = viewModelTest {
        val league = sampleLeague()
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(league)).apply {
            nextCompleted = league.copy(status = LeagueStatus.COMPLETED)
        }
        val viewModel = viewModel(leagueRepository, currentUserId = "someone-else")
        viewModel.retry()
        advanceUntilIdle()

        viewModel.markCompleted()
        advanceUntilIdle()

        assertEquals(LeagueStatus.ANNOUNCED, viewModel.state.value.league?.status)
    }

    // ---------------------------------------------------------------- Phase 3: follow / roster management

    @Test
    fun `toggleFollow follows when not currently following and reloads`() = viewModelTest {
        val league = sampleLeague().copy(isFollowing = false)
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(league))
        val viewModel = viewModel(leagueRepository, currentUserId = "someone-else")
        viewModel.retry()
        advanceUntilIdle()

        viewModel.toggleFollow()
        advanceUntilIdle()

        assertEquals(listOf("l1"), leagueRepository.followCalls)
        assertFalse(viewModel.state.value.isTogglingFollow)
    }

    @Test
    fun `toggleFollow unfollows when currently following`() = viewModelTest {
        val league = sampleLeague().copy(isFollowing = true)
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(league))
        val viewModel = viewModel(leagueRepository, currentUserId = "someone-else")
        viewModel.retry()
        advanceUntilIdle()

        viewModel.toggleFollow()
        advanceUntilIdle()

        assertEquals(listOf("l1"), leagueRepository.unfollowCalls)
    }

    @Test
    fun `removePlayer is a no-op for a non-organizer viewer`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val playerRepository = FakePlayerRepository()
        val viewModel = viewModel(leagueRepository, currentUserId = "someone-else", playerRepository = playerRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.removePlayer("p1")
        advanceUntilIdle()

        assertTrue(playerRepository.removeCalls.isEmpty())
    }

    @Test
    fun `removePlayer by the organizer calls the repository and reloads`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val playerRepository = FakePlayerRepository()
        val viewModel = viewModel(leagueRepository, currentUserId = "organizer-1", playerRepository = playerRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.removePlayer("p1")
        advanceUntilIdle()

        assertEquals(listOf("l1" to "p1"), playerRepository.removeCalls)
        assertTrue(viewModel.state.value.removingIds.isEmpty())
    }

    @Test
    fun `approvePlayerLeave by the organizer calls the repository and reloads`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val playerRepository = FakePlayerRepository().apply {
            nextApproved = LeaguePlayerDto(id = "p1", userId = "u2", joinedAt = "2026-09-12T00:00:00Z")
        }
        val viewModel = viewModel(leagueRepository, currentUserId = "organizer-1", playerRepository = playerRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.approvePlayerLeave("p1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.respondingToLeaveRequestIds.isEmpty())
        assertTrue(viewModel.state.value.errorMessage == null)
    }

    @Test
    fun `requestLeaveAsPlayer calls the repository and reloads`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val playerRepository = FakePlayerRepository().apply {
            nextLeaveRequested = LeaguePlayerDto(id = "p1", userId = "u2", joinedAt = "2026-09-12T00:00:00Z")
        }
        val viewModel = viewModel(leagueRepository, currentUserId = "u2", playerRepository = playerRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.requestLeaveAsPlayer("p1")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLeaveRequesting)
    }

    @Test
    fun `a failed action surfaces an error message and clears the in-flight flag`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val playerRepository = FakePlayerRepository().apply { removeError = RuntimeException("boom") }
        val viewModel = viewModel(leagueRepository, currentUserId = "organizer-1", playerRepository = playerRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.removePlayer("p1")
        advanceUntilIdle()

        assertEquals("boom", viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.removingIds.isEmpty())
    }
}

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.league

import com.crichere.app.auth.AuthRepository
import com.crichere.app.auth.AuthResult
import com.crichere.app.auth.viewModelTest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [AuctionViewModel] coverage: loads the league to derive `isOrganizer`/`myFranchiseId`, then the
 * SSE stream (not a manual reload) is the ongoing source of truth for auction state -- see
 * docs/PHASE5.md and this ViewModel's own class doc.
 */
class AuctionViewModelTest {

    private class StubAuthRepository(var currentUserId: String?) : AuthRepository {
        override suspend fun sendOtp(phoneNumber: String, resendToken: Any?) = error("not used in this test")
        override suspend fun verifyOtp(verificationId: String, code: String) = error("not used in this test")
        override suspend fun exchangeSession(idToken: String) = error("not used in this test")
        override suspend fun refresh(): AuthResult? = error("not used in this test")
        override suspend fun logout() = error("not used in this test")
        override suspend fun getCurrentUserId(): String? = currentUserId
    }

    private fun sampleLeague(organizerUserId: String = "organizer-1", franchises: List<LeagueFranchiseDto> = emptyList()) = LeagueDto(
        id = "l1",
        organizerUserId = organizerUserId,
        name = "Weekend League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = "2026-10-12",
        status = LeagueStatus.ANNOUNCED,
        franchises = franchises,
    )

    private fun franchise(id: String = "f1", ownerUserId: String = "owner-1") =
        LeagueFranchiseDto(id = id, ownerUserId = ownerUserId, name = "Chennai Kings", joinedAt = "2026-09-12T00:00:00Z")

    private fun auctionState(status: AuctionStatus = AuctionStatus.NOT_STARTED, currentBidAmount: Double? = null) =
        AuctionStateDto(auctionStatus = status, currentBidAmount = currentBidAmount)

    @Test
    fun `retry loads the league, marks organizer status, and picks up the first stream event`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val auctionRepository = FakeAuctionRepository().apply {
            stream = MutableSharedFlow<AuctionStateDto>(replay = 1).apply { tryEmit(auctionState(AuctionStatus.IN_PROGRESS)) }
        }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("organizer-1"))

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.isOrganizer)
        assertEquals(AuctionStatus.IN_PROGRESS, state.auction?.auctionStatus)
    }

    @Test
    fun `myFranchiseId resolves to the caller's own franchise in this league`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague(franchises = listOf(franchise(id = "f1", ownerUserId = "owner-1")))))
        val auctionRepository = FakeAuctionRepository()
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("owner-1"))

        viewModel.retry()
        advanceUntilIdle()

        assertEquals("f1", viewModel.state.value.myFranchiseId)
        assertFalse(viewModel.state.value.isOrganizer)
    }

    @Test
    fun `a stream error surfaces an error message instead of crashing`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val auctionRepository = FakeAuctionRepository().apply {
            stream = flow { throw RuntimeException("disconnected") }
        }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("organizer-1"))

        viewModel.retry()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("disconnected", viewModel.state.value.errorMessage)
    }

    @Test
    fun `organizer actions call the repository and apply the returned state immediately`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val auctionRepository = FakeAuctionRepository().apply {
            nextState = auctionState(AuctionStatus.IN_PROGRESS)
        }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("organizer-1"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.start()
        advanceUntilIdle()

        assertEquals(listOf("start"), auctionRepository.actionCalls)
        assertEquals(AuctionStatus.IN_PROGRESS, viewModel.state.value.auction?.auctionStatus)
        assertFalse(viewModel.state.value.isActing)
    }

    @Test
    fun `organizer actions are a no-op for a non-organizer`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val auctionRepository = FakeAuctionRepository()
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("someone-else"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.start()
        advanceUntilIdle()

        assertTrue(auctionRepository.actionCalls.isEmpty())
    }

    @Test
    fun `placeBid uses the caller's own franchise id and the entered amount`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague(franchises = listOf(franchise(id = "f1", ownerUserId = "owner-1")))))
        val auctionRepository = FakeAuctionRepository().apply { nextState = auctionState(currentBidAmount = 150.0) }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("owner-1"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onBidAmountChanged("150")
        viewModel.placeBid()
        advanceUntilIdle()

        assertEquals(PlaceBidRequestDto(franchiseId = "f1", amount = 150.0), auctionRepository.placeBidRequests.single())
        assertEquals(150.0, viewModel.state.value.auction?.currentBidAmount)
        assertEquals("", viewModel.state.value.bidAmountInput)
    }

    @Test
    fun `placeBid with no franchise owned by the caller is a no-op`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val auctionRepository = FakeAuctionRepository()
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("stranger"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onBidAmountChanged("100")
        viewModel.placeBid()
        advanceUntilIdle()

        assertTrue(auctionRepository.placeBidRequests.isEmpty())
    }

    @Test
    fun `a bid failure surfaces the server's error message instead of crashing`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague(franchises = listOf(franchise(id = "f1", ownerUserId = "owner-1")))))
        val auctionRepository = FakeAuctionRepository().apply { actionError = RuntimeException("Bid must be at least 150") }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("owner-1"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onBidAmountChanged("100")
        viewModel.placeBid()
        advanceUntilIdle()

        assertEquals("Bid must be at least 150", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isBidding)
    }

    @Test
    fun `results load automatically when the organizer's own action -- not the SSE stream -- completes the auction`() = viewModelTest {
        // Regression test: found on-device (2026-09-13) -- the organizer whose own `sold` call
        // completed the auction saw no results, because only the SSE-observer path checked for
        // the COMPLETED transition. Every other connected client (who only ever sees state via
        // the stream) got it correctly, which is why this only showed up for the actor themselves.
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val results = AuctionResultsDto(auctionStatus = AuctionStatus.COMPLETED)
        val auctionRepository = FakeAuctionRepository().apply {
            nextState = auctionState(AuctionStatus.COMPLETED)
            nextResults = results
        }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("organizer-1"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.sold()
        advanceUntilIdle()

        assertEquals(results, viewModel.state.value.results)
    }

    @Test
    fun `results load automatically once the stream reports the auction completed`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val results = AuctionResultsDto(auctionStatus = AuctionStatus.COMPLETED)
        val auctionRepository = FakeAuctionRepository().apply {
            nextResults = results
            stream = MutableSharedFlow<AuctionStateDto>(replay = 1).apply { tryEmit(auctionState(AuctionStatus.COMPLETED)) }
        }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("organizer-1"))

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(results, viewModel.state.value.results)
    }

    @Test
    fun `a load failure surfaces an error message instead of crashing`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = emptyList())
        val viewModel = AuctionViewModel("missing-id", leagueRepository, FakeAuctionRepository(), StubAuthRepository("organizer-1"))

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.errorMessage != null)
        assertNull(state.auction)
    }
}

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

    private fun sampleLeague(
        organizerUserId: String = "organizer-1",
        franchises: List<LeagueFranchiseDto> = emptyList(),
        coOrganizers: List<LeagueRoleDto> = emptyList(),
    ) = LeagueDto(
        id = "l1",
        organizerUserId = organizerUserId,
        name = "Weekend League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = "2026-10-12",
        status = LeagueStatus.ANNOUNCED,
        franchises = franchises,
        coOrganizers = coOrganizers,
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
    fun `a co-organizer, not just the plain organizer, also gets isOrganizer`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(
            leaguesByArea = listOf(sampleLeague(coOrganizers = listOf(LeagueRoleDto(id = "r1", userId = "delegate-1", grantedAt = "2026-09-13T00:00:00Z")))),
        )
        val viewModel = AuctionViewModel("l1", leagueRepository, FakeAuctionRepository(), StubAuthRepository("delegate-1"))

        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isOrganizer)
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
    fun `a stream error before any state is a load failure, not a crash`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val auctionRepository = FakeAuctionRepository().apply {
            stream = flow { throw RuntimeException("disconnected") }
        }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("organizer-1"))

        viewModel.retry()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.loadFailed)
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
    fun `a bid that fails on the network says so under the field`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague(franchises = listOf(franchise(id = "f1", ownerUserId = "owner-1")))))
        val auctionRepository = FakeAuctionRepository().apply { actionError = RuntimeException("timeout") }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("owner-1"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onBidAmountChanged("100")
        viewModel.placeBid()
        advanceUntilIdle()

        assertEquals("Couldn't place that bid. Check your connection and try again.", viewModel.state.value.bidError)
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
    fun `a load failure is flagged instead of crashing`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = emptyList())
        val viewModel = AuctionViewModel("missing-id", leagueRepository, FakeAuctionRepository(), StubAuthRepository("organizer-1"))

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
        assertNull(state.auction)
    }

    // ---------------------------------------------------------------- board L (2026-10-02)

    private fun liveLeague() = sampleLeague(franchises = listOf(franchise(id = "f1", ownerUserId = "owner-1"))).copy(
        auctionBasePrice = 1000.0,
        auctionBidIncrement = 500.0,
        auctionPurse = 75000.0,
        auctionSquadMax = 12,
    )

    private fun onTheBlock(currentBid: Double? = 15000.0, playersSold: Int = 11) = AuctionStateDto(
        auctionStatus = AuctionStatus.IN_PROGRESS,
        currentPlayerId = "p1",
        currentPlayerName = "Rohan Patil",
        currentBidAmount = currentBid,
        currentLeadingFranchiseId = currentBid?.let { "f2" },
        currentLeadingFranchiseName = currentBid?.let { "Kolhapur Kings" },
        playersTotal = 58,
        playersSold = playersSold,
    )

    private suspend fun kotlinx.coroutines.test.TestScope.ownerOnBlock(
        state: AuctionStateDto = onTheBlock(),
        configure: FakeAuctionRepository.() -> Unit = {},
    ): Pair<AuctionViewModel, FakeAuctionRepository> {
        val auctionRepository = FakeAuctionRepository().apply {
            stream = MutableSharedFlow<AuctionStateDto>(replay = 1).apply { tryEmit(state) }
            configure()
        }
        val viewModel = AuctionViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(liveLeague())), auctionRepository, StubAuthRepository("owner-1"))
        viewModel.retry()
        advanceUntilIdle()
        return viewModel to auctionRepository
    }

    @Test
    fun `the minimum next bid is the current bid plus the increment, and the amount is pre-filled with it`() = viewModelTest {
        val (viewModel, _) = ownerOnBlock()

        assertEquals(15500.0, viewModel.state.value.minimumNextBid)
        assertEquals("15500", viewModel.state.value.bidAmountInput)
    }

    @Test
    fun `before the first bid the minimum is the base price`() = viewModelTest {
        val (viewModel, _) = ownerOnBlock(onTheBlock(currentBid = null))

        assertEquals(1000.0, viewModel.state.value.minimumNextBid)
        assertEquals("1000", viewModel.state.value.bidAmountInput)
    }

    @Test
    fun `the step chip adds one bid increment to the typed amount`() = viewModelTest {
        val (viewModel, _) = ownerOnBlock()

        viewModel.onStepBid()
        assertEquals("16000", viewModel.state.value.bidAmountInput)

        viewModel.onBidAmountChanged("")
        viewModel.onStepBid()
        assertEquals("16000", viewModel.state.value.bidAmountInput) // blank -> minimum + increment
    }

    @Test
    fun `a bid below the minimum is caught before it is sent`() = viewModelTest {
        val (viewModel, repository) = ownerOnBlock()

        viewModel.onBidAmountChanged("15200")
        viewModel.placeBid()
        advanceUntilIdle()

        assertTrue(repository.placeBidRequests.isEmpty())
        assertEquals("Bid must be at least ₹15,500.", viewModel.state.value.bidError)
    }

    @Test
    fun `server bid rejections map to plain messages`() = viewModelTest {
        val (viewModel, repository) = ownerOnBlock { actionError = AuctionActionFailedException("409", code = "SQUAD_FULL") }

        viewModel.placeBid()
        advanceUntilIdle()
        assertEquals("Your squad is full (12/12).", viewModel.state.value.bidError)

        repository.actionError = AuctionActionFailedException("409", code = "PURSE_EXCEEDED")
        viewModel.placeBid()
        advanceUntilIdle()
        assertEquals("This bid is more than your remaining purse (₹75,000).", viewModel.state.value.bidError)
    }

    @Test
    fun `bidding context comes from the results -- purse left and players won`() = viewModelTest {
        val (viewModel, _) = ownerOnBlock {
            nextResults = AuctionResultsDto(
                auctionStatus = AuctionStatus.IN_PROGRESS,
                franchises = listOf(
                    FranchiseAuctionResultDto(
                        franchiseId = "f1",
                        franchiseName = "Chennai Kings",
                        playersWon = List(11) { PlayerAuctionResultDto(playerId = "p$it", userId = "u$it", soldPrice = 3000.0) },
                        purseSpent = 33000.0,
                        purseRemaining = 42000.0,
                    ),
                ),
            )
        }

        assertEquals(BiddingContext("f1", "Chennai Kings", purseLeft = 42000.0, playersWon = 11, squadMax = 12), viewModel.state.value.biddingContext)
    }

    @Test
    fun `a typed amount above the new minimum survives someone else's bid`() = viewModelTest {
        val stream = MutableSharedFlow<AuctionStateDto>(replay = 1).apply { tryEmit(onTheBlock()) }
        val (viewModel, _) = ownerOnBlock { this.stream = stream }

        viewModel.onBidAmountChanged("20000")
        stream.emit(onTheBlock(currentBid = 16000.0))
        advanceUntilIdle()
        assertEquals("20000", viewModel.state.value.bidAmountInput)

        stream.emit(onTheBlock(currentBid = 21000.0))
        advanceUntilIdle()
        assertEquals("21500", viewModel.state.value.bidAmountInput)
    }

    @Test
    fun `organizer action errors map to plain messages`() = viewModelTest {
        val auctionRepository = FakeAuctionRepository().apply { actionError = AuctionActionFailedException("409", code = "NO_BIDS_TO_SELL") }
        val viewModel = AuctionViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())), auctionRepository, StubAuthRepository("organizer-1"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.sold()
        advanceUntilIdle()

        assertEquals("No bids yet. Mark the player Unsold instead.", viewModel.state.value.actionError)
    }

    @Test
    fun `rupee text uses Indian grouping`() {
        assertEquals("₹500", rupeeText(500.0))
        assertEquals("₹15,500", rupeeText(15500.0))
        assertEquals("₹1,50,000", rupeeText(150000.0))
        assertEquals("₹12.5", rupeeText(12.5))
    }
}


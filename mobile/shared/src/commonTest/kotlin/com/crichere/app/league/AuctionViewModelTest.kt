@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.league

import com.crichere.app.auth.AuthRepository
import com.crichere.app.auth.AuthResult
import com.crichere.app.auth.viewModelTest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
        district = "Bengaluru Urban", groundId = "g1", groundName = "Test Ground",
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
    fun `a stream that throws after delivering state shows connection lost, reconnects, and recovers`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        var attempts = 0
        val auctionRepository = FakeAuctionRepository().apply {
            stream = flow {
                attempts++
                if (attempts == 1) {
                    emit(auctionState(AuctionStatus.IN_PROGRESS, currentBidAmount = 100.0))
                    throw RuntimeException("socket closed")
                }
                emit(auctionState(AuctionStatus.IN_PROGRESS, currentBidAmount = 250.0))
                awaitCancellation()
            }
        }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("organizer-1"), listOf(2_000L))

        viewModel.retry()
        runCurrent()
        assertTrue(viewModel.state.value.connectionLost)
        assertEquals(100.0, viewModel.state.value.auction?.currentBidAmount)

        advanceTimeBy(2_001)
        runCurrent()
        assertFalse(viewModel.state.value.connectionLost)
        assertEquals(250.0, viewModel.state.value.auction?.currentBidAmount)
        assertEquals(2, attempts)
        advanceUntilIdle() // let the "Back online" timer run out
    }

    @Test
    fun `a stream that goes quiet and times out is a drop, not a cancelled screen -- it reconnects`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        var attempts = 0
        val auctionRepository = FakeAuctionRepository().apply {
            stream = flow {
                attempts++
                emit(auctionState(AuctionStatus.IN_PROGRESS, currentBidAmount = attempts * 100.0))
                // The idle watchdog's failure is a TimeoutCancellationException -- a CancellationException.
                if (attempts == 1) withTimeout(500) { awaitCancellation() } else awaitCancellation()
            }
        }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("organizer-1"), listOf(1_000L))

        viewModel.retry()
        runCurrent()
        advanceTimeBy(501)
        runCurrent()
        assertTrue(viewModel.state.value.connectionLost)

        advanceTimeBy(1_001)
        runCurrent()
        assertFalse(viewModel.state.value.connectionLost)
        assertEquals(200.0, viewModel.state.value.auction?.currentBidAmount)
        advanceUntilIdle() // let the "Back online" timer run out
    }

    @Test
    fun `a stream that ends cleanly is treated as dropped and reconnected`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        var attempts = 0
        val auctionRepository = FakeAuctionRepository().apply {
            stream = flow {
                attempts++
                emit(auctionState(AuctionStatus.IN_PROGRESS, currentBidAmount = attempts * 100.0))
                if (attempts >= 2) awaitCancellation()
            }
        }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("organizer-1"), listOf(1_000L, 5_000L))

        viewModel.retry()
        runCurrent()
        assertTrue(viewModel.state.value.connectionLost)

        advanceTimeBy(1_001)
        runCurrent()
        assertFalse(viewModel.state.value.connectionLost)
        assertEquals(200.0, viewModel.state.value.auction?.currentBidAmount)
    }

    @Test
    fun `without reconnect delays a dropped stream is not retried`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        var attempts = 0
        val auctionRepository = FakeAuctionRepository().apply {
            stream = flow {
                attempts++
                emit(auctionState(AuctionStatus.IN_PROGRESS))
                throw RuntimeException("socket closed")
            }
        }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("organizer-1"))

        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.connectionLost)
        assertEquals(1, attempts)
    }

    @Test
    fun `a franchise owner who already leads is told so without a request, and ALREADY_LEADING from the server reads the same`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague(franchises = listOf(franchise()))))
        val auctionRepository = FakeAuctionRepository().apply {
            stream = MutableSharedFlow<AuctionStateDto>(replay = 1).apply {
                tryEmit(
                    AuctionStateDto(
                        auctionStatus = AuctionStatus.IN_PROGRESS, currentPlayerId = "p1", currentBidAmount = 100.0,
                        currentLeadingFranchiseId = "f1",
                    ),
                )
            }
        }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("owner-1"))

        viewModel.retry()
        advanceUntilIdle()
        viewModel.onBidAmountChanged("150")
        viewModel.placeBid()

        assertEquals("You already have the leading bid.", viewModel.state.value.bidError)
        assertTrue(auctionRepository.placeBidRequests.isEmpty())
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
    fun `end auction asks first, locks while ending, and closes on success with no failure`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val auctionRepository = FakeAuctionRepository().apply { nextState = auctionState(AuctionStatus.COMPLETED) }
        val viewModel = AuctionViewModel("l1", leagueRepository, auctionRepository, StubAuthRepository("organizer-1"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.requestEnd()
        assertTrue(viewModel.state.value.isEndConfirmOpen)
        assertTrue(auctionRepository.actionCalls.isEmpty())

        viewModel.confirmEnd()
        assertTrue(viewModel.state.value.isEnding)
        viewModel.dismissEnd() // L20: the dialog can't be dismissed mid-flight
        assertTrue(viewModel.state.value.isEndConfirmOpen)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("end"), auctionRepository.actionCalls)
        assertFalse(state.isEndConfirmOpen)
        assertFalse(state.isEnding)
        assertEquals(null, state.endFailure)
        assertEquals(AuctionStatus.COMPLETED, state.auction?.auctionStatus)
    }

    @Test
    fun `cancel closes the end dialog without calling the server`() = viewModelTest {
        val auctionRepository = FakeAuctionRepository()
        val viewModel = AuctionViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())), auctionRepository, StubAuthRepository("organizer-1"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.requestEnd()
        viewModel.dismissEnd()

        assertFalse(viewModel.state.value.isEndConfirmOpen)
        assertTrue(auctionRepository.actionCalls.isEmpty())
    }

    @Test
    fun `end auction failures - network, refusal, and already ended by someone else`() = viewModelTest {
        val auctionRepository = FakeAuctionRepository()
        val viewModel = AuctionViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())), auctionRepository, StubAuthRepository("organizer-1"))
        viewModel.retry()
        advanceUntilIdle()

        fun attempt(error: Throwable): EndAuctionFailure? {
            auctionRepository.actionError = error
            viewModel.requestEnd()
            viewModel.confirmEnd()
            advanceUntilIdle()
            assertFalse(viewModel.state.value.isEndConfirmOpen)
            return viewModel.state.value.endFailure
        }

        assertEquals(EndAuctionFailure.NETWORK, attempt(RuntimeException("timeout")))
        assertEquals(EndAuctionFailure.REFUSED, attempt(AuctionActionFailedException("403", "NOT_ORGANIZER")))
        assertEquals(null, attempt(AuctionActionFailedException("409", "AUCTION_NOT_IN_PROGRESS")))

        viewModel.clearEndFailure()
        assertEquals(null, viewModel.state.value.endFailure)
    }

    @Test
    fun `end auction is a no-op for a non-organizer`() = viewModelTest {
        val auctionRepository = FakeAuctionRepository()
        val viewModel = AuctionViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())), auctionRepository, StubAuthRepository("someone-else"))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.requestEnd()
        viewModel.confirmEnd()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isEndConfirmOpen)
        assertTrue(auctionRepository.actionCalls.isEmpty())
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

    // ---------------------------------------------------------------- design update #4 (A2-A4)

    private fun resultsFor(won: Int, left: Double) = AuctionResultsDto(
        auctionStatus = AuctionStatus.IN_PROGRESS,
        franchises = listOf(
            FranchiseAuctionResultDto(
                franchiseId = "f1",
                franchiseName = "Chennai Kings",
                playersWon = List(won) { PlayerAuctionResultDto(playerId = "p$it", userId = "u$it", soldPrice = 100.0) },
                purseSpent = 75000.0 - left,
                purseRemaining = left,
            ),
        ),
    )

    @Test
    fun `dock mode - squad full beats leading beats purse short, otherwise the bid field`() = viewModelTest {
        val leadingState = onTheBlock().copy(currentLeadingFranchiseId = "f1", currentLeadingFranchiseName = "Chennai Kings")

        val (full, _) = ownerOnBlock(leadingState) { nextResults = resultsFor(won = 12, left = 100.0) }
        assertEquals(DockMode.SquadFull(12, 12), full.state.value.dockMode)

        val (leading, _) = ownerOnBlock(leadingState) { nextResults = resultsFor(won = 3, left = 100.0) }
        assertEquals(DockMode.Leading(15000.0), leading.state.value.dockMode)

        val (short, _) = ownerOnBlock { nextResults = resultsFor(won = 3, left = 15499.0) }
        assertEquals(DockMode.PurseShort(15500.0), short.state.value.dockMode)

        val (allowed, _) = ownerOnBlock(onTheBlock().copy(allowExceedPurse = true)) { nextResults = resultsFor(won = 3, left = 3000.0) }
        assertEquals(DockMode.Bid, allowed.state.value.dockMode)

        val (normal, _) = ownerOnBlock { nextResults = resultsFor(won = 3, left = 15500.0) }
        assertEquals(DockMode.Bid, normal.state.value.dockMode)
    }

    @Test
    fun `between players a dead end shows the owner's own reason against the base price`() = viewModelTest {
        val deadEnd = AuctionStateDto(auctionStatus = AuctionStatus.IN_PROGRESS, canAnyoneBid = false, squadsFull = 0, purseBelowBase = 1, franchisesTotal = 1)
        val (viewModel, _) = ownerOnBlock(deadEnd) { nextResults = resultsFor(won = 3, left = 900.0) }

        assertTrue(viewModel.state.value.isDeadEnd)
        assertEquals(DockMode.PurseShort(1000.0), viewModel.state.value.dockMode)
    }

    @Test
    fun `connection pill - a blip shows nothing, then reconnecting after 1s, lost at 30s, back online for 1_5s`() = viewModelTest {
        val live = auctionState(AuctionStatus.IN_PROGRESS, currentBidAmount = 100.0)
        var attempts = 0
        val auctionRepository = FakeAuctionRepository().apply {
            stream = flow {
                attempts++
                when {
                    attempts == 1 -> { emit(live); throw RuntimeException("blip") } // t=0
                    attempts == 2 -> { emit(live); delay(100); throw RuntimeException("down") } // t=500, recovers, drops at 600
                    testScheduler.currentTime < 32_000 -> throw RuntimeException("still down") // every 500 ms
                    else -> { emit(live); awaitCancellation() }
                }
            }
        }
        val viewModel = AuctionViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())), auctionRepository, StubAuthRepository("organizer-1"), listOf(500L))

        viewModel.retry()
        runCurrent()
        assertTrue(viewModel.state.value.connectionLost)
        assertEquals(ConnectionPhase.ONLINE, viewModel.state.value.connectionPhase)

        advanceTimeBy(550) // t=550: the blip recovered at 500 -- nothing was shown
        runCurrent()
        assertFalse(viewModel.state.value.connectionLost)
        assertEquals(ConnectionPhase.ONLINE, viewModel.state.value.connectionPhase)

        advanceTimeBy(1_000) // t=1550: down since 600, still inside the grace second
        runCurrent()
        assertEquals(ConnectionPhase.ONLINE, viewModel.state.value.connectionPhase)

        advanceTimeBy(100) // t=1650
        runCurrent()
        assertEquals(ConnectionPhase.RECONNECTING, viewModel.state.value.connectionPhase)

        advanceTimeBy(28_900) // t=30550
        runCurrent()
        assertEquals(ConnectionPhase.RECONNECTING, viewModel.state.value.connectionPhase)

        advanceTimeBy(100) // t=30650: 30 s after the drop
        runCurrent()
        assertEquals(ConnectionPhase.LOST, viewModel.state.value.connectionPhase)

        advanceTimeBy(1_500) // t=32150: the feed is back
        runCurrent()
        assertEquals(ConnectionPhase.BACK_ONLINE, viewModel.state.value.connectionPhase)

        advanceTimeBy(1_501)
        runCurrent()
        assertEquals(ConnectionPhase.ONLINE, viewModel.state.value.connectionPhase)
        advanceUntilIdle()
    }

    @Test
    fun `retry on the lost pill goes straight to reconnecting`() = viewModelTest {
        val auctionRepository = FakeAuctionRepository().apply {
            stream = flow {
                emit(auctionState(AuctionStatus.IN_PROGRESS, currentBidAmount = 100.0))
                throw RuntimeException("down")
            }
        }
        val viewModel = AuctionViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())), auctionRepository, StubAuthRepository("organizer-1"))

        viewModel.retry()
        advanceTimeBy(31_000)
        runCurrent()
        assertEquals(ConnectionPhase.LOST, viewModel.state.value.connectionPhase)

        viewModel.retry()
        assertEquals(ConnectionPhase.RECONNECTING, viewModel.state.value.connectionPhase)
        advanceUntilIdle()
    }

    @Test
    fun `rupee text uses Indian grouping`() {
        assertEquals("₹500", rupeeText(500.0))
        assertEquals("₹15,500", rupeeText(15500.0))
        assertEquals("₹1,50,000", rupeeText(150000.0))
        assertEquals("₹12.5", rupeeText(12.5))
    }
}


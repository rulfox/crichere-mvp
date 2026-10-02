package com.crichere.backend.auction

import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.franchise.FranchiseEntity
import com.crichere.backend.franchise.FranchiseRepository
import com.crichere.backend.franchise.NotFranchiseOwnerException
import com.crichere.backend.league.AuctionLastActionType
import com.crichere.backend.league.AuctionStatus
import com.crichere.backend.league.LeagueAuthorization
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.league.LeagueRoleRepository
import com.crichere.backend.league.NotOrganizerException
import com.crichere.backend.notification.FcmSender
import com.crichere.backend.player.AuctionOutcome
import com.crichere.backend.player.PlayerEntity
import com.crichere.backend.player.PlayerRepository
import com.crichere.backend.profile.PlayingRole
import com.crichere.backend.profile.ProfileEntity
import com.crichere.backend.profile.ProfileRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit-level coverage of [AuctionService]. End-to-end HTTP behaviour (including the concurrent-bid race) is covered by `AuctionFlowIntegrationTest`. */
class AuctionServiceTest {

    private val leagueRepository = mockk<LeagueRepository>()
    private val playerRepository = mockk<PlayerRepository>().also {
        // toStateResponse() always resolves the current player's/leading franchise's name for
        // display -- default to "not found" so tests that don't care about display names don't
        // each need their own stub; tests that do (the id used is one they control) override it.
        every { it.findById(any()) } returns Optional.empty()
        // notifyAuctionStarted()'s recipient fan-out -- default to "no one else."
        every { it.findByLeagueIdAndRemovedAtIsNull(any()) } returns emptyList()
        // unsold()'s own notification lookup (findPlayerOrThrow) -- default to a synthetic player
        // matching whatever id/league was actually queried, so tests that don't care about player
        // identity (most of them) don't each need their own stub.
        every { it.findByIdAndLeagueId(any(), any()) } answers {
            Optional.of(PlayerEntity(id = firstArg(), leagueId = secondArg(), userId = UUID.randomUUID()))
        }
        // toStateResponse()'s "Player 12 of 58" counts -- default to zero; tests that care stub them.
        every { it.countByLeagueIdAndRemovedAtIsNull(any()) } returns 0L
        every { it.countByLeagueIdAndAuctionOutcome(any(), any()) } returns 0L
    }
    private val franchiseRepository = mockk<FranchiseRepository>().also {
        every { it.findById(any()) } returns Optional.empty()
        // notifyAuctionStarted()'s recipient fan-out -- default to "no one else."
        every { it.findByLeagueIdAndRemovedAtIsNull(any()) } returns emptyList()
    }
    private val auctionBidRepository = mockk<AuctionBidRepository>().also {
        // toStateResponse() always looks up the current player's bid ticker -- default to empty
        // so tests that don't care about it don't each need their own stub.
        every { it.findTop8ByLeagueIdAndPlayerIdAndReversedFalseOrderByPlacedAtDesc(any(), any()) } returns emptyList()
    }
    private val profileRepository = mockk<ProfileRepository>().also {
        every { it.findById(any()) } returns Optional.empty()
    }
    private val contentRateLimiter = mockk<ContentRateLimiter>().also {
        every { it.tryConsumeForBid(any()) } returns null
    }
    private val broadcastService = mockk<AuctionBroadcastService>().also {
        every { it.broadcast(any(), any()) } just runs
    }
    // Real LeagueAuthorization backed by a mock repository with no active grants -- preserves the
    // exact prior organizer-column-only behavior these tests already assert on (see docs/PHASE7.md).
    private val leagueAuthorization = LeagueAuthorization(
        mockk<LeagueRoleRepository>().also {
            every { it.existsByLeagueIdAndUserIdAndRevokedAtIsNull(any(), any()) } returns false
        },
    )
    private val fcmSender = mockk<FcmSender>(relaxed = true)
    private val service = AuctionService(
        leagueRepository, playerRepository, franchiseRepository, auctionBidRepository,
        profileRepository, contentRateLimiter, broadcastService, leagueAuthorization, fcmSender,
    )

    private val organizerId: UUID = UUID.randomUUID()
    private val franchiseOwnerId: UUID = UUID.randomUUID()
    private val leagueId: UUID = UUID.randomUUID()
    private val franchiseId: UUID = UUID.randomUUID()
    private val playerId: UUID = UUID.randomUUID()

    private fun league(
        status: AuctionStatus = AuctionStatus.IN_PROGRESS,
        currentPlayerId: UUID? = null,
        currentBidAmount: BigDecimal? = null,
        currentLeadingFranchiseId: UUID? = null,
        allowExceedPurse: Boolean = false,
        lastActionType: AuctionLastActionType? = null,
        lastActionBidId: UUID? = null,
        lastActionPlayerId: UUID? = null,
        basePrice: BigDecimal? = BigDecimal("100"),
        purse: BigDecimal? = BigDecimal("1000"),
        squadMin: Int? = 1,
        squadMax: Int? = 5,
        bidIncrement: BigDecimal? = BigDecimal("50"),
    ) = LeagueEntity(
        id = leagueId,
        organizerUserId = organizerId,
        name = "Test League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = LocalDate.of(2026, 10, 12),
        auctionBasePrice = basePrice,
        auctionPurse = purse,
        auctionSquadMin = squadMin,
        auctionSquadMax = squadMax,
        auctionBidIncrement = bidIncrement,
        auctionStatus = status,
        auctionCurrentPlayerId = currentPlayerId,
        auctionCurrentBidAmount = currentBidAmount,
        auctionCurrentLeadingFranchiseId = currentLeadingFranchiseId,
        auctionAllowExceedPurse = allowExceedPurse,
        auctionLastActionType = lastActionType,
        auctionLastActionBidId = lastActionBidId,
        auctionLastActionPlayerId = lastActionPlayerId,
    )

    private fun franchise() = FranchiseEntity(id = franchiseId, leagueId = leagueId, ownerUserId = franchiseOwnerId, name = "Chennai Kings")

    private fun player(outcome: AuctionOutcome = AuctionOutcome.PENDING) =
        PlayerEntity(id = playerId, leagueId = leagueId, userId = UUID.randomUUID(), auctionOutcome = outcome)

    private fun mockSaves() {
        every { leagueRepository.save(any()) } answers { firstArg() }
        every { playerRepository.save(any()) } answers { firstArg() }
        every { auctionBidRepository.save(any()) } answers { firstArg<AuctionBidEntity>().apply { if (id == null) id = UUID.randomUUID() } }
    }

    // ---------------------------------------------------------------- start

    @Test
    fun `start by someone other than the organizer is rejected`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(status = AuctionStatus.NOT_STARTED))

        assertFailsWith<NotOrganizerException> { service.start(leagueId, UUID.randomUUID()) }
    }

    @Test
    fun `start is rejected once the auction has already left NOT_STARTED`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(status = AuctionStatus.IN_PROGRESS))

        assertFailsWith<AuctionAlreadyStartedException> { service.start(leagueId, organizerId) }
    }

    @Test
    fun `start is rejected when settings are not fully configured`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(status = AuctionStatus.NOT_STARTED, basePrice = null))

        assertFailsWith<AuctionNotReadyException> { service.start(leagueId, organizerId) }
    }

    @Test
    fun `start is rejected when the pool is empty`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(status = AuctionStatus.NOT_STARTED))
        every { playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 0L

        assertFailsWith<AuctionNotReadyException> { service.start(leagueId, organizerId) }
    }

    @Test
    fun `start is rejected when squad max would require more players than are in the pool`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(status = AuctionStatus.NOT_STARTED, squadMax = 5))
        every { playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 3L
        every { franchiseRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 1L

        assertFailsWith<AuctionNotReadyException> { service.start(leagueId, organizerId) }
    }

    @Test
    fun `start succeeds when the readiness check passes`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(status = AuctionStatus.NOT_STARTED, squadMax = 2))
        every { playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 3L
        every { franchiseRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 1L
        mockSaves()

        val result = service.start(leagueId, organizerId)

        assertEquals(AuctionStatus.IN_PROGRESS, result.auctionStatus)
    }

    @Test
    fun `start notifies every active player and franchise owner in the league`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(status = AuctionStatus.NOT_STARTED, squadMax = 1))
        every { playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 1L
        every { franchiseRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 1L
        val playerUserId = UUID.randomUUID()
        val franchiseOwnerId = UUID.randomUUID()
        every { playerRepository.findByLeagueIdAndRemovedAtIsNull(leagueId) } returns
            listOf(PlayerEntity(id = playerId, leagueId = leagueId, userId = playerUserId))
        every { franchiseRepository.findByLeagueIdAndRemovedAtIsNull(leagueId) } returns
            listOf(FranchiseEntity(id = franchiseId, leagueId = leagueId, ownerUserId = franchiseOwnerId, name = "Chennai Kings"))
        mockSaves()

        service.start(leagueId, organizerId)

        verify { fcmSender.sendToUser(playerUserId, "Test League", any(), any()) }
        verify { fcmSender.sendToUser(franchiseOwnerId, "Test League", any(), any()) }
    }

    // ---------------------------------------------------------------- nextPlayer

    @Test
    fun `nextPlayer is rejected while a player is already open`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(currentPlayerId = playerId))

        assertFailsWith<AuctionPlayerAlreadyOpenException> { service.nextPlayer(leagueId, organizerId) }
    }

    @Test
    fun `nextPlayer auto-completes the auction once the pool is empty`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { playerRepository.findByLeagueIdAndAuctionOutcome(leagueId, AuctionOutcome.PENDING) } returns emptyList()
        mockSaves()

        val result = service.nextPlayer(leagueId, organizerId)

        assertEquals(AuctionStatus.COMPLETED, result.auctionStatus)
        assertNull(result.currentPlayerId)
    }

    @Test
    fun `nextPlayer opens a random pending player`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { playerRepository.findByLeagueIdAndAuctionOutcome(leagueId, AuctionOutcome.PENDING) } returns listOf(player())
        mockSaves()

        val result = service.nextPlayer(leagueId, organizerId)

        assertEquals(playerId, result.currentPlayerId)
        assertNull(result.currentBidAmount)
    }

    // ---------------------------------------------------------------- placeBid

    @Test
    fun `placeBid by someone who does not own the franchise is rejected`() {
        every { leagueRepository.findByIdForUpdate(leagueId) } returns league(currentPlayerId = playerId)
        every { franchiseRepository.findById(franchiseId) } returns Optional.of(franchise())

        assertFailsWith<NotFranchiseOwnerException> {
            service.placeBid(leagueId, franchiseId, UUID.randomUUID(), BigDecimal("100"))
        }
    }

    @Test
    fun `placeBid with no player open is rejected`() {
        every { leagueRepository.findByIdForUpdate(leagueId) } returns league(currentPlayerId = null)
        every { franchiseRepository.findById(franchiseId) } returns Optional.of(franchise())

        assertFailsWith<AuctionNoPlayerOpenException> {
            service.placeBid(leagueId, franchiseId, franchiseOwnerId, BigDecimal("100"))
        }
    }

    @Test
    fun `placeBid below the current bid plus increment is rejected`() {
        every { leagueRepository.findByIdForUpdate(leagueId) } returns
            league(currentPlayerId = playerId, currentBidAmount = BigDecimal("100"), currentLeadingFranchiseId = UUID.randomUUID())
        every { franchiseRepository.findById(franchiseId) } returns Optional.of(franchise())

        assertFailsWith<BidTooLowException> {
            service.placeBid(leagueId, franchiseId, franchiseOwnerId, BigDecimal("120"))
        }
    }

    @Test
    fun `placeBid exceeding squad max is rejected`() {
        every { leagueRepository.findByIdForUpdate(leagueId) } returns league(currentPlayerId = playerId, squadMax = 1)
        every { franchiseRepository.findById(franchiseId) } returns Optional.of(franchise())
        every { playerRepository.findBySoldToFranchiseId(franchiseId) } returns listOf(player(AuctionOutcome.SOLD))

        assertFailsWith<SquadFullException> {
            service.placeBid(leagueId, franchiseId, franchiseOwnerId, BigDecimal("100"))
        }
    }

    @Test
    fun `placeBid exceeding purse is rejected unless allowExceedPurse is on`() {
        every { leagueRepository.findByIdForUpdate(leagueId) } returns
            league(currentPlayerId = playerId, purse = BigDecimal("100"), allowExceedPurse = false)
        every { franchiseRepository.findById(franchiseId) } returns Optional.of(franchise())
        every { playerRepository.findBySoldToFranchiseId(franchiseId) } returns emptyList()

        assertFailsWith<PurseExceededException> {
            service.placeBid(leagueId, franchiseId, franchiseOwnerId, BigDecimal("150"))
        }
    }

    @Test
    fun `placeBid exceeding purse succeeds when allowExceedPurse is on -- no cap on how far over`() {
        every { leagueRepository.findByIdForUpdate(leagueId) } returns
            league(currentPlayerId = playerId, purse = BigDecimal("100"), allowExceedPurse = true)
        every { franchiseRepository.findById(franchiseId) } returns Optional.of(franchise())
        every { playerRepository.findBySoldToFranchiseId(franchiseId) } returns emptyList()
        mockSaves()

        val result = service.placeBid(leagueId, franchiseId, franchiseOwnerId, BigDecimal("10000"))

        assertEquals(BigDecimal("10000"), result.currentBidAmount)
    }

    @Test
    fun `a valid bid updates the current bid and leading franchise`() {
        every { leagueRepository.findByIdForUpdate(leagueId) } returns league(currentPlayerId = playerId)
        every { franchiseRepository.findById(franchiseId) } returns Optional.of(franchise())
        every { playerRepository.findBySoldToFranchiseId(franchiseId) } returns emptyList()
        mockSaves()

        val result = service.placeBid(leagueId, franchiseId, franchiseOwnerId, BigDecimal("100"))

        assertEquals(BigDecimal("100"), result.currentBidAmount)
        assertEquals(franchiseId, result.currentLeadingFranchiseId)
    }

    // ---------------------------------------------------------------- sold / unsold

    @Test
    fun `sold with no leading bid is rejected`() {
        every { leagueRepository.findById(leagueId) } returns league(currentPlayerId = playerId).let { Optional.of(it) }

        assertFailsWith<NoBidsToSellException> { service.sold(leagueId, organizerId) }
    }

    @Test
    fun `sold assigns the player and auto-completes when the pool is exhausted`() {
        every { leagueRepository.findById(leagueId) } returns
            Optional.of(league(currentPlayerId = playerId, currentBidAmount = BigDecimal("100"), currentLeadingFranchiseId = franchiseId))
        every { playerRepository.findByIdAndLeagueId(playerId, leagueId) } returns Optional.of(player())
        every { playerRepository.countByLeagueIdAndAuctionOutcome(leagueId, AuctionOutcome.PENDING) } returns 0L
        mockSaves()

        val result = service.sold(leagueId, organizerId)

        assertEquals(AuctionStatus.COMPLETED, result.auctionStatus)
        assertNull(result.currentPlayerId)
    }

    @Test
    fun `sold notifies the winning player`() {
        val soldPlayerUserId = UUID.randomUUID()
        every { leagueRepository.findById(leagueId) } returns
            Optional.of(league(currentPlayerId = playerId, currentBidAmount = BigDecimal("100"), currentLeadingFranchiseId = franchiseId))
        every { playerRepository.findByIdAndLeagueId(playerId, leagueId) } returns
            Optional.of(PlayerEntity(id = playerId, leagueId = leagueId, userId = soldPlayerUserId))
        every { playerRepository.countByLeagueIdAndAuctionOutcome(leagueId, AuctionOutcome.PENDING) } returns 1L
        every { franchiseRepository.findById(franchiseId) } returns Optional.of(FranchiseEntity(id = franchiseId, leagueId = leagueId, ownerUserId = UUID.randomUUID(), name = "Chennai Kings"))
        mockSaves()

        service.sold(leagueId, organizerId)

        verify { fcmSender.sendToUser(soldPlayerUserId, "Test League", match { it.contains("Chennai Kings") }, any()) }
    }

    @Test
    fun `unsold requeues the player by clearing the current-player slot`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(currentPlayerId = playerId))
        mockSaves()

        val result = service.unsold(leagueId, organizerId)

        assertNull(result.currentPlayerId)
        assertEquals(AuctionStatus.IN_PROGRESS, result.auctionStatus)
    }

    // ---------------------------------------------------------------- undo

    @Test
    fun `undo with nothing pending throws`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(lastActionType = null))

        assertFailsWith<NothingToUndoException> { service.undo(leagueId, organizerId) }
    }

    @Test
    fun `undo after a bid restores the prior leading bid`() {
        val bidId = UUID.randomUUID()
        val bid = AuctionBidEntity(id = bidId, leagueId = leagueId, playerId = playerId, franchiseId = franchiseId, amount = BigDecimal("150"))
        val priorBid = AuctionBidEntity(id = UUID.randomUUID(), leagueId = leagueId, playerId = playerId, franchiseId = franchiseId, amount = BigDecimal("100"))
        every { leagueRepository.findById(leagueId) } returns Optional.of(
            league(currentPlayerId = playerId, currentBidAmount = BigDecimal("150"), currentLeadingFranchiseId = franchiseId, lastActionType = AuctionLastActionType.BID, lastActionBidId = bidId),
        )
        every { auctionBidRepository.findByIdAndLeagueId(bidId, leagueId) } returns Optional.of(bid)
        every { auctionBidRepository.findTopByLeagueIdAndPlayerIdAndReversedFalseOrderByAmountDesc(leagueId, playerId) } returns priorBid
        mockSaves()

        val result = service.undo(leagueId, organizerId)

        assertEquals(BigDecimal("100"), result.currentBidAmount)
        assertTrue(bid.reversed)
    }

    @Test
    fun `undo after sold restores the player to pending and reopens it`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(
            league(status = AuctionStatus.COMPLETED, currentPlayerId = null, lastActionType = AuctionLastActionType.SOLD, lastActionPlayerId = playerId),
        )
        every { playerRepository.findByIdAndLeagueId(playerId, leagueId) } returns Optional.of(player(AuctionOutcome.SOLD))
        every { auctionBidRepository.findTopByLeagueIdAndPlayerIdAndReversedFalseOrderByAmountDesc(leagueId, playerId) } returns null
        mockSaves()

        val result = service.undo(leagueId, organizerId)

        assertEquals(playerId, result.currentPlayerId)
        assertEquals(AuctionStatus.IN_PROGRESS, result.auctionStatus)
    }

    // ---------------------------------------------------------------- end

    @Test
    fun `end marks every remaining pending player UNSOLD and completes the auction`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(currentPlayerId = playerId))
        val playerSlot = slot<PlayerEntity>()
        every { playerRepository.findByLeagueIdAndAuctionOutcome(leagueId, AuctionOutcome.PENDING) } returns listOf(player())
        every { playerRepository.save(capture(playerSlot)) } answers { firstArg() }
        every { leagueRepository.save(any()) } answers { firstArg() }

        val result = service.end(leagueId, organizerId)

        assertEquals(AuctionStatus.COMPLETED, result.auctionStatus)
        assertEquals(AuctionOutcome.UNSOLD, playerSlot.captured.auctionOutcome)
    }

    // ---------------------------------------------------------------- state display fields (screen L)

    @Test
    fun `state carries the current player's photo, role and the sold count`() {
        val userId = UUID.randomUUID()
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(currentPlayerId = playerId))
        every { playerRepository.findById(playerId) } returns Optional.of(PlayerEntity(id = playerId, leagueId = leagueId, userId = userId))
        every { profileRepository.findById(userId) } returns
            Optional.of(ProfileEntity(userId = userId, name = "Rohan Patil", photoUrl = "https://cdn/p.jpg", playingRole = PlayingRole.ALL_ROUNDER))
        every { playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 58L
        every { playerRepository.countByLeagueIdAndAuctionOutcome(leagueId, AuctionOutcome.SOLD) } returns 11L

        val state = service.currentState(leagueId)

        assertEquals("Rohan Patil", state.currentPlayerName)
        assertEquals("https://cdn/p.jpg", state.currentPlayerPhotoUrl)
        assertEquals(PlayingRole.ALL_ROUNDER, state.currentPlayerRole)
        assertEquals(58, state.playersTotal)
        assertEquals(11, state.playersSold)
        assertNull(state.lastResult)
    }

    @Test
    fun `between players, state reports the player just sold, to whom and for how much`() {
        val userId = UUID.randomUUID()
        every { leagueRepository.findById(leagueId) } returns
            Optional.of(league(lastActionType = AuctionLastActionType.SOLD, lastActionPlayerId = playerId))
        every { playerRepository.findById(playerId) } returns Optional.of(
            PlayerEntity(id = playerId, leagueId = leagueId, userId = userId, auctionOutcome = AuctionOutcome.SOLD).apply {
                soldToFranchiseId = franchiseId
                soldPrice = BigDecimal("15000")
            },
        )
        every { profileRepository.findById(userId) } returns Optional.of(ProfileEntity(userId = userId, name = "Rohan Patil"))
        every { franchiseRepository.findById(franchiseId) } returns Optional.of(franchise())

        val last = service.currentState(leagueId).lastResult

        assertEquals("Rohan Patil", last?.playerName)
        assertTrue(last!!.sold)
        assertEquals("Chennai Kings", last.franchiseName)
        assertEquals(BigDecimal("15000"), last.amount)
    }

    @Test
    fun `between players after unsold, state reports the player went unsold`() {
        val userId = UUID.randomUUID()
        every { leagueRepository.findById(leagueId) } returns
            Optional.of(league(lastActionType = AuctionLastActionType.UNSOLD, lastActionPlayerId = playerId))
        every { playerRepository.findById(playerId) } returns Optional.of(PlayerEntity(id = playerId, leagueId = leagueId, userId = userId))
        every { profileRepository.findById(userId) } returns Optional.of(ProfileEntity(userId = userId, name = "Rohan Patil"))

        val last = service.currentState(leagueId).lastResult

        assertEquals("Rohan Patil", last?.playerName)
        assertEquals(false, last?.sold)
        assertNull(last?.franchiseName)
        assertNull(last?.amount)
    }

    @Test
    fun `no last result once the organizer has moved on (last action is a bid)`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(lastActionType = AuctionLastActionType.BID, lastActionPlayerId = playerId))

        assertNull(service.currentState(leagueId).lastResult)
    }

    @Test
    fun `results include each won player's photo`() {
        val userId = UUID.randomUUID()
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(status = AuctionStatus.COMPLETED))
        every { franchiseRepository.findByLeagueIdAndRemovedAtIsNull(leagueId) } returns listOf(franchise())
        every { playerRepository.findBySoldToFranchiseId(franchiseId) } returns listOf(
            PlayerEntity(id = playerId, leagueId = leagueId, userId = userId, auctionOutcome = AuctionOutcome.SOLD).apply {
                soldToFranchiseId = franchiseId
                soldPrice = BigDecimal("900")
            },
        )
        every { profileRepository.findById(userId) } returns Optional.of(ProfileEntity(userId = userId, name = "Rohan Patil", photoUrl = "https://cdn/p.jpg"))

        val won = service.results(leagueId).franchises.single().playersWon.single()

        assertEquals("Rohan Patil", won.playerName)
        assertEquals("https://cdn/p.jpg", won.photoUrl)
    }
}

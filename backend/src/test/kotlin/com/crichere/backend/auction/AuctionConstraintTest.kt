package com.crichere.backend.auction

import com.crichere.backend.auth.UserEntity
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.common.AbstractIntegrationTest
import com.crichere.backend.franchise.FranchiseEntity
import com.crichere.backend.franchise.FranchiseRepository
import com.crichere.backend.league.AuctionLastActionType
import com.crichere.backend.league.AuctionStatus
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.player.AuctionOutcome
import com.crichere.backend.player.PlayerEntity
import com.crichere.backend.player.PlayerRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Constraint tests for V15__add_auction_engine.sql's new columns/table. Each test runs in its
 * own rolled-back transaction, same posture as `LeaguesConstraintTest`.
 */
@Transactional
class AuctionConstraintTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var leagueRepository: LeagueRepository

    @Autowired
    private lateinit var playerRepository: PlayerRepository

    @Autowired
    private lateinit var franchiseRepository: FranchiseRepository

    @Autowired
    private lateinit var auctionBidRepository: AuctionBidRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun persistedUser(hashSuffix: String): UserEntity =
        userRepository.saveAndFlush(UserEntity(phoneLookupHash = "hash-auction-$hashSuffix", phoneEncrypted = "enc"))

    private fun persistedLeague(organizer: UUID) = leagueRepository.saveAndFlush(
        LeagueEntity(
            organizerUserId = organizer,
            name = "Test League",
            state = "Karnataka",
            district = "Bengaluru Urban",
            city = "Bengaluru",
            startsOn = LocalDate.of(2026, 10, 12),
        ),
    )

    private fun persistedPlayer(leagueId: UUID, userId: UUID) =
        playerRepository.saveAndFlush(PlayerEntity(leagueId = leagueId, userId = userId))

    private fun persistedFranchise(leagueId: UUID, ownerId: UUID) =
        franchiseRepository.saveAndFlush(FranchiseEntity(leagueId = leagueId, ownerUserId = ownerId, name = "Chennai Kings"))

    @Test
    fun `new leagues columns default to NOT_STARTED, false, and null`() {
        val organizer = persistedUser("defaults")
        val league = persistedLeague(organizer.id!!)

        val reloaded = leagueRepository.findById(league.id!!).orElseThrow()
        assertEquals(AuctionStatus.NOT_STARTED, reloaded.auctionStatus)
        assertEquals(false, reloaded.auctionAllowExceedPurse)
        assertNull(reloaded.auctionCurrentPlayerId)
        assertNull(reloaded.auctionCurrentBidAmount)
        assertNull(reloaded.auctionCurrentLeadingFranchiseId)
        assertNull(reloaded.auctionLastActionType)
        assertNull(reloaded.auctionLastActionBidId)
        assertNull(reloaded.auctionLastActionPlayerId)
    }

    @Test
    fun `new league_players columns default to PENDING and null`() {
        val organizer = persistedUser("player-defaults")
        val league = persistedLeague(organizer.id!!)
        val player = persistedPlayer(league.id!!, organizer.id!!)

        val reloaded = playerRepository.findById(player.id!!).orElseThrow()
        assertEquals(AuctionOutcome.PENDING, reloaded.auctionOutcome)
        assertNull(reloaded.soldToFranchiseId)
        assertNull(reloaded.soldPrice)
    }

    @Test
    fun `auction_bids rows round-trip and default reversed to false`() {
        val organizer = persistedUser("bid-roundtrip")
        val league = persistedLeague(organizer.id!!)
        val player = persistedPlayer(league.id!!, organizer.id!!)
        val franchise = persistedFranchise(league.id!!, organizer.id!!)

        val bid = auctionBidRepository.saveAndFlush(
            AuctionBidEntity(leagueId = league.id!!, playerId = player.id!!, franchiseId = franchise.id!!, amount = BigDecimal("100")),
        )

        val reloaded = auctionBidRepository.findById(bid.id!!).orElseThrow()
        assertEquals(false, reloaded.reversed)
        assertEquals(BigDecimal("100"), reloaded.amount)
    }

    @Test
    fun `deleting a league cascades to its auction_bids`() {
        val organizer = persistedUser("bid-cascade")
        val league = persistedLeague(organizer.id!!)
        val player = persistedPlayer(league.id!!, organizer.id!!)
        val franchise = persistedFranchise(league.id!!, organizer.id!!)
        val bid = auctionBidRepository.saveAndFlush(
            AuctionBidEntity(leagueId = league.id!!, playerId = player.id!!, franchiseId = franchise.id!!, amount = BigDecimal("100")),
        )

        leagueRepository.delete(league)
        leagueRepository.flush()
        entityManager.clear()

        assertTrue(auctionBidRepository.findById(bid.id!!).isEmpty)
    }

    @Test
    fun `deleting the current player sets auction_current_player_id to null rather than blocking the delete`() {
        val organizer = persistedUser("current-player-set-null")
        val league = persistedLeague(organizer.id!!)
        val player = persistedPlayer(league.id!!, organizer.id!!)
        league.auctionCurrentPlayerId = player.id
        leagueRepository.saveAndFlush(league)

        playerRepository.delete(player)
        playerRepository.flush()
        entityManager.clear()

        val reloaded = leagueRepository.findById(league.id!!).orElseThrow()
        assertNull(reloaded.auctionCurrentPlayerId)
    }

    @Test
    fun `deleting a franchise sets sold_to_franchise_id to null on any player it won`() {
        val organizer = persistedUser("sold-franchise-set-null")
        val league = persistedLeague(organizer.id!!)
        val player = persistedPlayer(league.id!!, organizer.id!!)
        val franchise = persistedFranchise(league.id!!, organizer.id!!)
        player.auctionOutcome = AuctionOutcome.SOLD
        player.soldToFranchiseId = franchise.id
        player.soldPrice = BigDecimal("100")
        playerRepository.saveAndFlush(player)

        franchiseRepository.delete(franchise)
        franchiseRepository.flush()
        entityManager.clear()

        val reloaded = playerRepository.findById(player.id!!).orElseThrow()
        assertNull(reloaded.soldToFranchiseId)
    }
}

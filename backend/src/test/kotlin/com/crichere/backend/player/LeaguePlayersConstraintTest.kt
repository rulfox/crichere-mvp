package com.crichere.backend.player

import com.crichere.backend.auth.UserEntity
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.common.AbstractIntegrationTest
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Constraint tests for the `league_players` table (V11__create_league_players_table.sql) via
 * [PlayerEntity]/[PlayerRepository] -- in particular the `league_players_active_unique` partial
 * index (at most one active row per (league, user), see docs/PHASE3.md's Decisions Made).
 */
@Transactional
class LeaguePlayersConstraintTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var leagueRepository: LeagueRepository

    @Autowired
    private lateinit var playerRepository: PlayerRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun persistedUser(hashSuffix: String): UserEntity =
        userRepository.saveAndFlush(UserEntity(phoneLookupHash = "hash-player-$hashSuffix", phoneEncrypted = "enc"))

    private fun persistedLeague(organizer: UUID): LeagueEntity =
        leagueRepository.saveAndFlush(
            LeagueEntity(
                organizerUserId = organizer,
                name = "Test League",
                state = "Karnataka",
                district = "Bengaluru Urban",
                city = "Bengaluru",
                startsOn = LocalDate.of(2026, 10, 12),
            ),
        )

    @Test
    fun `a fully valid join round-trips`() {
        val organizer = persistedUser("organizer")
        val league = persistedLeague(organizer.id!!)
        val player = persistedUser("player")

        val saved = playerRepository.saveAndFlush(PlayerEntity(leagueId = league.id!!, userId = player.id!!))

        assertTrue(playerRepository.findById(saved.id!!).isPresent)
    }

    @Test
    fun `a second active join by the same user in the same league is rejected`() {
        val organizer = persistedUser("organizer-dup")
        val league = persistedLeague(organizer.id!!)
        val player = persistedUser("player-dup")
        playerRepository.saveAndFlush(PlayerEntity(leagueId = league.id!!, userId = player.id!!))

        assertFailsWith<DataIntegrityViolationException> {
            playerRepository.saveAndFlush(PlayerEntity(leagueId = league.id!!, userId = player.id!!))
        }
    }

    @Test
    fun `rejoining after the first row is removed is allowed`() {
        val organizer = persistedUser("organizer-rejoin")
        val league = persistedLeague(organizer.id!!)
        val player = persistedUser("player-rejoin")
        val first = playerRepository.saveAndFlush(PlayerEntity(leagueId = league.id!!, userId = player.id!!))
        first.removedAt = java.time.Instant.now()
        playerRepository.saveAndFlush(first)

        val second = playerRepository.saveAndFlush(PlayerEntity(leagueId = league.id!!, userId = player.id!!))

        assertTrue(playerRepository.findById(second.id!!).isPresent)
    }

    @Test
    fun `deleting the league cascades to its player rows`() {
        val organizer = persistedUser("organizer-cascade")
        val league = persistedLeague(organizer.id!!)
        val player = persistedUser("player-cascade")
        val row = playerRepository.saveAndFlush(PlayerEntity(leagueId = league.id!!, userId = player.id!!))

        leagueRepository.delete(league)
        leagueRepository.flush()
        entityManager.clear()

        assertTrue(playerRepository.findById(row.id!!).isEmpty)
    }

    @Test
    fun `deleting the joining user cascades to their player rows`() {
        val organizer = persistedUser("organizer-user-cascade")
        val league = persistedLeague(organizer.id!!)
        val player = persistedUser("player-user-cascade")
        val row = playerRepository.saveAndFlush(PlayerEntity(leagueId = league.id!!, userId = player.id!!))

        userRepository.delete(player)
        userRepository.flush()
        entityManager.clear()

        assertTrue(playerRepository.findById(row.id!!).isEmpty)
    }
}

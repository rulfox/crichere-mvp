package com.crichere.backend.league

import com.crichere.backend.auth.UserEntity
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.common.AbstractIntegrationTest
import com.crichere.backend.ground.GroundEntity
import com.crichere.backend.ground.GroundRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Constraint tests for the `leagues` table (V8__create_leagues_table.sql) via [LeagueEntity]/
 * [LeagueRepository]. Each test runs in its own rolled-back transaction so state never leaks
 * between tests sharing the singleton Testcontainers Postgres from [AbstractIntegrationTest].
 */
@Transactional
class LeaguesConstraintTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var groundRepository: GroundRepository

    @Autowired
    private lateinit var leagueRepository: LeagueRepository

    @Autowired
    private lateinit var leagueAwardRepository: LeagueAwardRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun persistedUser(hashSuffix: String): UserEntity =
        userRepository.saveAndFlush(UserEntity(phoneLookupHash = "hash-league-$hashSuffix", phoneEncrypted = "enc"))

    private fun persistedGround(registeredBy: UUID): GroundEntity =
        groundRepository.saveAndFlush(
            GroundEntity(
                name = "Test Ground",
                state = "Karnataka",
                district = "Bengaluru Urban",
                latitude = 12.9716,
                longitude = 77.5946,
                registeredByUserId = registeredBy,
            ),
        )

    /** A ground owned by its own fresh user, so [validLeague] works even for a non-existent organizer. */
    private fun someGroundId(): UUID = persistedGround(persistedUser("ground-owner-${UUID.randomUUID()}").id!!).id!!

    private fun validLeague(organizer: UUID, groundId: UUID = someGroundId()) = LeagueEntity(
        organizerUserId = organizer,
        name = "Test League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        groundId = groundId,
        startsOn = LocalDate.of(2026, 10, 12),
    )

    @Test
    fun `a fully valid league round-trips with ANNOUNCED status`() {
        val user = persistedUser("valid")
        val saved = leagueRepository.saveAndFlush(validLeague(user.id!!))

        val reloaded = leagueRepository.findById(saved.id!!).orElseThrow()
        assertTrue(reloaded.status == LeagueStatus.ANNOUNCED)
        assertNull(reloaded.completedAt)
    }

    @Test
    fun `auction settings columns round-trip and are nullable`() {
        val user = persistedUser("auction-settings")
        val withoutSettings = leagueRepository.saveAndFlush(validLeague(user.id!!))
        val reloadedWithout = leagueRepository.findById(withoutSettings.id!!).orElseThrow()
        assertNull(reloadedWithout.auctionBasePrice)
        assertNull(reloadedWithout.auctionPurse)
        assertNull(reloadedWithout.auctionSquadMin)
        assertNull(reloadedWithout.auctionSquadMax)
        assertNull(reloadedWithout.auctionBidIncrement)

        val withSettings = leagueRepository.saveAndFlush(
            validLeague(user.id!!).apply {
                auctionBasePrice = java.math.BigDecimal("500")
                auctionPurse = java.math.BigDecimal("10000")
                auctionSquadMin = 5
                auctionSquadMax = 15
                auctionBidIncrement = java.math.BigDecimal("100")
            },
        )
        val reloadedWith = leagueRepository.findById(withSettings.id!!).orElseThrow()
        assertTrue(reloadedWith.auctionBasePrice == java.math.BigDecimal("500"))
        assertTrue(reloadedWith.auctionSquadMax == 15)
    }

    @Test
    fun `creating a league for a non-existent organizer is rejected`() {
        assertFailsWith<DataIntegrityViolationException> {
            leagueRepository.saveAndFlush(validLeague(UUID.randomUUID()))
        }
    }

    @Test
    fun `a null name is rejected`() {
        val user = persistedUser("null-name")
        assertFailsWith<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "INSERT INTO leagues (organizer_user_id, name, state, district, ground_id, starts_on) VALUES (?, NULL, ?, ?, ?, ?)",
                user.id, "Karnataka", "Bengaluru Urban", someGroundId(), java.sql.Date.valueOf("2026-10-12"),
            )
        }
    }

    @Test
    fun `a null starts_on is rejected`() {
        val user = persistedUser("null-date")
        assertFailsWith<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "INSERT INTO leagues (organizer_user_id, name, state, district, ground_id, starts_on) VALUES (?, ?, ?, ?, ?, NULL)",
                user.id, "Test", "Karnataka", "Bengaluru Urban", someGroundId(),
            )
        }
    }

    @Test
    fun `a null ground_id is rejected`() {
        val user = persistedUser("null-ground")
        assertFailsWith<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "INSERT INTO leagues (organizer_user_id, name, state, district, ground_id, starts_on) VALUES (?, ?, ?, ?, NULL, ?)",
                user.id, "Test", "Karnataka", "Bengaluru Urban", java.sql.Date.valueOf("2026-10-12"),
            )
        }
    }

    @Test
    fun `referencing a non-existent ground is rejected`() {
        val user = persistedUser("bad-ground")
        assertFailsWith<DataIntegrityViolationException> {
            leagueRepository.saveAndFlush(validLeague(user.id!!, groundId = UUID.randomUUID()))
        }
    }

    @Test
    fun `deleting the organizing user cascades to their leagues`() {
        val user = persistedUser("cascade")
        val league = leagueRepository.saveAndFlush(validLeague(user.id!!))

        userRepository.delete(user)
        userRepository.flush()
        entityManager.clear()

        assertTrue(leagueRepository.findById(league.id!!).isEmpty)
    }

    @Test
    fun `deleting a ground that a league uses is rejected`() {
        val user = persistedUser("ground-restrict")
        val ground = persistedGround(user.id!!)
        leagueRepository.saveAndFlush(validLeague(user.id!!, groundId = ground.id!!))

        assertFailsWith<DataIntegrityViolationException> {
            groundRepository.delete(ground)
            groundRepository.flush()
        }
    }

    @Test
    fun `deleting a league cascades to its awards`() {
        val user = persistedUser("award-cascade")
        val league = leagueRepository.saveAndFlush(validLeague(user.id!!))
        val award = leagueAwardRepository.saveAndFlush(
            LeagueAwardEntity(leagueId = league.id!!, name = "First Prize", displayOrder = 0),
        )

        leagueRepository.delete(league)
        leagueRepository.flush()
        entityManager.clear()

        assertTrue(leagueAwardRepository.findById(award.id!!).isEmpty)
    }

    @Test
    fun `organizer_upi_id round-trips and is nullable`() {
        val user = persistedUser("upi")
        val withoutUpi = leagueRepository.saveAndFlush(validLeague(user.id!!))
        assertNull(leagueRepository.findById(withoutUpi.id!!).orElseThrow().organizerUpiId)

        val withUpi = leagueRepository.saveAndFlush(validLeague(user.id!!).apply { organizerUpiId = "organizer@upi" })
        assertTrue(leagueRepository.findById(withUpi.id!!).orElseThrow().organizerUpiId == "organizer@upi")
    }

    @Test
    fun `a null award name is rejected`() {
        val user = persistedUser("award-null-name")
        val league = leagueRepository.saveAndFlush(validLeague(user.id!!))
        assertFailsWith<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "INSERT INTO league_awards (id, league_id, name, display_order) VALUES (?, ?, NULL, 0)",
                UUID.randomUUID(), league.id,
            )
        }
    }
}

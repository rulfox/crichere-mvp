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
                city = "Bengaluru",
                latitude = 12.9716,
                longitude = 77.5946,
                registeredByUserId = registeredBy,
            ),
        )

    private fun validLeague(organizer: UUID, groundId: UUID? = null) = LeagueEntity(
        organizerUserId = organizer,
        name = "Test League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
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
                "INSERT INTO leagues (organizer_user_id, name, state, district, city, starts_on) VALUES (?, NULL, ?, ?, ?, ?)",
                user.id, "Karnataka", "Bengaluru Urban", "Bengaluru", java.sql.Date.valueOf("2026-10-12"),
            )
        }
    }

    @Test
    fun `a null starts_on is rejected`() {
        val user = persistedUser("null-date")
        assertFailsWith<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "INSERT INTO leagues (organizer_user_id, name, state, district, city, starts_on) VALUES (?, ?, ?, ?, ?, NULL)",
                user.id, "Test", "Karnataka", "Bengaluru Urban", "Bengaluru",
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
    fun `deleting an attached ground sets ground_id to null rather than deleting the league`() {
        val user = persistedUser("ground-set-null")
        val ground = persistedGround(user.id!!)
        val league = leagueRepository.saveAndFlush(validLeague(user.id!!, groundId = ground.id))

        groundRepository.delete(ground)
        groundRepository.flush()
        entityManager.clear()

        val reloaded = leagueRepository.findById(league.id!!).orElseThrow()
        assertNull(reloaded.groundId)
    }
}

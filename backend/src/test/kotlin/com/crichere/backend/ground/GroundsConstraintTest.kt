package com.crichere.backend.ground

import com.crichere.backend.auth.UserEntity
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.common.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Constraint tests for the `grounds` table (V7__create_grounds_table.sql) via [GroundEntity]/
 * [GroundRepository]. Each test runs in its own rolled-back transaction so state never leaks
 * between tests sharing the singleton Testcontainers Postgres from [AbstractIntegrationTest].
 */
@Transactional
class GroundsConstraintTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var groundRepository: GroundRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun persistedUser(hashSuffix: String): UserEntity =
        userRepository.saveAndFlush(
            UserEntity(phoneLookupHash = "hash-ground-$hashSuffix", phoneEncrypted = "enc"),
        )

    private fun validGround(registeredBy: UUID) = GroundEntity(
        name = "Test Ground",
        state = "Tamil Nadu",
        district = "Chennai",
        city = "Chennai",
        latitude = 13.0827,
        longitude = 80.2707,
        registeredByUserId = registeredBy,
    )

    @Test
    fun `a fully valid ground round-trips`() {
        val user = persistedUser("valid")
        val saved = groundRepository.saveAndFlush(validGround(user.id!!))

        val reloaded = groundRepository.findById(saved.id!!).orElseThrow()
        assertTrue(reloaded.name == "Test Ground")
        assertTrue(reloaded.registeredByUserId == user.id)
    }

    @Test
    fun `registering a ground for a non-existent user is rejected`() {
        assertFailsWith<DataIntegrityViolationException> {
            groundRepository.saveAndFlush(validGround(UUID.randomUUID()))
        }
    }

    @Test
    fun `deleting the registering user cascades to their grounds`() {
        val user = persistedUser("cascade")
        val ground = groundRepository.saveAndFlush(validGround(user.id!!))

        userRepository.delete(user)
        userRepository.flush()
        // The cascade delete happens at the database level (ON DELETE CASCADE), not via
        // JPA-managed cascade -- clear the persistence context so the check below actually
        // hits the database instead of returning a stale first-level-cache entry.
        entityManager.clear()

        assertTrue(groundRepository.findById(ground.id!!).isEmpty)
    }

    @Test
    fun `search finds a ground by a case-insensitive partial name match`() {
        val user = persistedUser("search")
        groundRepository.saveAndFlush(validGround(user.id!!).apply { name = "MRF Ground, Chennai" })

        val results = groundRepository.searchByPattern(searchPattern = "%mrf%", state = null, district = null, city = null)

        assertTrue(results.any { it.name == "MRF Ground, Chennai" })
    }

    @Test
    fun `an empty search pattern matches every ground`() {
        val user = persistedUser("search-empty")
        groundRepository.saveAndFlush(validGround(user.id!!).apply { name = "Match Everything Ground" })

        val results = groundRepository.searchByPattern(searchPattern = "%%", state = null, district = null, city = null)

        assertTrue(results.any { it.name == "Match Everything Ground" })
    }

    @Test
    fun `search filters combine -- a wrong district excludes an otherwise-matching name`() {
        val user = persistedUser("search-filter")
        groundRepository.saveAndFlush(validGround(user.id!!).apply { name = "Unique Filter Test Ground" })

        val results = groundRepository.searchByPattern(
            searchPattern = "%unique filter test%",
            state = null,
            district = "Some Other District",
            city = null,
        )

        assertTrue(results.isEmpty())
    }
}

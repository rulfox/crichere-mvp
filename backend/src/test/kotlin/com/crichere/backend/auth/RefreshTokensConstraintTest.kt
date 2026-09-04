package com.crichere.backend.auth

import com.crichere.backend.common.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Constraint tests for the `refresh_tokens` table (V3__create_refresh_tokens_table.sql) via
 * [RefreshTokenEntity] / [RefreshTokenRepository]. Each test runs in its own rolled-back
 * transaction so state never leaks between tests sharing the singleton Testcontainers
 * Postgres from [AbstractIntegrationTest].
 */
@Transactional
class RefreshTokensConstraintTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun persistedUser(hashSuffix: String): UserEntity =
        userRepository.saveAndFlush(
            UserEntity(phoneLookupHash = "hash-rt-$hashSuffix", phoneEncrypted = "enc"),
        )

    private fun freshToken(userId: UUID, tokenHash: String): RefreshTokenEntity {
        val now = Instant.now()
        return RefreshTokenEntity(
            userId = userId,
            tokenHash = tokenHash,
            issuedAt = now,
            expiresAt = now.plusSeconds(3600),
        )
    }

    @Test
    fun `duplicate token_hash is rejected`() {
        val user = persistedUser("dup")
        val sharedHash = "token-hash-duplicate-check"
        refreshTokenRepository.saveAndFlush(freshToken(user.id!!, sharedHash))

        assertFailsWith<DataIntegrityViolationException> {
            refreshTokenRepository.saveAndFlush(freshToken(user.id!!, sharedHash))
        }
    }

    @Test
    fun `inserting a refresh token for a non-existent user_id is rejected`() {
        assertFailsWith<DataIntegrityViolationException> {
            refreshTokenRepository.saveAndFlush(
                freshToken(UUID.randomUUID(), "token-hash-orphan"),
            )
        }
    }

    @Test
    fun `deleting the owning user cascades to their refresh tokens`() {
        val user = persistedUser("cascade")
        val token = refreshTokenRepository.saveAndFlush(
            freshToken(user.id!!, "token-hash-cascade"),
        )

        userRepository.delete(user)
        userRepository.flush()
        // The cascade delete happens at the database level (ON DELETE CASCADE), not via
        // JPA-managed cascade -- Hibernate's persistence context doesn't know about it, so
        // its first-level cache would otherwise still hand back the now-deleted entity.
        // Clear it so the checks below actually hit the database.
        entityManager.clear()

        assertTrue(refreshTokenRepository.findById(token.id!!).isEmpty)
        assertNull(refreshTokenRepository.findByTokenHash("token-hash-cascade"))
    }
}

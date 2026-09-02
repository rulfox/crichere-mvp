package com.crichere.backend.auth

import com.crichere.backend.common.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Constraint tests for the `users` table (V1__create_users_table.sql) via [UserEntity] /
 * [UserRepository] and, where the constraint can't be observed through the JPA layer, raw
 * SQL via [JdbcTemplate]. Each test runs in its own rolled-back transaction so state never
 * leaks between tests sharing the singleton Testcontainers Postgres from
 * [AbstractIntegrationTest].
 */
@Transactional
class UsersConstraintTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `duplicate phone_lookup_hash is rejected`() {
        val sharedHash = "hash-duplicate-check"
        userRepository.saveAndFlush(
            UserEntity(phoneLookupHash = sharedHash, phoneEncrypted = "enc-1"),
        )

        assertFailsWith<DataIntegrityViolationException> {
            userRepository.saveAndFlush(
                UserEntity(phoneLookupHash = sharedHash, phoneEncrypted = "enc-2"),
            )
        }
    }

    @Test
    fun `phone_encrypted is required`() {
        assertFailsWith<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "INSERT INTO users (id, phone_lookup_hash, phone_encrypted) VALUES (?, ?, NULL)",
                UUID.randomUUID(),
                "hash-null-phone-encrypted",
            )
        }
    }

    @Test
    fun `created_at defaults to now() at the database level when omitted`() {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO users (id, phone_lookup_hash, phone_encrypted) VALUES (?, ?, ?)",
            id,
            "hash-db-default-created-at",
            "enc",
        )

        val createdAt = jdbcTemplate.queryForObject(
            "SELECT created_at FROM users WHERE id = ?",
            java.sql.Timestamp::class.java,
            id,
        )

        assertNotNull(createdAt)
    }
}

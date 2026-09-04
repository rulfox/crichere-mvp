package com.crichere.backend.profile

import com.crichere.backend.auth.UserEntity
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.common.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Constraint tests for the `profiles` table (V2__create_profiles_table.sql) via
 * [ProfileEntity] / [ProfileRepository]. Invalid enum values are inserted via raw SQL
 * ([JdbcTemplate]) rather than through the entity, since the whole point of mapping
 * [PlayingRole] / [BattingStyle] / [BowlingStyle] as Kotlin enums is that the compiler
 * won't let application code construct an invalid one -- the CHECK constraint is the last
 * line of defense against anything that bypasses the entity (raw SQL, a different app,
 * manual data fixes), so that's what these tests exercise directly.
 *
 * Each test runs in its own rolled-back transaction so state never leaks between tests
 * sharing the singleton Testcontainers Postgres from [AbstractIntegrationTest].
 */
@Transactional
class ProfilesConstraintTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var profileRepository: ProfileRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun persistedUser(hashSuffix: String): UserEntity =
        userRepository.saveAndFlush(
            UserEntity(phoneLookupHash = "hash-profile-$hashSuffix", phoneEncrypted = "enc"),
        )

    @Test
    fun `a fully valid profile round-trips through the enum-mapped columns`() {
        val user = persistedUser("valid")
        val saved = profileRepository.saveAndFlush(
            ProfileEntity(
                userId = user.id!!,
                name = "Rahul Sharma",
                playingRole = PlayingRole.ALL_ROUNDER,
                battingStyle = BattingStyle.RIGHT_HAND,
                bowlingStyle = BowlingStyle.RIGHT_ARM_OFFBREAK,
            ),
        )

        val reloaded = profileRepository.findById(saved.userId).orElseThrow()
        assertTrue(reloaded.playingRole == PlayingRole.ALL_ROUNDER)
        assertTrue(reloaded.battingStyle == BattingStyle.RIGHT_HAND)
        assertTrue(reloaded.bowlingStyle == BowlingStyle.RIGHT_ARM_OFFBREAK)
    }

    @Test
    fun `invalid playing_role is rejected`() {
        val user = persistedUser("bad-role")
        assertFailsWith<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "INSERT INTO profiles (user_id, playing_role) VALUES (?, ?)",
                user.id,
                "CAPTAIN",
            )
        }
    }

    @Test
    fun `invalid batting_style is rejected`() {
        val user = persistedUser("bad-batting")
        assertFailsWith<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "INSERT INTO profiles (user_id, batting_style) VALUES (?, ?)",
                user.id,
                "AMBIDEXTROUS",
            )
        }
    }

    @Test
    fun `invalid bowling_style is rejected`() {
        val user = persistedUser("bad-bowling")
        assertFailsWith<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "INSERT INTO profiles (user_id, bowling_style) VALUES (?, ?)",
                user.id,
                "UNDERARM",
            )
        }
    }

    @Test
    fun `a null bowling_style is allowed (not every player bowls)`() {
        val user = persistedUser("no-bowling")
        profileRepository.saveAndFlush(
            ProfileEntity(userId = user.id!!, playingRole = PlayingRole.BATSMAN, bowlingStyle = null),
        )
    }

    @Test
    fun `inserting a profile for a non-existent user_id is rejected`() {
        assertFailsWith<DataIntegrityViolationException> {
            profileRepository.saveAndFlush(ProfileEntity(userId = UUID.randomUUID()))
        }
    }

    @Test
    fun `deleting the owning user cascades to their profile`() {
        val user = persistedUser("cascade")
        profileRepository.saveAndFlush(ProfileEntity(userId = user.id!!, name = "To Be Cascaded"))

        userRepository.delete(user)
        userRepository.flush()
        // The cascade delete happens at the database level (ON DELETE CASCADE), not via
        // JPA-managed cascade -- clear the persistence context so the check below actually
        // hits the database instead of returning a stale first-level-cache entry.
        entityManager.clear()

        assertTrue(profileRepository.findById(user.id!!).isEmpty)
    }
}

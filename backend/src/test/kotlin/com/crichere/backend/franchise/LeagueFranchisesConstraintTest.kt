package com.crichere.backend.franchise

import com.crichere.backend.auth.UserEntity
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.common.AbstractIntegrationTest
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Constraint tests for the `league_franchises` table (V12__create_league_franchises_table.sql)
 * via [FranchiseEntity]/[FranchiseRepository] -- in particular that, unlike `league_players`,
 * there is deliberately no uniqueness constraint (multi-franchise ownership by the same user is
 * allowed, see docs/PHASE3.md's Decisions Made).
 */
@Transactional
class LeagueFranchisesConstraintTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var leagueRepository: LeagueRepository

    @Autowired
    private lateinit var franchiseRepository: FranchiseRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun persistedUser(hashSuffix: String): UserEntity =
        userRepository.saveAndFlush(UserEntity(phoneLookupHash = "hash-franchise-$hashSuffix", phoneEncrypted = "enc"))

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
    fun `a fully valid claim round-trips`() {
        val organizer = persistedUser("organizer")
        val league = persistedLeague(organizer.id!!)
        val owner = persistedUser("owner")

        val saved = franchiseRepository.saveAndFlush(FranchiseEntity(leagueId = league.id!!, ownerUserId = owner.id!!, name = "Chennai Kings"))

        assertTrue(franchiseRepository.findById(saved.id!!).isPresent)
    }

    @Test
    fun `a second claim by the same user in the same league is allowed -- no uniqueness constraint`() {
        val organizer = persistedUser("organizer-multi")
        val league = persistedLeague(organizer.id!!)
        val owner = persistedUser("owner-multi")
        franchiseRepository.saveAndFlush(FranchiseEntity(leagueId = league.id!!, ownerUserId = owner.id!!, name = "Chennai Kings"))

        val second = franchiseRepository.saveAndFlush(FranchiseEntity(leagueId = league.id!!, ownerUserId = owner.id!!, name = "Bengaluru Blasters"))

        assertTrue(franchiseRepository.findById(second.id!!).isPresent)
    }

    @Test
    fun `deleting the league cascades to its franchise rows`() {
        val organizer = persistedUser("organizer-cascade")
        val league = persistedLeague(organizer.id!!)
        val owner = persistedUser("owner-cascade")
        val row = franchiseRepository.saveAndFlush(FranchiseEntity(leagueId = league.id!!, ownerUserId = owner.id!!, name = "Chennai Kings"))

        leagueRepository.delete(league)
        leagueRepository.flush()
        entityManager.clear()

        assertTrue(franchiseRepository.findById(row.id!!).isEmpty)
    }

    @Test
    fun `deleting the owning user cascades to their franchise rows`() {
        val organizer = persistedUser("organizer-user-cascade")
        val league = persistedLeague(organizer.id!!)
        val owner = persistedUser("owner-user-cascade")
        val row = franchiseRepository.saveAndFlush(FranchiseEntity(leagueId = league.id!!, ownerUserId = owner.id!!, name = "Chennai Kings"))

        userRepository.delete(owner)
        userRepository.flush()
        entityManager.clear()

        assertTrue(franchiseRepository.findById(row.id!!).isEmpty)
    }
}

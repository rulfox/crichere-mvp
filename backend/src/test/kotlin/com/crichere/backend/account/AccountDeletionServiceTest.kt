package com.crichere.backend.account

import com.crichere.backend.auth.UserEntity
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.league.LeagueRole
import com.crichere.backend.league.LeagueRoleEntity
import com.crichere.backend.league.LeagueRoleRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDeletionServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val leagueRepository = mockk<LeagueRepository>()
    private val leagueRoleRepository = mockk<LeagueRoleRepository>()
    private val service = AccountDeletionService(
        userRepository, leagueRepository, leagueRoleRepository,
        mockk(), mockk(), mockk(), mockk(), AccountDeletionProperties(), Clock.systemUTC(),
    )

    private val organizerId = UUID.randomUUID()
    private val leagueId = UUID.randomUUID()
    private val league = LeagueEntity(
        id = leagueId, organizerUserId = organizerId, name = "League", state = "Kerala", district = "Kottayam",
        groundId = UUID.randomUUID(), startsOn = LocalDate.of(2026, 12, 1),
    )

    private fun user(id: UUID, pending: Boolean = false) =
        UserEntity(id = id, phoneLookupHash = "h-$id", phoneEncrypted = "e", deletionRequestedAt = if (pending) Instant.now() else null)

    private fun role(userId: UUID) =
        LeagueRoleEntity(id = UUID.randomUUID(), leagueId = leagueId, userId = userId, role = LeagueRole.CO_ORGANIZER, grantedByUserId = organizerId)

    private fun givenRoles(vararg roles: LeagueRoleEntity) {
        every { leagueRoleRepository.findByLeagueIdAndRevokedAtIsNullOrderByGrantedAtAsc(leagueId) } returns roles.toList()
    }

    private fun givenUsers(vararg users: UserEntity) {
        users.forEach { every { userRepository.findById(requireNotNull(it.id)) } returns Optional.of(it) }
    }

    @Test
    fun `the successor is the earliest co-organizer who is not leaving too`() {
        val leaving = UUID.randomUUID()
        val staying = UUID.randomUUID()
        givenRoles(role(leaving), role(staying))
        givenUsers(user(leaving, pending = true), user(staying))

        assertEquals(staying, service.successorFor(leagueId, organizerId)?.userId)
    }

    @Test
    fun `no eligible co-organizer means no successor`() {
        val leaving = UUID.randomUUID()
        givenRoles(role(leaving))
        givenUsers(user(leaving, pending = true))

        assertNull(service.successorFor(leagueId, organizerId))
    }

    @Test
    fun `revoking the last successor of a leaving organizer would strand the league`() {
        val only = role(UUID.randomUUID())
        givenRoles(only)
        givenUsers(user(organizerId, pending = true), user(only.userId))

        assertTrue(service.wouldStrandLeague(league, requireNotNull(only.id)))
    }

    @Test
    fun `revoking is fine while the organizer is not leaving`() {
        val only = role(UUID.randomUUID())
        givenRoles(only)
        givenUsers(user(organizerId), user(only.userId))

        assertFalse(service.wouldStrandLeague(league, requireNotNull(only.id)))
    }
}

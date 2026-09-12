package com.crichere.backend.me

import com.crichere.backend.franchise.FranchiseEntity
import com.crichere.backend.franchise.FranchiseRepository
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueFollowEntity
import com.crichere.backend.league.LeagueFollowRepository
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.player.PlayerEntity
import com.crichere.backend.player.PlayerRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

/** Unit-level coverage of [MeService]'s four-list aggregation. End-to-end HTTP behaviour is covered by `MeFlowIntegrationTest`. */
class MeServiceTest {

    private val leagueRepository = mockk<LeagueRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val franchiseRepository = mockk<FranchiseRepository>()
    private val leagueFollowRepository = mockk<LeagueFollowRepository>()
    private val service = MeService(leagueRepository, playerRepository, franchiseRepository, leagueFollowRepository)

    private val callerId: UUID = UUID.randomUUID()

    private fun league(id: UUID, organizer: UUID) = LeagueEntity(
        id = id,
        organizerUserId = organizer,
        name = "League $id",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = LocalDate.of(2026, 10, 12),
    )

    @Test
    fun `a user can appear in more than one list at once -- organizer who also joined their own league as a player`() {
        val organizedLeagueId = UUID.randomUUID()
        val organizedLeague = league(organizedLeagueId, callerId)

        every { leagueRepository.findByOrganizerUserId(callerId) } returns listOf(organizedLeague)
        every { playerRepository.findByUserIdAndRemovedAtIsNull(callerId) } returns
            listOf(PlayerEntity(id = UUID.randomUUID(), leagueId = organizedLeagueId, userId = callerId))
        every { leagueRepository.findById(organizedLeagueId) } returns Optional.of(organizedLeague)
        every { franchiseRepository.findByOwnerUserIdAndRemovedAtIsNull(callerId) } returns emptyList()
        every { leagueFollowRepository.findByUserId(callerId) } returns emptyList()

        val result = service.getMyLeagues(callerId)

        assertEquals(1, result.organizing.size)
        assertEquals(1, result.playing.size)
        assertEquals(organizedLeagueId, result.organizing[0].id)
        assertEquals(organizedLeagueId, result.playing[0].id)
    }

    @Test
    fun `franchiseOwner and following lists resolve through their own repositories`() {
        val franchiseLeagueId = UUID.randomUUID()
        val followedLeagueId = UUID.randomUUID()
        every { leagueRepository.findByOrganizerUserId(callerId) } returns emptyList()
        every { playerRepository.findByUserIdAndRemovedAtIsNull(callerId) } returns emptyList()
        every { franchiseRepository.findByOwnerUserIdAndRemovedAtIsNull(callerId) } returns
            listOf(FranchiseEntity(id = UUID.randomUUID(), leagueId = franchiseLeagueId, ownerUserId = callerId, name = "Chennai Kings"))
        every { leagueRepository.findById(franchiseLeagueId) } returns Optional.of(league(franchiseLeagueId, UUID.randomUUID()))
        every { leagueFollowRepository.findByUserId(callerId) } returns
            listOf(LeagueFollowEntity(leagueId = followedLeagueId, userId = callerId))
        every { leagueRepository.findById(followedLeagueId) } returns Optional.of(league(followedLeagueId, UUID.randomUUID()))

        val result = service.getMyLeagues(callerId)

        assertEquals(listOf(franchiseLeagueId), result.franchiseOwner.map { it.id })
        assertEquals(listOf(followedLeagueId), result.following.map { it.id })
    }
}

package com.crichere.backend.me

import com.crichere.backend.franchise.FranchiseEntity
import com.crichere.backend.franchise.FranchiseRepository
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueFollowEntity
import com.crichere.backend.league.LeagueFollowRepository
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.notification.DeviceTokenEntity
import com.crichere.backend.notification.DeviceTokenRepository
import com.crichere.backend.player.PlayerEntity
import com.crichere.backend.player.PlayerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
    private val deviceTokenRepository = mockk<DeviceTokenRepository>()
    private val service = MeService(leagueRepository, playerRepository, franchiseRepository, leagueFollowRepository, deviceTokenRepository)

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

    @Test
    fun `owning two franchises in the same league lists that league only once`() {
        // Reproduces a real on-device crash: unlike players (unique per league+user), a user can
        // own more than one franchise in the same league (docs/PHASE3.md's "dual roles allowed
        // freely"), which previously put the same league into franchiseOwner twice -- a duplicate
        // key that crashed the mobile My Leagues screen's LazyColumn outright.
        val leagueId = UUID.randomUUID()
        val sharedLeague = league(leagueId, UUID.randomUUID())

        every { leagueRepository.findByOrganizerUserId(callerId) } returns emptyList()
        every { playerRepository.findByUserIdAndRemovedAtIsNull(callerId) } returns emptyList()
        every { franchiseRepository.findByOwnerUserIdAndRemovedAtIsNull(callerId) } returns listOf(
            FranchiseEntity(id = UUID.randomUUID(), leagueId = leagueId, ownerUserId = callerId, name = "Bihar Warriors"),
            FranchiseEntity(id = UUID.randomUUID(), leagueId = leagueId, ownerUserId = callerId, name = "Patna Panthers"),
        )
        every { leagueRepository.findById(leagueId) } returns Optional.of(sharedLeague)
        every { leagueFollowRepository.findByUserId(callerId) } returns emptyList()

        val result = service.getMyLeagues(callerId)

        assertEquals(listOf(leagueId), result.franchiseOwner.map { it.id })
    }

    @Test
    fun `registering a new token inserts a row owned by the caller`() {
        every { deviceTokenRepository.findByToken("token-a") } returns null
        val saved = slot<DeviceTokenEntity>()
        every { deviceTokenRepository.save(capture(saved)) } answers { firstArg() }

        service.registerDeviceToken(callerId, "token-a", "ANDROID")

        assertEquals(callerId, saved.captured.userId)
        assertEquals("token-a", saved.captured.token)
    }

    @Test
    fun `registering an already-known token reassigns it to the new caller -- switching accounts on the same device`() {
        // Reproduces this app's own tested behavior: signing out and into a different account on
        // the same physical device must move that device's notifications to the new account, not
        // leave them pointed at whoever was previously signed in (see docs/PHASE8.md).
        val previousOwnerId = UUID.randomUUID()
        val existing = DeviceTokenEntity(userId = previousOwnerId, token = "token-a", platform = "ANDROID")
        every { deviceTokenRepository.findByToken("token-a") } returns existing
        val saved = slot<DeviceTokenEntity>()
        every { deviceTokenRepository.save(capture(saved)) } answers { firstArg() }

        service.registerDeviceToken(callerId, "token-a", "ANDROID")

        assertEquals(callerId, saved.captured.userId)
    }

    @Test
    fun `unregistering a token only ever removes the caller's own`() {
        every { deviceTokenRepository.deleteByTokenAndUserId("token-a", callerId) } returns Unit

        service.unregisterDeviceToken(callerId, "token-a")

        verify { deviceTokenRepository.deleteByTokenAndUserId("token-a", callerId) }
    }
}

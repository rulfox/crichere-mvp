package com.crichere.backend.player

import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.league.LeagueAuthorization
import com.crichere.backend.league.LeagueCapacityFullException
import com.crichere.backend.league.LeagueCompletedException
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueNotFoundException
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.league.LeagueRoleRepository
import com.crichere.backend.league.NotOrganizerException
import com.crichere.backend.notification.FcmSender
import com.crichere.backend.league.PaymentScreenshotRequiredException
import com.crichere.backend.player.dto.LeaguePlayerJoinRequest
import com.crichere.backend.profile.ProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Unit-level coverage of [PlayerService]. End-to-end HTTP behaviour is covered by `PlayerFlowIntegrationTest`. */
class PlayerServiceTest {

    private val playerRepository = mockk<PlayerRepository>()
    private val leagueRepository = mockk<LeagueRepository>()
    private val profileRepository = mockk<ProfileRepository>().also {
        every { it.findById(any()) } returns Optional.empty()
    }
    private val contentRateLimiter = mockk<ContentRateLimiter>()
    private val leagueAuthorization = LeagueAuthorization(
        mockk<LeagueRoleRepository>().also {
            every { it.existsByLeagueIdAndUserIdAndRevokedAtIsNull(any(), any()) } returns false
        },
    )
    private val fcmSender = mockk<FcmSender>(relaxed = true)
    private val service = PlayerService(playerRepository, leagueRepository, profileRepository, contentRateLimiter, leagueAuthorization, fcmSender)

    private val organizerId: UUID = UUID.randomUUID()
    private val playerId: UUID = UUID.randomUUID()
    private val leagueId: UUID = UUID.randomUUID()
    private val entityId: UUID = UUID.randomUUID()

    private fun league(playersRequired: Int? = null, playerFee: BigDecimal? = null, completedAt: Instant? = null) = LeagueEntity(
        id = leagueId,
        organizerUserId = organizerId,
        name = "Test League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = LocalDate.of(2026, 10, 12),
        playersRequired = playersRequired,
        playerFee = playerFee,
        completedAt = completedAt,
    )

    // ---------------------------------------------------------------- join

    @Test
    fun `join is rejected once the league is completed`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(completedAt = Instant.now()))

        assertFailsWith<LeagueCompletedException> {
            service.join(leagueId, playerId, LeaguePlayerJoinRequest())
        }
    }

    @Test
    fun `join is rejected when the rate limit is tripped, before touching the repository`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { contentRateLimiter.tryConsumeForPlayerJoin(playerId) } returns Duration.ofMinutes(5)

        assertFailsWith<ContentRateLimitExceededException> {
            service.join(leagueId, playerId, LeaguePlayerJoinRequest())
        }
    }

    @Test
    fun `join is rejected once capacity is full`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(playersRequired = 1))
        every { contentRateLimiter.tryConsumeForPlayerJoin(playerId) } returns null
        every { playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 1L

        assertFailsWith<LeagueCapacityFullException> {
            service.join(leagueId, playerId, LeaguePlayerJoinRequest())
        }
    }

    @Test
    fun `join is rejected when a fee is set but no screenshot is supplied`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(playerFee = BigDecimal("100")))
        every { contentRateLimiter.tryConsumeForPlayerJoin(playerId) } returns null
        every { playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 0L

        assertFailsWith<PaymentScreenshotRequiredException> {
            service.join(leagueId, playerId, LeaguePlayerJoinRequest())
        }
    }

    @Test
    fun `join is rejected when the caller already has an active join row`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { contentRateLimiter.tryConsumeForPlayerJoin(playerId) } returns null
        every { playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 0L
        every { playerRepository.existsByLeagueIdAndUserIdAndRemovedAtIsNull(leagueId, playerId) } returns true

        assertFailsWith<AlreadyJoinedException> {
            service.join(leagueId, playerId, LeaguePlayerJoinRequest())
        }
    }

    @Test
    fun `a free join with no capacity limit succeeds`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { contentRateLimiter.tryConsumeForPlayerJoin(playerId) } returns null
        every { playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 0L
        every { playerRepository.existsByLeagueIdAndUserIdAndRemovedAtIsNull(leagueId, playerId) } returns false
        val saved = slot<PlayerEntity>()
        every { playerRepository.save(capture(saved)) } answers { firstArg<PlayerEntity>().apply { id = entityId } }

        val response = service.join(leagueId, playerId, LeaguePlayerJoinRequest())

        assertEquals(playerId, saved.captured.userId)
        assertEquals(playerId, response.userId)
    }

    // ---------------------------------------------------------------- remove / leave

    @Test
    fun `remove by someone other than the organizer is rejected`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())

        assertFailsWith<NotOrganizerException> {
            service.remove(leagueId, playerId, UUID.randomUUID())
        }
    }

    @Test
    fun `requestLeave by someone other than the row's own user is rejected`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { playerRepository.findById(entityId) } returns
            Optional.of(PlayerEntity(id = entityId, leagueId = leagueId, userId = playerId))

        assertFailsWith<NotPlayerOwnerException> {
            service.requestLeave(leagueId, entityId, UUID.randomUUID())
        }
    }

    @Test
    fun `requestLeave notifies the organizer`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { playerRepository.findById(entityId) } returns
            Optional.of(PlayerEntity(id = entityId, leagueId = leagueId, userId = playerId))
        every { playerRepository.save(any()) } answers { firstArg() }

        service.requestLeave(leagueId, entityId, playerId)

        verify { fcmSender.sendToUser(organizerId, "Test League", any(), any()) }
    }

    @Test
    fun `approveLeave requires organizer`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())

        assertFailsWith<NotOrganizerException> {
            service.approveLeave(leagueId, entityId, UUID.randomUUID())
        }
    }

    @Test
    fun `approveLeave requires a pending leave request`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { playerRepository.findById(entityId) } returns
            Optional.of(PlayerEntity(id = entityId, leagueId = leagueId, userId = playerId, leaveRequestedAt = null))

        assertFailsWith<NoLeaveRequestPendingException> {
            service.approveLeave(leagueId, entityId, organizerId)
        }
    }

    @Test
    fun `approveLeave frees the slot by setting removedAt`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        val entity = PlayerEntity(id = entityId, leagueId = leagueId, userId = playerId, leaveRequestedAt = Instant.now())
        every { playerRepository.findById(entityId) } returns Optional.of(entity)
        val saved = slot<PlayerEntity>()
        every { playerRepository.save(capture(saved)) } answers { firstArg() }

        service.approveLeave(leagueId, entityId, organizerId)

        assertEquals(true, saved.captured.removedAt != null)
        verify { fcmSender.sendToUser(playerId, "Test League", any(), any()) }
    }

    @Test
    fun `an entity id belonging to a different league is treated as not found`() {
        val otherLeagueId = UUID.randomUUID()
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { playerRepository.findById(entityId) } returns
            Optional.of(PlayerEntity(id = entityId, leagueId = otherLeagueId, userId = playerId))

        assertFailsWith<LeaguePlayerNotFoundException> {
            service.remove(leagueId, entityId, organizerId)
        }
    }

    @Test
    fun `operating under a nonexistent league surfaces LeagueNotFoundException`() {
        every { leagueRepository.findById(leagueId) } returns Optional.empty()

        assertFailsWith<LeagueNotFoundException> {
            service.join(leagueId, playerId, LeaguePlayerJoinRequest())
        }
    }
}

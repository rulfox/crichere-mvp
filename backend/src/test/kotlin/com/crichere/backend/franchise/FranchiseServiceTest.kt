package com.crichere.backend.franchise

import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.common.PhotoUploadService
import com.crichere.backend.common.PhotoUploadUrlResponse
import com.crichere.backend.franchise.dto.LeagueFranchiseClaimRequest
import com.crichere.backend.league.LeagueAuthorization
import com.crichere.backend.league.LeagueCapacityFullException
import com.crichere.backend.league.LeagueCompletedException
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueNotFoundException
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.league.LeagueRoleRepository
import com.crichere.backend.league.NotOrganizerException
import com.crichere.backend.league.PaymentScreenshotRequiredException
import com.crichere.backend.profile.ProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Unit-level coverage of [FranchiseService]. End-to-end HTTP behaviour is covered by `FranchiseFlowIntegrationTest`. */
class FranchiseServiceTest {

    private val franchiseRepository = mockk<FranchiseRepository>()
    private val leagueRepository = mockk<LeagueRepository>()
    private val profileRepository = mockk<ProfileRepository>().also {
        every { it.findById(any()) } returns Optional.empty()
    }
    private val contentRateLimiter = mockk<ContentRateLimiter>()
    private val photoUploadService = mockk<PhotoUploadService>()
    private val leagueAuthorization = LeagueAuthorization(
        mockk<LeagueRoleRepository>().also {
            every { it.existsByLeagueIdAndUserIdAndRevokedAtIsNull(any(), any()) } returns false
        },
    )
    private val service = FranchiseService(franchiseRepository, leagueRepository, profileRepository, contentRateLimiter, photoUploadService, leagueAuthorization)

    private val organizerId: UUID = UUID.randomUUID()
    private val ownerId: UUID = UUID.randomUUID()
    private val leagueId: UUID = UUID.randomUUID()
    private val entityId: UUID = UUID.randomUUID()

    private fun league(franchisesRequired: Int? = null, franchiseFee: BigDecimal? = null, completedAt: Instant? = null) = LeagueEntity(
        id = leagueId,
        organizerUserId = organizerId,
        name = "Test League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = LocalDate.of(2026, 10, 12),
        franchisesRequired = franchisesRequired,
        franchiseFee = franchiseFee,
        completedAt = completedAt,
    )

    private fun claimRequest(paymentScreenshotUrl: String? = null) = LeagueFranchiseClaimRequest(name = "Chennai Kings", paymentScreenshotUrl = paymentScreenshotUrl)

    // ---------------------------------------------------------------- claim

    @Test
    fun `claim is rejected once the league is completed`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(completedAt = Instant.now()))

        assertFailsWith<LeagueCompletedException> {
            service.claim(leagueId, ownerId, claimRequest())
        }
    }

    @Test
    fun `claim is rejected when the rate limit is tripped`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { contentRateLimiter.tryConsumeForFranchiseClaim(ownerId) } returns Duration.ofMinutes(5)

        assertFailsWith<ContentRateLimitExceededException> {
            service.claim(leagueId, ownerId, claimRequest())
        }
    }

    @Test
    fun `claim is rejected once capacity is full`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(franchisesRequired = 1))
        every { contentRateLimiter.tryConsumeForFranchiseClaim(ownerId) } returns null
        every { franchiseRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 1L

        assertFailsWith<LeagueCapacityFullException> {
            service.claim(leagueId, ownerId, claimRequest())
        }
    }

    @Test
    fun `claim is rejected when a fee is set but no screenshot is supplied`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league(franchiseFee = BigDecimal("500")))
        every { contentRateLimiter.tryConsumeForFranchiseClaim(ownerId) } returns null
        every { franchiseRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 0L

        assertFailsWith<PaymentScreenshotRequiredException> {
            service.claim(leagueId, ownerId, claimRequest())
        }
    }

    @Test
    fun `a second claim by the same user in the same league succeeds -- multi-franchise ownership is allowed`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { contentRateLimiter.tryConsumeForFranchiseClaim(ownerId) } returns null
        every { franchiseRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 0L
        val saved = slot<FranchiseEntity>()
        every { franchiseRepository.save(capture(saved)) } answers { firstArg<FranchiseEntity>().apply { id = UUID.randomUUID() } }

        service.claim(leagueId, ownerId, claimRequest())
        service.claim(leagueId, ownerId, claimRequest())

        assertEquals(ownerId, saved.captured.ownerUserId)
    }

    // ---------------------------------------------------------------- remove / leave / logo

    @Test
    fun `remove by someone other than the organizer is rejected`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())

        assertFailsWith<NotOrganizerException> {
            service.remove(leagueId, entityId, UUID.randomUUID())
        }
    }

    @Test
    fun `requestLeave by someone other than the franchise's own owner is rejected`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { franchiseRepository.findById(entityId) } returns
            Optional.of(FranchiseEntity(id = entityId, leagueId = leagueId, ownerUserId = ownerId, name = "Chennai Kings"))

        assertFailsWith<NotFranchiseOwnerException> {
            service.requestLeave(leagueId, entityId, UUID.randomUUID())
        }
    }

    @Test
    fun `approveLeave requires a pending leave request`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { franchiseRepository.findById(entityId) } returns
            Optional.of(FranchiseEntity(id = entityId, leagueId = leagueId, ownerUserId = ownerId, name = "Chennai Kings", leaveRequestedAt = null))

        assertFailsWith<NoLeaveRequestPendingException> {
            service.approveLeave(leagueId, entityId, organizerId)
        }
    }

    @Test
    fun `logo upload url is allowed for the organizer or the franchise's own owner, but no one else`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league())
        every { franchiseRepository.findById(entityId) } returns
            Optional.of(FranchiseEntity(id = entityId, leagueId = leagueId, ownerUserId = ownerId, name = "Chennai Kings"))

        assertFailsWith<NotFranchiseOwnerException> {
            service.createLogoUploadUrl(leagueId, entityId, UUID.randomUUID())
        }

        val presigned = mockk<PhotoUploadUrlResponse>()
        every { photoUploadService.createFranchiseLogoUploadUrl(entityId) } returns presigned
        assertEquals(presigned, service.createLogoUploadUrl(leagueId, entityId, ownerId))
    }

    @Test
    fun `operating under a nonexistent league surfaces LeagueNotFoundException`() {
        every { leagueRepository.findById(leagueId) } returns Optional.empty()

        assertFailsWith<LeagueNotFoundException> {
            service.claim(leagueId, ownerId, claimRequest())
        }
    }
}

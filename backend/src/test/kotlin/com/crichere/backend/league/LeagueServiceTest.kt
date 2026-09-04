package com.crichere.backend.league

import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.common.PhotoUploadService
import com.crichere.backend.common.PhotoUploadUrlResponse
import com.crichere.backend.ground.GroundRepository
import com.crichere.backend.league.dto.LeagueSaveRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit-level coverage of [LeagueService]: ownership enforcement on every mutating method,
 * rate-limit rejection, ground-existence checking, status derivation, and full-replace edit
 * semantics. The end-to-end HTTP behaviour (real Postgres, real validation, real auth) is
 * covered separately by `LeagueFlowIntegrationTest`.
 */
class LeagueServiceTest {

    private val leagueRepository = mockk<LeagueRepository>()
    private val groundRepository = mockk<GroundRepository>()
    private val contentRateLimiter = mockk<ContentRateLimiter>()
    private val photoUploadService = mockk<PhotoUploadService>()
    private val service = LeagueService(leagueRepository, groundRepository, contentRateLimiter, photoUploadService)

    private val organizerId: UUID = UUID.randomUUID()
    private val otherUserId: UUID = UUID.randomUUID()
    private val leagueId: UUID = UUID.randomUUID()

    private fun validRequest(groundId: UUID? = null) = LeagueSaveRequest(
        name = "Weekend Box Cricket League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        groundId = groundId,
        startsOn = LocalDate.of(2026, 10, 12),
    )

    private fun existingLeague(organizer: UUID = organizerId, completedAt: java.time.Instant? = null) = LeagueEntity(
        id = leagueId,
        organizerUserId = organizer,
        name = "Existing League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = LocalDate.of(2026, 10, 12),
        completedAt = completedAt,
    )

    // ---------------------------------------------------------------- create

    @Test
    fun `create is rejected when the rate limit is tripped, before touching the repository`() {
        every { contentRateLimiter.tryConsumeForLeagueCreate(organizerId) } returns Duration.ofMinutes(5)

        assertFailsWith<ContentRateLimitExceededException> {
            service.create(organizerId, validRequest())
        }
        verify(exactly = 0) { leagueRepository.save(any()) }
    }

    @Test
    fun `create rejects a groundId that does not exist`() {
        val groundId = UUID.randomUUID()
        every { contentRateLimiter.tryConsumeForLeagueCreate(organizerId) } returns null
        every { groundRepository.existsById(groundId) } returns false

        assertFailsWith<GroundNotFoundException> {
            service.create(organizerId, validRequest(groundId = groundId))
        }
        verify(exactly = 0) { leagueRepository.save(any()) }
    }

    @Test
    fun `create persists with the caller as organizer and ANNOUNCED status`() {
        every { contentRateLimiter.tryConsumeForLeagueCreate(organizerId) } returns null
        val saved = slot<LeagueEntity>()
        every { leagueRepository.save(capture(saved)) } answers { firstArg<LeagueEntity>().apply { id = leagueId } }

        val response = service.create(organizerId, validRequest())

        assertEquals(organizerId, saved.captured.organizerUserId)
        assertEquals("Weekend Box Cricket League", saved.captured.name)
        assertEquals(LeagueStatus.ANNOUNCED, response.status)
        assertNull(saved.captured.completedAt)
    }

    // ---------------------------------------------------------------- ownership

    @Test
    fun `update by someone other than the organizer is rejected`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())

        assertFailsWith<NotOrganizerException> {
            service.update(leagueId, otherUserId, validRequest())
        }
        verify(exactly = 0) { leagueRepository.save(any()) }
    }

    @Test
    fun `complete by someone other than the organizer is rejected`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())

        assertFailsWith<NotOrganizerException> {
            service.complete(leagueId, otherUserId)
        }
    }

    @Test
    fun `logo upload url by someone other than the organizer is rejected before touching the presigner`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())

        assertFailsWith<NotOrganizerException> {
            service.createLogoUploadUrl(leagueId, otherUserId)
        }
        verify(exactly = 0) { photoUploadService.createLeagueLogoUploadUrl(any()) }
    }

    @Test
    fun `banner upload url by the real organizer delegates to the presigner with the league id`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())
        val presigned = mockk<PhotoUploadUrlResponse>()
        every { photoUploadService.createLeagueBannerUploadUrl(leagueId) } returns presigned

        val result = service.createBannerUploadUrl(leagueId, organizerId)

        assertEquals(presigned, result)
    }

    @Test
    fun `operating on a nonexistent league surfaces LeagueNotFoundException`() {
        every { leagueRepository.findById(leagueId) } returns Optional.empty()

        assertFailsWith<LeagueNotFoundException> {
            service.getLeague(leagueId)
        }
    }

    // ---------------------------------------------------------------- edit (full replace) + status

    @Test
    fun `update is a full replace -- an omitted optional field clears the previously-stored value`() {
        val existing = existingLeague().apply { format = "T20"; franchisesRequired = 8 }
        every { leagueRepository.findById(leagueId) } returns Optional.of(existing)
        val saved = slot<LeagueEntity>()
        every { leagueRepository.save(capture(saved)) } answers { firstArg() }

        service.update(leagueId, organizerId, validRequest())

        assertNull(saved.captured.format)
        assertNull(saved.captured.franchisesRequired)
    }

    @Test
    fun `complete sets completedAt and the response reports COMPLETED, without locking further edits`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())
        every { leagueRepository.save(any()) } answers { firstArg() }

        val response = service.complete(leagueId, organizerId)

        assertEquals(LeagueStatus.COMPLETED, response.status)

        // Editing after completion is still allowed -- no freeze (see docs/PHASE2.md's Decisions Made).
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague(completedAt = java.time.Instant.now()))
        val editResponse = service.update(leagueId, organizerId, validRequest())
        assertNotNull(editResponse)
    }
}

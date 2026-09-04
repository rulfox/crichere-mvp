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
    private val leagueAwardRepository = mockk<LeagueAwardRepository>().also {
        // Every toResponse() call loads the awards list -- default to "no awards yet" so tests
        // that don't care about awards specifically don't each need their own stub.
        every { it.findByLeagueIdOrderByDisplayOrder(any()) } returns emptyList()
    }
    private val groundRepository = mockk<GroundRepository>()
    private val contentRateLimiter = mockk<ContentRateLimiter>()
    private val photoUploadService = mockk<PhotoUploadService>()
    private val service = LeagueService(leagueRepository, leagueAwardRepository, groundRepository, contentRateLimiter, photoUploadService)

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

    // ---------------------------------------------------------------- awards

    @Test
    fun `addAward by someone other than the organizer is rejected before touching the rate limiter`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())

        assertFailsWith<NotOrganizerException> {
            service.addAward(leagueId, otherUserId, com.crichere.backend.league.dto.LeagueAwardSaveRequest(name = "First Prize"))
        }
        verify(exactly = 0) { contentRateLimiter.tryConsumeForAwardCreate(any()) }
    }

    @Test
    fun `addAward is rejected when the award rate limit is tripped`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())
        every { contentRateLimiter.tryConsumeForAwardCreate(organizerId) } returns Duration.ofMinutes(1)

        assertFailsWith<ContentRateLimitExceededException> {
            service.addAward(leagueId, organizerId, com.crichere.backend.league.dto.LeagueAwardSaveRequest(name = "First Prize"))
        }
    }

    @Test
    fun `addAward appends at the end of the existing list`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())
        every { contentRateLimiter.tryConsumeForAwardCreate(organizerId) } returns null
        every { leagueAwardRepository.countByLeagueId(leagueId) } returns 2L
        val saved = slot<LeagueAwardEntity>()
        every { leagueAwardRepository.save(capture(saved)) } answers { firstArg<LeagueAwardEntity>().apply { id = UUID.randomUUID() } }

        service.addAward(leagueId, organizerId, com.crichere.backend.league.dto.LeagueAwardSaveRequest(name = "Third Prize"))

        assertEquals(2, saved.captured.displayOrder)
        assertEquals(leagueId, saved.captured.leagueId)
    }

    @Test
    fun `an award id belonging to a different league is treated as not found`() {
        val otherLeagueId = UUID.randomUUID()
        val awardId = UUID.randomUUID()
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())
        every { leagueAwardRepository.findById(awardId) } returns
            Optional.of(LeagueAwardEntity(id = awardId, leagueId = otherLeagueId, name = "Someone else's award"))

        assertFailsWith<LeagueAwardNotFoundException> {
            service.updateAward(leagueId, awardId, organizerId, com.crichere.backend.league.dto.LeagueAwardSaveRequest(name = "Hijacked"))
        }
    }

    @Test
    fun `deleteAward by the real organizer removes the award`() {
        val awardId = UUID.randomUUID()
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())
        every { leagueAwardRepository.findById(awardId) } returns
            Optional.of(LeagueAwardEntity(id = awardId, leagueId = leagueId, name = "First Prize"))
        every { leagueAwardRepository.delete(any()) } returns Unit

        service.deleteAward(leagueId, awardId, organizerId)

        verify(exactly = 1) { leagueAwardRepository.delete(any()) }
    }
}

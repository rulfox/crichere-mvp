package com.crichere.backend.league

import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.common.PhotoUploadService
import com.crichere.backend.common.PhotoUploadUrlResponse
import com.crichere.backend.franchise.FranchiseRepository
import com.crichere.backend.ground.GroundRepository
import com.crichere.backend.league.dto.AuctionSettingsSaveRequest
import com.crichere.backend.league.dto.LeagueSaveRequest
import com.crichere.backend.player.PlayerRepository
import com.crichere.backend.profile.ProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
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
    private val leagueFollowRepository = mockk<LeagueFollowRepository>().also {
        every { it.existsByLeagueIdAndUserId(any(), any()) } returns false
    }
    private val groundRepository = mockk<GroundRepository>()
    private val playerRepository = mockk<PlayerRepository>().also {
        every { it.findByLeagueIdAndRemovedAtIsNull(any()) } returns emptyList()
        every { it.countByLeagueIdAndRemovedAtIsNull(any()) } returns 0L
    }
    private val franchiseRepository = mockk<FranchiseRepository>().also {
        every { it.findByLeagueIdAndRemovedAtIsNull(any()) } returns emptyList()
        every { it.countByLeagueIdAndRemovedAtIsNull(any()) } returns 0L
    }
    private val profileRepository = mockk<ProfileRepository>()
    private val contentRateLimiter = mockk<ContentRateLimiter>()
    private val photoUploadService = mockk<PhotoUploadService>()
    private val leagueRoleRepository = mockk<LeagueRoleRepository>().also {
        // Every toResponse() call loads active co-organizers -- default to "none" so tests that
        // don't care about roles specifically don't each need their own stub.
        every { it.findByLeagueIdAndRevokedAtIsNull(any()) } returns emptyList()
        every { it.existsByLeagueIdAndUserIdAndRevokedAtIsNull(any(), any()) } returns false
    }
    private val leagueAuthorization = LeagueAuthorization(leagueRoleRepository)
    private val service = LeagueService(
        leagueRepository,
        leagueAwardRepository,
        leagueFollowRepository,
        groundRepository,
        playerRepository,
        franchiseRepository,
        profileRepository,
        contentRateLimiter,
        photoUploadService,
        leagueAuthorization,
        leagueRoleRepository,
    )

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
            service.getLeague(leagueId, null)
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

    // ---------------------------------------------------------------- Phase 3: organizerUpiId / capacity / fee-lock

    private fun validRequestWithFee(playerFee: BigDecimal? = null, franchiseFee: BigDecimal? = null, organizerUpiId: String? = null) =
        validRequest().copy(playerFee = playerFee, franchiseFee = franchiseFee, organizerUpiId = organizerUpiId)

    @Test
    fun `create is rejected when a fee is set but organizerUpiId is blank`() {
        every { contentRateLimiter.tryConsumeForLeagueCreate(organizerId) } returns null

        assertFailsWith<OrganizerUpiRequiredException> {
            service.create(organizerId, validRequestWithFee(playerFee = BigDecimal("100")))
        }
        verify(exactly = 0) { leagueRepository.save(any()) }
    }

    @Test
    fun `create succeeds with a fee when organizerUpiId is supplied`() {
        every { contentRateLimiter.tryConsumeForLeagueCreate(organizerId) } returns null
        every { leagueRepository.save(any()) } answers { firstArg<LeagueEntity>().apply { id = leagueId } }

        val response = service.create(organizerId, validRequestWithFee(playerFee = BigDecimal("100"), organizerUpiId = "organizer@upi"))

        assertEquals("organizer@upi", response.organizerUpiId)
    }

    @Test
    fun `update is rejected when it would drop playersRequired below the current active player count`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())
        every { playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 5L

        assertFailsWith<CapacityBelowActiveCountException> {
            service.update(leagueId, organizerId, validRequest().copy(playersRequired = 2))
        }
        verify(exactly = 0) { leagueRepository.save(any()) }
    }

    @Test
    fun `update is rejected when it would change the player fee while active players exist`() {
        val existing = existingLeague().apply { playerFee = BigDecimal("100"); organizerUpiId = "organizer@upi" }
        every { leagueRepository.findById(leagueId) } returns Optional.of(existing)
        every { playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 1L

        assertFailsWith<FeeLockedException> {
            service.update(leagueId, organizerId, validRequestWithFee(playerFee = BigDecimal("200"), organizerUpiId = "organizer@upi"))
        }
    }

    @Test
    fun `update is allowed to raise capacity and keep the same fee while active rows exist`() {
        val existing = existingLeague().apply { playersRequired = 5; playerFee = BigDecimal("100"); organizerUpiId = "organizer@upi" }
        every { leagueRepository.findById(leagueId) } returns Optional.of(existing)
        every { playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId) } returns 5L
        every { leagueRepository.save(any()) } answers { firstArg() }

        val response = service.update(
            leagueId,
            organizerId,
            validRequestWithFee(playerFee = BigDecimal("100"), organizerUpiId = "organizer@upi").copy(playersRequired = 10),
        )

        assertEquals(10, response.playersRequired)
    }

    @Test
    fun `franchise logo upload url is self-scoped -- any authenticated caller can request one for their own upload`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())
        val presigned = mockk<PhotoUploadUrlResponse>()
        every { photoUploadService.createPendingFranchiseLogoUploadUrl(leagueId, otherUserId) } returns presigned

        val result = service.createFranchiseLogoUploadUrl(leagueId, otherUserId)

        assertEquals(presigned, result)
    }

    @Test
    fun `follow is idempotent -- following twice does not throw or double-insert`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())
        every { leagueFollowRepository.existsByLeagueIdAndUserId(leagueId, otherUserId) } returns false andThen true
        every { leagueFollowRepository.save(any()) } answers { firstArg() }

        service.follow(leagueId, otherUserId)
        service.follow(leagueId, otherUserId)

        verify(exactly = 1) { leagueFollowRepository.save(any()) }
    }

    @Test
    fun `unfollow when not following does not throw`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())
        every { leagueFollowRepository.deleteByLeagueIdAndUserId(leagueId, otherUserId) } returns Unit

        service.unfollow(leagueId, otherUserId)

        verify(exactly = 1) { leagueFollowRepository.deleteByLeagueIdAndUserId(leagueId, otherUserId) }
    }

    // ---------------------------------------------------------------- Phase 4: auction settings

    private fun validAuctionSettings(squadMin: Int = 5, squadMax: Int = 15) = AuctionSettingsSaveRequest(
        basePrice = BigDecimal("500"),
        purse = BigDecimal("10000"),
        squadMin = squadMin,
        squadMax = squadMax,
        bidIncrement = BigDecimal("100"),
    )

    @Test
    fun `updateAuctionSettings by someone other than the organizer is rejected`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())

        assertFailsWith<NotOrganizerException> {
            service.updateAuctionSettings(leagueId, otherUserId, validAuctionSettings())
        }
        verify(exactly = 0) { leagueRepository.save(any()) }
    }

    @Test
    fun `updateAuctionSettings rejects squadMin greater than squadMax`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())

        assertFailsWith<SquadSizeInvalidException> {
            service.updateAuctionSettings(leagueId, organizerId, validAuctionSettings(squadMin = 10, squadMax = 5))
        }
        verify(exactly = 0) { leagueRepository.save(any()) }
    }

    @Test
    fun `updateAuctionSettings persists all five fields`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())
        val saved = slot<LeagueEntity>()
        every { leagueRepository.save(capture(saved)) } answers { firstArg() }

        val response = service.updateAuctionSettings(leagueId, organizerId, validAuctionSettings())

        assertEquals(BigDecimal("500"), saved.captured.auctionBasePrice)
        assertEquals(BigDecimal("10000"), saved.captured.auctionPurse)
        assertEquals(5, saved.captured.auctionSquadMin)
        assertEquals(15, saved.captured.auctionSquadMax)
        assertEquals(BigDecimal("100"), saved.captured.auctionBidIncrement)
        assertEquals(BigDecimal("500"), response.auctionBasePrice)
    }

    @Test
    fun `auctionSquadMaxWarning is true when squadMax times franchisesRequired exceeds playersRequired`() {
        val existing = existingLeague().apply {
            franchisesRequired = 10
            playersRequired = 50
        }
        every { leagueRepository.findById(leagueId) } returns Optional.of(existing)
        every { leagueRepository.save(any()) } answers { firstArg() }

        // 6 * 10 = 60 > 50 -- warning expected.
        val response = service.updateAuctionSettings(leagueId, organizerId, validAuctionSettings(squadMin = 1, squadMax = 6))

        assertEquals(true, response.auctionSquadMaxWarning)
    }

    @Test
    fun `auctionSquadMaxWarning is false when squadMax times franchisesRequired fits within playersRequired`() {
        val existing = existingLeague().apply {
            franchisesRequired = 10
            playersRequired = 50
        }
        every { leagueRepository.findById(leagueId) } returns Optional.of(existing)
        every { leagueRepository.save(any()) } answers { firstArg() }

        // 5 * 10 = 50, not greater than 50 -- no warning.
        val response = service.updateAuctionSettings(leagueId, organizerId, validAuctionSettings(squadMin = 1, squadMax = 5))

        assertEquals(false, response.auctionSquadMaxWarning)
    }

    @Test
    fun `auctionSquadMaxWarning is false, not a crash, when franchisesRequired or playersRequired is unset`() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(existingLeague())
        every { leagueRepository.save(any()) } answers { firstArg() }

        val response = service.updateAuctionSettings(leagueId, organizerId, validAuctionSettings(squadMin = 1, squadMax = 100))

        assertEquals(false, response.auctionSquadMaxWarning)
    }
}

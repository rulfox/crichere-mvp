package com.crichere.backend.league

import com.crichere.backend.auth.PhoneCryptoService
import com.crichere.backend.auth.UserEntity
import com.crichere.backend.auth.UserRepository
import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.ground.GroundEntity
import com.crichere.backend.ground.GroundRepository
import com.crichere.backend.notification.FcmSender
import com.crichere.backend.profile.ProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Unit-level coverage of [RoleService]. End-to-end HTTP behaviour is covered by `RoleFlowIntegrationTest`. */
class RoleServiceTest {
    // LeagueService.toResponse resolves the league's ground name (every league has a ground since V21).
    private val groundRepository = mockk<GroundRepository>().also { repo ->
        every { repo.findById(any()) } answers {
            Optional.of(
                GroundEntity(
                    id = firstArg(), name = "Test Ground", state = "Karnataka", district = "Bengaluru Urban",
                    latitude = 12.97, longitude = 77.59, registeredByUserId = UUID.randomUUID(),
                ),
            )
        }
    }


    private val leagueRepository = mockk<LeagueRepository>()
    private val leagueRoleRepository = mockk<LeagueRoleRepository>()
    private val leagueAuthorization = LeagueAuthorization(leagueRoleRepository)
    private val userRepository = mockk<UserRepository>()
    private val profileRepository = mockk<ProfileRepository>()
    private val phoneCryptoService = mockk<PhoneCryptoService>()
    private val contentRateLimiter = mockk<ContentRateLimiter>().also {
        every { it.tryConsumeForRoleLookup(any()) } returns null
    }
    // LeagueService pulled in for real so `grant`/`revoke` exercise its actual toResponse()
    // mapping, not a mock -- these tests care about RoleService's own decisions, and stubbing
    // LeagueService here would just hide a real integration point AuctionServiceTest-style unit
    // tests don't otherwise need to fake.
    private val leagueService = LeagueService(
        leagueRepository,
        mockk<LeagueAwardRepository>().also { every { it.findByLeagueIdOrderByDisplayOrder(any()) } returns emptyList() },
        mockk<com.crichere.backend.league.LeagueFollowRepository>().also { every { it.existsByLeagueIdAndUserId(any(), any()) } returns false },
        groundRepository,
        mockk<com.crichere.backend.player.PlayerRepository>().also {
            every { it.findByLeagueIdAndRemovedAtIsNull(any()) } returns emptyList()
            every { it.countByLeagueIdAndRemovedAtIsNull(any()) } returns 0L
        },
        mockk<com.crichere.backend.franchise.FranchiseRepository>().also {
            every { it.findByLeagueIdAndRemovedAtIsNull(any()) } returns emptyList()
            every { it.countByLeagueIdAndRemovedAtIsNull(any()) } returns 0L
        },
        profileRepository,
        mockk<ContentRateLimiter>(),
        mockk<com.crichere.backend.common.PhotoUploadService>(),
        leagueAuthorization,
        leagueRoleRepository,
        com.crichere.backend.common.PaymentScreenshotUrlSigner { url, _, _ -> url },
    )
    private val fcmSender = mockk<FcmSender>(relaxed = true)
    private val service = RoleService(
        leagueRepository, leagueRoleRepository, leagueAuthorization, leagueService,
        userRepository, profileRepository, phoneCryptoService, contentRateLimiter, fcmSender,
    )

    private val organizerId = UUID.randomUUID()
    private val leagueId = UUID.randomUUID()
    private val league = LeagueEntity(
        id = leagueId, organizerUserId = organizerId, name = "Test League", country = "India",
        state = "Karnataka", district = "Bengaluru Urban", groundId = UUID.randomUUID(), startsOn = LocalDate.of(2026, 10, 12),
    )

    private fun givenLeague() {
        every { leagueRepository.findById(leagueId) } returns Optional.of(league)
    }

    @Test
    fun `lookup rejects a non-organizer before ever touching the rate limiter or the user table`() {
        givenLeague()
        val stranger = UUID.randomUUID()
        every { leagueRoleRepository.existsByLeagueIdAndUserIdAndRevokedAtIsNull(leagueId, stranger) } returns false

        assertFailsWith<NotOrganizerException> { service.lookup(leagueId, stranger, "+919876543210") }
    }

    @Test
    fun `lookup surfaces the rate limiter's rejection`() {
        givenLeague()
        every { contentRateLimiter.tryConsumeForRoleLookup(organizerId) } returns Duration.ofMinutes(5)

        assertFailsWith<ContentRateLimitExceededException> { service.lookup(leagueId, organizerId, "+919876543210") }
    }

    @Test
    fun `lookup 404s when the phone hash matches no user`() {
        givenLeague()
        every { phoneCryptoService.hmacLookupHash("+919876543210") } returns "hash"
        every { userRepository.findByPhoneLookupHash("hash") } returns null

        assertFailsWith<UserNotFoundException> { service.lookup(leagueId, organizerId, "+919876543210") }
    }

    @Test
    fun `a found user's profile name is resolved onto the lookup response`() {
        givenLeague()
        val targetId = UUID.randomUUID()
        every { phoneCryptoService.hmacLookupHash("+919876543210") } returns "hash"
        every { userRepository.findByPhoneLookupHash("hash") } returns UserEntity(id = targetId, phoneLookupHash = "hash", phoneEncrypted = "enc")
        every { profileRepository.findById(targetId) } returns Optional.of(
            com.crichere.backend.profile.ProfileEntity(userId = targetId, name = "Delegate Name"),
        )

        val result = service.lookup(leagueId, organizerId, "+919876543210")

        assertEquals(targetId, result.userId)
        assertEquals("Delegate Name", result.name)
    }

    @Test
    fun `lookup canonicalizes however the number was typed, so all spellings hit the same account`() {
        val targetId = UUID.randomUUID()
        every { phoneCryptoService.hmacLookupHash("+919876543210") } returns "hash"
        every { userRepository.findByPhoneLookupHash("hash") } returns UserEntity(id = targetId, phoneLookupHash = "hash", phoneEncrypted = "enc")
        every { profileRepository.findById(targetId) } returns Optional.empty()

        listOf("9876543210", "98765 43210", "09876543210", "+91 98765 43210", "919876543210").forEach { typed ->
            givenLeague()
            assertEquals(targetId, service.lookup(leagueId, organizerId, typed).userId, "typed as: $typed")
        }
    }

    @Test
    fun `lookup of a number that is not an Indian mobile is a plain not-found and never reaches the hash`() {
        givenLeague()

        listOf("+14155552671", "5876543210", "12345", "abc").forEach { typed ->
            assertFailsWith<UserNotFoundException> { service.lookup(leagueId, organizerId, typed) }
        }
        io.mockk.verify(exactly = 0) { phoneCryptoService.hmacLookupHash(any()) }
    }

    @Test
    fun `grant rejects a target who is already the organizer`() {
        givenLeague()
        assertFailsWith<CannotGrantRoleToOrganizerException> { service.grant(leagueId, organizerId, organizerId) }
    }

    @Test
    fun `grant notifies the target user`() {
        givenLeague()
        val targetId = UUID.randomUUID()
        every { leagueRoleRepository.existsByLeagueIdAndUserIdAndRevokedAtIsNull(leagueId, targetId) } returns false
        every { leagueRoleRepository.save(any()) } answers { firstArg() }
        every { leagueRoleRepository.findByLeagueIdAndRevokedAtIsNull(leagueId) } returns emptyList()

        service.grant(leagueId, organizerId, targetId)

        verify { fcmSender.sendToUser(targetId, "Test League", any(), any()) }
    }

    @Test
    fun `grant rejects a duplicate active grant`() {
        givenLeague()
        val targetId = UUID.randomUUID()
        every { leagueRoleRepository.existsByLeagueIdAndUserIdAndRevokedAtIsNull(leagueId, targetId) } returns true

        assertFailsWith<RoleAlreadyGrantedException> { service.grant(leagueId, organizerId, targetId) }
    }

    @Test
    fun `revoke rejects an id that's already revoked`() {
        givenLeague()
        val roleId = UUID.randomUUID()
        val revokedRole = LeagueRoleEntity(
            id = roleId, leagueId = leagueId, userId = UUID.randomUUID(), role = LeagueRole.CO_ORGANIZER,
            grantedByUserId = organizerId, revokedAt = java.time.Instant.now(),
        )
        every { leagueRoleRepository.findByIdAndLeagueId(roleId, leagueId) } returns Optional.of(revokedRole)

        assertFailsWith<RoleNotFoundException> { service.revoke(leagueId, organizerId, roleId) }
    }

    @Test
    fun `revoke notifies the revoked user when the organizer revokes it`() {
        givenLeague()
        val roleId = UUID.randomUUID()
        val delegateId = UUID.randomUUID()
        val role = LeagueRoleEntity(id = roleId, leagueId = leagueId, userId = delegateId, role = LeagueRole.CO_ORGANIZER, grantedByUserId = organizerId)
        every { leagueRoleRepository.findByIdAndLeagueId(roleId, leagueId) } returns Optional.of(role)
        every { leagueRoleRepository.save(any()) } answers { firstArg() }
        every { leagueRoleRepository.findByLeagueIdAndRevokedAtIsNull(leagueId) } returns emptyList()

        service.revoke(leagueId, organizerId, roleId)

        verify { fcmSender.sendToUser(delegateId, "Test League", any(), any()) }
    }

    @Test
    fun `revoke does not notify anyone on a self-revoke`() {
        givenLeague()
        val roleId = UUID.randomUUID()
        val delegateId = UUID.randomUUID()
        val role = LeagueRoleEntity(id = roleId, leagueId = leagueId, userId = delegateId, role = LeagueRole.CO_ORGANIZER, grantedByUserId = organizerId)
        every { leagueRoleRepository.existsByLeagueIdAndUserIdAndRevokedAtIsNull(leagueId, delegateId) } returns true
        every { leagueRoleRepository.findByIdAndLeagueId(roleId, leagueId) } returns Optional.of(role)
        every { leagueRoleRepository.save(any()) } answers { firstArg() }
        every { leagueRoleRepository.findByLeagueIdAndRevokedAtIsNull(leagueId) } returns emptyList()

        service.revoke(leagueId, delegateId, roleId)

        verify(exactly = 0) { fcmSender.sendToUser(delegateId, any(), any(), any()) }
    }
}

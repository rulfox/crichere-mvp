package com.crichere.backend.profile

import com.crichere.backend.profile.dto.ProfileUpdateRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit-level coverage of [ProfileService]'s own logic: the role/bowling-style cross-field rule,
 * the full-replace upsert semantics, and that completeness is always asked of
 * [ProfileCompletionLookup] rather than re-derived. The end-to-end HTTP behaviour (real
 * Postgres, real validation pipeline, real `ProblemDetail` shapes) is covered separately by
 * `ProfileFlowIntegrationTest`.
 */
class ProfileServiceTest {

    private val profileRepository = mockk<ProfileRepository>()
    private val profileCompletionLookup = mockk<ProfileCompletionLookup>()
    private val service = ProfileService(profileRepository, profileCompletionLookup)

    private val userId: UUID = UUID.randomUUID()

    // ---------------------------------------------------------------- GET

    @Test
    fun `a fresh user with no profile row gets an all-null response, not an error`() {
        every { profileRepository.findById(userId) } returns Optional.empty()
        every { profileCompletionLookup.isComplete(userId) } returns false

        val response = service.getMyProfile(userId)

        assertEquals(userId, response.userId)
        assertNull(response.name)
        assertNull(response.photoUrl)
        assertNull(response.country)
        assertNull(response.state)
        assertNull(response.city)
        assertNull(response.playingRole)
        assertNull(response.battingStyle)
        assertNull(response.bowlingStyle)
        assertFalse(response.profileComplete)
    }

    @Test
    fun `GET asks ProfileCompletionLookup rather than deriving completeness itself`() {
        val profile = ProfileEntity(userId = userId, name = "Rohit")
        every { profileRepository.findById(userId) } returns Optional.of(profile)
        every { profileCompletionLookup.isComplete(userId) } returns true

        val response = service.getMyProfile(userId)

        assertTrue(response.profileComplete)
        verify(exactly = 1) { profileCompletionLookup.isComplete(userId) }
    }

    // ---------------------------------------------------------------- PUT: cross-field validation

    @Test
    fun `a bowler without a bowling style is rejected before touching the repository`() {
        assertFailsWith<BowlingStyleRequiredException> {
            service.upsert(userId, ProfileUpdateRequest(playingRole = PlayingRole.BOWLER, bowlingStyle = null))
        }
        verify(exactly = 0) { profileRepository.save(any()) }
    }

    @Test
    fun `an all-rounder without a bowling style is rejected`() {
        assertFailsWith<BowlingStyleRequiredException> {
            service.upsert(userId, ProfileUpdateRequest(playingRole = PlayingRole.ALL_ROUNDER, bowlingStyle = null))
        }
    }

    @Test
    fun `a batsman with a bowling style is rejected`() {
        assertFailsWith<BowlingStyleNotAllowedException> {
            service.upsert(
                userId,
                ProfileUpdateRequest(
                    playingRole = PlayingRole.BATSMAN,
                    battingStyle = BattingStyle.RIGHT_HAND,
                    bowlingStyle = BowlingStyle.RIGHT_ARM_FAST,
                ),
            )
        }
    }

    @Test
    fun `a bowling style with no role at all in the same request is rejected`() {
        // "must be absent/null otherwise" is read literally: bowlingStyle may only accompany a
        // bowling role in this same request, not be set ahead of choosing a role.
        assertFailsWith<BowlingStyleNotAllowedException> {
            service.upsert(userId, ProfileUpdateRequest(bowlingStyle = BowlingStyle.RIGHT_ARM_FAST))
        }
    }

    @Test
    fun `a bowler with a bowling style is accepted`() {
        every { profileRepository.findById(userId) } returns Optional.empty()
        every { profileRepository.save(any()) } answers { firstArg() }
        every { profileCompletionLookup.isComplete(userId) } returns false

        service.upsert(
            userId,
            ProfileUpdateRequest(playingRole = PlayingRole.BOWLER, bowlingStyle = BowlingStyle.LEFT_ARM_FAST),
        )

        verify(exactly = 1) { profileRepository.save(any()) }
    }

    // ---------------------------------------------------------------- PUT: full-replace semantics

    @Test
    fun `PUT creates a new row for a first-time caller`() {
        every { profileRepository.findById(userId) } returns Optional.empty()
        val saved = slot<ProfileEntity>()
        every { profileRepository.save(capture(saved)) } answers { firstArg() }
        every { profileCompletionLookup.isComplete(userId) } returns false

        service.upsert(userId, ProfileUpdateRequest(name = "Virat Kohli"))

        assertEquals(userId, saved.captured.userId)
        assertEquals("Virat Kohli", saved.captured.name)
    }

    @Test
    fun `PUT is a full replace, not a merge -- an omitted field overwrites the stored value with null`() {
        val existing = ProfileEntity(
            userId = userId,
            name = "Old Name",
            photoUrl = "https://cdn.crichere.app/old.jpg",
            state = "Karnataka",
            city = "Bengaluru",
            playingRole = PlayingRole.BATSMAN,
            battingStyle = BattingStyle.RIGHT_HAND,
        )
        every { profileRepository.findById(userId) } returns Optional.of(existing)
        val saved = slot<ProfileEntity>()
        every { profileRepository.save(capture(saved)) } answers { firstArg() }
        every { profileCompletionLookup.isComplete(userId) } returns false

        // Only `name` is supplied this time; every other field is therefore cleared, not kept.
        service.upsert(userId, ProfileUpdateRequest(name = "New Name"))

        assertEquals("New Name", saved.captured.name)
        assertNull(saved.captured.photoUrl)
        assertNull(saved.captured.state)
        assertNull(saved.captured.city)
        assertNull(saved.captured.playingRole)
        assertNull(saved.captured.battingStyle)
        assertNull(saved.captured.bowlingStyle)
    }

    @Test
    fun `country is untouched by the request DTO, which has no country field`() {
        every { profileRepository.findById(userId) } returns Optional.empty()
        val saved = slot<ProfileEntity>()
        every { profileRepository.save(capture(saved)) } answers { firstArg() }
        every { profileCompletionLookup.isComplete(userId) } returns false

        service.upsert(userId, ProfileUpdateRequest(name = "Test"))

        assertEquals("IN", saved.captured.country)
    }
}

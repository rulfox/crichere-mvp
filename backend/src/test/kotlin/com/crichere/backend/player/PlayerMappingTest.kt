package com.crichere.backend.player

import com.crichere.backend.common.PaymentScreenshotUrlSigner
import com.crichere.backend.profile.PlayingRole
import com.crichere.backend.profile.ProfileEntity
import com.crichere.backend.profile.ProfileRepository
import io.mockk.every
import io.mockk.mockk
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [PlayerEntity.toResponse]: roster rows carry the player's name and playing role from their profile. */
class PlayerMappingTest {

    private val organizerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val leagueId = UUID.randomUUID()
    private val player = PlayerEntity(id = UUID.randomUUID(), leagueId = leagueId, userId = userId, paymentScreenshotUrl = "https://stored/proof.jpg")
    private val signer = PaymentScreenshotUrlSigner { url, league, payer -> "signed:$url:$league:$payer" }
    private val noProfiles = mockk<ProfileRepository> { every { findById(any()) } returns Optional.empty() }

    @Test
    fun `includes the player's name and playing role from their profile`() {
        val profiles = mockk<ProfileRepository> {
            every { findById(userId) } returns Optional.of(ProfileEntity(userId = userId, name = "Rahul Salunkhe", playingRole = PlayingRole.ALL_ROUNDER))
        }

        val response = player.toResponse(callerId = null, organizerUserId = organizerId, profileRepository = profiles, screenshotSigner = signer)

        assertEquals("Rahul Salunkhe", response.name)
        assertEquals(PlayingRole.ALL_ROUNDER, response.playingRole)
    }

    @Test
    fun `role is null when the player has no profile yet`() {
        val profiles = mockk<ProfileRepository> { every { findById(userId) } returns Optional.empty() }

        val response = player.toResponse(callerId = null, organizerUserId = organizerId, profileRepository = profiles, screenshotSigner = signer)

        assertNull(response.playingRole)
        assertNull(response.name)
    }

    @Test
    fun `the row's own user gets the screenshot as a signed link bound to this league and payer`() {
        val response = player.toResponse(callerId = userId, organizerUserId = organizerId, profileRepository = noProfiles, screenshotSigner = signer)

        assertEquals("signed:https://stored/proof.jpg:$leagueId:$userId", response.paymentScreenshotUrl)
    }

    @Test
    fun `the organizer gets the screenshot as a signed link`() {
        val response = player.toResponse(callerId = organizerId, organizerUserId = organizerId, profileRepository = noProfiles, screenshotSigner = signer)

        assertEquals("signed:https://stored/proof.jpg:$leagueId:$userId", response.paymentScreenshotUrl)
    }

    @Test
    fun `anyone else gets no screenshot and nothing is signed for them`() {
        val strict = PaymentScreenshotUrlSigner { _, _, _ -> error("must not sign for a caller who can't see the row") }

        val response = player.toResponse(callerId = UUID.randomUUID(), organizerUserId = organizerId, profileRepository = noProfiles, screenshotSigner = strict)

        assertNull(response.paymentScreenshotUrl)
    }
}

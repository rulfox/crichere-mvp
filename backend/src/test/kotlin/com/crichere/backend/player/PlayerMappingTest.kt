package com.crichere.backend.player

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
    private val player = PlayerEntity(id = UUID.randomUUID(), leagueId = UUID.randomUUID(), userId = userId)

    @Test
    fun `includes the player's name and playing role from their profile`() {
        val profiles = mockk<ProfileRepository> {
            every { findById(userId) } returns Optional.of(ProfileEntity(userId = userId, name = "Rahul Salunkhe", playingRole = PlayingRole.ALL_ROUNDER))
        }

        val response = player.toResponse(callerId = null, organizerUserId = organizerId, profileRepository = profiles)

        assertEquals("Rahul Salunkhe", response.name)
        assertEquals(PlayingRole.ALL_ROUNDER, response.playingRole)
    }

    @Test
    fun `role is null when the player has no profile yet`() {
        val profiles = mockk<ProfileRepository> { every { findById(userId) } returns Optional.empty() }

        val response = player.toResponse(callerId = null, organizerUserId = organizerId, profileRepository = profiles)

        assertNull(response.playingRole)
        assertNull(response.name)
    }
}

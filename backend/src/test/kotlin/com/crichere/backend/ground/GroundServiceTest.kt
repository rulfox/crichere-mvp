package com.crichere.backend.ground

import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.ground.dto.GroundCreateRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit-level coverage of [GroundService]: search delegates straight through to the repository,
 * create sets the caller as registrant and enforces the per-user creation rate limit. The
 * end-to-end HTTP behaviour (real Postgres, real validation pipeline, real auth) is covered
 * separately by `GroundFlowIntegrationTest`.
 */
class GroundServiceTest {

    private val groundRepository = mockk<GroundRepository>()
    private val contentRateLimiter = mockk<ContentRateLimiter>()
    private val service = GroundService(groundRepository, contentRateLimiter)

    private val userId: UUID = UUID.randomUUID()

    private fun validRequest() = GroundCreateRequest(
        name = "MRF Ground",
        state = "Tamil Nadu",
        district = "Chennai",
        latitude = 13.0827,
        longitude = 80.2707,
    )

    @Test
    fun `search builds a lowercased wildcard pattern and delegates the area filters straight through`() {
        every { groundRepository.searchByPattern("%mrf%", "Tamil Nadu", "Chennai") } returns emptyList()

        service.search("MRF", "Tamil Nadu", "Chennai")

        verify(exactly = 1) { groundRepository.searchByPattern("%mrf%", "Tamil Nadu", "Chennai") }
    }

    @Test
    fun `a null search becomes a match-everything wildcard, not a null parameter`() {
        every { groundRepository.searchByPattern("%%", null, null) } returns emptyList()

        service.search(null, null, null)

        verify(exactly = 1) { groundRepository.searchByPattern("%%", null, null) }
    }

    @Test
    fun `create is rejected when the rate limit is tripped, before touching the repository`() {
        every { contentRateLimiter.tryConsumeForGroundCreate(userId) } returns Duration.ofMinutes(5)

        assertFailsWith<ContentRateLimitExceededException> {
            service.create(userId, validRequest())
        }
        verify(exactly = 0) { groundRepository.save(any()) }
    }

    @Test
    fun `create sets the caller as registrant and persists`() {
        every { contentRateLimiter.tryConsumeForGroundCreate(userId) } returns null
        val saved = slot<GroundEntity>()
        every { groundRepository.save(capture(saved)) } answers { firstArg<GroundEntity>().apply { id = UUID.randomUUID() } }

        val response = service.create(userId, validRequest())

        assertEquals(userId, saved.captured.registeredByUserId)
        assertEquals("MRF Ground", saved.captured.name)
        assertEquals(saved.captured.id, response.id)
        assertEquals("Chennai", response.district)
        assertEquals(13.0827, response.latitude)
        assertEquals(80.2707, response.longitude)
    }
}

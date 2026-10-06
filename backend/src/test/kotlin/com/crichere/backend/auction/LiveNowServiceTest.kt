package com.crichere.backend.auction

import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveNowServiceTest {

    private val leagueRepository = mockk<LeagueRepository>()
    private var now: Instant = Instant.parse("2026-10-02T12:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private val service = LiveNowService(leagueRepository, clock)

    private fun league(id: UUID = UUID.randomUUID()) = LeagueEntity(
        id = id,
        organizerUserId = UUID.randomUUID(),
        name = "Spartanz Premier League",
        state = "Kerala",
        district = "Alappuzha",
        groundId = UUID.randomUUID(),
        startsOn = LocalDate.of(2026, 10, 16),
    )

    @Test
    fun `returns the live league's id and name`() {
        val id = UUID.randomUUID()
        every { leagueRepository.findLiveNow() } returns league(id)

        val result = service.liveNow()

        assertEquals(id, result?.leagueId)
        assertEquals("Spartanz Premier League", result?.leagueName)
    }

    @Test
    fun `returns null when nothing is live`() {
        every { leagueRepository.findLiveNow() } returns null

        assertNull(service.liveNow())
    }

    @Test
    fun `memoizes the answer, including null, until the TTL passes`() {
        every { leagueRepository.findLiveNow() } returns null
        service.liveNow()
        now = now.plus(LiveNowService.TTL).minus(Duration.ofMillis(1))
        service.liveNow()
        verify(exactly = 1) { leagueRepository.findLiveNow() }

        now = now.plus(Duration.ofMillis(1))
        service.liveNow()
        verify(exactly = 2) { leagueRepository.findLiveNow() }
    }
}

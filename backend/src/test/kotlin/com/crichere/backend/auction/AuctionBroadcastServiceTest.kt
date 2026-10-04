package com.crichere.backend.auction

import com.crichere.backend.auction.dto.AuctionStateResponse
import com.crichere.backend.league.AuctionStatus
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuctionBroadcastServiceTest {

    /** Records each frame the service sends instead of writing to a connection. */
    private class CapturingEmitter : SseEmitter(0L) {
        val frames = mutableListOf<String>()

        override fun send(builder: SseEventBuilder) {
            frames += builder.build().joinToString("") { it.data.toString() }
        }
    }

    private val emitter = CapturingEmitter()
    private val service = object : AuctionBroadcastService() {
        override fun createEmitter(): SseEmitter = emitter
    }

    private val leagueId = UUID.randomUUID()

    private fun state(bid: Double?) = AuctionStateResponse(
        auctionStatus = AuctionStatus.IN_PROGRESS,
        currentPlayerId = null,
        currentPlayerName = null,
        currentBidAmount = bid?.toBigDecimal(),
        currentLeadingFranchiseId = null,
        currentLeadingFranchiseName = null,
        allowExceedPurse = false,
        recentBids = emptyList(),
    )

    private val auctionStateFrames get() = emitter.frames.filter { it.contains("event:auction-state") }

    @Test
    fun `a new subscriber gets the current state straight away`() {
        service.subscribe(leagueId, state(null))
        assertEquals(1, auctionStateFrames.size)
    }

    @Test
    fun `the heartbeat re-sends the latest broadcast state as a real auction-state event`() {
        service.subscribe(leagueId, state(null))
        service.broadcast(leagueId, state(250.0))
        emitter.frames.clear()

        service.heartbeat()

        assertEquals(1, auctionStateFrames.size)
        assertTrue(auctionStateFrames.single().contains("250"), "heartbeat should carry the newest state, got ${emitter.frames}")
        assertTrue(emitter.frames.none { it.contains("keep-alive") })
    }

    @Test
    fun `the heartbeat does nothing for a league without subscribers`() {
        service.heartbeat()
        assertTrue(emitter.frames.isEmpty())
    }
}

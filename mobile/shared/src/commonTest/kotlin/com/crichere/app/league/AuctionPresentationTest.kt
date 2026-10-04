package com.crichere.app.league

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuctionPresentationTest {

    @Test
    fun `over purse is how far below zero the purse went, never a negative number`() {
        assertEquals(250.0, overPurseAmount(-250.0))
        assertNull(overPurseAmount(0.0))
        assertNull(overPurseAmount(500.0))
        assertNull(overPurseAmount(null))
    }

    @Test
    fun `dead end body follows the franchise breakdown`() {
        assertEquals(DeadEndKind.ALL_FULL, deadEndKind(squadsFull = 6, purseBelowBase = 0))
        assertEquals(DeadEndKind.ALL_PURSE, deadEndKind(squadsFull = 0, purseBelowBase = 6))
        assertEquals(DeadEndKind.MIXED, deadEndKind(squadsFull = 4, purseBelowBase = 2))
    }

    @Test
    fun `end auction body - plural, singular, none, and the dead-end wording`() {
        assertEquals(
            "28 players are still in the pool. They'll all be marked unsold and squads become final. This can't be undone.",
            endAuctionBody(28, deadEnd = false).text,
        )
        assertEquals("28 players", endAuctionBody(28, deadEnd = false).emphasis)
        assertEquals(
            "1 player is still in the pool. They'll be marked unsold and squads become final. This can't be undone.",
            endAuctionBody(1, deadEnd = false).text,
        )
        assertEquals("Every player has been auctioned. Squads become final.", endAuctionBody(0, deadEnd = false).text)
        assertEquals(
            "No franchise can buy the remaining 28 players, so they'll be marked unsold. Squads become final.",
            endAuctionBody(28, deadEnd = true).text,
        )
    }
}

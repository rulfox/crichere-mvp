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
}

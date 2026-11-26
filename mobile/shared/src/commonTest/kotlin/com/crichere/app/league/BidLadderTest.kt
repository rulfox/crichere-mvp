package com.crichere.app.league

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Same table as the backend's `BidLadderTest` (docs/PHASE22.md) -- the two copies must agree. */
class BidLadderTest {
    private val tiered = BidLadder(250, listOf(BidRangeDto(1000.0, 250.0), BidRangeDto(3000.0, 500.0), BidRangeDto(15000.0, 1000.0)), 2500)
    private val flat = BidLadder(100, emptyList(), 50)

    @Test
    fun `minimum is the base price before any bid, then the next step across every boundary`() {
        assertEquals(250L, tiered.minimumNext(null))
        val cases = mapOf(250L to 500L, 750L to 1000L, 1000L to 1500L, 2500L to 3000L, 3000L to 4000L, 14000L to 15000L, 15000L to 17500L, 17500L to 20000L)
        cases.forEach { (current, expected) -> assertEquals(expected, tiered.minimumNext(current.toDouble()), "after $current") }
    }

    @Test
    fun `an off-ladder current bid snaps back onto the ladder`() {
        assertEquals(1500L, tiered.minimumNext(1100.0))
        assertEquals(15000L, tiered.minimumNext(14999.0))
        assertEquals(17500L, tiered.minimumNext(16000.0))
    }

    @Test
    fun `no ranges is the old flat increment`() {
        assertEquals(150L, flat.minimumNext(100.0))
        assertTrue(flat.isOnLadder(10000.0))
        assertFalse(flat.isOnLadder(120.0))
        assertFalse(flat.hasRanges)
    }

    @Test
    fun `ladder membership and the step at every boundary`() {
        listOf(250, 500, 1000, 1500, 3000, 4000, 15000, 17500).forEach { assertTrue(tiered.isOnLadder(it.toDouble()), "$it") }
        listOf(200, 300, 1250, 3500, 15500).forEach { assertFalse(tiered.isOnLadder(it.toDouble()), "$it") }
        assertFalse(tiered.isOnLadder(500.5))
        assertEquals(250L, tiered.stepAt(750))
        assertEquals(500L, tiered.stepAt(1000))
        assertEquals(2500L, tiered.stepAt(15000))
    }

    @Test
    fun `neighbours give the valid bids either side, never below the minimum`() {
        assertEquals(listOf(2000L, 2500L), tiered.neighbours(2200.0, minimum = 2000))
        assertEquals(listOf(2500L), tiered.neighbours(2200.0, minimum = 2500))
    }

    @Test
    fun `compact amounts for the step labels`() {
        assertEquals(listOf("250", "1K", "2.5K", "15K", "1L", "1.5L"), listOf(250L, 1000L, 2500L, 15000L, 100000L, 150000L).map { shortRupees(it) })
    }
}

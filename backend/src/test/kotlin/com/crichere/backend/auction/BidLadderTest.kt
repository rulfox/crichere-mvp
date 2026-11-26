package com.crichere.backend.auction

import com.crichere.backend.league.BidRange
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The worked example from docs/PHASE22.md: base 250; +250 to 1,000; +500 to 3,000; +1,000 to 15,000; +2,500 above. */
class BidLadderTest {
    private fun bd(value: Long) = BigDecimal.valueOf(value)

    private val tiered = BidLadder(
        basePrice = bd(250),
        ranges = listOf(BidRange(bd(1000), bd(250)), BidRange(bd(3000), bd(500)), BidRange(bd(15000), bd(1000))),
        aboveStep = bd(2500),
    )
    private val flat = BidLadder(basePrice = bd(100), ranges = emptyList(), aboveStep = bd(50))

    @Test
    fun `minimum is the base price before any bid`() {
        assertEquals(bd(250), tiered.minimumNext(null))
        assertEquals(bd(100), flat.minimumNext(null))
    }

    @Test
    fun `minimum is the next ladder value above the current bid, across every boundary`() {
        val cases = mapOf(250L to 500L, 750L to 1000L, 1000L to 1500L, 2500L to 3000L, 3000L to 4000L, 14000L to 15000L, 15000L to 17500L, 17500L to 20000L)
        cases.forEach { (current, expected) -> assertEquals(bd(expected), tiered.minimumNext(bd(current)), "after $current") }
    }

    @Test
    fun `an off-ladder current bid snaps back onto the ladder`() {
        assertEquals(bd(1500), tiered.minimumNext(bd(1100)))
        assertEquals(bd(1000), tiered.minimumNext(bd(900)))
        assertEquals(bd(15000), tiered.minimumNext(bd(14999)))
        assertEquals(bd(17500), tiered.minimumNext(bd(16000)))
    }

    @Test
    fun `no ranges is the old flat increment`() {
        assertEquals(bd(150), flat.minimumNext(bd(100)))
        assertEquals(bd(10000), flat.minimumNext(bd(9950)))
        assertTrue(flat.isOnLadder(bd(10000)))
        assertFalse(flat.isOnLadder(bd(120)))
    }

    @Test
    fun `ladder membership at and around every boundary`() {
        listOf(250L, 500L, 1000L, 1500L, 3000L, 4000L, 15000L, 17500L, 20000L).forEach { assertTrue(tiered.isOnLadder(bd(it)), "$it") }
        listOf(200L, 300L, 1250L, 3500L, 15500L, 16000L).forEach { assertFalse(tiered.isOnLadder(bd(it)), "$it") }
    }

    @Test
    fun `the step follows the range, which starts at its lower number`() {
        assertEquals(bd(250), tiered.stepAt(bd(750)))
        assertEquals(bd(500), tiered.stepAt(bd(1000)))
        assertEquals(bd(1000), tiered.stepAt(bd(3000)))
        assertEquals(bd(2500), tiered.stepAt(bd(15000)))
    }

    @Test
    fun `neighbours give the valid bids either side, never below the minimum`() {
        assertEquals(bd(2000) to bd(2500), tiered.neighbours(bd(2200), minimum = bd(2000)))
        assertEquals(bd(15000) to bd(17500), tiered.neighbours(bd(16000), minimum = bd(4000)))
        val (lower, upper) = tiered.neighbours(bd(2200), minimum = bd(2500))
        assertNull(lower)
        assertEquals(bd(2500), upper)
    }
}

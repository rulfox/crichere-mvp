package com.crichere.backend.auction

import com.crichere.backend.league.BidRange
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The bid ladder (docs/PHASE22.md): the only amounts a bid may be. It starts at [basePrice]; each
 * [ranges] entry covers `[previous upTo (or basePrice), upTo)` with its own step, and [aboveStep] (the
 * league's `auction_bid_increment`) covers everything from the last `upTo` (or [basePrice]) upwards.
 * With no ranges this is today's flat ladder: base, base + step, base + 2·step, …
 *
 * Settings validation ([BidRangesValidator]) guarantees every range divides evenly by its step, so each
 * range boundary is itself a ladder value -- the reason [minimumNext] can work segment by segment.
 * The shared mobile module carries the same algorithm (`BidLadder.kt` there) so the phone can show the
 * minimum without a round trip; keep the two in step.
 */
class BidLadder(
    private val basePrice: BigDecimal,
    ranges: List<BidRange>,
    private val aboveStep: BigDecimal,
) {
    /** One stretch of the ladder with a single step; [to] is `null` for the open-ended last one. */
    data class Segment(val from: BigDecimal, val to: BigDecimal?, val step: BigDecimal)

    val segments: List<Segment> = buildList {
        var from = basePrice
        for (range in ranges) {
            add(Segment(from, range.upTo, range.step))
            from = range.upTo
        }
        add(Segment(from, null, aboveStep))
    }

    private fun segmentAt(amount: BigDecimal): Segment =
        segments.firstOrNull { amount >= it.from && (it.to == null || amount < it.to) } ?: segments.first()

    /** The step that applies at [amount] (a range starts at its lower number). */
    fun stepAt(amount: BigDecimal): BigDecimal = segmentAt(amount).step

    fun isOnLadder(amount: BigDecimal): Boolean {
        if (amount < basePrice) return false
        val segment = segmentAt(amount)
        return (amount - segment.from).remainder(segment.step).signum() == 0
    }

    /** The smallest ladder value strictly above [amount]; [basePrice] when [amount] is below it. */
    fun nextAbove(amount: BigDecimal): BigDecimal {
        if (amount < basePrice) return basePrice
        val segment = segmentAt(amount)
        val stepsIn = (amount - segment.from).divide(segment.step, 0, RoundingMode.FLOOR)
        val next = segment.from + (stepsIn + BigDecimal.ONE) * segment.step
        return if (segment.to != null && next > segment.to) segment.to else next
    }

    /** The lowest bid allowed now: [basePrice] before any bid, else the next ladder value above [currentBid]. */
    fun minimumNext(currentBid: BigDecimal?): BigDecimal = currentBid?.let(::nextAbove) ?: basePrice

    /**
     * The two ladder values either side of an off-ladder [amount], for the "Try X or Y" message: the
     * largest one below it (never under [minimum]; `null` if none) and the smallest one above it.
     */
    fun neighbours(amount: BigDecimal, minimum: BigDecimal): Pair<BigDecimal?, BigDecimal> {
        val upper = nextAbove(amount)
        if (amount < basePrice) return null to upper
        val segment = segmentAt(amount)
        val below = segment.from + (amount - segment.from).divide(segment.step, 0, RoundingMode.FLOOR) * segment.step
        val lower = below.takeIf { it < amount && it >= minimum }
        return lower to upper
    }
}

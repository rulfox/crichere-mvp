package com.crichere.app.league

import kotlin.math.roundToLong

/**
 * The bid ladder (docs/PHASE22.md) -- the phone's copy of the backend's `auction/BidLadder.kt`, so the
 * minimum next bid and the "+" step show instantly, without a round trip. Keep the two in step; the
 * server re-checks every bid either way.
 *
 * Bids start at [basePrice]; each of [ranges] covers `[previous upTo (or basePrice), upTo)` with its own
 * step, and [aboveStep] (the league's bid increment) covers everything from the last `upTo` upwards. No
 * ranges = the old flat increment. Amounts are whole rupees (settings validation guarantees it).
 */
class BidLadder(
    val basePrice: Long,
    ranges: List<BidRangeDto>,
    val aboveStep: Long,
) {
    /** One stretch with a single step; [to] is `null` for the open-ended last one. */
    data class Segment(val from: Long, val to: Long?, val step: Long)

    val segments: List<Segment> = buildList {
        var from = basePrice
        for (range in ranges) {
            val upTo = range.upTo.roundToLong()
            add(Segment(from, upTo, range.step.roundToLong()))
            from = upTo
        }
        add(Segment(from, null, aboveStep))
    }

    /** `true` when the organizer set ranges -- the live screen only shows its step labels then. */
    val hasRanges: Boolean get() = segments.size > 1

    fun segmentAt(amount: Long): Segment =
        segments.firstOrNull { amount >= it.from && (it.to == null || amount < it.to) } ?: segments.first()

    /** The step that applies at [amount] (a range starts at its lower number). */
    fun stepAt(amount: Long): Long = segmentAt(amount).step

    fun isOnLadder(amount: Double): Boolean {
        if (amount % 1.0 != 0.0) return false
        val whole = amount.toLong()
        if (whole < basePrice) return false
        val segment = segmentAt(whole)
        return (whole - segment.from) % segment.step == 0L
    }

    /** The smallest ladder value strictly above [amount]; [basePrice] when [amount] is below it. */
    fun nextAbove(amount: Double): Long {
        if (amount < basePrice) return basePrice
        val whole = floorLong(amount)
        val segment = segmentAt(whole)
        val next = segment.from + ((whole - segment.from) / segment.step + 1) * segment.step
        return if (segment.to != null && next > segment.to) segment.to else next
    }

    /** The lowest bid allowed now: [basePrice] before any bid, else the next ladder value above [currentBid]. */
    fun minimumNext(currentBid: Double?): Long = currentBid?.let(::nextAbove) ?: basePrice

    /** The valid bids either side of an off-ladder [amount] -- the one below only if it is at least [minimum]. */
    fun neighbours(amount: Double, minimum: Long): List<Long> {
        val upper = nextAbove(amount)
        if (amount < basePrice) return listOf(upper)
        val whole = floorLong(amount)
        val segment = segmentAt(whole)
        val below = segment.from + (whole - segment.from) / segment.step * segment.step
        return listOfNotNull(below.takeIf { it < amount && it >= minimum }, upper)
    }

    private fun floorLong(amount: Double): Long = kotlin.math.floor(amount).toLong()

    /**
     * Design S1's "Bids will go" preview: one line per segment, its step and the bids it adds
     * (`250 · 500 · 750 · 1,000`, long runs shortened to `4,000 · 5,000 · 6,000 … 15,000`, the open
     * last one to its first three and `…`).
     */
    fun previewLines(): List<LadderPreviewLine> = segments.mapIndexed { index, segment ->
        val first = if (index == 0) segment.from else segment.from + segment.step
        val values = if (segment.to == null) {
            List(3) { first + it * segment.step }.joinToString(" · ") { groupedRupees(it) } + " …"
        } else {
            val all = generateSequence(first) { it + segment.step }.takeWhile { it <= segment.to }.toList()
            if (all.size <= 4) all.joinToString(" · ") { groupedRupees(it) }
            else all.take(3).joinToString(" · ") { groupedRupees(it) } + " … " + groupedRupees(all.last())
        }
        LadderPreviewLine(step = "+" + groupedRupees(segment.step), values = values)
    }

    /**
     * Design L1's step labels (`+250 to 1K`, `+2.5K above`), with the one holding [currentBid] (or the
     * base price before any bid) marked [StepLabel.isCurrent].
     */
    fun stepLabels(currentBid: Double?): List<StepLabel> {
        val current = segmentAt(floorLong(currentBid ?: basePrice.toDouble()))
        return segments.map { segment ->
            val text = "+" + shortRupees(segment.step) + (segment.to?.let { " to " + shortRupees(it) } ?: " above")
            StepLabel(text, isCurrent = segment == current)
        }
    }

    companion object {
        /** The league's ladder, or `null` until its base price and bid increment are set. */
        fun of(league: LeagueDto?): BidLadder? {
            val base = league?.auctionBasePrice ?: return null
            val step = league.auctionBidIncrement ?: return null
            if (step <= 0.0) return null
            return BidLadder(base.roundToLong(), league.auctionBidRanges, step.roundToLong())
        }
    }
}

data class LadderPreviewLine(val step: String, val values: String)

data class StepLabel(val text: String, val isCurrent: Boolean)

/** `15000` -> `15,000` (Indian grouping, no ₹). */
internal fun groupedRupees(amount: Long): String = groupIndianAmount(amount.toString()).text

/** `15000` -> `15K`, `2500` -> `2.5K`, `250` -> `250`, `150000` -> `1.5L` -- for the compact step labels. */
internal fun shortRupees(amount: Long): String = when {
    amount >= 100_000 && amount % 1_000 == 0L -> trimDecimal(amount / 100_000.0) + "L"
    amount >= 1_000 && amount % 100 == 0L -> trimDecimal(amount / 1_000.0) + "K"
    else -> groupedRupees(amount)
}

private fun trimDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else ((value * 10).roundToLong() / 10.0).toString()

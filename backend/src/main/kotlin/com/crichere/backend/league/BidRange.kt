package com.crichere.backend.league

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.math.BigDecimal

/**
 * One bid-step range (docs/PHASE22.md): bids from where the previous range ended (the base price for
 * the first) up to, not including, [upTo] go up by [step]. Only the end is stored -- the start always
 * follows from the range before it, so gaps and overlaps can't be saved.
 */
data class BidRange(val upTo: BigDecimal, val step: BigDecimal)

/**
 * Stores [LeagueEntity.auctionBidRanges] in the `auction_bid_ranges` TEXT column as `upTo:step`
 * pairs joined by `,` (e.g. `1000:250,3000:500`) -- a handful of whole numbers, so no JSON mapper is
 * needed. Empty string = no ranges.
 */
@Converter
class BidRangesConverter : AttributeConverter<List<BidRange>, String> {
    override fun convertToDatabaseColumn(attribute: List<BidRange>?): String =
        attribute.orEmpty().joinToString(",") { "${it.upTo.toPlainString()}:${it.step.toPlainString()}" }

    override fun convertToEntityAttribute(dbData: String?): List<BidRange> =
        dbData.orEmpty().split(',').filter { it.isNotBlank() }.map { pair ->
            val (upTo, step) = pair.split(':')
            BidRange(BigDecimal(upTo.trim()), BigDecimal(step.trim()))
        }
}

/** Why a bid-step save was rejected; [rangeIndex] is the 0-based range, or `null` for the "and above" step. */
class BidRangesInvalidException(val rangeIndex: Int?, val reason: String) : RuntimeException(reason)

/**
 * The bid-step rules (docs/PHASE22.md), checked on every auction-settings save. The mobile settings
 * screen runs the same checks first so the organizer sees them per row; this is the backstop.
 */
object BidRangesValidator {
    const val MAX_RANGES = 10

    private fun BigDecimal.isWhole() = signum() == 0 || stripTrailingZeros().scale() <= 0

    private fun rupees(value: BigDecimal) = "₹" + value.stripTrailingZeros().toPlainString()

    /** @throws BidRangesInvalidException the first rule broken. */
    fun validate(basePrice: BigDecimal, ranges: List<BidRange>, aboveStep: BigDecimal) {
        if (!basePrice.isWhole()) throw BidRangesInvalidException(null, "Base price must be in whole rupees.")
        if (ranges.size > MAX_RANGES) throw BidRangesInvalidException(MAX_RANGES, "At most $MAX_RANGES ranges.")
        var from = basePrice
        var previousStep: BigDecimal? = null
        ranges.forEachIndexed { index, range ->
            if (!range.upTo.isWhole() || !range.step.isWhole()) throw BidRangesInvalidException(index, "Use whole rupees.")
            if (range.step.signum() <= 0) throw BidRangesInvalidException(index, "Step must be more than 0.")
            if (range.upTo <= from) throw BidRangesInvalidException(index, "Must be more than ${rupees(from)}.")
            if ((range.upTo - from).remainder(range.step).signum() != 0) {
                throw BidRangesInvalidException(index, "${rupees(from)} to ${rupees(range.upTo)} doesn't split into steps of ${rupees(range.step)}.")
            }
            previousStep?.let { if (range.step <= it) throw BidRangesInvalidException(index, "Must be bigger than the step above (${rupees(it)}).") }
            previousStep = range.step
            from = range.upTo
        }
        if (!aboveStep.isWhole()) throw BidRangesInvalidException(null, "Use whole rupees.")
        previousStep?.let { if (aboveStep <= it) throw BidRangesInvalidException(null, "Must be bigger than the step above (${rupees(it)}).") }
    }
}

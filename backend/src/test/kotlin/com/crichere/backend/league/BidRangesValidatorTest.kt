package com.crichere.backend.league

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BidRangesValidatorTest {
    private fun bd(value: String) = BigDecimal(value)
    private fun range(upTo: String, step: String) = BidRange(bd(upTo), bd(step))
    private val example = listOf(range("1000", "250"), range("3000", "500"), range("15000", "1000"))

    private fun fails(base: String, ranges: List<BidRange>, above: String) =
        assertFailsWith<BidRangesInvalidException> { BidRangesValidator.validate(bd(base), ranges, bd(above)) }

    @Test
    fun `the worked example and no ranges at all are both valid`() {
        assertDoesNotThrow { BidRangesValidator.validate(bd("250"), example, bd("2500")) }
        assertDoesNotThrow { BidRangesValidator.validate(bd("250"), emptyList(), bd("250")) }
        assertDoesNotThrow { BidRangesValidator.validate(bd("250.00"), example, bd("2500.00")) }
    }

    @Test
    fun `a range must end above where it starts`() {
        val error = fails("250", listOf(range("1000", "250"), range("3000", "500"), range("2500", "1000")), "2500")
        assertEquals(2, error.rangeIndex)
        assertEquals("Must be more than ₹3000.", error.reason)
        assertEquals(0, fails("250", listOf(range("250", "250")), "500").rangeIndex)
    }

    @Test
    fun `a range must split evenly into its step`() {
        val error = fails("250", listOf(range("1000", "250"), range("3000", "300")), "2500")
        assertEquals(1, error.rangeIndex)
        assertEquals(0, fails("300", listOf(range("1000", "250")), "500").rangeIndex)
    }

    @Test
    fun `each step must be bigger than the one before, the above step included`() {
        assertEquals(1, fails("250", listOf(range("1000", "250"), range("3000", "250")), "2500").rangeIndex)
        assertEquals(null, fails("250", example, "1000").rangeIndex)
    }

    @Test
    fun `whole rupees only, and at most ten ranges`() {
        assertEquals(0, fails("250", listOf(range("1000.5", "250")), "500").rangeIndex)
        assertEquals(null, fails("250.5", emptyList(), "250").rangeIndex)
        assertEquals(null, fails("250", emptyList(), "12.5").rangeIndex)
        val eleven = (1..11).map { range((250 + it * 1000).toString(), (it * 10 + 990).toString()) }
        assertEquals(BidRangesValidator.MAX_RANGES, fails("250", eleven, "100000").rangeIndex)
    }

    @Test
    fun `ranges round-trip through the column`() {
        val converter = BidRangesConverter()
        assertEquals("1000:250,3000:500,15000:1000", converter.convertToDatabaseColumn(example))
        assertEquals(example, converter.convertToEntityAttribute("1000:250,3000:500,15000:1000"))
        assertEquals(emptyList(), converter.convertToEntityAttribute(""))
        assertEquals("", converter.convertToDatabaseColumn(emptyList()))
    }
}

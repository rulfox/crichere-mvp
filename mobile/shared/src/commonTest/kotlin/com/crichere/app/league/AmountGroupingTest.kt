package com.crichere.app.league

import kotlin.test.Test
import kotlin.test.assertEquals

class AmountGroupingTest {

    @Test
    fun `groups digits the Indian way`() {
        assertEquals("", groupIndianAmount("").text)
        assertEquals("5", groupIndianAmount("5").text)
        assertEquals("500", groupIndianAmount("500").text)
        assertEquals("1,500", groupIndianAmount("1500").text)
        assertEquals("15,500", groupIndianAmount("15500").text)
        assertEquals("1,55,000", groupIndianAmount("155000").text)
        assertEquals("12,50,000", groupIndianAmount("1250000").text)
        assertEquals("1,23,45,678", groupIndianAmount("12345678").text)
    }

    @Test
    fun `only the part before the decimal point is grouped`() {
        assertEquals("15,500.5", groupIndianAmount("15500.5").text)
        assertEquals("15,500.", groupIndianAmount("15500.").text)
        assertEquals("100.12345", groupIndianAmount("100.12345").text)
    }

    @Test
    fun `caret maps forward past inserted commas and back again`() {
        val grouped = groupIndianAmount("15500") // drawn as "15,500"
        assertEquals(0, grouped.toTransformed(0))
        assertEquals(1, grouped.toTransformed(1))
        assertEquals(3, grouped.toTransformed(2)) // a caret between the 2nd and 3rd digit sits after the comma
        assertEquals(4, grouped.toTransformed(3))
        assertEquals(6, grouped.toTransformed(5))

        assertEquals(0, grouped.toOriginal(0))
        assertEquals(2, grouped.toOriginal(2)) // before the comma
        assertEquals(2, grouped.toOriginal(3)) // just after the comma: still 2 typed characters behind it
        assertEquals(5, grouped.toOriginal(6))
    }

    @Test
    fun `every typed caret position round-trips`() {
        for (raw in listOf("", "7", "1500", "15500", "1250000", "15500.25")) {
            val grouped = groupIndianAmount(raw)
            for (original in 0..raw.length) {
                assertEquals(original, grouped.toOriginal(grouped.toTransformed(original)), "raw=$raw caret=$original")
            }
        }
    }
}

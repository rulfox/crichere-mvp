package com.crichere.app.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneNumberInputTest {

    private val india = Countries.India

    @Test
    fun `sanitize keeps only digits`() {
        assertEquals("9876543210", PhoneNumberInput.sanitize(india, "98765 43210"))
        assertEquals("9876543210", PhoneNumberInput.sanitize(india, "98765-43210"))
        assertEquals("9876543210", PhoneNumberInput.sanitize(india, "(98765) 43210"))
        assertEquals("", PhoneNumberInput.sanitize(india, "abc"))
    }

    @Test
    fun `a pasted international or trunk-prefixed number becomes the national number`() {
        assertEquals("9876543210", PhoneNumberInput.sanitize(india, "+919876543210"))
        assertEquals("9876543210", PhoneNumberInput.sanitize(india, "+91 98765 43210"))
        assertEquals("9876543210", PhoneNumberInput.sanitize(india, "919876543210"))
        assertEquals("9876543210", PhoneNumberInput.sanitize(india, "09876543210"))
    }

    @Test
    fun `a genuine national number that starts with the dial digits is not mangled`() {
        assertEquals("9123456789", PhoneNumberInput.sanitize(india, "9123456789"))
        assertEquals("9198765432", PhoneNumberInput.sanitize(india, "9198765432"))
    }

    @Test
    fun `input never grows past the national length`() {
        assertEquals("9876543210", PhoneNumberInput.sanitize(india, "98765432109999"))
    }

    @Test
    fun `typing digit by digit builds the number up unchanged`() {
        var field = ""
        "9876543210".forEach { digit ->
            field = PhoneNumberInput.sanitize(india, field + digit)
        }
        assertEquals("9876543210", field)
    }

    @Test
    fun `toE164 adds the country code to a valid number`() {
        assertEquals("+919876543210", PhoneNumberInput.toE164(india, "9876543210"))
        assertEquals("+916000000000", PhoneNumberInput.toE164(india, "6000000000"))
    }

    @Test
    fun `toE164 rejects anything that is not an Indian mobile number`() {
        assertNull(PhoneNumberInput.toE164(india, "5876543210"), "mobiles start 6-9")
        assertNull(PhoneNumberInput.toE164(india, "987654321"), "too short")
        assertNull(PhoneNumberInput.toE164(india, ""))
        assertNull(PhoneNumberInput.toE164(india, "98765abcde"))
    }

    @Test
    fun `displayNational groups a local number and leaves a foreign one with its code`() {
        assertEquals("98765 43210", PhoneNumberInput.displayNational("+919876543210"))
        assertEquals("+14155552671", PhoneNumberInput.displayNational("+14155552671"))
        assertEquals("not a number", PhoneNumberInput.displayNational("not a number"))
    }

    @Test
    fun `a second country plugs in without changing the helper`() {
        val us = Country(isoCode = "US", dialCode = "+1", nationalLength = 10, nationalPattern = "^[2-9][0-9]{9}$")

        assertEquals("4155552671", PhoneNumberInput.sanitize(us, "+1 (415) 555-2671"))
        assertEquals("+14155552671", PhoneNumberInput.toE164(us, "4155552671"))
        assertEquals("4155552671", PhoneNumberInput.sanitize(us, "415 555 2671"))
    }
}

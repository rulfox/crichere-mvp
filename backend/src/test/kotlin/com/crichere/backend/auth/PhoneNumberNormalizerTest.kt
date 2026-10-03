package com.crichere.backend.auth

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneNumberNormalizerTest {

    @Test
    fun `every accepted spelling normalises to the same E164 string`() {
        listOf(
            "+919876543210",
            "919876543210",
            "09876543210",
            "9876543210",
            "+91 98765 43210",
            "+91-98765-43210",
            "(098) 765-43210",
        ).forEach { input ->
            assertEquals("+919876543210", PhoneNumberNormalizer.toIndianE164(input), "input: $input")
        }
    }

    @Test
    fun `non-Indian country codes are rejected`() {
        assertNull(PhoneNumberNormalizer.toIndianE164("+14155552671"))
        assertNull(PhoneNumberNormalizer.toIndianE164("+447911123456"))
        assertNull(PhoneNumberNormalizer.toIndianE164("+8613812345678"))
    }

    @Test
    fun `numbers that are not mobiles are rejected`() {
        assertNull(PhoneNumberNormalizer.toIndianE164("+915876543210"), "mobiles start 6-9")
        assertNull(PhoneNumberNormalizer.toIndianE164("+91987654321"), "too short")
        assertNull(PhoneNumberNormalizer.toIndianE164("+9198765432100"), "too long")
        assertNull(PhoneNumberNormalizer.toIndianE164("+91abcdefghij"))
        assertNull(PhoneNumberNormalizer.toIndianE164(""))
        assertNull(PhoneNumberNormalizer.toIndianE164("+91"))
    }

    @Test
    fun `output matches the form Firebase hands back so account hashes agree across providers`() {
        val crypto = PhoneCryptoService(PhoneCryptoProperties(secret = "dGVzdC1vbmx5LXBob25lLWNyeXB0by1zZWNyZXQtISE="))
        val viaOtp = PhoneNumberNormalizer.toIndianE164("98765 43210")!!
        // Firebase's phone_number claim for the same person.
        val viaFirebase = "+919876543210"
        assertEquals(crypto.hmacLookupHash(viaFirebase), crypto.hmacLookupHash(viaOtp))
    }

    @Test
    fun `provider identifier drops the plus`() {
        assertEquals("919876543210", PhoneNumberNormalizer.toProviderIdentifier("+919876543210"))
    }
}

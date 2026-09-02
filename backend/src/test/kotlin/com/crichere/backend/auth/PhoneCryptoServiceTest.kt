package com.crichere.backend.auth

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.crypto.AEADBadTagException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PhoneCryptoServiceTest {

    private val service = serviceWith(SECRET_A)

    // ---------------------------------------------------------------- lookup hash

    @Test
    fun `the same phone number always hashes to the same value`() {
        assertEquals(service.hmacLookupHash(PHONE), service.hmacLookupHash(PHONE))
    }

    @Test
    fun `the hash is stable across service instances sharing a secret`() {
        // This is the property that makes the hash usable as a database lookup key: a restart,
        // or a second instance, must resolve an existing account rather than create a new one.
        assertEquals(service.hmacLookupHash(PHONE), serviceWith(SECRET_A).hmacLookupHash(PHONE))
    }

    @Test
    fun `different phone numbers hash to different values`() {
        assertNotEquals(service.hmacLookupHash(PHONE), service.hmacLookupHash("+919876543211"))
    }

    @Test
    fun `a different secret produces a different hash for the same number`() {
        // Confirms the hash is actually keyed. If it were a bare SHA-256 this would fail, and
        // an attacker with the database could brute-force the number space offline.
        assertNotEquals(service.hmacLookupHash(PHONE), serviceWith(SECRET_B).hmacLookupHash(PHONE))
    }

    @Test
    fun `formatting differences resolve to the same account`() {
        val canonical = service.hmacLookupHash("+919876543210")
        assertEquals(canonical, service.hmacLookupHash("+91 98765 43210"))
        assertEquals(canonical, service.hmacLookupHash(" +91-98765-43210 "))
    }

    @Test
    fun `the hash is hex-encoded SHA-256 width`() {
        val hash = service.hmacLookupHash(PHONE)
        assertEquals(64, hash.length, "HMAC-SHA256 is 32 bytes, so 64 hex characters")
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun `a phone number with no digits is rejected`() {
        assertThrows<IllegalArgumentException> { service.hmacLookupHash("not-a-number") }
    }

    // ---------------------------------------------------------------- encryption

    @Test
    fun `encrypt then decrypt recovers the original number`() {
        assertEquals(PHONE, service.decrypt(service.encrypt(PHONE)))
    }

    @Test
    fun `encrypting the same number twice produces different ciphertext`() {
        val first = service.encrypt(PHONE)
        val second = service.encrypt(PHONE)

        assertFalse(
            first.contentEquals(second),
            "a fresh random IV per call is what stops two rows holding the same number from " +
                "being visibly identical",
        )
        // The IV is the 12-byte prefix; it is what must differ.
        assertFalse(first.take(12) == second.take(12))
        // ...and both still decrypt.
        assertEquals(PHONE, service.decrypt(first))
        assertEquals(PHONE, service.decrypt(second))
    }

    @Test
    fun `tampering with the ciphertext is detected`() {
        val sealed = service.encrypt(PHONE)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()

        // GCM authenticates, so a modified value fails loudly instead of decrypting to garbage.
        assertThrows<AEADBadTagException> { service.decrypt(sealed) }
    }

    @Test
    fun `tampering with the IV is detected`() {
        val sealed = service.encrypt(PHONE)
        sealed[0] = (sealed[0].toInt() xor 0x01).toByte()

        assertThrows<AEADBadTagException> { service.decrypt(sealed) }
    }

    @Test
    fun `ciphertext from one secret cannot be decrypted with another`() {
        val sealed = service.encrypt(PHONE)
        assertThrows<AEADBadTagException> { serviceWith(SECRET_B).decrypt(sealed) }
    }

    @Test
    fun `a truncated payload is rejected rather than misread`() {
        assertThrows<IllegalArgumentException> { service.decrypt(ByteArray(5)) }
    }

    @Test
    fun `the base64 string form round-trips for the TEXT column`() {
        val encoded = service.encryptToString(PHONE)
        assertEquals(PHONE, service.decryptFromString(encoded))
    }

    // ---------------------------------------------------------------- key material

    @Test
    fun `a secret shorter than 32 bytes is refused at construction`() {
        // Fail fast and loudly rather than run with a weak key.
        val failure = assertThrows<IllegalArgumentException> { serviceWith("too-short") }
        assertFalse(
            failure.message.orEmpty().contains("too-short"),
            "the error must not echo the secret back",
        )
    }

    @Test
    fun `the lookup key and the encryption key are independent`() {
        // Both sub-keys come from one master secret, so the meaningful assertion is that
        // knowledge of one operation's output tells you nothing about the other's. The
        // observable proxy: the HMAC of a phone number never appears inside its ciphertext.
        val hashBytes = service.hmacLookupHash(PHONE)
        val sealed = service.encrypt(PHONE).joinToString("") { "%02x".format(it) }
        assertFalse(sealed.contains(hashBytes))
    }

    private fun serviceWith(secret: String) = PhoneCryptoService(PhoneCryptoProperties(secret = secret))

    private companion object {
        const val PHONE = "+919876543210"

        // 32+ bytes of raw text; PhoneCryptoService accepts either raw or base64 input.
        const val SECRET_A = "unit-test-phone-crypto-secret-aaaaaa"
        const val SECRET_B = "unit-test-phone-crypto-secret-bbbbbb"
    }
}

package com.crichere.backend.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.annotation.Profile

class LocalFakeOtpSenderTest {

    private val sender = LocalFakeOtpSender("+917293318484, +919999999999")

    @Test
    fun `allowlisted number gets a request id and the fixed code verifies`() {
        val id = sender.send("+917293318484")
        assertEquals(OtpCheckResult.VALID, sender.verify(id, LocalFakeOtpSender.FIXED_CODE))
    }

    @Test
    fun `wrong code is invalid, not an outage`() {
        val id = sender.send("+919999999999")
        assertEquals(OtpCheckResult.INVALID, sender.verify(id, "000000"))
    }

    @Test
    fun `number outside the allowlist cannot start a challenge`() {
        assertThrows<OtpUnavailableException> { sender.send("+918888888888") }
    }

    @Test
    fun `unknown request id never verifies and cannot be resent`() {
        assertEquals(OtpCheckResult.INVALID, sender.verify("local-nope", LocalFakeOtpSender.FIXED_CODE))
        assertThrows<OtpUnavailableException> { sender.resend("local-nope") }
    }

    @Test
    fun `bean is restricted to the local profile`() {
        val profile = LocalFakeOtpSender::class.java.getAnnotation(Profile::class.java)
        assertTrue(profile.value.contentEquals(arrayOf("local")))
    }
}

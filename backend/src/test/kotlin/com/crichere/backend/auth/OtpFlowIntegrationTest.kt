package com.crichere.backend.auth

import com.crichere.backend.common.AbstractWebIntegrationTest
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The backend-driven OTP flow over real HTTP against a real Postgres (V19 migration, JPA, the
 * security filter chain, the atomic attempt/consume queries). Only [OtpSender] is mocked, so
 * MSG91 is never called.
 *
 * Cooldown is 0 here so consecutive sends are possible inside one test; the cooldown's own
 * behaviour is covered in [OtpAuthServiceTest].
 *
 * NOTE: needs Docker (Testcontainers). Not run in the session that wrote it -- no Docker
 * daemon was available -- so treat as unexecuted until it has been run (docs/PHASE12.md).
 */
@TestPropertySource(
    properties = [
        "crichere.otp.provider=msg91",
        "crichere.otp.resend-cooldown=0s",
    ],
)
class OtpFlowIntegrationTest : AbstractWebIntegrationTest() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var challengeRepository: OtpChallengeRepository

    @Autowired
    private lateinit var phoneCryptoService: PhoneCryptoService

    private lateinit var nationalNumber: String
    private lateinit var e164: String

    @BeforeEach
    fun freshNumber() {
        // 9 + nine random digits: always a valid Indian mobile, unique per test.
        nationalNumber = "9" + (100_000_000..999_999_999).random()
        e164 = "+91$nationalNumber"
        every { otpSender.send(any()) } returns "req-${UUID.randomUUID()}"
        every { otpSender.resend(any()) } returns Unit
    }

    @Test
    fun `config advertises the active provider`() {
        mockMvc.perform(get("/api/v1/auth/config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.otpProvider").value("msg91"))
    }

    @Test
    fun `send then verify creates the account and a session`() {
        every { otpSender.verify(any(), "123456") } returns OtpCheckResult.VALID

        val challenge = send(nationalNumber).andExpect(status().isOk).body()
        val challengeId = challenge["challengeId"] as String
        assertNotNull(challenge["expiresAt"])
        verify { otpSender.send(e164) }

        val session = verify(challengeId, "123456")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn().response.contentAsString

        assertNotNull(session)
        // Same account key as the Firebase path would have produced for this number.
        assertNotNull(userRepository.findByPhoneLookupHash(phoneCryptoService.hmacLookupHash(e164)))
    }

    @Test
    fun `a challenge cannot be redeemed twice`() {
        every { otpSender.verify(any(), "123456") } returns OtpCheckResult.VALID
        val challengeId = send(nationalNumber).andExpect(status().isOk).body()["challengeId"] as String

        verify(challengeId, "123456").andExpect(status().isOk)
        verify(challengeId, "123456").andExpect(status().isGone).andExpect(jsonPath("$.code").value("OTP_EXPIRED"))
    }

    @Test
    fun `five wrong codes burn the challenge, and even the right code is refused afterwards`() {
        every { otpSender.verify(any(), "000000") } returns OtpCheckResult.INVALID
        every { otpSender.verify(any(), "123456") } returns OtpCheckResult.VALID
        val challengeId = send(nationalNumber).andExpect(status().isOk).body()["challengeId"] as String

        (4 downTo 0).forEach { remaining ->
            verify(challengeId, "000000")
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_OTP"))
                .andExpect(jsonPath("$.attemptsRemaining").value(remaining))
        }

        verify(challengeId, "123456").andExpect(status().isGone)
        // The provider was asked exactly five times -- never for the sixth guess.
        verify(exactly = 5) { otpSender.verify(any(), any()) }
        assertNull(userRepository.findByPhoneLookupHash(phoneCryptoService.hmacLookupHash(e164)))
    }

    @Test
    fun `attempts are spent atomically in the database`() {
        val challengeId = send(nationalNumber).andExpect(status().isOk).body()["challengeId"] as String
        val id = UUID.fromString(challengeId)
        val now = Instant.now()

        val grants = (1..8).map { challengeRepository.spendAttempt(id, now, 5) }

        assertEquals(listOf(1, 1, 1, 1, 1, 0, 0, 0), grants)
    }

    @Test
    fun `resend is capped at three`() {
        val challengeId = send(nationalNumber).andExpect(status().isOk).body()["challengeId"] as String

        repeat(3) { resend(challengeId).andExpect(status().isOk) }
        resend(challengeId).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("OTP_RESEND_LIMIT"))
        verify(exactly = 3) { otpSender.resend(any()) }
    }

    @Test
    fun `non-Indian numbers are rejected before any SMS`() {
        send("+14155552671").andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_PHONE_NUMBER"))
        verify(exactly = 0) { otpSender.send(any()) }
    }

    @Test
    fun `a provider outage is a generic 503 that reveals nothing`() {
        every { otpSender.send(any()) } throws OtpUnavailableException("MSG91 said insufficient balance")

        val body = send(nationalNumber).andExpect(status().isServiceUnavailable).andReturn().response.contentAsString

        assertEquals(false, body.contains("MSG91"), "provider detail must not reach the client")
        assertEquals(false, body.contains("balance"))
    }

    @Test
    fun `an unknown challenge id is the same 410 as an expired one`() {
        verify(UUID.randomUUID().toString(), "123456").andExpect(status().isGone)
    }

    @Test
    fun `verify has no phone field and ignores one if supplied`() {
        every { otpSender.verify(any(), "123456") } returns OtpCheckResult.VALID
        val challengeId = send(nationalNumber).andExpect(status().isOk).body()["challengeId"] as String

        // A different number smuggled into the body must not change who gets signed in.
        postJson(
            "/api/v1/auth/otp/verify",
            mapOf("challengeId" to challengeId, "code" to "123456", "phoneNumber" to "+919000000000"),
        ).andExpect(status().isOk)

        assertNotNull(userRepository.findByPhoneLookupHash(phoneCryptoService.hmacLookupHash(e164)))
        assertNull(userRepository.findByPhoneLookupHash(phoneCryptoService.hmacLookupHash("+919000000000")))
    }

    @Test
    fun `the per-IP send limit trips with a Retry-After`() {
        every { otpSender.send(any()) } returns "req"
        var tripped = false
        // 20 sends per IP per hour by default; vary the number so only the IP bucket can trip.
        for (i in 1..25) {
            val result = send("9" + (100_000_000 + i)).andReturn().response
            if (result.status == 429) {
                tripped = true
                assertNotNull(result.getHeader("Retry-After"))
                break
            }
        }
        assertEquals(true, tripped)
    }

    // ---------------------------------------------------------------- helpers

    private fun send(phone: String) = postJson("/api/v1/auth/otp/send", mapOf("phoneNumber" to phone))

    private fun resend(challengeId: String) = postJson("/api/v1/auth/otp/resend", mapOf("challengeId" to challengeId))

    private fun verify(challengeId: String, code: String) =
        postJson("/api/v1/auth/otp/verify", mapOf("challengeId" to challengeId, "code" to code))

    private fun postJson(path: String, body: Map<String, Any?>): ResultActions =
        mockMvc.perform(
            post(path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)),
        )

    @Suppress("UNCHECKED_CAST")
    private fun ResultActions.body(): Map<String, Any?> =
        objectMapper.readValue(andReturn().response.contentAsString, Map::class.java) as Map<String, Any?>
}

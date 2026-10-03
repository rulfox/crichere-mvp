package com.crichere.backend.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the backend-driven OTP flow. The provider ([OtpSender]) and the persistence
 * layer are mocked; the crypto service and rate limiter are real so that the limits asserted
 * here are the limits that ship.
 *
 * The atomic SQL guards in [OtpChallengeRepository] are modelled by stubbing their return
 * values (0 = guard failed); their real behaviour is covered by [OtpFlowIntegrationTest].
 */
class OtpAuthServiceTest {

    private val now: Instant = Instant.parse("2026-01-01T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val otpSender = mockk<OtpSender>()
    private val authService = mockk<AuthService>()
    private val challengeRepository = mockk<OtpChallengeRepository>()
    private val phoneCryptoService = PhoneCryptoService(PhoneCryptoProperties(secret = PHONE_SECRET))

    private lateinit var rateLimiter: AuthRateLimiter
    private lateinit var service: OtpAuthService

    private val challengeId = UUID.randomUUID()
    private val expectedAuthResult = AuthResult(
        userId = UUID.randomUUID(),
        accessToken = "access",
        accessTokenExpiresAt = now.plusSeconds(900),
        refreshToken = "refresh",
        profileComplete = false,
    )

    @BeforeEach
    fun setUp() {
        build(RateLimitProperties(), OtpProperties(provider = OtpProviderType.MSG91))
        every { challengeRepository.deleteExpiredBefore(any()) } returns 0
        every { challengeRepository.save(any()) } answers { firstArg() }
        every { challengeRepository.consume(any(), any()) } returns 1
    }

    private fun build(rate: RateLimitProperties, otp: OtpProperties) {
        rateLimiter = AuthRateLimiter(rate, clock)
        service = OtpAuthService(
            otpSender = otpSender,
            authService = authService,
            phoneCryptoService = phoneCryptoService,
            challengeRepository = challengeRepository,
            rateLimiter = rateLimiter,
            properties = otp,
            clock = clock,
        )
    }

    // ---------------------------------------------------------------- send

    @Test
    fun `send normalises the number, calls the provider and stores a challenge for that number`() {
        givenNoPreviousChallenge()
        every { otpSender.send("+919876543210") } returns "req-1"
        val saved = slot<OtpChallengeEntity>()
        every { challengeRepository.save(capture(saved)) } answers { firstArg() }

        val ticket = service.requestOtp("98765 43210")

        assertEquals(saved.captured.id, ticket.challengeId)
        assertEquals("req-1", saved.captured.providerReqId)
        assertEquals(phoneCryptoService.hmacLookupHash(PHONE), saved.captured.phoneLookupHash)
        assertEquals(PHONE, phoneCryptoService.decryptFromString(saved.captured.phoneEncrypted))
        assertEquals(now.plus(Duration.ofMinutes(5)), ticket.expiresAt)
        assertEquals(now.plusSeconds(60), ticket.resendAvailableAt)
    }

    @Test
    fun `send rejects non-Indian numbers before any provider call`() {
        assertThrows<InvalidPhoneNumberException> { service.requestOtp("+14155552671") }
        assertThrows<InvalidPhoneNumberException> { service.requestOtp("not a number") }
        verify(exactly = 0) { otpSender.send(any()) }
    }

    @Test
    fun `send is a 404-equivalent when MSG91 is not the active provider`() {
        build(RateLimitProperties(), OtpProperties(provider = OtpProviderType.FIREBASE))
        assertThrows<OtpDisabledException> { service.requestOtp(PHONE) }
        verify(exactly = 0) { otpSender.send(any()) }
    }

    @Test
    fun `a second send inside the cooldown is refused and spends no budget`() {
        every { challengeRepository.findFirstByPhoneLookupHashAndConsumedAtIsNullOrderByCreatedAtDesc(any()) } returns
            challenge(lastSentAt = now.minusSeconds(10))

        val e = assertThrows<RateLimitExceededException> { service.requestOtp(PHONE) }

        assertEquals(Duration.ofSeconds(50), e.retryAfter)
        verify(exactly = 0) { otpSender.send(any()) }
    }

    @Test
    fun `a send after the cooldown supersedes the old challenge`() {
        val old = challenge(lastSentAt = now.minusSeconds(61))
        every { challengeRepository.findFirstByPhoneLookupHashAndConsumedAtIsNullOrderByCreatedAtDesc(any()) } returns old
        every { otpSender.send(any()) } returns "req-2"

        service.requestOtp(PHONE)

        verify { challengeRepository.consume(old.id, now) }
    }

    @Test
    fun `a failed provider send leaves the previous challenge alive and stores nothing`() {
        val old = challenge(lastSentAt = now.minusSeconds(61))
        every { challengeRepository.findFirstByPhoneLookupHashAndConsumedAtIsNullOrderByCreatedAtDesc(any()) } returns old
        every { otpSender.send(any()) } throws OtpUnavailableException()

        assertThrows<OtpUnavailableException> { service.requestOtp(PHONE) }

        verify(exactly = 0) { challengeRepository.consume(any(), any()) }
        verify(exactly = 0) { challengeRepository.save(any()) }
    }

    @Test
    fun `per-phone send budget caps repeated sends`() {
        build(RateLimitProperties(otpSendPhoneCapacity = 2), OtpProperties(provider = OtpProviderType.MSG91, resendCooldown = Duration.ZERO))
        givenNoPreviousChallenge()
        every { otpSender.send(any()) } returns "req"

        service.requestOtp(PHONE)
        service.requestOtp(PHONE)
        assertThrows<RateLimitExceededException> { service.requestOtp(PHONE) }
        verify(exactly = 2) { otpSender.send(any()) }
    }

    @Test
    fun `the global daily cap stops sends across different phones`() {
        build(RateLimitProperties(otpGlobalDailyCap = 2), OtpProperties(provider = OtpProviderType.MSG91))
        givenNoPreviousChallenge()
        every { otpSender.send(any()) } returns "req"

        service.requestOtp("9876543210")
        service.requestOtp("9876543211")
        assertThrows<OtpUnavailableException> { service.requestOtp("9876543212") }
        verify(exactly = 2) { otpSender.send(any()) }
    }

    // ---------------------------------------------------------------- resend

    @Test
    fun `resend calls the provider with the stored request id and records it`() {
        givenChallenge(challenge(lastSentAt = now.minusSeconds(61), resends = 1))
        every { otpSender.resend("req-1") } returns Unit
        every { challengeRepository.recordResend(challengeId, now, any(), 3) } returns 1

        service.resendOtp(challengeId)

        verify { otpSender.resend("req-1") }
    }

    @Test
    fun `resend inside the cooldown is refused`() {
        givenChallenge(challenge(lastSentAt = now.minusSeconds(5)))
        assertThrows<RateLimitExceededException> { service.resendOtp(challengeId) }
        verify(exactly = 0) { otpSender.resend(any()) }
    }

    @Test
    fun `the fourth resend is refused and nothing is sent`() {
        givenChallenge(challenge(lastSentAt = now.minusSeconds(61), resends = 3))
        assertThrows<OtpResendLimitReachedException> { service.resendOtp(challengeId) }
        verify(exactly = 0) { otpSender.resend(any()) }
    }

    @Test
    fun `resend on an unknown, spent or expired challenge is one generic failure`() {
        every { challengeRepository.findById(challengeId) } returns Optional.empty()
        assertThrows<OtpChallengeExpiredException> { service.resendOtp(challengeId) }

        givenChallenge(challenge(consumedAt = now.minusSeconds(1)))
        assertThrows<OtpChallengeExpiredException> { service.resendOtp(challengeId) }

        givenChallenge(challenge(expiresAt = now.minusSeconds(1)))
        assertThrows<OtpChallengeExpiredException> { service.resendOtp(challengeId) }
    }

    // ---------------------------------------------------------------- verify

    @Test
    fun `a correct code signs in the phone stored on the challenge`() {
        givenChallenge(challenge())
        every { challengeRepository.spendAttempt(challengeId, now, 5) } returns 1
        every { otpSender.verify("req-1", "123456") } returns OtpCheckResult.VALID
        every { authService.signInVerifiedPhone(any(), any()) } returns expectedAuthResult

        val result = service.verifyOtp(challengeId, "123456")

        assertEquals(expectedAuthResult, result)
        // The phone comes from the challenge row -- the verify call carried none.
        verify { authService.signInVerifiedPhone(phoneCryptoService.hmacLookupHash(PHONE), PHONE) }
        verify { challengeRepository.consume(challengeId, now) }
    }

    @Test
    fun `a wrong code reports the attempts remaining and signs nobody in`() {
        givenChallenge(challenge())
        every { challengeRepository.spendAttempt(challengeId, now, 5) } returns 1
        every { otpSender.verify(any(), any()) } returns OtpCheckResult.INVALID
        // After this attempt the row says 2 used.
        every { challengeRepository.findById(challengeId) } returnsMany
            listOf(Optional.of(challenge()), Optional.of(challenge(verifyAttempts = 2)))

        val e = assertThrows<OtpInvalidCodeException> { service.verifyOtp(challengeId, "000000") }

        assertEquals(3, e.attemptsRemaining)
        verify(exactly = 0) { authService.signInVerifiedPhone(any(), any()) }
        verify(exactly = 0) { challengeRepository.consume(any(), any()) }
    }

    @Test
    fun `the fifth wrong code invalidates the challenge`() {
        every { challengeRepository.findById(challengeId) } returnsMany
            listOf(Optional.of(challenge(verifyAttempts = 4)), Optional.of(challenge(verifyAttempts = 5)))
        every { challengeRepository.spendAttempt(challengeId, now, 5) } returns 1
        every { otpSender.verify(any(), any()) } returns OtpCheckResult.INVALID

        val e = assertThrows<OtpInvalidCodeException> { service.verifyOtp(challengeId, "000000") }

        assertEquals(0, e.attemptsRemaining)
        verify { challengeRepository.consume(challengeId, now) }
    }

    @Test
    fun `once attempts are spent the provider is never asked again`() {
        givenChallenge(challenge(verifyAttempts = 5))
        every { challengeRepository.spendAttempt(challengeId, now, 5) } returns 0

        assertThrows<OtpChallengeExpiredException> { service.verifyOtp(challengeId, "123456") }

        verify(exactly = 0) { otpSender.verify(any(), any()) }
    }

    @Test
    fun `losing the single-use race yields no session`() {
        givenChallenge(challenge())
        every { challengeRepository.spendAttempt(challengeId, now, 5) } returns 1
        every { otpSender.verify(any(), any()) } returns OtpCheckResult.VALID
        every { challengeRepository.consume(challengeId, now) } returns 0

        assertThrows<OtpChallengeExpiredException> { service.verifyOtp(challengeId, "123456") }

        verify(exactly = 0) { authService.signInVerifiedPhone(any(), any()) }
    }

    @Test
    fun `a provider outage during verify surfaces as unavailable, not as a wrong code`() {
        givenChallenge(challenge())
        every { challengeRepository.spendAttempt(challengeId, now, 5) } returns 1
        every { otpSender.verify(any(), any()) } throws OtpUnavailableException()

        assertThrows<OtpUnavailableException> { service.verifyOtp(challengeId, "123456") }
    }

    @Test
    fun `an unknown challenge id is indistinguishable from an expired one`() {
        every { challengeRepository.findById(challengeId) } returns Optional.empty()
        assertThrows<OtpChallengeExpiredException> { service.verifyOtp(challengeId, "123456") }
    }

    @Test
    fun `the global send cap is per UTC day`() {
        val limiter = AuthRateLimiter(RateLimitProperties(otpGlobalDailyCap = 1), clock)
        assertTrue(limiter.tryConsumeGlobalOtpSend())
        assertTrue(!limiter.tryConsumeGlobalOtpSend())

        val tomorrow = Clock.fixed(now.plus(Duration.ofDays(1)), ZoneOffset.UTC)
        val nextDay = AuthRateLimiter(RateLimitProperties(otpGlobalDailyCap = 1), tomorrow)
        assertTrue(nextDay.tryConsumeGlobalOtpSend())
    }

    // ---------------------------------------------------------------- helpers

    private fun givenNoPreviousChallenge() {
        every { challengeRepository.findFirstByPhoneLookupHashAndConsumedAtIsNullOrderByCreatedAtDesc(any()) } returns null
    }

    private fun givenChallenge(challenge: OtpChallengeEntity) {
        every { challengeRepository.findById(challengeId) } returns Optional.of(challenge)
    }

    private fun challenge(
        lastSentAt: Instant = now.minusSeconds(30),
        expiresAt: Instant = now.plusSeconds(240),
        resends: Int = 0,
        verifyAttempts: Int = 0,
        consumedAt: Instant? = null,
    ) = OtpChallengeEntity(
        id = challengeId,
        phoneLookupHash = phoneCryptoService.hmacLookupHash(PHONE),
        phoneEncrypted = phoneCryptoService.encryptToString(PHONE),
        providerReqId = "req-1",
        createdAt = now.minusSeconds(60),
        lastSentAt = lastSentAt,
        expiresAt = expiresAt,
        verifyAttempts = verifyAttempts,
        resends = resends,
        consumedAt = consumedAt,
    )

    private companion object {
        const val PHONE = "+919876543210"
        const val PHONE_SECRET = "dGVzdC1vbmx5LXBob25lLWNyeXB0by1zZWNyZXQtISE="
    }
}

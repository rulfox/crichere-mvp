package com.crichere.backend.auth

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Result of a successful send/resend: what the client needs to drive its OTP screen. */
data class OtpChallengeTicket(
    val challengeId: UUID,
    val expiresAt: Instant,
    val resendAvailableAt: Instant,
)

/**
 * Backend-driven phone OTP (MSG91; docs/PHASE12.md). This is where every protection Firebase
 * used to provide for free is enforced:
 *
 *  - **Region policy:** [PhoneNumberNormalizer] rejects non-Indian-mobile input before any SMS.
 *  - **Send abuse / SMS pumping:** server-side 60s cooldown, per-phone and global-daily send
 *    caps ([AuthRateLimiter]); per-IP caps sit in [AuthRateLimitFilter].
 *  - **Brute force:** at most [OtpProperties.maxVerifyAttempts] guesses per challenge, spent
 *    atomically *before* the provider is asked ([OtpChallengeRepository.spendAttempt]);
 *    single-use; short expiry.
 *  - **Identity binding:** the phone that gets signed in is the one stored on the challenge at
 *    send time. The verify request carries only the challenge id and the code.
 *  - **No oracles:** unknown, expired, spent and exhausted challenges are one exception.
 *
 * Phone numbers and codes are never logged.
 */
@Service
class OtpAuthService(
    private val otpSender: OtpSender,
    private val authService: AuthService,
    private val phoneCryptoService: PhoneCryptoService,
    private val challengeRepository: OtpChallengeRepository,
    private val rateLimiter: AuthRateLimiter,
    private val properties: OtpProperties,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun requireEnabled() {
        if (properties.provider != OtpProviderType.MSG91) throw OtpDisabledException()
    }

    /** Starts a challenge: validates the number, enforces limits, sends the first code. */
    fun requestOtp(rawPhone: String): OtpChallengeTicket {
        requireEnabled()
        val phone = PhoneNumberNormalizer.toIndianE164(rawPhone) ?: throw InvalidPhoneNumberException()
        val lookupHash = phoneCryptoService.hmacLookupHash(phone)
        val now = clock.instant()

        challengeRepository.deleteExpiredBefore(now.minus(RETENTION))

        // Cooldown first: a too-early retry must not spend rate-limit budget.
        val previous = challengeRepository.findFirstByPhoneLookupHashAndConsumedAtIsNullOrderByCreatedAtDesc(lookupHash)
        previous?.let { cooldownRemaining(it.lastSentAt, now) }?.let { throw RateLimitExceededException(it) }

        spendSendBudget(lookupHash)

        val providerReqId = otpSender.send(phone)

        // Only now supersede the old challenge, so a failed send does not kill a still-good code.
        previous?.let { challengeRepository.consume(it.id, now) }

        val challenge = challengeRepository.save(
            OtpChallengeEntity(
                id = UUID.randomUUID(),
                phoneLookupHash = lookupHash,
                phoneEncrypted = phoneCryptoService.encryptToString(phone),
                providerReqId = providerReqId,
                createdAt = now,
                lastSentAt = now,
                expiresAt = now.plus(properties.challengeTtl),
            ),
        )
        return ticket(challenge)
    }

    /** Re-sends the code for an open challenge, within the resend and cooldown limits. */
    fun resendOtp(challengeId: UUID): OtpChallengeTicket {
        requireEnabled()
        val now = clock.instant()
        val challenge = openChallenge(challengeId, now)

        if (challenge.resends >= properties.maxResends) throw OtpResendLimitReachedException()
        cooldownRemaining(challenge.lastSentAt, now)?.let { throw RateLimitExceededException(it) }

        spendSendBudget(challenge.phoneLookupHash)

        otpSender.resend(challenge.providerReqId)

        // The conditional update is the real guard: two parallel resends cannot both pass the cap.
        val newExpiry = now.plus(properties.challengeTtl)
        if (challengeRepository.recordResend(challengeId, now, newExpiry, properties.maxResends) == 0) {
            throw OtpResendLimitReachedException()
        }
        return ticket(challenge.also { it.lastSentAt = now; it.expiresAt = newExpiry })
    }

    /** Checks a code and, if right, signs the challenge's phone in. */
    fun verifyOtp(challengeId: UUID, code: String): AuthResult {
        requireEnabled()
        val now = clock.instant()
        val challenge = openChallenge(challengeId, now)

        // Pay for the attempt before asking the provider -- see OtpChallengeRepository.spendAttempt.
        if (challengeRepository.spendAttempt(challengeId, now, properties.maxVerifyAttempts) == 0) {
            throw OtpChallengeExpiredException()
        }

        when (otpSender.verify(challenge.providerReqId, code)) {
            OtpCheckResult.INVALID -> {
                val used = challengeRepository.findById(challengeId).map { it.verifyAttempts }
                    .orElse(properties.maxVerifyAttempts)
                val remaining = (properties.maxVerifyAttempts - used).coerceAtLeast(0)
                if (remaining == 0) challengeRepository.consume(challengeId, now)
                throw OtpInvalidCodeException(remaining)
            }
            OtpCheckResult.VALID -> Unit
        }

        // Single use: the loser of a double-submit race gets "expired", not a second session.
        if (challengeRepository.consume(challengeId, now) == 0) throw OtpChallengeExpiredException()

        val phone = phoneCryptoService.decryptFromString(challenge.phoneEncrypted)
        log.info("OTP verified for challenge {}", challengeId)
        return authService.signInVerifiedPhone(challenge.phoneLookupHash, phone)
    }

    private fun openChallenge(challengeId: UUID, now: Instant): OtpChallengeEntity {
        val challenge = challengeRepository.findById(challengeId).orElse(null)
            ?: throw OtpChallengeExpiredException()
        if (challenge.consumedAt != null || !challenge.expiresAt.isAfter(now)) throw OtpChallengeExpiredException()
        return challenge
    }

    private fun spendSendBudget(lookupHash: String) {
        rateLimiter.tryConsumeOtpSendForPhone(lookupHash)?.let { throw RateLimitExceededException(it) }
        if (!rateLimiter.tryConsumeGlobalOtpSend()) {
            // Loud on purpose: either real growth outran the cap or this is an SMS-pumping attack.
            log.error("Global daily OTP send cap reached; refusing further sends until the next UTC day")
            throw OtpUnavailableException("Daily OTP send cap reached")
        }
    }

    private fun cooldownRemaining(lastSentAt: Instant, now: Instant): Duration? {
        val remaining = Duration.between(now, lastSentAt.plus(properties.resendCooldown))
        return remaining.takeIf { !it.isNegative && !it.isZero }
    }

    private fun ticket(challenge: OtpChallengeEntity) = OtpChallengeTicket(
        challengeId = challenge.id,
        expiresAt = challenge.expiresAt,
        resendAvailableAt = challenge.lastSentAt.plus(properties.resendCooldown),
    )

    private companion object {
        /** Keep spent/expired rows briefly for debugging, then let the next send sweep them. */
        val RETENTION: Duration = Duration.ofDays(1)
    }
}

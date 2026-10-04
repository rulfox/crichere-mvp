package com.crichere.backend.auth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * [OtpSender] for a backend running on a developer machine (`local` Spring profile only), so the
 * real backend-driven OTP flow (`/auth/otp/send` + `/verify`) can be exercised end to end -- app,
 * rate limits, challenge rows, session issue -- without MSG91 credentials or a network call
 * (docs/PHASE15.md).
 *
 * Only the numbers in `crichere.otp.local-fake.numbers` get a code, and it is always
 * [FIXED_CODE]; any other number fails like an unconfigured provider. [Profile] keeps it out of
 * every environment that does not activate `local` (the dev-machine profile with committed
 * fallback secrets), so [Msg91OtpSender] stays the only sender anywhere else.
 */
@Component
@Primary
@Profile("local")
class LocalFakeOtpSender(
    @Value("\${crichere.otp.local-fake.numbers:+917293318484}") numbers: String,
) : OtpSender {

    private val log = LoggerFactory.getLogger(javaClass)
    private val allowedNumbers: Set<String> = numbers.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    private val phoneByRequest = ConcurrentHashMap<String, String>()

    override fun send(phoneE164: String): String {
        if (phoneE164 !in allowedNumbers) {
            log.warn("Local fake OTP: number is not in crichere.otp.local-fake.numbers")
            throw OtpUnavailableException("Number not enabled for the local fake OTP sender")
        }
        val requestId = "local-${UUID.randomUUID()}"
        phoneByRequest[requestId] = phoneE164
        return requestId
    }

    override fun resend(providerReqId: String) {
        if (!phoneByRequest.containsKey(providerReqId)) throw OtpUnavailableException("Unknown local OTP request")
    }

    override fun verify(providerReqId: String, code: String): OtpCheckResult =
        if (phoneByRequest.containsKey(providerReqId) && code == FIXED_CODE) OtpCheckResult.VALID else OtpCheckResult.INVALID

    companion object {
        const val FIXED_CODE = "123456"
    }
}

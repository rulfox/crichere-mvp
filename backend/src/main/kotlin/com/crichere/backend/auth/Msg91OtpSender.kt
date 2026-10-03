package com.crichere.backend.auth

import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

/**
 * [OtpSender] backed by MSG91's OTP widget APIs (default template/sender, no DLT).
 *
 * **UNVERIFIED AGAINST THE LIVE API (docs/PHASE12.md, Phase 0).** MSG91 does not publish the
 * widget endpoints' request/response shapes. The paths, field names and the `type`/`message`
 * response convention below are the widget's observed behaviour, assumed -- not confirmed with
 * a real account. Phase 0 must confirm or correct them (and that these calls are allowed with
 * the authkey server-side) before this is switched on. Until then `crichere.otp.provider`
 * stays `firebase`, and a blank [Msg91Properties.authKey] makes every call fail closed.
 *
 * Failure policy: any transport error, non-2xx, or `type != success` on send/resend becomes a
 * generic [OtpUnavailableException]. Provider text is logged by class/status only, never the
 * body (it can echo the phone number) and never returned to the caller.
 */
@Component
class Msg91OtpSender(private val properties: Msg91Properties) : OtpSender {

    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient by lazy {
        val http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build()
        RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(JdkClientHttpRequestFactory(http).apply { setReadTimeout(properties.readTimeout) })
            .build()
    }

    override fun send(phoneE164: String): String {
        val response = post(
            "/api/v5/widget/sendOtp",
            mapOf("widgetId" to properties.widgetId, "identifier" to PhoneNumberNormalizer.toProviderIdentifier(phoneE164)),
        )
        requireSuccess(response)
        // For a successful send MSG91 returns the request id in `message`.
        return (response["message"] as? String)?.takeIf { it.isNotBlank() }
            ?: run {
                log.error("MSG91 send succeeded but returned no request id")
                throw OtpUnavailableException()
            }
    }

    override fun resend(providerReqId: String) {
        requireSuccess(
            post("/api/v5/widget/retryOtp", mapOf("widgetId" to properties.widgetId, "reqId" to providerReqId)),
        )
    }

    override fun verify(providerReqId: String, code: String): OtpCheckResult {
        val response = post(
            "/api/v5/widget/verifyOtp",
            mapOf("widgetId" to properties.widgetId, "reqId" to providerReqId, "otp" to code),
        )
        // A wrong/expired code is a normal outcome, not an outage.
        return if (response["type"] == "success") OtpCheckResult.VALID else OtpCheckResult.INVALID
    }

    private fun post(path: String, body: Map<String, String>): Map<String, Any?> {
        if (properties.tokenAuth.isBlank() || properties.widgetId.isBlank()) {
            log.error("MSG91 is not configured (crichere.msg91.token-auth / widget-id blank)")
            throw OtpUnavailableException("MSG91 not configured")
        }
        return try {
            val entity = client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                // Observed in production: the widget endpoints answer 403 "Invalid request" to the
                // account Auth Key. The widget token is the credential the dashboard points to
                // ("recommended ... in OTP Widget"). Still to be confirmed with a live send.
                .header("tokenAuth", properties.tokenAuth)
                .body(body)
                .retrieve()
                // A 4xx from verify can still be a "wrong code" answer, and a 4xx from anything is
                // MSG91 explaining itself: keep the body instead of letting RestClient throw.
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .onStatus({ it.is5xxServerError }) { _, res ->
                    log.error("MSG91 {} returned HTTP {}", path, res.statusCode.value())
                    throw OtpUnavailableException("MSG91 returned ${res.statusCode.value()}")
                }
                .toEntity(object : ParameterizedTypeReference<Map<String, Any?>>() {})
            val response = entity.body ?: emptyMap()
            if (!entity.statusCode.is2xxSuccessful) {
                log.error("MSG91 {} returned HTTP {}: {}", path, entity.statusCode.value(), describe(response))
            }
            response
        } catch (e: OtpUnavailableException) {
            throw e
        } catch (e: Exception) {
            log.error("MSG91 call failed: {}", e.javaClass.simpleName)
            throw OtpUnavailableException(cause = e)
        }
    }

    private fun requireSuccess(response: Map<String, Any?>) {
        if (response["type"] != "success") {
            log.error("MSG91 rejected the request: {}", describe(response))
            throw OtpUnavailableException()
        }
    }

    /**
     * MSG91's own error fields (`type`, `code`, `message`) for the log, so an auth or whitelist
     * problem is diagnosable. Digit runs of six or more are masked: a reply can echo a phone
     * number, and we never log phone numbers or codes. The authkey is never part of a reply.
     */
    private fun describe(response: Map<String, Any?>): String =
        listOf("type", "code", "message")
            .mapNotNull { key -> response[key]?.let { "$key=${it.toString().take(MAX_LOGGED).replace(LONG_DIGITS, "***")}" } }
            .ifEmpty { listOf("(no recognisable fields)") }
            .joinToString(" ")

    private companion object {
        const val MAX_LOGGED = 200
        val LONG_DIGITS = Regex("[0-9]{6,}")
    }
}

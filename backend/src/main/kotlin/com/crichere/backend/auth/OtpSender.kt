package com.crichere.backend.auth

/** What the provider said about a code the user typed. Provider/transport failures are exceptions instead. */
enum class OtpCheckResult { VALID, INVALID }

/**
 * Sends and checks one-time codes with an external provider. An interface so [OtpAuthService]
 * is testable without network access, and so the vendor stays confined to one class
 * ([Msg91OtpSender]) -- same reasoning as [FirebaseTokenVerifier].
 *
 * Implementations throw [OtpUnavailableException] for anything that is the provider's or the
 * network's fault (timeout, 5xx, low wallet balance, missing credentials). The exception
 * carries no provider detail on purpose: it ends up in a response body.
 */
interface OtpSender {
    /** Sends a fresh code to [phoneE164] (`+91...`) and returns the provider's request id. */
    fun send(phoneE164: String): String

    /** Re-sends the code for [providerReqId]. */
    fun resend(providerReqId: String)

    /** Checks [code] against the code issued for [providerReqId]. */
    fun verify(providerReqId: String, code: String): OtpCheckResult
}

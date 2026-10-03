package com.crichere.backend.auth

/**
 * Turns user-typed input into the exact E.164 string Firebase would have produced, and refuses
 * everything else.
 *
 * Two jobs, both load-bearing:
 *
 *  - **Region policy.** Firebase was restricted to India (PHASE1.md); with MSG91 there is no
 *    console toggle, so this is the only thing stopping someone from making the backend text
 *    arbitrary international numbers (SMS pumping). Indian mobiles only: `+91` then ten digits
 *    starting 6-9.
 *  - **Account identity.** Accounts key on `HMAC(phone)`. The output must be byte-identical to
 *    Firebase's `phone_number` claim (`+91XXXXXXXXXX`) or the same person would get a second
 *    account depending on which provider they signed in through.
 *
 * Accepted inputs: `+919876543210`, `919876543210`, `09876543210`, `9876543210`, with spaces,
 * dashes or parentheses anywhere.
 */
object PhoneNumberNormalizer {

    private val INDIAN_MOBILE = Regex("^[6-9][0-9]{9}$")

    /** @return `+91XXXXXXXXXX`, or `null` if [input] is not an Indian mobile number. */
    fun toIndianE164(input: String): String? {
        val compact = input.filter { !it.isWhitespace() && it != '-' && it != '(' && it != ')' }
        val national = when {
            compact.startsWith("+91") -> compact.removePrefix("+91")
            compact.startsWith("+") -> return null
            compact.startsWith("91") && compact.length == 12 -> compact.removePrefix("91")
            compact.startsWith("0") && compact.length == 11 -> compact.removePrefix("0")
            else -> compact
        }
        if (!INDIAN_MOBILE.matches(national)) return null
        return "+91$national"
    }

    /** The form MSG91 expects: country code and number, no `+`. */
    fun toProviderIdentifier(e164: String): String = e164.removePrefix("+")
}

package com.crichere.app.auth

/**
 * A country the phone-number input knows how to handle. Users type only the *national* number;
 * [dialCode] is added behind the scenes. India is the only entry today -- the country-code picker
 * (future scope) is a matter of offering more [Country] values and letting the user choose one;
 * nothing below this class assumes India.
 *
 * [nationalPattern] mirrors the backend rule for the same country (`PhoneNumberNormalizer`, which is
 * India-only by policy); the backend stays the authority, this just gives instant feedback.
 */
data class Country(
    val isoCode: String,
    /** With the leading `+`, e.g. `"+91"`. */
    val dialCode: String,
    val nationalLength: Int,
    val nationalPattern: String,
)

object Countries {
    val India = Country(isoCode = "IN", dialCode = "+91", nationalLength = 10, nationalPattern = "^[6-9][0-9]{9}$")

    /** The country used until the user can pick one. */
    val default: Country = India
}

/**
 * Turns what a user types (or pastes) into a national number and back into the E.164 form every
 * other layer uses (Firebase, MSG91 and the `HMAC(phone)` account key all want `+91XXXXXXXXXX`).
 */
object PhoneNumberInput {

    /**
     * Keeps only the digits, and treats a pasted international or trunk-prefixed form as the national
     * number: `+91 98765 43210`, `91 98765 43210` (12 digits) and `098765 43210` (11 digits) all
     * become `9876543210`. A genuine 10-digit number that merely *starts* with the dial digits
     * (`9123456789`) is left alone -- only the exact 12- and 11-digit shapes are treated as prefixed.
     * The result never exceeds [Country.nationalLength].
     */
    fun sanitize(country: Country, raw: String): String {
        val digits = raw.filter { it.isDigit() }
        val dial = country.dialCode.removePrefix("+")
        val national = when {
            raw.trimStart().startsWith("+") && digits.startsWith(dial) -> digits.removePrefix(dial)
            digits.length == dial.length + country.nationalLength && digits.startsWith(dial) -> digits.removePrefix(dial)
            digits.length == country.nationalLength + 1 && digits.startsWith("0") -> digits.drop(1)
            else -> digits
        }
        return national.take(country.nationalLength)
    }

    /** `+919876543210` for a valid [national] number, or `null` if it isn't one. */
    fun toE164(country: Country, national: String): String? =
        if (Regex(country.nationalPattern).matches(national)) country.dialCode + national else null

    /**
     * How a number is shown back to the user: the national number in two groups (`98765 43210`) when
     * [e164] is in the default country; anything else is shown as given, with its country code, so a
     * foreign number is never mistaken for a local one once a picker exists.
     */
    fun displayNational(e164: String, country: Country = Countries.default): String {
        val national = e164.removePrefix(country.dialCode)
        val isLocal = e164.startsWith(country.dialCode) && national.length == country.nationalLength && national.all(Char::isDigit)
        return if (isLocal) "${national.take(5)} ${national.drop(5)}" else e164
    }
}

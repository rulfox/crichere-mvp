package com.crichere.backend.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Configuration for [PhoneCryptoService]. A single master secret is supplied by the
 * environment (`PHONE_CRYPTO_SECRET`); the service derives two independent sub-keys from it
 * so the same bytes are never used for two different cryptographic purposes.
 */
@ConfigurationProperties(prefix = "crichere.phone-crypto")
data class PhoneCryptoProperties(
    /**
     * Master secret. Either a Base64-encoded byte string or raw text; must decode to at
     * least 32 bytes of entropy. Never logged, never returned by any endpoint.
     */
    val secret: String = "",
)

@Configuration
@EnableConfigurationProperties(PhoneCryptoProperties::class)
class PhoneCryptoConfiguration

/**
 * Protects phone numbers at rest.
 *
 * A phone number is the account identifier for this app, so it has to be both *searchable*
 * (log in by phone) and *recoverable* (send SMS / display the number). Those two needs pull
 * in opposite directions, so the number is stored twice:
 *
 *  - [hmacLookupHash] — a deterministic HMAC-SHA256 of the normalised number, used as the
 *    unique lookup key (`users.phone_lookup_hash`). Deterministic so lookup works; keyed
 *    (HMAC, not a bare digest) so an attacker with a database dump cannot simply hash the
 *    ~10^10 possible Indian mobile numbers and recover the plaintext — they would need the
 *    secret too.
 *  - [encrypt] / [decrypt] — AES-256-GCM with a fresh random 96-bit IV per call, so the
 *    ciphertext is recoverable but reveals nothing (two rows holding the same number produce
 *    different ciphertext). GCM is authenticated, so tampering with a stored value is
 *    detected on decrypt rather than silently yielding garbage.
 *
 * ## Key derivation
 *
 * Reusing one key for both HMAC and AES would be a key-reuse smell, and the brief specifies a
 * single environment secret. So two independent 256-bit sub-keys are derived from the master
 * secret with a single HMAC-SHA256 invocation each over a distinct, versioned label:
 *
 * ```
 * lookupKey     = HMAC-SHA256(master, "crichere/phone-lookup-hmac/v1")
 * encryptionKey = HMAC-SHA256(master, "crichere/phone-encryption/v1")
 * ```
 *
 * This is the standard "derive sub-keys with a PRF over a domain-separation label" pattern
 * (the same shape as a single-block HKDF-Expand). It uses only the JDK's HMAC-SHA256
 * primitive — nothing here is a hand-rolled cryptographic primitive. Because HMAC is a PRF,
 * neither sub-key reveals anything about the other or about the master secret. The `/v1`
 * suffix leaves room to rotate the derivation scheme later without changing the env var.
 */
@Service
class PhoneCryptoService(properties: PhoneCryptoProperties) {

    private val lookupKey: SecretKeySpec
    private val encryptionKey: SecretKeySpec
    private val random = SecureRandom()

    init {
        val master = decodeMasterSecret(properties.secret)
        require(master.size >= MIN_SECRET_BYTES) {
            // Deliberately reports only the *length*, never any part of the secret itself.
            "crichere.phone-crypto.secret must decode to at least $MIN_SECRET_BYTES bytes " +
                "(got ${master.size}). Set the PHONE_CRYPTO_SECRET environment variable."
        }
        lookupKey = SecretKeySpec(deriveSubKey(master, LOOKUP_KEY_LABEL), HMAC_ALGORITHM)
        encryptionKey = SecretKeySpec(deriveSubKey(master, ENCRYPTION_KEY_LABEL), "AES")
        master.fill(0)
    }

    /**
     * Deterministic, keyed hash of [phone], hex-encoded. The same phone number always maps to
     * the same value (that is what makes it usable as a lookup key), and no two different
     * numbers realistically collide.
     */
    fun hmacLookupHash(phone: String): String {
        val normalised = normalise(phone)
        require(normalised.isNotEmpty()) { "phone number must contain at least one digit" }
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply { init(lookupKey) }
        return mac.doFinal(normalised.toByteArray(StandardCharsets.UTF_8)).toHex()
    }

    /**
     * Encrypts [phone] with AES-256-GCM under a fresh random IV.
     *
     * @return `IV (12 bytes) || ciphertext || GCM tag (16 bytes)`. The IV is not secret, only
     *   required to be unique per encryption, so prefixing it is the conventional layout.
     */
    fun encrypt(phone: String): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val sealed = cipher.doFinal(normalise(phone).toByteArray(StandardCharsets.UTF_8))
        return iv + sealed
    }

    /**
     * Reverses [encrypt]. Throws if the payload was truncated, or if the ciphertext or IV was
     * modified (GCM authenticates both), or if it was produced under a different key.
     */
    fun decrypt(payload: ByteArray): String {
        require(payload.size >= IV_BYTES + GCM_TAG_BITS / 8) { "encrypted phone payload is too short" }
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            encryptionKey,
            GCMParameterSpec(GCM_TAG_BITS, payload, 0, IV_BYTES),
        )
        val plaintext = cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES)
        return String(plaintext, StandardCharsets.UTF_8)
    }

    /**
     * [encrypt] rendered as Base64, for the `users.phone_encrypted` TEXT column (the schema
     * from Task 2 stores this as text, not `bytea`).
     */
    fun encryptToString(phone: String): String = Base64.getEncoder().encodeToString(encrypt(phone))

    /** Reverses [encryptToString]. */
    fun decryptFromString(encoded: String): String = decrypt(Base64.getDecoder().decode(encoded))

    /**
     * Canonicalises a phone number before it is hashed or encrypted, so that cosmetic
     * differences ("+91 98765 43210" vs "+919876543210") resolve to the same account. Firebase
     * hands us E.164 already; this only strips formatting characters, it never rewrites or
     * infers a country code (which could merge two genuinely different numbers).
     */
    private fun normalise(phone: String): String = phone.filter { it.isDigit() || it == '+' }

    private fun deriveSubKey(master: ByteArray, label: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply { init(SecretKeySpec(master, HMAC_ALGORITHM)) }
        return mac.doFinal(label.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Accepts the master secret either Base64-encoded (the recommended form for a randomly
     * generated 32-byte key) or as raw text (convenient for local development). Base64 is
     * preferred, but only when it actually decodes to enough entropy -- otherwise a
     * passphrase that happens to be valid Base64 would be silently shortened. Whichever
     * branch is taken, the mapping from configured string to key material is deterministic,
     * which is what matters for the lookup hash to stay stable across restarts.
     */
    private fun decodeMasterSecret(secret: String): ByteArray {
        val raw = secret.toByteArray(StandardCharsets.UTF_8)
        val decoded =
            try {
                Base64.getDecoder().decode(secret)
            } catch (_: IllegalArgumentException) {
                null
            }
        return if (decoded != null && decoded.size >= MIN_SECRET_BYTES) decoded else raw
    }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) {
            out.append(HEX[(b.toInt() shr 4) and 0xF])
            out.append(HEX[b.toInt() and 0xF])
        }
        return out.toString()
    }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val MIN_SECRET_BYTES = 32
        const val LOOKUP_KEY_LABEL = "crichere/phone-lookup-hmac/v1"
        const val ENCRYPTION_KEY_LABEL = "crichere/phone-encryption/v1"
        val HEX = "0123456789abcdef".toCharArray()
    }
}

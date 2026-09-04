package com.crichere.backend.auth

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * Configuration for [JwtService].
 */
@ConfigurationProperties(prefix = "crichere.jwt")
data class JwtProperties(
    /**
     * HMAC signing secret. Base64-encoded or raw text; must yield at least 32 bytes, which is
     * the minimum key length HS256 permits. Never logged.
     */
    val secret: String = "",
    /** Value placed in, and required of, the `iss` claim. */
    val issuer: String = "crichere",
    /** Access-token lifetime. 15 minutes per the phase plan. */
    val accessTokenTtl: Duration = Duration.ofMinutes(15),
    /** Refresh-token lifetime. Long-lived so the mobile app rarely forces a re-login. */
    val refreshTokenTtl: Duration = Duration.ofDays(30),
)

@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfiguration

/** An issued access token plus the instant it stops being valid. */
data class IssuedAccessToken(val token: String, val expiresAt: Instant)

/** An issued refresh token: the raw value handed to the client, and the hash we persist. */
data class IssuedRefreshToken(val rawToken: String, val tokenHash: String, val expiresAt: Instant)

/**
 * Issues and validates the two credentials this API hands out.
 *
 * **Access token** — a signed JWT (HS256). Short-lived (15 minutes) and stateless: it is not
 * stored anywhere, so it cannot be revoked before it expires. That is the trade-off that
 * makes it cheap to verify on every request; revocation lives on the refresh token instead.
 *
 * **Refresh token** — *not* a JWT. It is 256 bits of `SecureRandom` output, opaque to the
 * client, and its authority comes entirely from a row in `refresh_tokens`. Only a SHA-256
 * hash of it is persisted ([hashRefreshToken]), so a database dump does not yield usable
 * tokens. A plain digest (rather than bcrypt/argon2) is the right tool here precisely because
 * the token is full-entropy random: there is no low-entropy guess space to slow an attacker
 * down over, unlike a password.
 */
@Service
class JwtService(
    private val properties: JwtProperties,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val secretBytes: ByteArray = decodeSecret(properties.secret).also {
        require(it.size >= MIN_SECRET_BYTES) {
            // Length only -- never any part of the secret.
            "crichere.jwt.secret must decode to at least $MIN_SECRET_BYTES bytes (got ${it.size}). " +
                "Set the JWT_SECRET environment variable."
        }
    }

    private val random = SecureRandom()

    /** How long a freshly issued refresh token stays valid. */
    val refreshTokenTtl: Duration get() = properties.refreshTokenTtl

    /**
     * Mints an access token for [userId].
     *
     * Claims: `sub` (the user id), `iss`, `iat`, `exp`, `jti` (a unique id, so a specific
     * token can be correlated in logs or denylisted later without changing the format), and
     * the custom `token_use: "access"`. `token_use` is cheap insurance against a future
     * token of a different kind (an upload ticket, say) being replayed as a session
     * credential -- the check happens in [parseAccessToken].
     */
    fun issueAccessToken(userId: UUID): IssuedAccessToken {
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plus(properties.accessTokenTtl)
        val claims = JWTClaimsSet.Builder()
            .subject(userId.toString())
            .issuer(properties.issuer)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .jwtID(UUID.randomUUID().toString())
            .claim(TOKEN_USE_CLAIM, ACCESS_TOKEN_USE)
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build()
        val jwt = SignedJWT(header, claims)
        jwt.sign(MACSigner(secretBytes))
        return IssuedAccessToken(jwt.serialize(), expiresAt)
    }

    /**
     * Validates [token] and returns the user id it was issued for.
     *
     * Every one of these checks matters, and skipping any of them is a classic JWT
     * vulnerability:
     *  - the header algorithm must be exactly HS256, checked *before* verification, so an
     *    attacker cannot present `alg: none` or an asymmetric algorithm and have the library
     *    pick a verification path we did not intend;
     *  - the MAC must verify against our secret (this is a real signature check, not a decode);
     *  - `exp` must be in the future, and `iat` must not be implausibly in the future;
     *  - `iss` must match ours, so a token minted by some other service sharing a secret is
     *    not accepted;
     *  - `token_use` must be `access`.
     *
     * @throws InvalidAccessTokenException if the token fails any check. The exception carries
     *   no detail about *which* check failed -- callers turn it into a flat 401.
     */
    fun parseAccessToken(token: String): UUID {
        val jwt =
            try {
                SignedJWT.parse(token)
            } catch (_: Exception) {
                throw InvalidAccessTokenException()
            }

        if (jwt.header.algorithm != JWSAlgorithm.HS256) throw InvalidAccessTokenException()

        val verified =
            try {
                jwt.verify(MACVerifier(secretBytes))
            } catch (_: Exception) {
                throw InvalidAccessTokenException()
            }
        if (!verified) throw InvalidAccessTokenException()

        val claims =
            try {
                jwt.jwtClaimsSet
            } catch (_: Exception) {
                throw InvalidAccessTokenException()
            }

        val now = clock.instant()
        val expiresAt = claims.expirationTime?.toInstant() ?: throw InvalidAccessTokenException()
        if (!expiresAt.isAfter(now)) throw InvalidAccessTokenException()

        val issuedAt = claims.issueTime?.toInstant() ?: throw InvalidAccessTokenException()
        if (issuedAt.isAfter(now.plus(CLOCK_SKEW))) throw InvalidAccessTokenException()

        if (claims.issuer != properties.issuer) throw InvalidAccessTokenException()
        if (claims.getClaim(TOKEN_USE_CLAIM) != ACCESS_TOKEN_USE) throw InvalidAccessTokenException()

        return try {
            UUID.fromString(claims.subject ?: throw InvalidAccessTokenException())
        } catch (_: IllegalArgumentException) {
            throw InvalidAccessTokenException()
        }
    }

    /**
     * Generates a fresh refresh token: 256 bits of cryptographically secure randomness,
     * Base64url-encoded without padding so it is URL/JSON safe.
     */
    fun issueRefreshToken(): IssuedRefreshToken {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES).also(random::nextBytes)
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return IssuedRefreshToken(
            rawToken = raw,
            tokenHash = hashRefreshToken(raw),
            expiresAt = clock.instant().plus(properties.refreshTokenTtl),
        )
    }

    /**
     * The value stored in `refresh_tokens.token_hash`. Deterministic, so an incoming raw
     * token can be looked up by hashing it; one-way, so the stored form is useless to anyone
     * who reads the table.
     */
    fun hashRefreshToken(rawToken: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray(StandardCharsets.UTF_8))
            .toHex()

    private fun decodeSecret(secret: String): ByteArray {
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
        const val MIN_SECRET_BYTES = 32
        const val REFRESH_TOKEN_BYTES = 32
        const val TOKEN_USE_CLAIM = "token_use"
        const val ACCESS_TOKEN_USE = "access"
        val CLOCK_SKEW: Duration = Duration.ofSeconds(60)
        val HEX = "0123456789abcdef".toCharArray()
    }
}

package com.crichere.backend.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JwtServiceTest {

    private val now: Instant = Instant.parse("2026-01-01T12:00:00Z")
    private val service = serviceAt(now)

    // ---------------------------------------------------------------- access tokens

    @Test
    fun `an issued token parses back to the user it was issued for`() {
        val userId = UUID.randomUUID()

        val issued = service.issueAccessToken(userId)

        assertEquals(userId, service.parseAccessToken(issued.token))
    }

    @Test
    fun `an access token expires 15 minutes after issue`() {
        val issued = service.issueAccessToken(UUID.randomUUID())

        assertEquals(now.plus(Duration.ofMinutes(15)), issued.expiresAt)
    }

    @Test
    fun `a token carries the claims the API contract promises`() {
        val userId = UUID.randomUUID()

        val claims = SignedJWT.parse(service.issueAccessToken(userId).token).jwtClaimsSet

        assertEquals(userId.toString(), claims.subject)
        assertEquals("crichere", claims.issuer)
        assertEquals("access", claims.getStringClaim("token_use"))
        assertEquals(Date.from(now), claims.issueTime)
        assertEquals(Date.from(now.plus(Duration.ofMinutes(15))), claims.expirationTime)
        assertTrue(claims.jwtid.isNotBlank())
    }

    @Test
    fun `every token gets a distinct jti`() {
        val userId = UUID.randomUUID()

        val first = SignedJWT.parse(service.issueAccessToken(userId).token).jwtClaimsSet.jwtid
        val second = SignedJWT.parse(service.issueAccessToken(userId).token).jwtClaimsSet.jwtid

        assertNotEquals(first, second)
    }

    @Test
    fun `an expired token is rejected`() {
        val issued = service.issueAccessToken(UUID.randomUUID())

        // Same secret, same token -- only the clock has moved past the expiry.
        val later = serviceAt(now.plus(Duration.ofMinutes(15)).plusSeconds(1))

        assertThrows<InvalidAccessTokenException> { later.parseAccessToken(issued.token) }
    }

    @Test
    fun `a token is still accepted one second before it expires`() {
        val issued = service.issueAccessToken(UUID.randomUUID())

        val justBefore = serviceAt(now.plus(Duration.ofMinutes(15)).minusSeconds(1))

        justBefore.parseAccessToken(issued.token)
    }

    @Test
    fun `a token signed with a different secret is rejected`() {
        val foreign = JwtService(JwtProperties(secret = OTHER_SECRET), Clock.fixed(now, ZoneOffset.UTC))
        val forged = foreign.issueAccessToken(UUID.randomUUID())

        assertThrows<InvalidAccessTokenException> { service.parseAccessToken(forged.token) }
    }

    @Test
    fun `a tampered payload is rejected`() {
        val issued = service.issueAccessToken(UUID.randomUUID())
        val (header, payload, signature) = issued.token.split(".")

        // Swap the subject for someone else's id, keeping the original signature. This is the
        // attack a decode-without-verify implementation waves straight through.
        val original = String(Base64.getUrlDecoder().decode(payload))
        val tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            original.replace(
                Regex("\"sub\":\"[^\"]+\""),
                "\"sub\":\"${UUID.randomUUID()}\"",
            ).toByteArray(),
        )

        assertThrows<InvalidAccessTokenException> {
            service.parseAccessToken("$header.$tamperedPayload.$signature")
        }
    }

    @Test
    fun `a tampered signature is rejected`() {
        val issued = service.issueAccessToken(UUID.randomUUID())
        val flipped = issued.token.dropLast(1) + if (issued.token.last() == 'A') 'B' else 'A'

        assertThrows<InvalidAccessTokenException> { service.parseAccessToken(flipped) }
    }

    @Test
    fun `an unsigned alg-none token is rejected`() {
        // The canonical JWT vulnerability: an attacker strips the signature and sets
        // "alg": "none", hoping the server trusts the claims anyway.
        val unsigned = PlainJWT(
            JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer("crichere")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .claim("token_use", "access")
                .build(),
        ).serialize()

        assertThrows<InvalidAccessTokenException> { service.parseAccessToken(unsigned) }
    }

    @Test
    fun `a token from another issuer is rejected`() {
        val foreign = signWithOurSecret(
            JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer("someone-else")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .claim("token_use", "access")
                .build(),
        )

        assertThrows<InvalidAccessTokenException> { service.parseAccessToken(foreign) }
    }

    @Test
    fun `a token issued for some other purpose is not accepted as a session credential`() {
        val wrongUse = signWithOurSecret(
            JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer("crichere")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .claim("token_use", "upload-ticket")
                .build(),
        )

        assertThrows<InvalidAccessTokenException> { service.parseAccessToken(wrongUse) }
    }

    @Test
    fun `a token with no expiry is rejected`() {
        val eternal = signWithOurSecret(
            JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer("crichere")
                .issueTime(Date.from(now))
                .claim("token_use", "access")
                .build(),
        )

        assertThrows<InvalidAccessTokenException> { service.parseAccessToken(eternal) }
    }

    @Test
    fun `a token whose subject is not a user id is rejected`() {
        val nonsense = signWithOurSecret(
            JWTClaimsSet.Builder()
                .subject("' OR 1=1 --")
                .issuer("crichere")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .claim("token_use", "access")
                .build(),
        )

        assertThrows<InvalidAccessTokenException> { service.parseAccessToken(nonsense) }
    }

    @Test
    fun `garbage is rejected without throwing anything unexpected`() {
        listOf("", "not-a-jwt", "a.b.c", "....").forEach { candidate ->
            assertThrows<InvalidAccessTokenException>("rejecting: '$candidate'") {
                service.parseAccessToken(candidate)
            }
        }
    }

    @Test
    fun `a secret shorter than 32 bytes is refused at construction`() {
        val failure = assertThrows<IllegalArgumentException> { JwtService(JwtProperties(secret = "short")) }
        assertTrue(failure.message.orEmpty().contains("32 bytes"))
        assertTrue(!failure.message.orEmpty().contains("short"), "must not echo the secret")
    }

    // ---------------------------------------------------------------- refresh tokens

    @Test
    fun `refresh tokens are unique and high-entropy`() {
        val tokens = (1..500).map { service.issueRefreshToken().rawToken }

        assertEquals(500, tokens.toSet().size, "no two refresh tokens may collide")
        // 32 random bytes, base64url without padding -> 43 characters.
        assertTrue(tokens.all { it.length == 43 }, "expected 256 bits of entropy per token")
    }

    @Test
    fun `a refresh token expires after the configured lifetime`() {
        val issued = service.issueRefreshToken()

        assertEquals(now.plus(Duration.ofDays(30)), issued.expiresAt)
    }

    @Test
    fun `the persisted hash is deterministic and is not the token itself`() {
        val issued = service.issueRefreshToken()

        assertEquals(issued.tokenHash, service.hashRefreshToken(issued.rawToken))
        assertNotEquals(issued.rawToken, issued.tokenHash)
        assertEquals(64, issued.tokenHash.length, "SHA-256 as hex")
    }

    @Test
    fun `different refresh tokens hash differently`() {
        assertNotEquals(
            service.hashRefreshToken(service.issueRefreshToken().rawToken),
            service.hashRefreshToken(service.issueRefreshToken().rawToken),
        )
    }

    private fun serviceAt(instant: Instant) =
        JwtService(JwtProperties(secret = SECRET), Clock.fixed(instant, ZoneOffset.UTC))

    /** Mints a token with our real signing key but arbitrary claims, to probe claim checks. */
    private fun signWithOurSecret(claims: JWTClaimsSet): String {
        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        jwt.sign(MACSigner(SECRET.toByteArray()))
        return jwt.serialize()
    }

    private companion object {
        const val SECRET = "unit-test-jwt-signing-secret-aaaaaaaa"
        const val OTHER_SECRET = "unit-test-jwt-signing-secret-bbbbbbbb"
    }
}

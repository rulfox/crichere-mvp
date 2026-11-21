package com.crichere.backend.admin

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Fail-closed defaults (docs/PHASE20.md A1). */
class GoogleIdTokenVerifierTest {

    private val source = ImmutableJWKSet<com.nimbusds.jose.proc.SecurityContext>(JWKSet(TestGoogleKeys.key.toPublicJWK()))

    private fun token(aud: String) =
        SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TestGoogleKeys.key.keyID).build(),
            JWTClaimsSet.Builder().issuer("accounts.google.com").audience(aud).subject("1")
                .claim("email", "Admin@Example.com").claim("email_verified", true)
                .issueTime(Date()).expirationTime(Date(System.currentTimeMillis() + 60_000)).build(),
        ).apply { sign(RSASSASigner(TestGoogleKeys.key)) }.serialize()

    @Test
    fun `a blank client ID rejects every token`() {
        val verifier = GoogleIdTokenVerifier(AdminProperties(googleClientId = ""), source)
        assertNull(verifier.verifiedEmail(token("")))
        assertNull(verifier.verifiedEmail(token("anything")))
    }

    @Test
    fun `a good token yields the lowercased email`() {
        val verifier = GoogleIdTokenVerifier(AdminProperties(googleClientId = "client"), source)
        assertEquals("admin@example.com", verifier.verifiedEmail(token("client")))
    }

    @Test
    fun `a blank allowlist is empty`() {
        assertEquals(emptySet(), AdminProperties(emails = " , ").emailSet)
        assertEquals(setOf("a@b.com", "c@d.com"), AdminProperties(emails = "A@b.com, c@D.com").emailSet)
    }
}

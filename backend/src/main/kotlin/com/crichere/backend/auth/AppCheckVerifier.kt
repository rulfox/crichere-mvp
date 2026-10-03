package com.crichere.backend.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Verifies a Firebase App Check token (Play Integrity on Android, App Attest on iOS): proof the
 * request comes from our genuine app build, not a script. It replaces the client-side bot
 * protection Firebase Phone Auth applied itself, now that the SMS is triggered through our
 * backend. An interface for the same testability reasons as [FirebaseTokenVerifier].
 */
interface AppCheckVerifier {
    /** @return `true` only for a token Firebase App Check issued for this project. */
    fun isValid(token: String?): Boolean
}

/**
 * Manual App Check verification, per Firebase's documented method for backends without a
 * first-party SDK call (the Admin SDK version in use has no App Check API): an RS256 JWT signed
 * by a key from Firebase's JWKS, `iss` = `https://firebaseappcheck.googleapis.com/<project number>`,
 * `aud` containing `projects/<project number>`, and not expired.
 *
 * Fails closed: no project number configured means every token is rejected (and logged), so
 * turning `crichere.otp.app-check-required` on without configuring this blocks sends instead of
 * silently skipping the check. **Unverified against a live App Check token** (docs/PHASE12.md)
 * -- the mobile client does not ship App Check yet.
 */
@Component
class JwksAppCheckVerifier(private val properties: OtpProperties) : AppCheckVerifier {

    private val log = LoggerFactory.getLogger(javaClass)

    private val processor: DefaultJWTProcessor<SecurityContext> by lazy {
        val source: JWKSource<SecurityContext> = JWKSourceBuilder.create<SecurityContext>(URI(JWKS_URL).toURL()).build()
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, source)
            jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
                // Exact issuer; `aud` is checked by hand below because it is a list we must contain.
                JWTClaimsSet.Builder().issuer("$ISSUER_PREFIX${properties.appCheckProjectNumber}").build(),
                setOf("exp", "iat", "sub"),
            )
        }
    }

    override fun isValid(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        if (properties.appCheckProjectNumber.isBlank()) {
            log.error("App Check is required but crichere.otp.app-check-project-number is blank; rejecting")
            return false
        }
        return try {
            val claims = processor.process(token, null)
            claims.audience.contains("projects/${properties.appCheckProjectNumber}")
        } catch (e: Exception) {
            // Never log the token; the class name is enough to tell misconfiguration from rejection.
            log.debug("App Check verification failed: {}", e.javaClass.simpleName)
            false
        }
    }

    private companion object {
        const val JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks"
        const val ISSUER_PREFIX = "https://firebaseappcheck.googleapis.com/"
    }
}

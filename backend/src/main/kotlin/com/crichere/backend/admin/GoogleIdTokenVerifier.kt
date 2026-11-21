package com.crichere.backend.admin

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.net.URI

/** Google's signing keys for ID tokens. A bean so tests can sign tokens with a local key. */
@Configuration
class GoogleJwksConfiguration {
    @Bean
    fun googleJwkSource(): JWKSource<SecurityContext> =
        // Cached and refreshed by the builder; Google rotates these keys every few days.
        JWKSourceBuilder.create<SecurityContext>(URI(GOOGLE_JWKS_URL).toURL()).build()

    private companion object {
        const val GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
    }
}

/**
 * Checks a "Sign in with Google" ID token and returns its verified email, or `null`.
 *
 * Accepted only if: RS256 signed by one of Google's current keys, issuer is Google, audience is
 * exactly our admin client ID, not expired, and `email_verified` is true. Fails closed when no
 * client ID is configured. The allowlist is checked by the caller, so this class never decides
 * who is an admin.
 */
@Component
class GoogleIdTokenVerifier(
    private val properties: AdminProperties,
    private val googleJwkSource: JWKSource<SecurityContext>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val processor: DefaultJWTProcessor<SecurityContext> by lazy {
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, googleJwkSource)
            jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
                setOf(properties.googleClientId),
                JWTClaimsSet.Builder().build(),
                setOf("iss", "sub", "email", "exp", "iat"),
                null,
            )
        }
    }

    fun verifiedEmail(idToken: String): String? {
        if (properties.googleClientId.isBlank() || idToken.isBlank() || idToken.length > MAX_TOKEN_LENGTH) return null
        return try {
            val claims = processor.process(idToken, null)
            if (claims.issuer !in GOOGLE_ISSUERS) return null
            if (claims.getBooleanClaim("email_verified") != true) return null
            claims.getStringClaim("email")?.trim()?.lowercase()
        } catch (e: Exception) {
            // Never log the token; the exception class is enough to tell a bad token from an outage.
            log.info("Admin Google token rejected: {}", e.javaClass.simpleName)
            null
        }
    }

    private companion object {
        val GOOGLE_ISSUERS = setOf("accounts.google.com", "https://accounts.google.com")
        const val MAX_TOKEN_LENGTH = 4096
    }
}

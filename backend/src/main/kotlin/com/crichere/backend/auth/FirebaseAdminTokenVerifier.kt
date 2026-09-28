package com.crichere.backend.auth

import com.crichere.backend.notification.FirebaseAppProvider
import com.google.firebase.auth.FirebaseAuth
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

/**
 * Configuration for [FirebaseAdminTokenVerifier].
 */
@ConfigurationProperties(prefix = "crichere.firebase")
data class FirebaseProperties(
    /**
     * Filesystem path to the Firebase service-account JSON. Blank in dev/CI, where no
     * credentials exist yet -- see [FirebaseAdminTokenVerifier] for why that is not fatal.
     */
    val serviceAccountPath: String = "",
)

@Configuration
@EnableConfigurationProperties(FirebaseProperties::class)
class FirebaseConfiguration

/**
 * The production [FirebaseTokenVerifier], backed by the Firebase Admin SDK.
 *
 * ## Why the SDK is initialised lazily
 *
 * There is no `firebase-service-account.json` in the dev, test, or CI environment -- that is
 * external setup that has not happened yet. Initialising `FirebaseApp` in a constructor or an
 * `@PostConstruct` would therefore fail the *application context startup* everywhere except
 * production, taking every integration test down with it, including tests that have nothing
 * to do with Firebase.
 *
 * So the [FirebaseAuth] handle is built on first use, behind a `lazy` delegate, from the shared
 * [FirebaseAppProvider] (which does the actual lazy `FirebaseApp` init -- see its own doc; that
 * logic used to live here directly before docs/PHASE8.md's push-notification sender needed the
 * same `FirebaseApp`). The bean is always present and the context always starts; a request that
 * actually needs Firebase fails with a 401 (and a logged error) if credentials are absent. That
 * confines the blast radius of a missing credential file to the endpoint that needs it.
 *
 * Integration tests replace this bean with a mock of the [FirebaseTokenVerifier] interface, so
 * no test in this codebase ever reaches the real Admin SDK.
 */
@Component
class FirebaseAdminTokenVerifier(
    private val firebaseAppProvider: FirebaseAppProvider,
) : FirebaseTokenVerifier {

    private val log = LoggerFactory.getLogger(javaClass)

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance(firebaseAppProvider.app) }

    override fun verify(idToken: String): VerifiedFirebaseToken {
        val auth =
            try {
                firebaseAuth
            } catch (e: Exception) {
                // Misconfiguration, not a bad token. Logged server-side (no token material),
                // reported to the caller as a plain authentication failure.
                log.error("Firebase Admin SDK is not usable; rejecting ID token verification", e)
                throw InvalidFirebaseIdTokenException()
            }

        val decoded =
            try {
                // checkRevoked = true costs a lookup but means a session revoked in the
                // Firebase console (e.g. after a stolen-device report) stops working here too.
                auth.verifyIdToken(idToken, true)
            } catch (e: Exception) {
                // Never log the token itself, and never surface the SDK's message: it
                // distinguishes "expired" from "wrong project" from "bad signature", which is
                // more than a caller needs to know.
                log.debug("Firebase ID token verification failed: {}", e.javaClass.simpleName)
                throw InvalidFirebaseIdTokenException()
            }

        val phoneNumber = decoded.claims["phone_number"] as? String
        if (phoneNumber.isNullOrBlank()) {
            // A genuine Firebase token from a non-phone provider (Google sign-in, say) has no
            // phone claim. Phone is the account key in this app, so such a token is unusable.
            log.debug("Firebase ID token carried no phone_number claim")
            throw InvalidFirebaseIdTokenException()
        }

        return VerifiedFirebaseToken(uid = decoded.uid, phoneNumber = phoneNumber)
    }
}

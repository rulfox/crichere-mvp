package com.crichere.backend.notification

import com.crichere.backend.auth.FirebaseProperties
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * The one lazily-initialised [FirebaseApp] this backend ever builds -- shared by
 * [com.crichere.backend.auth.FirebaseAdminTokenVerifier] (ID-token verification) and [FcmSender]
 * (push notifications), extracted here (docs/PHASE8.md) so the "tolerate a missing service
 * account in dev/CI" lazy-init logic exists in exactly one place instead of two.
 *
 * Same reasoning `FirebaseAdminTokenVerifier`'s own doc already gives for lazy init: there is no
 * service-account file in dev/test/CI, so building this eagerly would fail application-context
 * startup everywhere except production. A caller that actually needs Firebase fails at the point
 * of use, not at boot.
 */
@Component
class FirebaseAppProvider(private val properties: FirebaseProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    val app: FirebaseApp by lazy { initialiseFirebaseApp() }

    private fun initialiseFirebaseApp(): FirebaseApp {
        // Prefer the JSON content directly (Railway: the value lives entirely in an env var --
        // no filesystem write step to get wrong). Fall back to a file path for local dev, where
        // the service account is mounted directly on disk.
        val json = properties.serviceAccountJson
        val path = properties.serviceAccountPath
        check(json.isNotBlank() || path.isNotBlank()) {
            "Neither crichere.firebase.service-account-json nor service-account-path is configured"
        }

        val existing = FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        val app =
            existing
                ?: credentialsStream(json, path).use { stream ->
                    FirebaseApp.initializeApp(
                        FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(stream))
                            .build(),
                    )
                }
        log.info("Firebase Admin SDK initialised")
        return app
    }

    private fun credentialsStream(json: String, path: String) =
        if (json.isNotBlank()) {
            ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8))
        } else {
            check(Files.isReadable(Path.of(path))) {
                "Firebase service account file is missing or unreadable at the configured path"
            }
            FileInputStream(path)
        }
}

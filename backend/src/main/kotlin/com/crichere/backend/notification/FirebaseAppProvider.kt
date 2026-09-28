package com.crichere.backend.notification

import com.crichere.backend.auth.FirebaseProperties
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.FileInputStream
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
        val path = properties.serviceAccountPath
        check(path.isNotBlank()) {
            "crichere.firebase.service-account-path is not configured; set FIREBASE_SERVICE_ACCOUNT_PATH"
        }
        check(Files.isReadable(Path.of(path))) {
            "Firebase service account file is missing or unreadable at the configured path"
        }

        val existing = FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        val app =
            existing
                ?: FileInputStream(path).use { stream ->
                    FirebaseApp.initializeApp(
                        FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(stream))
                            .build(),
                    )
                }
        log.info("Firebase Admin SDK initialised")
        return app
    }
}

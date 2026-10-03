package com.crichere.backend.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Which mechanism proves phone ownership. Exposed to the app via `GET /auth/config`. */
enum class OtpProviderType(val wireName: String) {
    /** Client-side Firebase Phone Auth; backend verifies the ID token (`POST /auth/session`). */
    FIREBASE("firebase"),

    /** Backend-driven OTP through MSG91 (the `/auth/otp` endpoints). */
    MSG91("msg91"),
}

/**
 * Policy for the backend-driven OTP flow (docs/PHASE12.md). The numbers mirror the mobile OTP
 * screen (PHASE1.md: 60s cooldown, 3 resends, 5 wrong codes) -- but here they are *enforced*,
 * not merely displayed.
 *
 * [provider] defaults to Firebase so merging this code changes nothing until it is flipped.
 */
@ConfigurationProperties(prefix = "crichere.otp")
data class OtpProperties(
    val provider: OtpProviderType = OtpProviderType.FIREBASE,
    /** How long a code stays valid; also bounds how long a challenge row is usable. */
    val challengeTtl: Duration = Duration.ofMinutes(5),
    val maxVerifyAttempts: Int = 5,
    val maxResends: Int = 3,
    /** Minimum gap between two sends for one phone (initial send and each resend). */
    val resendCooldown: Duration = Duration.ofSeconds(60),
    /**
     * When true, `/auth/otp/send|resend` require a valid Firebase App Check token
     * (`X-Firebase-AppCheck`). Off by default until the mobile client ships App Check.
     */
    val appCheckRequired: Boolean = false,
    /** Firebase project *number* (not id), used as App Check's issuer/audience. See [JwksAppCheckVerifier]. */
    val appCheckProjectNumber: String = "",
)

/**
 * MSG91 credentials. Blank in dev/CI; a blank [authKey] makes sends fail with a 503 rather than
 * stopping the app from starting (same posture as [FirebaseProperties]).
 */
@ConfigurationProperties(prefix = "crichere.msg91")
data class Msg91Properties(
    /** Account Auth Key. Not used by the widget send/retry/verify calls (MSG91 rejects it there); kept for account-level APIs. */
    val authKey: String = "",
    val widgetId: String = "",
    /**
     * The widget token ("Tokens" page under OTP Widget/SDK in the MSG91 dashboard), sent as the
     * `tokenAuth` header on every widget call. A secret here, since only the backend uses it.
     */
    val tokenAuth: String = "",
    val baseUrl: String = "https://control.msg91.com",
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(8),
)

@Configuration
@EnableConfigurationProperties(OtpProperties::class, Msg91Properties::class)
class OtpConfiguration

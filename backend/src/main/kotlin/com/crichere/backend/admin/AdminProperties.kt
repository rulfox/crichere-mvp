package com.crichere.backend.admin

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Admin web settings (docs/PHASE20.md A1-A4). Every default fails closed: no allowlist, no client
 * ID or no origin means nobody can sign in and no browser can call the admin API.
 */
@ConfigurationProperties(prefix = "crichere.admin")
data class AdminProperties(
    /** Comma-separated Google emails allowed in. Blank means nobody. */
    val emails: String = "",
    /** The admin site's Google OAuth web client ID; an ID token must be issued for exactly this. */
    val googleClientId: String = "",
    /** The admin site's origin, e.g. https://admin.crichere.com. Blank turns admin CORS off. */
    val origin: String = "",
    val sessionTtl: Duration = Duration.ofMinutes(30),
    /** False only for plain-http local development. */
    val cookieSecure: Boolean = true,
    /** Sign-in attempts per client IP per window. */
    val signInCapacity: Long = 10,
    val signInWindow: Duration = Duration.ofMinutes(15),
) {
    val emailSet: Set<String>
        get() = emails.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
}

@Configuration
@EnableConfigurationProperties(AdminProperties::class)
class AdminConfiguration

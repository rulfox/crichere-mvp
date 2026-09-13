package com.crichere.backend.common

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * The public web viewer's origin(s) (docs/PHASE6.md) -- the only CORS allowlist this backend has,
 * scoped to exactly the public GET routes it consumes; see [com.crichere.backend.auth.SecurityConfig].
 */
@ConfigurationProperties(prefix = "crichere.web-viewer")
data class WebViewerProperties(
    /** Comma-separated. Blank means no cross-origin browser access at all -- CORS stays entirely off rather than defaulting open. */
    val origins: String = "",
) {
    val originList: List<String> get() = origins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Configuration
@EnableConfigurationProperties(WebViewerProperties::class)
class WebViewerConfiguration

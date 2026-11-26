package com.crichere.backend.appupdate

import com.crichere.backend.common.ProblemDetails
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

/**
 * Minimum app versions (docs/PHASE23.md). Empty = no minimum for that platform, which is the default:
 * the check stays off until a release needs to force users forward. Set on Railway through
 * `MIN_ANDROID_VERSION` / `MIN_IOS_VERSION`, no deploy needed; the URLs are where the Update button goes
 * (Play Store; TestFlight while testing, then the App Store).
 */
@ConfigurationProperties(prefix = "crichere.app-update")
data class AppUpdateProperties(
    val minAndroid: String? = null,
    val minIos: String? = null,
    val androidUrl: String? = null,
    val iosUrl: String? = null,
)

@Configuration
@EnableConfigurationProperties(AppUpdateProperties::class)
class AppUpdateConfiguration

/** A `major.minor.patch` app version; missing parts count as 0 (`1.1` = `1.1.0`). */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        /** `null` for anything that isn't one to three dot-separated whole numbers. */
        fun parse(text: String?): AppVersion? {
            val parts = text?.trim()?.takeIf { it.isNotEmpty() }?.split('.') ?: return null
            if (parts.size > 3) return null
            val numbers = parts.map { part -> part.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull() ?: return null }
            return AppVersion(numbers[0], numbers.getOrElse(1) { 0 }, numbers.getOrElse(2) { 0 })
        }
    }
}

/**
 * Turns away app builds older than the platform's minimum (docs/PHASE23.md) with
 * `426 APP_UPDATE_REQUIRED`, so the app can show its "Update required" screen. Reads the
 * `X-App-Platform` (`android`/`ios`) and `X-App-Version` headers the app sends on every request.
 *
 * Never blocks a request it can't judge: no headers (the web viewer, the admin site, app builds from
 * before this check), an unknown platform, a version that doesn't parse, or no minimum set. Runs first
 * in the chain, so sign-in is covered and a blocked build doesn't use up rate-limit allowance.
 *
 * Built inline in `SecurityConfig` rather than as a `@Bean` (see its note), and writes its response by
 * hand through [ProblemDetails] for the same reason as `AuthRateLimitFilter`.
 */
class AppUpdateFilter(
    private val properties: AppUpdateProperties,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val (minimumText, updateUrl) = when (request.getHeader(PLATFORM_HEADER)?.trim()?.lowercase()) {
            ANDROID -> properties.minAndroid to properties.androidUrl
            IOS -> properties.minIos to properties.iosUrl
            else -> null to null
        }
        val minimum = AppVersion.parse(minimumText)
        val version = AppVersion.parse(request.getHeader(VERSION_HEADER))
        if (minimum == null || version == null || version >= minimum) {
            filterChain.doFilter(request, response)
            return
        }

        val problem = ProblemDetails.of(
            status = HttpStatus.UPGRADE_REQUIRED,
            slug = "app-update-required",
            title = "App update required",
            code = "APP_UPDATE_REQUIRED",
            detail = "This version of the app is no longer supported. Update to continue.",
            instance = request.requestURI,
            extensions = buildMap {
                put("minVersion", minimumText!!.trim())
                updateUrl?.takeIf { it.isNotBlank() }?.let { put("updateUrl", it.trim()) }
            },
        )
        response.status = HttpStatus.UPGRADE_REQUIRED.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, problem)
    }

    companion object {
        const val PLATFORM_HEADER = "X-App-Platform"
        const val VERSION_HEADER = "X-App-Version"
        private const val ANDROID = "android"
        private const val IOS = "ios"
    }
}

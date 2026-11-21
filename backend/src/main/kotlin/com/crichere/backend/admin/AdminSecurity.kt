package com.crichere.backend.admin

import com.crichere.backend.common.ProblemDetails
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.time.Duration

const val ADMIN_PATHS = "/api/v1/admin/**"
const val ADMIN_COOKIE = "crichere_admin"
const val ADMIN_COOKIE_PATH = "/api/v1/admin"
/** Required on every admin change, with a matching Origin (docs/PHASE20.md section 5). */
const val ADMIN_HEADER = "X-Crichere-Admin"

/**
 * The admin API's own security chain, checked before the app's chain (docs/PHASE20.md A5).
 *
 * It never looks at an app bearer token, and the app chain never looks at the admin cookie, so a
 * token from one side is worthless on the other. Everything here needs the ADMIN role except
 * signing in. CSRF is handled by [AdminRequestGuardFilter] (Origin plus a custom header, on top of
 * the SameSite=Strict cookie) instead of Spring's token, which a static site can't fetch cheaply.
 */
@Configuration
class AdminSecurityConfig(
    private val properties: AdminProperties,
    private val sessions: AdminSessionService,
    private val objectMapper: ObjectMapper,
    private val authenticationEntryPoint: AuthenticationEntryPoint,
    private val accessDeniedHandler: AccessDeniedHandler,
) {
    @Bean
    @Order(1)
    fun adminSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(ADMIN_PATHS)
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .cors { it.configurationSource(adminCors()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { registry ->
                registry
                    // An async dispatch only finishes a request that was already authorized (the live stream
                    // closing); the session filter doesn't run on it, so it would otherwise count as anonymous.
                    .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, ADMIN_PATHS).permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/admin/session").permitAll()
                    .anyRequest().hasRole("ADMIN")
            }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler)
            }
            .addFilterBefore(AdminRequestGuardFilter(properties, objectMapper), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(AdminAuthenticationFilter(sessions), AdminRequestGuardFilter::class.java)
        return http.build()
    }

    /** Only the admin site, with credentials. Blank origin: no cross-origin access at all. */
    private fun adminCors(): UrlBasedCorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        if (properties.origin.isNotBlank()) {
            source.registerCorsConfiguration(
                ADMIN_PATHS,
                CorsConfiguration().apply {
                    allowedOrigins = listOf(properties.origin)
                    allowedMethods = listOf("GET", "POST", "PUT", "DELETE")
                    allowedHeaders = listOf("Content-Type", ADMIN_HEADER)
                    allowCredentials = true
                    maxAge = 600
                },
            )
        }
        return source
    }
}

/** Cookie value in, admin principal out. Never rejects by itself; the chain's rules do. */
class AdminAuthenticationFilter(private val sessions: AdminSessionService) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        SecurityContextHolder.clearContext()
        val raw = request.cookies?.firstOrNull { it.name == ADMIN_COOKIE }?.value
        sessions.authenticate(raw)?.let { principal ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
        }
        filterChain.doFilter(request, response)
    }
}

/**
 * Every admin change (anything but GET, HEAD, OPTIONS), including sign-in, must come from the
 * admin origin and carry [ADMIN_HEADER]. A form or script on another site can't set the header
 * without a CORS preflight, and the preflight only passes for the admin origin.
 */
class AdminRequestGuardFilter(
    private val properties: AdminProperties,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val safe = request.method in SAFE_METHODS
        val originOk = properties.origin.isNotBlank() && request.getHeader("Origin") == properties.origin
        val headerOk = request.getHeader(ADMIN_HEADER) == "1"
        if (!safe && !(originOk && headerOk)) {
            response.status = HttpStatus.FORBIDDEN.value()
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            objectMapper.writeValue(
                response.outputStream,
                ProblemDetails.of(
                    status = HttpStatus.FORBIDDEN,
                    slug = "admin-request-rejected",
                    title = "Request rejected",
                    code = "ADMIN_REQUEST_REJECTED",
                    detail = "This request must come from the admin site.",
                    instance = request.requestURI,
                ),
            )
            return
        }
        filterChain.doFilter(request, response)
    }

    private companion object {
        val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS")
    }
}

/** The session cookie: httpOnly, Secure, SameSite=Strict, host-only, admin paths only. */
fun adminCookie(properties: AdminProperties, value: String, maxAge: Duration): ResponseCookie =
    ResponseCookie.from(ADMIN_COOKIE, value)
        .httpOnly(true)
        .secure(properties.cookieSecure)
        .sameSite("Strict")
        .path(ADMIN_COOKIE_PATH)
        .maxAge(maxAge)
        .build()

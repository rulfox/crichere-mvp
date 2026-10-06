package com.crichere.backend.auth

import com.crichere.backend.common.ProblemDetails
import com.crichere.backend.common.WebViewerProperties
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

/**
 * Security for this stateless, token-authenticated API.
 *
 * Decisions worth spelling out:
 *
 *  - **CSRF disabled.** Spring Security enables it by default because it assumes cookie-based
 *    sessions. Credentials here travel in an `Authorization` header that a browser will never
 *    attach automatically, so there is no cross-site request forgery surface to protect and
 *    CSRF tokens would only produce spurious 403s.
 *  - **`STATELESS` session policy.** No `HttpSession` is created or consulted; every request
 *    re-establishes identity from its bearer token. This also stops Spring from allocating a
 *    session for anonymous traffic.
 *  - **Form login and HTTP Basic explicitly disabled.** Neither is wanted, and leaving them on
 *    means a browser gets a login form or a basic-auth prompt where it should get a 401 JSON
 *    body.
 *  - **Everything under `/api/v1/reference/` is public already.** Those endpoints do not exist yet -- they
 *    arrive with the reference-data feature -- but the rule is wired now so that task does not
 *    have to reopen this file. State/district lists are public data used to render the signup form
 *    *before* the user has a token, so they cannot require one.
 *  - **The filters are constructed here rather than declared as `@Bean`s.** Spring Boot
 *    auto-registers any `Filter` bean into the main servlet chain; that would run each of
 *    these twice -- once in the security chain and once outside it. Building them inline keeps
 *    them in exactly one chain.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtService: JwtService,
    private val rateLimiter: AuthRateLimiter,
    private val objectMapper: ObjectMapper,
    private val webViewerProperties: WebViewerProperties,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .cors { it.configurationSource(webViewerCorsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { registry ->
                registry
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/api/v1/reference/**").permitAll()
                    // Grounds are public data (see docs/PHASE2.md's Decisions Made) -- only the
                    // GET/search endpoint is public; POST (registering a new one) still needs
                    // authentication and falls through to anyRequest().authenticated() below.
                    .requestMatchers(HttpMethod.GET, "/api/v1/grounds").permitAll()
                    // Leagues are public data too (same posture as grounds) -- only GET (list +
                    // detail) is public; create/edit/complete/awards/uploads are authenticated
                    // and organizer-scoped, falling through to anyRequest().authenticated().
                    .requestMatchers(HttpMethod.GET, "/api/v1/leagues", "/api/v1/leagues/*").permitAll()
                    // The auction's live state and results are public too, same posture (see
                    // docs/PHASE5.md's Decisions Made) -- every other auction endpoint (start,
                    // bids, sold, etc.) is a mutation and falls through to anyRequest().authenticated().
                    .requestMatchers(HttpMethod.GET, "/api/v1/leagues/*/auction/stream", "/api/v1/leagues/*/auction/results").permitAll()
                    // The landing page's "Watch live" lookup (docs/PHASE11.md D5) -- fetched
                    // server-side by the web viewer, so it needs no CORS entry below.
                    .requestMatchers(HttpMethod.GET, "/api/v1/auctions/live-now").permitAll()
                    // The API description is generated by springdoc, which Task 1 put on the
                    // classpath. Left open so the docs stay usable during development; everywhere
                    // else springdoc is disabled (SPRINGDOC_ENABLED, application.yml), so these 404.
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(problemDetailAuthenticationEntryPoint())
                    .accessDeniedHandler(problemDetailAccessDeniedHandler())
            }
            // Rate limiting runs before authentication so a flood of session attempts is
            // rejected before any token work happens.
            .addFilterBefore(
                AuthRateLimitFilter(rateLimiter, objectMapper),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .addFilterBefore(
                JwtAuthenticationFilter(jwtService),
                UsernamePasswordAuthenticationFilter::class.java,
            )

        return http.build()
    }

    /**
     * CORS for the public web viewer (docs/PHASE6.md) -- nothing needed this before it (the
     * mobile app's Ktor client never triggers a browser preflight). Scoped narrowly on purpose:
     * only the three routes already `permitAll()` below, `GET` only, no credentials, and only the
     * origin(s) in [WebViewerProperties] -- never a wildcard, even though the underlying data is
     * public, since an open `*` would let any third-party site read this API from a browser at
     * zero benefit to us. An empty origin list (the property's default) disables CORS entirely --
     * `UrlBasedCorsConfigurationSource` with no registered patterns rejects every cross-origin
     * browser request the same as if this bean didn't exist, so nothing is open until a real
     * origin is configured.
     */
    @Bean
    fun webViewerCorsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        val origins = webViewerProperties.originList
        if (origins.isNotEmpty()) {
            val config = CorsConfiguration().apply {
                allowedOrigins = origins
                allowedMethods = listOf(HttpMethod.GET.name())
                allowedHeaders = listOf("*")
                allowCredentials = false
            }
            source.registerCorsConfiguration("/api/v1/leagues/*", config)
            source.registerCorsConfiguration("/api/v1/leagues/*/auction/stream", config)
            source.registerCorsConfiguration("/api/v1/leagues/*/auction/results", config)
        }
        return source
    }

    /**
     * 401 for an unauthenticated request to a protected endpoint.
     *
     * This exists because Spring Security rejects requests from inside the filter chain, which
     * is *outside* the dispatcher servlet -- `@RestControllerAdvice` never sees them. Without
     * this the caller would get Spring Boot's default error page shape instead of the RFC 7807
     * body every other error uses, which is a real and commonly-missed inconsistency.
     */
    @Bean
    fun problemDetailAuthenticationEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { request, response, _ ->
            writeProblem(
                response,
                ProblemDetails.of(
                    status = HttpStatus.UNAUTHORIZED,
                    slug = "unauthenticated",
                    title = "Unauthenticated",
                    code = "UNAUTHENTICATED",
                    // No hint about whether a token was absent, expired, or forged.
                    detail = "Authentication is required to access this resource.",
                    instance = request.requestURI,
                ),
            )
        }

    /** 403 for an authenticated caller who is not allowed to do this. Same shape as the 401. */
    @Bean
    fun problemDetailAccessDeniedHandler(): AccessDeniedHandler =
        AccessDeniedHandler { request, response, _ ->
            writeProblem(
                response,
                ProblemDetails.of(
                    status = HttpStatus.FORBIDDEN,
                    slug = "access-denied",
                    title = "Access denied",
                    code = "ACCESS_DENIED",
                    detail = "You do not have permission to access this resource.",
                    instance = request.requestURI,
                ),
            )
        }

    private fun writeProblem(
        response: HttpServletResponse,
        problem: ProblemDetail,
    ) {
        if (response.isCommitted) return
        response.status = problem.status
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, problem)
    }
}

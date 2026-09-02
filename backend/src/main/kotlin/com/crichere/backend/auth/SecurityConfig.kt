package com.crichere.backend.auth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Baseline security configuration for this stateless JWT API.
 *
 * Spring Security 7 enables CSRF protection by default, which assumes cookie-based
 * session auth. This API never uses cookie sessions, so CSRF protection is explicitly
 * disabled here rather than left as an unintentional 403 surprise during development.
 *
 * Real authentication/authorization rules (JWT filter, endpoint matchers, entry points)
 * land in a later task alongside the rest of the auth feature.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { csrf -> csrf.disable() }
        return http.build()
    }
}

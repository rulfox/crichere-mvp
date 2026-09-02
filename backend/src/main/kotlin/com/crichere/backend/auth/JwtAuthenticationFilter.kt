package com.crichere.backend.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Turns a `Authorization: Bearer <jwt>` header into an authenticated security context.
 *
 * Three properties matter here:
 *
 *  - **It never rejects a request itself.** A missing or bad token leaves the context empty
 *    and the chain continues; whether that is a problem is `SecurityConfig`'s decision, and
 *    the 401 comes from the `AuthenticationEntryPoint`. That keeps one place responsible for
 *    "this endpoint is public" and avoids a filter that 401s requests to `/auth/session`.
 *  - **It only ever *sets* a context, never trusts a pre-existing one.** The context is
 *    cleared before a token is inspected, so nothing left over from a pooled thread can be
 *    mistaken for authentication.
 *  - **The token is verified, not decoded.** [JwtService.parseAccessToken] checks the
 *    signature, algorithm, issuer, expiry and token use; the principal is only set if all of
 *    that passes.
 *
 * The principal is the user's [UUID] and the authority list is empty: this app has no roles
 * yet, and inventing one now would be a guess about a model that does not exist.
 */
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = bearerToken(request)
        if (token != null) {
            SecurityContextHolder.clearContext()
            val userId =
                try {
                    jwtService.parseAccessToken(token)
                } catch (_: InvalidAccessTokenException) {
                    // Anonymous from here on; the entry point decides the response.
                    null
                }
            if (userId != null) {
                val authentication = UsernamePasswordAuthenticationToken(userId, null, emptyList())
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun bearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX, ignoreCase = true)) return null
        return header.substring(BEARER_PREFIX.length).trim().ifEmpty { null }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}

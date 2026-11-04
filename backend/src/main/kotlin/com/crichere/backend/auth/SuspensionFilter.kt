package com.crichere.backend.auth

import com.crichere.backend.common.ProblemDetails
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether an account is suspended (docs/PHASE18.md). Access tokens are stateless, so this is what
 * stops a suspended user during the rest of their token's life. Each answer is held briefly so a
 * busy auction doesn't cost a query per bid: a suspension takes at most [TTL] to bite.
 */
@Component
class SuspensionChecker(
    private val userRepository: UserRepository,
    private val clock: Clock,
) {
    private data class Entry(val suspended: Boolean, val expiresAt: Instant)

    private val cache = ConcurrentHashMap<UUID, Entry>()

    fun isSuspended(userId: UUID): Boolean {
        val now = clock.instant()
        cache[userId]?.takeIf { it.expiresAt.isAfter(now) }?.let { return it.suspended }
        if (cache.size >= MAX_ENTRIES) cache.clear()
        val suspended = userRepository.existsByIdAndSuspendedAtIsNotNull(userId)
        cache[userId] = Entry(suspended, now.plus(TTL))
        return suspended
    }

    private companion object {
        val TTL: Duration = Duration.ofSeconds(30)
        const val MAX_ENTRIES = 50_000
    }
}

/**
 * Refuses every request from a suspended account's still-valid token, including public reads, so
 * the app learns at once and can show the suspended screen. An anonymous request is untouched:
 * anyone can still watch a public page. `/auth/` is left to [AuthService], which has its own checks
 * and must keep logout working.
 *
 * Like [AuthRateLimitFilter], it runs outside the dispatcher servlet, so the body is written by hand.
 */
class SuspensionFilter(
    private val suspensionChecker: SuspensionChecker,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI.startsWith(AUTH_PATH)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val userId = SecurityContextHolder.getContext().authentication?.principal as? UUID
        if (userId != null && suspensionChecker.isSuspended(userId)) {
            response.status = HttpStatus.FORBIDDEN.value()
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            objectMapper.writeValue(response.outputStream, ProblemDetails.accountSuspended(request.requestURI))
            return
        }
        filterChain.doFilter(request, response)
    }

    private companion object {
        const val AUTH_PATH = "/api/v1/auth/"
    }
}

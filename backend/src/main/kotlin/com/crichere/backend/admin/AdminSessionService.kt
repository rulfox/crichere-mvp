package com.crichere.backend.admin

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The signed-in admin, as the admin security chain sees it. */
data class AdminPrincipal(val sessionId: UUID, val email: String, val expiresAt: Instant)

data class IssuedAdminSession(val rawToken: String, val email: String, val expiresAt: Instant)

/** One message for every sign-in refusal, so the response never says who is on the allowlist. */
class AdminSignInFailedException : RuntimeException("Admin sign-in failed")

class AdminSignInRateLimitedException : RuntimeException("Too many admin sign-in attempts")

/** Writes admin events to the shared audit table (moderation_actions, docs/PHASE20.md A6). */
@Component
class AdminAuditWriter(private val jdbcTemplate: JdbcTemplate) {
    fun record(
        action: String,
        adminEmail: String,
        note: String? = null,
        reportId: UUID? = null,
        userId: UUID? = null,
        leagueId: UUID? = null,
    ) {
        jdbcTemplate.update(
            "insert into moderation_actions (id, action, admin_email, report_id, user_id, league_id, note) values (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), action, adminEmail, reportId, userId, leagueId, note?.take(NOTE_LIMIT),
        )
    }

    private companion object {
        const val NOTE_LIMIT = 2000
    }
}

/** Sign-in attempts per client IP. In memory, like the app's other limiters. */
@Component
class AdminSignInRateLimiter(private val properties: AdminProperties) {
    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun tryConsume(clientIp: String): Boolean {
        if (buckets.size >= MAX_KEYS) buckets.clear()
        return buckets.computeIfAbsent(clientIp) {
            Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(properties.signInCapacity).refillGreedy(properties.signInCapacity, properties.signInWindow).build())
                .build()
        }.tryConsume(1)
    }

    /** Test hook. */
    fun reset() = buckets.clear()

    private companion object {
        const val MAX_KEYS = 10_000
    }
}

/**
 * Admin sessions (docs/PHASE20.md A4, G3): an opaque random token in an httpOnly cookie, stored
 * only as a SHA-256 hash. A session lasts [AdminProperties.sessionTtl] with no refresh, ends at
 * once on sign-out, and stops working the moment its email leaves the allowlist.
 */
@Service
class AdminSessionService(
    private val properties: AdminProperties,
    private val repository: AdminSessionRepository,
    private val verifier: GoogleIdTokenVerifier,
    private val audit: AdminAuditWriter,
    private val events: ApplicationEventPublisher,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val random = SecureRandom()

    @Transactional
    fun signIn(idToken: String, userAgent: String?, clientIp: String?): IssuedAdminSession {
        val email = verifier.verifiedEmail(idToken) ?: throw AdminSignInFailedException()
        if (email !in properties.emailSet) throw AdminSignInFailedException()

        val raw = ByteArray(TOKEN_BYTES).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val now = clock.instant()
        val expiresAt = now.plus(properties.sessionTtl)
        repository.save(
            AdminSessionEntity(
                id = UUID.randomUUID(),
                tokenHash = hash(raw),
                email = email,
                createdAt = now,
                expiresAt = expiresAt,
                userAgent = userAgent?.take(USER_AGENT_LIMIT),
            ),
        )
        audit.record("ADMIN_SIGN_IN", email, userAgent)
        // Emailed after commit (AdminSignInAlertListener), so a failed sign-in never sends one.
        events.publishEvent(AdminSignedIn(email, now, clientIp, userAgent))
        return IssuedAdminSession(raw, email, expiresAt)
    }

    @Transactional(readOnly = true)
    fun authenticate(rawToken: String?): AdminPrincipal? {
        if (rawToken.isNullOrBlank() || rawToken.length > MAX_TOKEN_LENGTH) return null
        val session = repository.findByTokenHash(hash(rawToken)) ?: return null
        if (session.revokedAt != null || !session.expiresAt.isAfter(clock.instant())) return null
        if (session.email !in properties.emailSet) return null
        return AdminPrincipal(session.id, session.email, session.expiresAt)
    }

    @Transactional(readOnly = true)
    fun liveSessionCount(principal: AdminPrincipal): Long = repository.countLive(principal.email, clock.instant())

    @Transactional
    fun signOut(principal: AdminPrincipal) {
        repository.findById(principal.sessionId).ifPresent { if (it.revokedAt == null) it.revokedAt = clock.instant() }
        audit.record("ADMIN_SIGN_OUT", principal.email)
        events.publishEvent(AdminSessionsEnded(sessionId = principal.sessionId))
    }

    @Transactional
    fun signOutEverywhere(principal: AdminPrincipal): Int {
        val ended = repository.revokeAllFor(principal.email, clock.instant())
        audit.record("ADMIN_SIGN_OUT_ALL", principal.email, "$ended sessions ended")
        events.publishEvent(AdminSessionsEnded(email = principal.email))
        return ended
    }

    private fun hash(raw: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)))

    private companion object {
        const val TOKEN_BYTES = 32
        const val MAX_TOKEN_LENGTH = 100
        const val USER_AGENT_LIMIT = 300
    }
}

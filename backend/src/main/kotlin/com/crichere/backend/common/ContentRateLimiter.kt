package com.crichere.backend.common

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Rate-limit configuration for content-creation endpoints (league, ground, award). Separate
 * from [com.crichere.backend.auth.RateLimitProperties] -- that one guards the auth surface
 * against credential-stuffing; this one guards the public dashboard against being filled with
 * junk by one account. Keyed on the caller's `userId` rather than phone/IP: unlike auth, these
 * endpoints only run once already authenticated, so the identifying key is trustworthy
 * immediately and there's no anonymous-edge case to cover with a separate IP dimension.
 *
 * The numbers are deliberately generous -- a real organizer creates a handful of leagues, not
 * hundreds, in an hour. Twenty covers someone iterating on a new league/ground a few times while
 * setting it up; awards get a higher ceiling since adding one is a much smaller, more repeatable
 * action (e.g. building out a 10-award prize list) than creating a league or ground itself.
 */
@ConfigurationProperties(prefix = "crichere.content-rate-limit")
data class ContentRateLimitProperties(
    /** Master switch. Turned off in tests that are not about rate limiting. */
    val enabled: Boolean = true,
    val leagueCreateCapacity: Long = 20,
    val groundCreateCapacity: Long = 20,
    val awardCreateCapacity: Long = 100,
    /** The refill period every capacity above uses. */
    val window: Duration = Duration.ofHours(1),
    /** Safety valve on each in-memory bucket map -- see [ContentRateLimiter] for what happens then. */
    val maxTrackedKeys: Int = 100_000,
)

@Configuration
@EnableConfigurationProperties(ContentRateLimitProperties::class)
class ContentRateLimitConfiguration

/**
 * The token buckets behind the content-creation rate limits, held in process memory -- same
 * documented single-instance-deployment limitation as
 * [com.crichere.backend.auth.AuthRateLimiter]; move to a shared store (Redis/Hazelcast) if this
 * API is ever scaled horizontally.
 */
@Component
class ContentRateLimiter(private val properties: ContentRateLimitProperties) {

    private val leagueCreateBuckets = ConcurrentHashMap<UUID, Bucket>()
    private val groundCreateBuckets = ConcurrentHashMap<UUID, Bucket>()
    private val awardCreateBuckets = ConcurrentHashMap<UUID, Bucket>()

    /** @return `null` if the attempt is allowed, or how long the caller must wait if it is not. */
    fun tryConsumeForLeagueCreate(userId: UUID): Duration? =
        consume(leagueCreateBuckets, userId, properties.leagueCreateCapacity)

    /** @return `null` if the attempt is allowed, or how long the caller must wait if it is not. */
    fun tryConsumeForGroundCreate(userId: UUID): Duration? =
        consume(groundCreateBuckets, userId, properties.groundCreateCapacity)

    /** @return `null` if the attempt is allowed, or how long the caller must wait if it is not. */
    fun tryConsumeForAwardCreate(userId: UUID): Duration? =
        consume(awardCreateBuckets, userId, properties.awardCreateCapacity)

    /** Test hook: forget every bucket. Not used by production code. */
    fun reset() {
        leagueCreateBuckets.clear()
        groundCreateBuckets.clear()
        awardCreateBuckets.clear()
    }

    private fun consume(buckets: ConcurrentHashMap<UUID, Bucket>, key: UUID, capacity: Long): Duration? {
        if (!properties.enabled) return null

        // Bound the map -- see AuthRateLimiter's identical reasoning: a blunt full-clear is
        // memory-safe and cannot itself become a denial of service, unlike an unbounded map.
        if (buckets.size >= properties.maxTrackedKeys) buckets.clear()

        val bucket = buckets.computeIfAbsent(key) { newBucket(capacity) }
        val probe = bucket.tryConsumeAndReturnRemaining(1)
        if (probe.isConsumed) return null
        return Duration.ofNanos(probe.nanosToWaitForRefill).coerceAtLeast(Duration.ofSeconds(1))
    }

    private fun newBucket(capacity: Long): Bucket =
        Bucket.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(capacity)
                    .refillGreedy(capacity, properties.window)
                    .build(),
            )
            .build()
}

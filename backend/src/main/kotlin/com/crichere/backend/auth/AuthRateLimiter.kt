package com.crichere.backend.auth

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Rate-limit configuration for the auth endpoints.
 *
 * ## The numbers, and why
 *
 * **10 session attempts per hour per phone number.** A real person signs in once and then
 * lives on refresh tokens for a month. Ten covers a bad day -- reinstall, new device, a
 * couple of failed OTPs, switching between the app and a tablet -- with room to spare, while
 * still capping how fast one number can be hammered.
 *
 * **20 session attempts per hour per IP.** Deliberately more permissive than the per-phone
 * limit, because an IP is a much blunter identifier: an office, a college hostel, or an
 * Indian mobile carrier's CGNAT pool can legitimately put a lot of distinct users behind one
 * address. Twenty an hour still stops an automated token-stuffing script cold. This is the
 * dimension most likely to need tuning once there is real traffic; it is a property, not a
 * constant, precisely so it can be raised without a code change.
 *
 * Either limit tripping rejects the request.
 *
 * `/auth/refresh` and `/auth/logout` are intentionally **not** IP rate-limited. Both require a
 * 256-bit random token that cannot be guessed, so there is nothing to brute-force, and an
 * IP-wide limit on refresh would be the one most likely to lock legitimate CGNAT users out of
 * an app that refreshes every fifteen minutes.
 */
@ConfigurationProperties(prefix = "crichere.rate-limit")
data class RateLimitProperties(
    /** Master switch. Turned off in tests that are not about rate limiting. */
    val enabled: Boolean = true,
    /** Session attempts allowed per [window] for one phone number. */
    val phoneCapacity: Long = 10,
    /** Session attempts allowed per [window] for one client IP. */
    val ipCapacity: Long = 20,
    /** The refill period both limits use. */
    val window: Duration = Duration.ofHours(1),
    /**
     * Safety valve on the in-memory bucket maps. Reached only under a distributed attack with
     * a very large number of distinct keys; see [AuthRateLimiter] for what happens then.
     */
    val maxTrackedKeys: Int = 100_000,
)

@Configuration
@EnableConfigurationProperties(RateLimitProperties::class)
class RateLimitConfiguration

/**
 * The token buckets behind the auth rate limits, held in process memory.
 *
 * In-memory is a deliberate, documented limitation: it is correct for the single-instance
 * deployment this MVP targets, and it means the limiter has no external dependency to fail.
 * If the API is ever scaled horizontally, the limits become per-instance and this needs to
 * move to a shared store (Bucket4j has a Redis/Hazelcast backend for exactly this).
 *
 * ## Where each dimension is applied, and why they differ
 *
 * The **IP** limit is enforced in [AuthRateLimitFilter], at the very edge, before any work
 * happens.
 *
 * The **phone** limit cannot be enforced there. The `/auth/session` request body contains a
 * Firebase ID token, not a phone number -- the phone only exists after that token has been
 * verified. A filter could decode the token's payload without verifying it to read the claim,
 * but then the rate-limit key would be attacker-controlled and trivially rotated, which is
 * worse than useless. So the phone limit is applied inside [AuthService], immediately after
 * Firebase vouches for the number. The IP limit is what protects the verification step itself
 * from being used as a workload amplifier.
 *
 * The phone bucket is keyed on the number's HMAC lookup hash rather than the number itself,
 * so no plaintext phone number is held in the limiter's memory.
 */
@Component
class AuthRateLimiter(private val properties: RateLimitProperties) {

    private val phoneBuckets = ConcurrentHashMap<String, Bucket>()
    private val ipBuckets = ConcurrentHashMap<String, Bucket>()

    /**
     * @return `null` if the attempt is allowed, or how long the caller must wait if it is not.
     */
    fun tryConsumeForPhone(phoneLookupHash: String): Duration? =
        consume(phoneBuckets, phoneLookupHash, properties.phoneCapacity)

    /**
     * @return `null` if the attempt is allowed, or how long the caller must wait if it is not.
     */
    fun tryConsumeForIp(clientIp: String): Duration? =
        consume(ipBuckets, clientIp, properties.ipCapacity)

    /** Test hook: forget every bucket. Not used by production code. */
    fun reset() {
        phoneBuckets.clear()
        ipBuckets.clear()
    }

    private fun consume(buckets: ConcurrentHashMap<String, Bucket>, key: String, capacity: Long): Duration? {
        if (!properties.enabled) return null

        // Bound the map. An attacker cycling through millions of distinct keys would otherwise
        // grow it without limit; dropping every bucket is a blunt reset (it briefly forgives
        // offenders) but it is memory-safe and cannot itself be turned into a denial of
        // service the way an unbounded map can.
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
                    // Greedy refill: tokens trickle back continuously over the window rather
                    // than all at once on the hour, so a blocked caller recovers gradually
                    // instead of getting a full burst allowance at a predictable instant.
                    .refillGreedy(capacity, properties.window)
                    .build(),
            )
            .build()
}

package com.crichere.backend.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Maps to `otp_challenges` (V19). One row per OTP send; see the migration for why the phone is
 * held here and why the counters are persisted.
 *
 * [id] is assigned by the service (not `@GeneratedValue`) because it is also what the client
 * receives as its opaque handle. [verifyAttempts] and [consumedAt] are only ever changed through
 * the atomic queries on [OtpChallengeRepository], never by mutating the entity, so concurrent
 * guesses cannot read-modify-write their way around the attempt cap.
 */
@Entity
@Table(name = "otp_challenges")
class OtpChallengeEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "phone_lookup_hash", nullable = false)
    var phoneLookupHash: String,

    @Column(name = "phone_encrypted", nullable = false)
    var phoneEncrypted: String,

    @Column(name = "provider_req_id", nullable = false)
    var providerReqId: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,

    @Column(name = "last_sent_at", nullable = false)
    var lastSentAt: Instant,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "verify_attempts", nullable = false)
    var verifyAttempts: Int = 0,

    @Column(name = "resends", nullable = false)
    var resends: Int = 0,

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null,
)

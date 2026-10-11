package com.crichere.backend.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Maps to the `users` table (V1__create_users_table.sql, V22__account_deletion.sql). One row per
 * authenticated phone number. The phone number is never stored in plaintext: [phoneLookupHash] is
 * a deterministic hash used to look a user up by phone at login, [phoneEncrypted] is a reversible
 * encrypted value used when the plaintext phone is needed.
 *
 * Both phone columns are `null` only on a purged account ([deletedAt] set): the row stays as a
 * tombstone so other people's leagues keep their history, and the number is free to sign up again
 * (docs/PHASE16.md). [deletionRequestedAt] marks the 14-day grace period before that purge.
 */
@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "phone_lookup_hash", unique = true)
    var phoneLookupHash: String?,

    @Column(name = "phone_encrypted", columnDefinition = "TEXT")
    var phoneEncrypted: String?,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "deletion_requested_at")
    var deletionRequestedAt: Instant? = null,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)

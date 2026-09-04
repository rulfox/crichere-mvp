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
 * Maps to the `users` table (V1__create_users_table.sql). One row per authenticated phone
 * number. The phone number is never stored in plaintext: [phoneLookupHash] is a
 * deterministic hash used to look a user up by phone at login, [phoneEncrypted] is a
 * reversible encrypted value used when the plaintext phone is needed. Both are populated
 * by the auth feature's service layer in a later task -- this entity only maps the schema.
 */
@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "phone_lookup_hash", nullable = false, unique = true)
    var phoneLookupHash: String,

    @Column(name = "phone_encrypted", nullable = false, columnDefinition = "TEXT")
    var phoneEncrypted: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

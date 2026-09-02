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
 * Maps to the `refresh_tokens` table (V3__create_refresh_tokens_table.sql). Only a hash of
 * the token is ever persisted ([tokenHash]) -- the plaintext token is handed to the client
 * once at issuance and never stored. [revokedAt] is set (not deleted) when a token is
 * invalidated so revocation history survives.
 *
 * [userId] is a plain foreign-key column rather than a JPA `@ManyToOne` to [UserEntity]:
 * this entity only needs the id to satisfy the FK constraint, and avoiding an association
 * keeps repository queries simple and lazy-loading semantics out of scope for this task.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "token_hash", nullable = false, unique = true)
    var tokenHash: String,

    @Column(name = "issued_at", nullable = false)
    var issuedAt: Instant,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
)

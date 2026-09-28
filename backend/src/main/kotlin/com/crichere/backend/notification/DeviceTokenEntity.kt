package com.crichere.backend.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Maps to the `device_tokens` table (V17__create_device_tokens_table.sql) -- see docs/PHASE8.md.
 * Unique on [token] alone, not `(userId, token)` -- see that doc's Decisions Made on why an
 * upsert-by-token is what correctly handles signing into a different account on the same device.
 * Plain FK column, no JPA relations, matching every other entity in this codebase.
 */
@Entity
@Table(name = "device_tokens")
class DeviceTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "token", nullable = false, updatable = false)
    var token: String,

    @Column(name = "platform", nullable = false)
    var platform: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

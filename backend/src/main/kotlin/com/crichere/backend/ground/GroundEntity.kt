package com.crichere.backend.ground

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Maps to the `grounds` table (V7__create_grounds_table.sql). A shared, reusable venue --
 * many leagues can reference the same ground via `leagues.ground_id` rather than each
 * re-entering/re-pinning the same physical location (see docs/PHASE2.md's Decisions Made).
 *
 * Not hard-linked to any league's own state/district -- a league can (unusually)
 * reference a ground whose location text disagrees with its own, and nothing here validates
 * against that; see docs/PHASE2.md.
 *
 * [registeredByUserId] is a plain foreign-key column, not a JPA `@ManyToOne`, matching every
 * other user-owned entity in this codebase (`ProfileEntity`, `RefreshTokenEntity`). It is
 * provenance only for now -- Phase 2 has no ground-edit feature to enforce ownership against.
 *
 * No update/delete methods anywhere in this feature -- Phase 2 deliberately has no ground-edit
 * capability (see docs/PHASE2.md's Decisions Made); a mis-registered ground is corrected by
 * registering a new one.
 */
@Entity
@Table(name = "grounds")
class GroundEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "country", nullable = false, length = 2)
    var country: String = "IN",

    @Column(name = "state", nullable = false)
    var state: String,

    @Column(name = "district", nullable = false)
    var district: String,

    @Column(name = "latitude", nullable = false)
    var latitude: Double,

    @Column(name = "longitude", nullable = false)
    var longitude: Double,

    @Column(name = "registered_by_user_id", nullable = false)
    var registeredByUserId: UUID,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

package com.crichere.backend.profile

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Maps to the `profiles` table (V2__create_profiles_table.sql). One-to-one extension of a
 * user, keyed on [userId] -- there is no surrogate id, mirroring the table's `user_id PK`.
 * A user can exist without a profile row (first-time login, profile not completed yet).
 *
 * [userId] is a plain foreign-key column rather than a JPA `@ManyToOne`/`@MapsId` to the
 * auth feature's `UserEntity`: the profile and auth packages must not reach into each
 * other's internals, so this entity only holds the id needed to satisfy the FK constraint.
 *
 * Deliberately has no `profileComplete` field/column -- "complete" is derived on read by
 * application code in a later task, not stored.
 *
 * [playingRole], [battingStyle], and [bowlingStyle] are mapped `@Enumerated(EnumType.STRING)`
 * so the persisted values are the enum constant names, matching the CHECK constraints in the
 * migration exactly.
 */
@Entity
@Table(name = "profiles")
class ProfileEntity(
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,

    @Column(name = "name", length = 100)
    var name: String? = null,

    @Column(name = "photo_url", columnDefinition = "TEXT")
    var photoUrl: String? = null,

    @Column(name = "country", nullable = false, length = 2)
    var country: String = "IN",

    @Column(name = "state")
    var state: String? = null,

    @Column(name = "district")
    var district: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "playing_role")
    var playingRole: PlayingRole? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "batting_style")
    var battingStyle: BattingStyle? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "bowling_style")
    var bowlingStyle: BowlingStyle? = null,
)

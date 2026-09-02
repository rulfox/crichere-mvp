package com.crichere.backend.reference

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Maps to the `states` table (V4__seed_states_cities.sql), seeded with India's states and
 * union territories. [code] is a natural key -- the common vehicle-registration-style
 * abbreviation for the state/UT (e.g. "MH", "KA") -- not a generated surrogate id.
 */
@Entity
@Table(name = "states")
class StateEntity(
    @Id
    @Column(name = "code", nullable = false, updatable = false)
    var code: String,

    @Column(name = "name", nullable = false)
    var name: String,
)

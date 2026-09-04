package com.crichere.backend.reference

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Maps to the `cities` table (V4__seed_states_cities.sql), seeded with a representative set
 * of major cities per Indian state/UT. [stateCode] is a plain foreign-key column (not a JPA
 * `@ManyToOne`) referencing [StateEntity.code] -- both entities live in this same `reference`
 * package, but a plain FK column keeps this reference-data mapping simple since no lazy-load
 * traversal from city to state is needed by anything built so far.
 */
@Entity
@Table(name = "cities")
class CityEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "state_code", nullable = false)
    var stateCode: String,

    @Column(name = "name", nullable = false)
    var name: String,
)

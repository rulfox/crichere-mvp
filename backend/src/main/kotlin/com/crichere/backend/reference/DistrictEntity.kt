package com.crichere.backend.reference

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Maps to the `districts` table (V5__add_districts.sql), seeded with a curated set of real
 * districts covering exactly the cities [CityEntity] already seeds -- not an exhaustive
 * India-wide district dataset, same non-exhaustive posture V4's own doc comment already
 * established for states/cities. [stateCode] is a plain foreign-key column (not a JPA
 * `@ManyToOne`), same reasoning as [CityEntity.stateCode] used to be: everything lives in
 * this one `reference` package, no lazy-load traversal is needed.
 */
@Entity
@Table(name = "districts")
class DistrictEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "state_code", nullable = false)
    var stateCode: String,

    @Column(name = "name", nullable = false)
    var name: String,
)

package com.crichere.backend.reference

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Maps to the `cities` table (V4__seed_states_cities.sql, retrofitted by
 * V5__add_districts.sql). [districtId] is a plain foreign-key column (not a JPA
 * `@ManyToOne`) referencing [DistrictEntity.id] -- both entities live in this same
 * `reference` package, but a plain FK column keeps this reference-data mapping simple since
 * no lazy-load traversal from city to district is needed by anything built so far. A city's
 * state is derivable via its district (no direct `state_code` column any more, since
 * selection always flows State -> District -> City and nothing needs a city's state without
 * already knowing its district).
 */
@Entity
@Table(name = "cities")
class CityEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "district_id", nullable = false)
    var districtId: UUID,

    @Column(name = "name", nullable = false)
    var name: String,
)

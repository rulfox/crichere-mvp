package com.crichere.backend.reference

import com.crichere.backend.common.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Constraint tests for the `states`/`cities` tables (V4__seed_states_cities.sql) via
 * [StateEntity]/[CityEntity] and their repositories. Also verifies the seed data itself
 * loaded (a known state and a known city under it), since that's the actual payload of
 * this migration. Each test runs in its own rolled-back transaction so state never leaks
 * between tests sharing the singleton Testcontainers Postgres from [AbstractIntegrationTest].
 */
@Transactional
class StatesCitiesConstraintTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var stateRepository: StateRepository

    @Autowired
    private lateinit var cityRepository: CityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `seed data loaded a known state and its known cities`() {
        val karnataka = stateRepository.findById("KA").orElseThrow()
        assertTrue(karnataka.name == "Karnataka")

        val karnatakaCities = cityRepository.findByStateCode("KA").map { it.name }
        assertTrue("Bengaluru" in karnatakaCities)
    }

    @Test
    fun `seed data covers all 28 states plus 8 union territories`() {
        assertTrue(stateRepository.count() == 36L)
    }

    @Test
    fun `inserting a city for a non-existent state_code is rejected`() {
        assertFailsWith<DataIntegrityViolationException> {
            cityRepository.saveAndFlush(CityEntity(stateCode = "ZZ", name = "Nowhere City"))
        }
    }

    @Test
    fun `duplicate city name within the same state is rejected`() {
        assertFailsWith<DataIntegrityViolationException> {
            // Bengaluru already exists under KA from the seed data.
            cityRepository.saveAndFlush(CityEntity(stateCode = "KA", name = "Bengaluru"))
        }
    }

    @Test
    fun `the same city name is allowed under a different state`() {
        // No collision expected: uniqueness is scoped to (state_code, name), not name alone.
        cityRepository.saveAndFlush(CityEntity(stateCode = "MH", name = "Bengaluru"))
    }

    @Test
    fun `deleting a state cascades to its cities`() {
        val newState = stateRepository.saveAndFlush(StateEntity(code = "ZT", name = "Zzz Test State"))
        val city = cityRepository.saveAndFlush(CityEntity(stateCode = newState.code, name = "Zzz Test City"))

        stateRepository.delete(newState)
        stateRepository.flush()
        // The cascade delete happens at the database level (ON DELETE CASCADE), not via
        // JPA-managed cascade -- clear the persistence context so the check below actually
        // hits the database instead of returning a stale first-level-cache entry.
        entityManager.clear()

        assertTrue(cityRepository.findById(city.id!!).isEmpty)
    }
}

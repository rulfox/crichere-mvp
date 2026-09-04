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
 * Constraint tests for the `states`/`districts`/`cities` tables (V4__seed_states_cities.sql,
 * retrofitted by V5__add_districts.sql) via [StateEntity]/[DistrictEntity]/[CityEntity] and
 * their repositories. Also verifies the seed data itself loaded (a known state, its known
 * district, and a known city under that district), since that's the actual payload of these
 * migrations. Each test runs in its own rolled-back transaction so state never leaks between
 * tests sharing the singleton Testcontainers Postgres from [AbstractIntegrationTest].
 */
@Transactional
class StatesCitiesConstraintTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var stateRepository: StateRepository

    @Autowired
    private lateinit var districtRepository: DistrictRepository

    @Autowired
    private lateinit var cityRepository: CityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `seed data loaded a known state, its known district, and a known city under it`() {
        val karnataka = stateRepository.findById("KA").orElseThrow()
        assertTrue(karnataka.name == "Karnataka")

        val bengaluruUrban = districtRepository.findByStateCode("KA").first { it.name == "Bengaluru Urban" }
        val karnatakaCities = cityRepository.findByDistrictId(requireNotNull(bengaluruUrban.id)).map { it.name }
        assertTrue("Bengaluru" in karnatakaCities)
    }

    @Test
    fun `seed data covers all 28 states plus 8 union territories`() {
        assertTrue(stateRepository.count() == 36L)
    }

    @Test
    fun `inserting a district for a non-existent state_code is rejected`() {
        assertFailsWith<DataIntegrityViolationException> {
            districtRepository.saveAndFlush(DistrictEntity(stateCode = "ZZ", name = "Nowhere District"))
        }
    }

    @Test
    fun `duplicate district name within the same state is rejected`() {
        assertFailsWith<DataIntegrityViolationException> {
            // Bengaluru Urban already exists under KA from the seed data.
            districtRepository.saveAndFlush(DistrictEntity(stateCode = "KA", name = "Bengaluru Urban"))
        }
    }

    @Test
    fun `inserting a city for a non-existent district is rejected`() {
        assertFailsWith<DataIntegrityViolationException> {
            cityRepository.saveAndFlush(CityEntity(districtId = java.util.UUID.randomUUID(), name = "Nowhere City"))
        }
    }

    @Test
    fun `duplicate city name within the same district is rejected`() {
        val bengaluruUrban = districtRepository.findByStateCode("KA").first { it.name == "Bengaluru Urban" }
        assertFailsWith<DataIntegrityViolationException> {
            // Bengaluru already exists under this district from the seed data.
            cityRepository.saveAndFlush(CityEntity(districtId = requireNotNull(bengaluruUrban.id), name = "Bengaluru"))
        }
    }

    @Test
    fun `the same city name is allowed under a different district`() {
        val bengaluruUrban = districtRepository.findByStateCode("KA").first { it.name == "Bengaluru Urban" }
        val mysuru = districtRepository.findByStateCode("KA").first { it.name == "Mysuru" }
        assertTrue(bengaluruUrban.id != mysuru.id)
        // No collision expected: uniqueness is scoped to (district_id, name), not name alone.
        cityRepository.saveAndFlush(CityEntity(districtId = requireNotNull(mysuru.id), name = "Test City Name"))
        cityRepository.saveAndFlush(CityEntity(districtId = requireNotNull(bengaluruUrban.id), name = "Test City Name"))
    }

    @Test
    fun `deleting a state cascades to its districts and their cities`() {
        val newState = stateRepository.saveAndFlush(StateEntity(code = "ZT", name = "Zzz Test State"))
        val newDistrict = districtRepository.saveAndFlush(DistrictEntity(stateCode = newState.code, name = "Zzz Test District"))
        val city = cityRepository.saveAndFlush(CityEntity(districtId = requireNotNull(newDistrict.id), name = "Zzz Test City"))

        stateRepository.delete(newState)
        stateRepository.flush()
        // The cascade delete happens at the database level (ON DELETE CASCADE), not via
        // JPA-managed cascade -- clear the persistence context so the check below actually
        // hits the database instead of returning a stale first-level-cache entry.
        entityManager.clear()

        assertTrue(districtRepository.findById(requireNotNull(newDistrict.id)).isEmpty)
        assertTrue(cityRepository.findById(requireNotNull(city.id)).isEmpty)
    }
}

package com.crichere.backend.reference

import com.crichere.backend.common.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Constraint tests for the `states`/`districts` tables (V4/V5, reseeded from LGD by V20; the
 * `cities` table was dropped by V21) via [StateEntity]/[DistrictEntity] and their repositories.
 * Also verifies the LGD seed itself loaded. Each test runs in its own rolled-back transaction so
 * state never leaks between tests sharing the singleton Testcontainers Postgres from
 * [AbstractIntegrationTest].
 */
@Transactional
class StatesDistrictsConstraintTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var stateRepository: StateRepository

    @Autowired
    private lateinit var districtRepository: DistrictRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `seed data loaded a known state and its known districts`() {
        val karnataka = stateRepository.findById("KA").orElseThrow()
        assertTrue(karnataka.name == "Karnataka")

        val karnatakaDistricts = districtRepository.findByStateCode("KA").map { it.name }
        assertTrue("Bengaluru Urban" in karnatakaDistricts)
        assertTrue("Mysuru" in karnatakaDistricts)
    }

    @Test
    fun `the LGD seed has every district -- Kerala has all 14`() {
        assertEquals(14, districtRepository.findByStateCode("KL").size)
        assertEquals(784L, districtRepository.count())
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
    fun `deleting a state cascades to its districts`() {
        val newState = stateRepository.saveAndFlush(StateEntity(code = "ZT", name = "Zzz Test State"))
        val newDistrict = districtRepository.saveAndFlush(DistrictEntity(stateCode = newState.code, name = "Zzz Test District"))

        stateRepository.delete(newState)
        stateRepository.flush()
        // The cascade delete happens at the database level (ON DELETE CASCADE), not via
        // JPA-managed cascade -- clear the persistence context so the check below actually
        // hits the database instead of returning a stale first-level-cache entry.
        entityManager.clear()

        assertTrue(districtRepository.findById(requireNotNull(newDistrict.id)).isEmpty)
    }
}

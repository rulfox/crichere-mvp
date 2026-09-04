package com.crichere.backend.reference

import com.crichere.backend.reference.dto.CityResponse
import com.crichere.backend.reference.dto.DistrictResponse
import com.crichere.backend.reference.dto.StateResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Public (`permitAll()` in `SecurityConfig`, unauthenticated by design) reference-data lookups
 * for India's states/union territories, districts, and cities, seeded by
 * `V4__seed_states_cities.sql` + `V5__add_districts.sql`. No service layer: this is a direct,
 * trivial read-through to [StateRepository]/[DistrictRepository]/[CityRepository], and a
 * service class in between would add nothing but indirection.
 */
@RestController
@RequestMapping("/api/v1/reference")
class ReferenceController(
    private val stateRepository: StateRepository,
    private val districtRepository: DistrictRepository,
    private val cityRepository: CityRepository,
) {

    @GetMapping("/states")
    fun listStates(): List<StateResponse> =
        stateRepository.findAll()
            .sortedBy { it.name }
            .map { StateResponse(code = it.code, name = it.name) }

    /**
     * Districts for one state code, e.g. `KA` -> Bengaluru Urban, Mysuru, ...
     *
     * The [state] path segment gets the same two-outcome treatment [ReferenceController] has
     * always used for a state code: syntactically valid but unrecognised -> empty list (`200`);
     * not even shaped like a state code -> `404` via [MalformedStateCodeException].
     */
    @GetMapping("/states/{state}/districts")
    fun listDistrictsForState(@PathVariable state: String): List<DistrictResponse> {
        val code = state.uppercase()
        if (!STATE_CODE_SHAPE.matches(code)) throw MalformedStateCodeException()

        return districtRepository.findByStateCode(code)
            .sortedBy { it.name }
            .map { DistrictResponse(id = requireNotNull(it.id), name = it.name) }
    }

    /**
     * Cities for one district id, e.g. Bengaluru Urban -> Bengaluru. Replaces the pre-District-
     * retrofit `states/{state}/cities` endpoint (District retrofit, see docs/PHASE1.md Section
     * 6) -- a city's state is no longer looked up directly, only via its district.
     *
     * Same "shape first, existence second" split as [listDistrictsForState]: a path segment
     * that isn't a valid UUID can't possibly be a district id -> `404` via
     * [MalformedDistrictIdException]; a syntactically valid UUID with no matching district ->
     * empty list, `200`.
     */
    @GetMapping("/districts/{district}/cities")
    fun listCitiesForDistrict(@PathVariable district: String): List<CityResponse> {
        val districtId = runCatching { UUID.fromString(district) }.getOrElse { throw MalformedDistrictIdException() }

        return cityRepository.findByDistrictId(districtId)
            .sortedBy { it.name }
            .map { CityResponse(name = it.name) }
    }

    private companion object {
        val STATE_CODE_SHAPE = Regex("^[A-Z]{2}$")
    }
}

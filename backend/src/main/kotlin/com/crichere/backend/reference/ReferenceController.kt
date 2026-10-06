package com.crichere.backend.reference

import com.crichere.backend.reference.dto.DistrictResponse
import com.crichere.backend.reference.dto.StateResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public (`permitAll()` in `SecurityConfig`, unauthenticated by design) reference-data lookups
 * for India's states/union territories and districts (LGD data, `V20__refresh_locations_from_lgd.sql`;
 * there is no city tier since `V21__drop_city_require_ground.sql`). No service layer: this is a
 * direct, trivial read-through to [StateRepository]/[DistrictRepository], and a
 * service class in between would add nothing but indirection.
 */
@RestController
@RequestMapping("/api/v1/reference")
class ReferenceController(
    private val stateRepository: StateRepository,
    private val districtRepository: DistrictRepository,
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

    private companion object {
        val STATE_CODE_SHAPE = Regex("^[A-Z]{2}$")
    }
}

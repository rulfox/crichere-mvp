package com.crichere.backend.reference

import com.crichere.backend.reference.dto.CityResponse
import com.crichere.backend.reference.dto.StateResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public (`permitAll()` in `SecurityConfig`, unauthenticated by design) reference-data lookups
 * for India's states/union territories and their cities, seeded by
 * `V4__seed_states_cities.sql`. No service layer: this is a direct, trivial read-through to
 * [StateRepository]/[CityRepository], and a service class in between would add nothing but
 * indirection.
 */
@RestController
@RequestMapping("/api/v1/reference")
class ReferenceController(
    private val stateRepository: StateRepository,
    private val cityRepository: CityRepository,
) {

    @GetMapping("/states")
    fun listStates(): List<StateResponse> =
        stateRepository.findAll()
            .sortedBy { it.name }
            .map { StateResponse(code = it.code, name = it.name) }

    /**
     * Cities for one state code, e.g. `KA` -> Bengaluru, Mysuru, ...
     *
     * The [state] path segment gets two different outcomes depending on its shape, per this
     * task's ruling on the "unknown state code" gap:
     *  - **Syntactically a state code** (exactly two ASCII letters, matching the shape every
     *    seeded [StateEntity.code] actually has) **but not a code that exists** -- an empty
     *    list, `200`. A client walking a picker built from [listStates] can never hit this, but
     *    a stale/hand-typed code is not an error worth a `404` for.
     *  - **Not even shaped like a state code** (wrong length, digits, punctuation, ...) --
     *    treated as "no such resource", `404` `ProblemDetail`, via [MalformedStateCodeException].
     *    This also doubles as this task's coverage of a `404` `ProblemDetail` shape, which
     *    nothing in Task 3 exercised.
     *
     * The lookup itself is case-insensitive (normalised to the stored uppercase form) since
     * there's no reason to make a mobile client get letter-casing exactly right.
     */
    @GetMapping("/states/{state}/cities")
    fun listCitiesForState(@PathVariable state: String): List<CityResponse> {
        val code = state.uppercase()
        if (!STATE_CODE_SHAPE.matches(code)) throw MalformedStateCodeException()

        return cityRepository.findByStateCode(code)
            .sortedBy { it.name }
            .map { CityResponse(name = it.name) }
    }

    private companion object {
        val STATE_CODE_SHAPE = Regex("^[A-Z]{2}$")
    }
}

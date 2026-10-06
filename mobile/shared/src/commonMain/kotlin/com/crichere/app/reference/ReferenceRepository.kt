package com.crichere.app.reference

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * The toolchain-proof data source: a real, unauthenticated GET against the backend's
 * `/api/v1/reference/states` (per this task's ruling -- there is no health-check endpoint on the
 * backend, so the already-built, `permitAll()` reference-data endpoint stands in for one). Proves
 * the shared Ktor client, kotlinx.serialization, and Koin DI wiring all work end to end.
 *
 * [getDistrictsForState] is the same unauthenticated pattern against
 * `GET /api/v1/reference/states/{stateCode}/districts` -- [stateCode] is always the two-letter
 * code (`StateDto.code`), never a display name. District is the finest location tier (no city
 * since design update #6); see `ReferenceController.kt`'s doc for the malformed-vs-unknown distinction (404
 * `ProblemDetail` vs empty `200` list), both of which just propagate/return through here
 * unchanged.
 */
interface ReferenceRepository {
    suspend fun getStates(): List<StateDto>
    suspend fun getDistrictsForState(stateCode: String): List<DistrictDto>
}

internal class KtorReferenceRepository(
    private val httpClient: HttpClient,
) : ReferenceRepository {

    override suspend fun getStates(): List<StateDto> =
        httpClient.get("/api/v1/reference/states").body()

    override suspend fun getDistrictsForState(stateCode: String): List<DistrictDto> =
        httpClient.get("/api/v1/reference/states/$stateCode/districts").body()
}

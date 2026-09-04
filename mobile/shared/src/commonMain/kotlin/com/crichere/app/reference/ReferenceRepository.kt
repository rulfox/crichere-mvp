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
 * [getDistrictsForState]/[getCitiesForDistrict] (District retrofit) are the same unauthenticated
 * pattern against `GET /api/v1/reference/states/{stateCode}/districts` and
 * `GET /api/v1/reference/districts/{districtId}/cities` -- [stateCode] is always the two-letter
 * code (`StateDto.code`), [districtId] is always the district's `DistrictDto.id`, neither is a
 * display name; see `ReferenceController.kt`'s doc for the malformed-vs-unknown distinction (404
 * `ProblemDetail` vs empty `200` list), both of which just propagate/return through here
 * unchanged.
 */
interface ReferenceRepository {
    suspend fun getStates(): List<StateDto>
    suspend fun getDistrictsForState(stateCode: String): List<DistrictDto>
    suspend fun getCitiesForDistrict(districtId: String): List<CityDto>
}

internal class KtorReferenceRepository(
    private val httpClient: HttpClient,
) : ReferenceRepository {

    override suspend fun getStates(): List<StateDto> =
        httpClient.get("/api/v1/reference/states").body()

    override suspend fun getDistrictsForState(stateCode: String): List<DistrictDto> =
        httpClient.get("/api/v1/reference/states/$stateCode/districts").body()

    override suspend fun getCitiesForDistrict(districtId: String): List<CityDto> =
        httpClient.get("/api/v1/reference/districts/$districtId/cities").body()
}

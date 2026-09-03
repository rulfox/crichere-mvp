package com.crichere.app.reference

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * The toolchain-proof data source: a real, unauthenticated GET against the backend's
 * `/api/v1/reference/states` (per this task's ruling -- there is no health-check endpoint on the
 * backend, so the already-built, `permitAll()` reference-data endpoint stands in for one). Proves
 * the shared Ktor client, kotlinx.serialization, and Koin DI wiring all work end to end.
 */
interface ReferenceRepository {
    suspend fun getStates(): List<StateDto>
}

internal class KtorReferenceRepository(
    private val httpClient: HttpClient,
) : ReferenceRepository {

    override suspend fun getStates(): List<StateDto> =
        httpClient.get("/api/v1/reference/states").body()
}

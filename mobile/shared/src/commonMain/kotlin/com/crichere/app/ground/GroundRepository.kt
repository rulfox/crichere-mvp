package com.crichere.app.ground

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Thrown by [GroundRepository.registerGround] for anything other than a clean 2xx. */
class GroundSaveFailedException(message: String) : Exception(message)

/**
 * `commonMain` ground use cases against the real backend (`GET`/`POST /api/v1/grounds`) --
 * follows [com.crichere.app.reference.ReferenceRepository]'s established interface+`Ktor*Impl`
 * pattern. `GET` is public (no auth needed, matches the backend's `permitAll()` posture); `POST`
 * needs the default authenticated client like every other mutation in this app.
 */
interface GroundRepository {
    /** `GET /api/v1/grounds` -- every filter optional; a blank/null [search] with no other filters lists every ground. */
    suspend fun search(search: String? = null, state: String? = null, district: String? = null, city: String? = null): List<GroundDto>

    /** `POST /api/v1/grounds`. No ground-edit feature in Phase 2 -- a wrong ground is corrected by registering a new one. */
    suspend fun registerGround(request: GroundCreateRequestDto): GroundDto
}

internal class KtorGroundRepository(
    private val httpClient: HttpClient,
) : GroundRepository {

    override suspend fun search(search: String?, state: String?, district: String?, city: String?): List<GroundDto> =
        httpClient.get("/api/v1/grounds") {
            search?.let { parameter("search", it) }
            state?.let { parameter("state", it) }
            district?.let { parameter("district", it) }
            city?.let { parameter("city", it) }
        }.body()

    override suspend fun registerGround(request: GroundCreateRequestDto): GroundDto {
        val response = httpClient.post("/api/v1/grounds") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw GroundSaveFailedException("Ground registration failed with status ${response.status}")
        }
        return response.body()
    }
}

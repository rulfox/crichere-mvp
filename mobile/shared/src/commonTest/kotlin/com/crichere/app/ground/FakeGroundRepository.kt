package com.crichere.app.ground

/** In-memory [GroundRepository] test double -- `commonTest` has no real backend to hit. */
class FakeGroundRepository(
    var searchResults: List<GroundDto> = emptyList(),
) : GroundRepository {

    val searchCalls = mutableListOf<String?>()
    var nextRegistered: GroundDto? = null
    val registeredRequests = mutableListOf<GroundCreateRequestDto>()

    override suspend fun search(search: String?, state: String?, district: String?, city: String?): List<GroundDto> {
        searchCalls += search
        return searchResults
    }

    override suspend fun registerGround(request: GroundCreateRequestDto): GroundDto {
        registeredRequests += request
        return nextRegistered ?: error("nextRegistered not stubbed")
    }
}

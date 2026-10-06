package com.crichere.app.ground

/** In-memory [GroundRepository] test double -- no real backend to hit in tests. */
class FakeGroundRepository(
    var searchResults: List<GroundDto> = emptyList(),
) : GroundRepository {

    val searchCalls = mutableListOf<String?>()
    var nextRegistered: GroundDto? = null
    val registeredRequests = mutableListOf<GroundCreateRequestDto>()

    override suspend fun search(search: String?, state: String?, district: String?): List<GroundDto> {
        searchCalls += search
        return searchResults
    }

    override suspend fun registerGround(request: GroundCreateRequestDto): GroundDto {
        registeredRequests += request
        return nextRegistered ?: error("nextRegistered not stubbed")
    }
}

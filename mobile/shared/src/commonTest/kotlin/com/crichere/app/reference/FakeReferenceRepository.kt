package com.crichere.app.reference

/** In-memory [ReferenceRepository] test double -- `commonTest` has no real backend to hit. */
class FakeReferenceRepository(
    var states: List<StateDto> = emptyList(),
    var citiesByStateCode: Map<String, List<CityDto>> = emptyMap(),
) : ReferenceRepository {

    val getCitiesForStateCalls = mutableListOf<String>()

    override suspend fun getStates(): List<StateDto> = states

    override suspend fun getCitiesForState(stateCode: String): List<CityDto> {
        getCitiesForStateCalls += stateCode
        return citiesByStateCode[stateCode] ?: emptyList()
    }
}

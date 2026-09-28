package com.crichere.app.reference

/** In-memory [ReferenceRepository] test double -- no real backend to hit in tests. */
class FakeReferenceRepository(
    var states: List<StateDto> = emptyList(),
    var districtsByStateCode: Map<String, List<DistrictDto>> = emptyMap(),
    var citiesByDistrictId: Map<String, List<CityDto>> = emptyMap(),
) : ReferenceRepository {

    val getDistrictsForStateCalls = mutableListOf<String>()
    val getCitiesForDistrictCalls = mutableListOf<String>()

    override suspend fun getStates(): List<StateDto> = states

    override suspend fun getDistrictsForState(stateCode: String): List<DistrictDto> {
        getDistrictsForStateCalls += stateCode
        return districtsByStateCode[stateCode] ?: emptyList()
    }

    override suspend fun getCitiesForDistrict(districtId: String): List<CityDto> {
        getCitiesForDistrictCalls += districtId
        return citiesByDistrictId[districtId] ?: emptyList()
    }
}

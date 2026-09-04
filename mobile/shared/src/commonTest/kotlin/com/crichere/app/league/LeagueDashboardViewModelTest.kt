@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.league

import com.crichere.app.auth.viewModelTest
import com.crichere.app.location.FakeLocationProvider
import com.crichere.app.location.GeoPoint
import com.crichere.app.reference.CityDto
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.FakeReferenceRepository
import com.crichere.app.reference.StateDto
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [LeagueDashboardViewModel] coverage: initial list load, State/District/City filter cascade
 * (mirrors `ProfileSetupViewModel`'s pattern), and "near me" being mutually exclusive with the
 * area filters (see docs/PHASE2.md's Decisions Made).
 */
class LeagueDashboardViewModelTest {

    private val karnataka = StateDto(code = "KA", name = "Karnataka")
    private val bengaluruUrban = DistrictDto(id = "d-ka-1", name = "Bengaluru Urban")
    private val bengaluruCity = CityDto(name = "Bengaluru")

    private fun sampleLeague(id: String = "l1") = LeagueDto(
        id = id,
        organizerUserId = "u1",
        name = "Weekend League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = "2026-10-12",
        status = LeagueStatus.ANNOUNCED,
    )

    private fun newViewModel(
        leagueRepository: FakeLeagueRepository = FakeLeagueRepository(),
        referenceRepository: FakeReferenceRepository = FakeReferenceRepository(
            states = listOf(karnataka),
            districtsByStateCode = mapOf("KA" to listOf(bengaluruUrban)),
            citiesByDistrictId = mapOf(bengaluruUrban.id to listOf(bengaluruCity)),
        ),
        locationProvider: FakeLocationProvider = FakeLocationProvider(),
    ) = LeagueDashboardViewModel(leagueRepository, referenceRepository, locationProvider)

    @Test
    fun `loads announced leagues with no filters on init`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val viewModel = newViewModel(leagueRepository = leagueRepository)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.leagues.size)
        assertEquals(listOf(Triple<String?, String?, String?>(null, null, null)), leagueRepository.listByAreaCalls)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `selecting a state clears near mode and re-lists by area`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository()
        val viewModel = newViewModel(leagueRepository = leagueRepository)
        advanceUntilIdle()

        viewModel.onStateSelected(karnataka)
        advanceUntilIdle()

        assertEquals("Karnataka", viewModel.state.value.selectedState)
        assertEquals(listOf(bengaluruUrban), viewModel.state.value.districts)
        assertFalse(viewModel.state.value.isNearMode)
        assertTrue(leagueRepository.listByAreaCalls.any { it.first == "Karnataka" })
    }

    @Test
    fun `selecting a district then a city narrows the filter and clears the other's stale selection`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository()
        val viewModel = newViewModel(leagueRepository = leagueRepository)
        advanceUntilIdle()
        viewModel.onStateSelected(karnataka)
        advanceUntilIdle()

        viewModel.onDistrictSelected(bengaluruUrban)
        advanceUntilIdle()
        assertEquals(listOf(bengaluruCity), viewModel.state.value.cities)

        viewModel.onCitySelected(bengaluruCity)
        advanceUntilIdle()

        val lastCall = leagueRepository.listByAreaCalls.last()
        assertEquals(Triple("Karnataka", "Bengaluru Urban", "Bengaluru"), lastCall)
    }

    @Test
    fun `enabling near me clears area filters and lists by nearest`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesNearest = listOf(sampleLeague()))
        val locationProvider = FakeLocationProvider(location = GeoPoint(12.9716, 77.5946))
        val viewModel = newViewModel(leagueRepository = leagueRepository, locationProvider = locationProvider)
        advanceUntilIdle()
        viewModel.onStateSelected(karnataka)
        advanceUntilIdle()

        viewModel.onToggleNearMe()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isNearMode)
        assertNull(viewModel.state.value.selectedState)
        assertEquals(listOf(12.9716 to 77.5946), leagueRepository.listNearestCalls)
        assertEquals(1, viewModel.state.value.leagues.size)
    }

    @Test
    fun `near me is a no-op when location is unavailable -- does not enter near mode`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository()
        val locationProvider = FakeLocationProvider(location = null)
        val viewModel = newViewModel(leagueRepository = leagueRepository, locationProvider = locationProvider)
        advanceUntilIdle()

        viewModel.onToggleNearMe()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isNearMode)
        assertTrue(viewModel.state.value.errorMessage != null)
        assertTrue(leagueRepository.listNearestCalls.isEmpty())
    }

    @Test
    fun `toggling near me off reverts to the area filters`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository()
        val locationProvider = FakeLocationProvider(location = GeoPoint(12.9716, 77.5946))
        val viewModel = newViewModel(leagueRepository = leagueRepository, locationProvider = locationProvider)
        advanceUntilIdle()
        viewModel.onToggleNearMe()
        advanceUntilIdle()

        viewModel.onToggleNearMe()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isNearMode)
        assertTrue(leagueRepository.listByAreaCalls.isNotEmpty())
    }
}

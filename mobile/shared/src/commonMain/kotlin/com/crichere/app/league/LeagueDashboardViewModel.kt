package com.crichere.app.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crichere.app.location.GeoPoint
import com.crichere.app.location.LocationProvider
import com.crichere.app.reference.CityDto
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.ReferenceRepository
import com.crichere.app.reference.StateDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeagueDashboardState(
    val isLoading: Boolean = true,
    val leagues: List<LeagueDto> = emptyList(),
    val states: List<StateDto> = emptyList(),
    val districts: List<DistrictDto> = emptyList(),
    val cities: List<CityDto> = emptyList(),
    val selectedState: String? = null,
    val selectedDistrict: String? = null,
    val selectedCity: String? = null,
    /** "Near me" is mutually exclusive with the area filters above -- see docs/PHASE2.md's Decisions Made. */
    val isNearMode: Boolean = false,
    val isLocating: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * League Dashboard's ViewModel: the app's new post-login landing content (see `AppStartViewModel`/
 * `AuthNavHost`'s `Main` destination). Lists announced leagues, filterable by State/District/City
 * or by "nearest to me" -- the two modes are mutually exclusive, matching the UI decision in
 * docs/PHASE2.md: selecting an area filter clears "near me" and vice versa.
 */
class LeagueDashboardViewModel(
    private val leagueRepository: LeagueRepository,
    private val referenceRepository: ReferenceRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(LeagueDashboardState())
    val state: StateFlow<LeagueDashboardState> = _state.asStateFlow()

    private var lastNearPoint: GeoPoint? = null

    init {
        viewModelScope.launch {
            val states = runCatching { referenceRepository.getStates() }.getOrDefault(emptyList())
            _state.update { it.copy(states = states) }
        }
        // Deliberately not calling refresh() here -- this ViewModel outlives a single dashboard
        // visit (Koin's koinViewModel() with no key returns the same instance every time the
        // Dashboard tab is re-entered, e.g. after creating or completing a league), so a one-time
        // init load would go stale the moment the underlying list changes elsewhere. The route
        // composable calls refresh() itself on every entry instead -- see LeagueDashboardRoute.
    }

    /** Re-runs whichever list (area filters or nearest) is currently active. Also the pull-to-refresh entry point. */
    fun refresh() {
        viewModelScope.launch { loadLeagues() }
    }

    fun onStateSelected(stateDto: StateDto) {
        _state.update {
            it.copy(
                selectedState = stateDto.name,
                selectedDistrict = null,
                districts = emptyList(),
                selectedCity = null,
                cities = emptyList(),
                isNearMode = false,
            )
        }
        viewModelScope.launch {
            loadDistricts(stateDto.code)
            loadLeagues()
        }
    }

    fun onDistrictSelected(districtDto: DistrictDto) {
        _state.update { it.copy(selectedDistrict = districtDto.name, selectedCity = null, cities = emptyList(), isNearMode = false) }
        viewModelScope.launch {
            loadCities(districtDto.id)
            loadLeagues()
        }
    }

    fun onCitySelected(cityDto: CityDto) {
        _state.update { it.copy(selectedCity = cityDto.name, isNearMode = false) }
        viewModelScope.launch { loadLeagues() }
    }

    fun onClearAreaFilters() {
        _state.update {
            it.copy(
                selectedState = null,
                selectedDistrict = null,
                selectedCity = null,
                districts = emptyList(),
                cities = emptyList(),
            )
        }
        viewModelScope.launch { loadLeagues() }
    }

    /** User-initiated -- never auto-triggered (that would fire an unprompted permission dialog). */
    fun onToggleNearMe() {
        if (_state.value.isNearMode) {
            lastNearPoint = null
            _state.update { it.copy(isNearMode = false) }
            viewModelScope.launch { loadLeagues() }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLocating = true,
                    isNearMode = true,
                    selectedState = null,
                    selectedDistrict = null,
                    selectedCity = null,
                    districts = emptyList(),
                    cities = emptyList(),
                )
            }
            val point = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
            if (point == null) {
                lastNearPoint = null
                _state.update { it.copy(isLocating = false, isNearMode = false, errorMessage = "Couldn't get your location.") }
                return@launch
            }
            lastNearPoint = point
            _state.update { it.copy(isLocating = false) }
            loadLeagues()
        }
    }

    private suspend fun loadDistricts(stateCode: String) {
        val districts = runCatching { referenceRepository.getDistrictsForState(stateCode) }.getOrDefault(emptyList())
        _state.update { it.copy(districts = districts) }
    }

    private suspend fun loadCities(districtId: String) {
        val cities = runCatching { referenceRepository.getCitiesForDistrict(districtId) }.getOrDefault(emptyList())
        _state.update { it.copy(cities = cities) }
    }

    private suspend fun loadLeagues() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        val current = _state.value
        val near = lastNearPoint

        runCatching {
            if (current.isNearMode && near != null) {
                leagueRepository.listNearest(near.latitude, near.longitude)
            } else {
                leagueRepository.listByArea(current.selectedState, current.selectedDistrict, current.selectedCity)
            }
        }
            .onSuccess { leagues -> _state.update { it.copy(isLoading = false, leagues = leagues) } }
            .onFailure { throwable ->
                _state.update {
                    it.copy(isLoading = false, errorMessage = throwable.message ?: "Couldn't load leagues. Please try again.")
                }
            }
    }
}

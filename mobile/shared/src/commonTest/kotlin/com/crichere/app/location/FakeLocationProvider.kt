package com.crichere.app.location

/**
 * In-memory [LocationProvider] test double -- per this task's Testing Strategy, GPS-match logic
 * must be unit-testable "using a fake `LocationProvider`, not a real one".
 */
class FakeLocationProvider(
    var location: GeoPoint? = null,
    var geocoded: GeocodedLocation? = null,
) : LocationProvider {

    var getCurrentLocationCallCount = 0
        private set
    var reverseGeocodeCallCount = 0
        private set

    override suspend fun getCurrentLocation(): GeoPoint? {
        getCurrentLocationCallCount++
        return location
    }

    override suspend fun reverseGeocode(point: GeoPoint): GeocodedLocation? {
        reverseGeocodeCallCount++
        return geocoded
    }
}

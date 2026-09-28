package com.crichere.app.location

/** In-memory [LocationProvider] test double -- no real GPS/geocoder to hit in tests. */
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

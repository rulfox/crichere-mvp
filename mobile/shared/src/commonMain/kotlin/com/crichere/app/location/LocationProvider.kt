package com.crichere.app.location

/** A raw GPS fix -- degrees, WGS84, same convention `android.location.Location`/`CLLocation` use. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/**
 * The result of reverse-geocoding a [GeoPoint] into human-readable place names. Both fields are
 * best-effort and nullable -- the platform geocoder may resolve one but not the other, or neither.
 *
 * @property administrativeArea The state/province-level name (Android: `Address.adminArea`; iOS:
 *   `CLPlacemark.administrativeArea`), matched against the fetched states list's display name.
 * @property subAdministrativeArea The district-level name (Android: `Address.subAdminArea`; iOS:
 *   `CLPlacemark.subAdministrativeArea`), matched against the matched state's fetched districts list.
 *   District is the finest location tier (no city since design update #6).
 */
data class GeocodedLocation(
    val administrativeArea: String?,
    val subAdministrativeArea: String?,
)

/**
 * Platform-agnostic GPS + reverse-geocoding contract for Profile Setup's "use my location"
 * auto-fill. [DeviceLocationProvider] is the real, per-platform `expect`/`actual` implementation
 * (Android: `FusedLocationProviderClient`/`LocationManager` + `android.location.Geocoder`; iOS:
 * `CLLocationManager`/`CLGeocoder`).
 *
 * `ProfileSetupViewModel` depends on this interface, not the concrete `expect class` directly --
 * same reasoning `PhoneAuthClient` documents for its own `FirebasePhoneAuthClient` boundary --
 * so `commonTest` can substitute a fake, deterministic implementation for the GPS-match-against-
 * fetched-list logic per this task's Testing Strategy ("using a fake `LocationProvider`, not a
 * real one").
 *
 * Both methods return `null` on any expected failure (permission denied, no fix available, or no
 * geocoder match) rather than throwing -- per this task's ruling, a failed auto-fill is a no-op,
 * never a blocking error, since the state/district selectors are always hand-editable regardless.
 */
interface LocationProvider {
    /** A best-effort current fix, or `null` if location permission is denied or no fix is available. */
    suspend fun getCurrentLocation(): GeoPoint?

    /** Best-effort reverse geocoding of [point], or `null` if the platform geocoder can't resolve it. */
    suspend fun reverseGeocode(point: GeoPoint): GeocodedLocation?
}

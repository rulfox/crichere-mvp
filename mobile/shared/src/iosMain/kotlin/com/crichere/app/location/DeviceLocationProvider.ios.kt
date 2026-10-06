package com.crichere.app.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS [LocationProvider]: `CLLocationManager` for the fix, `CLGeocoder` for reverse geocoding --
 * authored against the real, documented Core Location API but **unverified**: this environment
 * has no Mac/Xcode to compile/link/run Swift or Kotlin/Native-against-CoreLocation code, same
 * standing note as every other `iosMain` file in this repo (see task-5/6-report.md).
 *
 * Unlike Android's [DeviceLocationProvider] (which reads a Koin-injected `Context`), this needs
 * no per-instance dependency -- `CLLocationManager`/`CLGeocoder` are usable directly.
 *
 * Requesting the actual "when in use" authorization prompt is left to the app shell (`iosAppApp.swift`
 * would need `NSLocationWhenInUseUsageDescription` in `Info.plist` plus a
 * `requestWhenInUseAuthorization()` call before this is first used) -- this class only reads
 * whatever authorization status already exists and returns `null` (a no-op, per this task's
 * ruling) rather than requesting it itself, mirroring the Android side's "request at the UI layer,
 * check-and-no-op at the provider layer" split.
 */
@OptIn(ExperimentalForeignApi::class)
class DeviceLocationProvider : LocationProvider {

    private val locationManager = CLLocationManager()

    override suspend fun getCurrentLocation(): GeoPoint? {
        val status = locationManager.authorizationStatus
        if (!isAuthorized(status)) return null

        return suspendCancellableCoroutine { continuation ->
            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                    val location = didUpdateLocations.lastOrNull() as? CLLocation
                    if (continuation.isActive) {
                        val coordinate = location?.coordinate
                        continuation.resume(
                            coordinate?.useContents { GeoPoint(latitude = latitude, longitude = longitude) },
                        )
                    }
                    manager.stopUpdatingLocation()
                }

                override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                    if (continuation.isActive) continuation.resume(null)
                    manager.stopUpdatingLocation()
                }
            }
            locationManager.delegate = delegate
            continuation.invokeOnCancellation { locationManager.stopUpdatingLocation() }
            locationManager.requestLocation()
        }
    }

    override suspend fun reverseGeocode(point: GeoPoint): GeocodedLocation? {
        val location = CLLocation(latitude = point.latitude, longitude = point.longitude)
        return suspendCancellableCoroutine { continuation ->
            CLGeocoder().reverseGeocodeLocation(location) { placemarks, error ->
                if (!continuation.isActive) return@reverseGeocodeLocation
                if (error != null) {
                    continuation.resume(null)
                    return@reverseGeocodeLocation
                }
                val placemark = (placemarks?.firstOrNull() as? platform.CoreLocation.CLPlacemark)
                continuation.resume(
                    placemark?.let {
                        GeocodedLocation(
                            administrativeArea = it.administrativeArea,
                            subAdministrativeArea = it.subAdministrativeArea,
                        )
                    },
                )
            }
        }
    }

    private fun isAuthorized(status: CLAuthorizationStatus): Boolean =
        status == kCLAuthorizationStatusAuthorizedAlways || status == kCLAuthorizationStatusAuthorizedWhenInUse
}

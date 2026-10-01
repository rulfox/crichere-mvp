package com.crichere.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Android [LocationProvider]: plain `android.location.LocationManager` for the GPS fix rather than
 * `FusedLocationProviderClient` -- avoids adding the Play Services location dependency for a
 * feature that only needs a best-effort, state/city-level fix (not fused/high-accuracy tracking),
 * and this app has no other Play Services dependency to amortize the cost against. Reverse
 * geocoding uses the platform's own `android.location.Geocoder`.
 *
 * Genuinely runnable on the Android emulator: the AVDs on this machine support setting a custom
 * location (extended controls / `adb emu geo fix`), which drives both `LocationManager`'s GPS
 * provider and `Geocoder`'s real (Google Play Services-backed on a Google APIs system image)
 * geocoding for real -- see task-7-report.md for what was verified.
 *
 * [context] is injected the same way `SecureStorage`'s Android `actual` takes one (via Koin's
 * `androidContext`), not an `expect`/`actual class` -- `LocationProvider` is a plain `commonMain`
 * interface (see its doc), so there's no shared constructor signature to keep in sync across
 * platforms the way `FirebasePhoneAuthClient`'s no-arg `expect class` needs.
 */
class DeviceLocationProvider(private val context: Context) : LocationProvider {

    override suspend fun getCurrentLocation(): GeoPoint? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        // City-level precision is all this feature needs, so a recent fix from any provider beats
        // waiting on a fresh one. Only the network provider used to be asked, and it often has no
        // fix at all (found on-device: fused and GPS had recent fixes, network had none), which
        // surfaced as "Couldn't get your location."
        lastKnown(locationManager, maxAgeMillis = RECENT_FIX_MAX_AGE_MILLIS)?.let { return it }

        val fresh = bestAvailableProvider(locationManager)?.let { provider ->
            runCatching { requestLocation(locationManager, provider) }.getOrNull()
        }
        return fresh ?: lastKnown(locationManager, maxAgeMillis = Long.MAX_VALUE)
    }

    /** Most recent last-known fix across enabled providers, if it is at most [maxAgeMillis] old. */
    @SuppressLint("MissingPermission") // Guarded by getCurrentLocation()'s permission check.
    private fun lastKnown(locationManager: LocationManager, maxAgeMillis: Long): GeoPoint? {
        val newest = locationManager.getProviders(true)
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?: return null
        val ageMillis = (SystemClock.elapsedRealtimeNanos() - newest.elapsedRealtimeNanos) / 1_000_000
        return if (ageMillis <= maxAgeMillis) GeoPoint(newest.latitude, newest.longitude) else null
    }

    // Lint's MissingPermission check can't trace the ACCESS_COARSE_LOCATION guard in
    // getCurrentLocation() (above) across this private helper's call boundary -- the guard
    // already runs on every path that reaches here.
    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(locationManager: LocationManager, provider: String): GeoPoint? =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+: a real one-shot fix via the modern callback API.
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                locationManager.getCurrentLocation(provider, cancellationSignal, context.mainExecutor) { location ->
                    if (continuation.isActive) {
                        continuation.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
                    }
                }
            } else {
                // Pre-API 30 has no one-shot getCurrentLocation; the last known fix is sufficient
                // for this feature's state/city-level precision need (not live tracking).
                @Suppress("DEPRECATION")
                val location = locationManager.getLastKnownLocation(provider)
                continuation.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
            }
        }

    /**
     * `Geocoder.getFromLocation` (the overload used below) is a **synchronous, blocking** call --
     * real network I/O that can take multiple seconds. Dispatched onto [Dispatchers.IO] so it
     * never blocks the caller's thread: this is invoked from `ProfileSetupViewModel.useMyLocation`
     * inside `viewModelScope.launch {}`, whose default dispatcher is `Dispatchers.Main.immediate`
     * -- without this `withContext`, the call would block the UI thread for its duration, a real
     * ANR risk on a physical device. (Caught by code review; this environment's emulator testing
     * never actually reached this line, since `adb emu geo fix` never delivered a fix to
     * `getCurrentLocation()` here -- see task-7-report.md.)
     */
    override suspend fun reverseGeocode(point: GeoPoint): GeocodedLocation? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION") // The synchronous overload is deprecated (API 33+) but still functional; the async
                // callback overload adds real complexity (another suspendCancellableCoroutine bridge) for a
                // best-effort, non-latency-sensitive lookup this feature doesn't need. Running it on
                // Dispatchers.IO (see this method's doc) is what keeps it safe to call synchronously.
                val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                addresses?.firstOrNull()?.let { address ->
                    GeocodedLocation(
                        administrativeArea = address.adminArea,
                        subAdministrativeArea = address.subAdminArea,
                        locality = address.locality,
                    )
                }
            }.getOrNull()
        }
    }

    // GPS_PROVIDER requires ACCESS_FINE_LOCATION, which this app never requests -- only
    // ACCESS_COARSE_LOCATION, matching this feature's actual state/city-level precision need
    // (see this class's doc). Requesting GPS_PROVIDER with only coarse permission throws a
    // SecurityException that getCurrentLocation()'s runCatching silently swallows as null, so
    // "Use my location" would look like it does nothing at all -- confirmed on a real device: the
    // request returned null in under a millisecond, not after any real GPS search. NETWORK_PROVIDER
    // is the one coarse permission actually authorizes.
    private fun bestAvailableProvider(locationManager: LocationManager): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> null
    }

    private companion object {
        const val RECENT_FIX_MAX_AGE_MILLIS = 10 * 60 * 1000L
    }
}

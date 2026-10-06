package com.crichere.app.location

import android.location.Address
import android.location.Geocoder
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Robolectric coverage for [DeviceLocationProvider.reverseGeocode]'s dispatcher usage -- per code
 * review, `Geocoder.getFromLocation` is a synchronous, blocking (real network I/O) call, and
 * calling it undispatched from `viewModelScope.launch {}` (default `Dispatchers.Main.immediate`)
 * would block the UI thread for its duration, a real ANR risk on a physical device. The fix wraps
 * the call in `withContext(Dispatchers.IO)`; this test proves that wrapping actually takes effect
 * by recording which thread [ShadowRecordingGeocoder] observes the call arrive on, and asserting
 * it differs from the thread the test itself (standing in for the caller/UI thread) runs on. This
 * test fails without the `withContext(Dispatchers.IO)` fix (both threads would be the same, since
 * `kotlinx-coroutines-test`'s `runTest` confines everything to one thread by default) and passes
 * with it (Dispatchers.IO dispatches onto its own thread pool).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowRecordingGeocoder::class])
class DeviceLocationProviderTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `reverseGeocode runs the blocking Geocoder call off the calling thread`() = runTest {
        val callingThreadName = Thread.currentThread().name
        ShadowRecordingGeocoder.invokedFromThreadName = null
        val provider = DeviceLocationProvider(context)

        val result = provider.reverseGeocode(GeoPoint(latitude = 12.9716, longitude = 77.5946))

        assertEquals("Karnataka", result?.administrativeArea)
        assertNotEquals(
            callingThreadName,
            ShadowRecordingGeocoder.invokedFromThreadName,
            "Geocoder.getFromLocation must not run on the caller's thread -- see withContext(Dispatchers.IO) in reverseGeocode()",
        )
    }
}

/**
 * Records which thread [getFromLocation] was actually invoked on, and returns a canned address
 * instantly -- standing in for a real (slow, blocking) geocoder lookup so the test above can
 * assert on dispatching without needing an actual multi-second network call.
 */
@Implements(Geocoder::class)
class ShadowRecordingGeocoder {

    companion object {
        var invokedFromThreadName: String? = null

        // Geocoder.isPresent() is a *static* real method (API 33+) -- Robolectric requires the
        // shadow method to be static too (a companion @JvmStatic member), unlike getFromLocation
        // below which shadows a real instance method.
        @Suppress("unused")
        @JvmStatic
        @Implementation
        fun isPresent(): Boolean = true
    }

    @Suppress("unused")
    @Implementation
    fun getFromLocation(latitude: Double, longitude: Double, maxResults: Int): List<Address> {
        invokedFromThreadName = Thread.currentThread().name
        val address = Address(Locale.getDefault())
        address.adminArea = "Karnataka"
        address.locality = "Bengaluru"
        return listOf(address)
    }
}

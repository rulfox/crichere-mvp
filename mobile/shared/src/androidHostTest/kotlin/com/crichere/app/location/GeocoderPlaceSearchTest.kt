package com.crichere.app.location

import android.location.Address
import android.location.Geocoder
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowNameGeocoder::class])
class GeocoderPlaceSearchTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun reset() {
        ShadowNameGeocoder.addresses = emptyList()
        ShadowNameGeocoder.lastCall = null
        ShadowNameGeocoder.throwOnCall = false
    }

    @Test
    fun `maps addresses to results with coordinates, restricted to India's box`() = runTest {
        ShadowNameGeocoder.addresses = listOf(
            address(feature = "Rajaram College", line = "Vidyanagar, Kolhapur, Maharashtra 416004", lat = 16.6767, lng = 74.2563),
        )

        val results = GeocoderPlaceSearch(context).search("Rajaram College, Kolhapur", near = null)

        val call = ShadowNameGeocoder.lastCall!!
        assertEquals("Rajaram College, Kolhapur", call.query)
        assertEquals(listOf(6.0, 68.0, 37.5, 97.5), call.bounds)
        assertEquals(1, results.size)
        assertEquals("Rajaram College", results[0].title)
        assertEquals("Vidyanagar, Kolhapur, Maharashtra 416004", results[0].subtitle)
        assertEquals(GeoPoint(16.6767, 74.2563), results[0].point)
    }

    @Test
    fun `a house-number feature name falls back to the locality as title`() = runTest {
        ShadowNameGeocoder.addresses = listOf(address(feature = "12", line = "12, MG Road, Pune", locality = "Pune", lat = 18.5, lng = 73.8))

        val result = GeocoderPlaceSearch(context).search("12 MG Road", near = null).single()

        assertEquals("Pune", result.title)
        assertEquals("12, MG Road, Pune", result.subtitle)
    }

    @Test
    fun `runs the blocking lookup off the calling thread`() = runTest {
        val callingThread = Thread.currentThread().name

        GeocoderPlaceSearch(context).search("Kolhapur", near = null)

        assertNotEquals(callingThread, ShadowNameGeocoder.lastCall?.threadName)
    }

    @Test
    fun `a geocoder failure is an empty result, not a crash`() = runTest {
        ShadowNameGeocoder.throwOnCall = true

        assertTrue(GeocoderPlaceSearch(context).search("Kolhapur", near = null).isEmpty())
    }

    @Test
    fun `a blank query never reaches the geocoder`() = runTest {
        assertTrue(GeocoderPlaceSearch(context).search("  ", near = null).isEmpty())
        assertNull(ShadowNameGeocoder.lastCall)
    }

    private fun address(feature: String?, line: String?, lat: Double, lng: Double, locality: String? = null) =
        Address(Locale.getDefault()).apply {
            featureName = feature
            line?.let { setAddressLine(0, it) }
            this.locality = locality
            latitude = lat
            longitude = lng
        }
}

@Implements(Geocoder::class)
class ShadowNameGeocoder {

    data class Call(val query: String, val bounds: List<Double>, val threadName: String)

    companion object {
        var addresses: List<Address> = emptyList()
        var lastCall: Call? = null
        var throwOnCall = false

        @Suppress("unused")
        @JvmStatic
        @Implementation
        fun isPresent(): Boolean = true
    }

    @Suppress("unused")
    @Implementation
    fun getFromLocationName(
        locationName: String,
        maxResults: Int,
        lowerLeftLatitude: Double,
        lowerLeftLongitude: Double,
        upperRightLatitude: Double,
        upperRightLongitude: Double,
    ): List<Address> {
        lastCall = Call(locationName, listOf(lowerLeftLatitude, lowerLeftLongitude, upperRightLatitude, upperRightLongitude), Thread.currentThread().name)
        if (throwOnCall) throw java.io.IOException("grpc failed")
        return addresses
    }
}

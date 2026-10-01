package com.crichere.app.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * [PlaceSearch] backed by the platform's `android.location.Geocoder` -- no API key, no billing.
 * No suggestions while typing (each lookup is a real network round trip with no session pricing to
 * amortize it), so it runs only when the user presses search.
 *
 * Results are restricted to India's bounding box, the only country this app serves; [search]'s
 * `near` is unused because the geocoder has no soft bias, only that hard box -- the league's city
 * is appended to the query by the caller instead.
 */
class GeocoderPlaceSearch(private val context: Context) : PlaceSearch {

    override val searchesAsYouType: Boolean = false

    /** Blocking network I/O (same as [DeviceLocationProvider.reverseGeocode]) -- kept off the caller's thread. */
    override suspend fun search(query: String, near: GeoPoint?): List<PlaceResult> {
        if (query.isBlank() || !Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())
        return withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION") // Synchronous overload; see DeviceLocationProvider.reverseGeocode for why it's kept.
                val addresses = geocoder.getFromLocationName(
                    query.trim(), MAX_RESULTS,
                    INDIA_SOUTH_LAT, INDIA_WEST_LNG, INDIA_NORTH_LAT, INDIA_EAST_LNG,
                )
                addresses.orEmpty().mapIndexed { index, address -> address.toPlaceResult(index) }
            }.getOrDefault(emptyList())
        }
    }

    override suspend fun locate(result: PlaceResult): GeoPoint? = result.point

    private fun Address.toPlaceResult(index: Int): PlaceResult {
        val line = if (maxAddressLineIndex >= 0) getAddressLine(0) else null
        val title = featureName?.takeIf { it.isNotBlank() && !it.all { c -> c.isDigit() } }
            ?: locality ?: line ?: "${latitude}, ${longitude}"
        return PlaceResult(
            id = "geocoder-$index",
            title = title,
            subtitle = line?.takeIf { it != title },
            point = GeoPoint(latitude, longitude),
        )
    }

    private companion object {
        const val MAX_RESULTS = 5
        const val INDIA_SOUTH_LAT = 6.0
        const val INDIA_WEST_LNG = 68.0
        const val INDIA_NORTH_LAT = 37.5
        const val INDIA_EAST_LNG = 97.5
    }
}

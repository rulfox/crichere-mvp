package com.crichere.app.location

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.tasks.await

/**
 * [PlaceSearch] backed by Google Places autocomplete (Places API (New), same key as Maps). Lives in
 * `androidApp`, not `shared`, since it needs the key from this module's `BuildConfig` and the
 * Android-only Places SDK.
 *
 * Billing: keystrokes and the final [locate] share one [AutocompleteSessionToken], so a search
 * that ends in a pick is billed as one session; the token is renewed after each pick.
 */
class GooglePlacesSearch(context: Context, apiKey: String) : PlaceSearch {

    private val client: PlacesClient
    private var sessionToken = AutocompleteSessionToken.newInstance()

    init {
        if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context.applicationContext, apiKey)
        client = Places.createClient(context.applicationContext)
    }

    override val searchesAsYouType: Boolean = true

    override suspend fun search(query: String, near: GeoPoint?): List<PlaceResult> {
        if (query.isBlank()) return emptyList()
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query.trim())
            .setSessionToken(sessionToken)
            .setCountries(listOf("IN"))
            .apply { near?.let { setLocationBias(CircularBounds.newInstance(LatLng(it.latitude, it.longitude), BIAS_RADIUS_METERS)) } }
            .build()
        return runCatching {
            client.findAutocompletePredictions(request).await().autocompletePredictions.map {
                PlaceResult(
                    id = it.placeId,
                    title = it.getPrimaryText(null).toString(),
                    subtitle = it.getSecondaryText(null).toString().ifBlank { null },
                    point = null,
                )
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun locate(result: PlaceResult): GeoPoint? {
        result.point?.let { return it }
        val request = FetchPlaceRequest.builder(result.id, listOf(Place.Field.LOCATION)).setSessionToken(sessionToken).build()
        sessionToken = AutocompleteSessionToken.newInstance()
        return runCatching {
            client.fetchPlace(request).await().place.location?.let { GeoPoint(it.latitude, it.longitude) }
        }.getOrNull()
    }

    private companion object {
        const val BIAS_RADIUS_METERS = 50_000.0
    }
}

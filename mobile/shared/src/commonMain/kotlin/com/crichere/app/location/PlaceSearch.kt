package com.crichere.app.location

/**
 * One match for a typed place name. [point] is filled when the backend already returned
 * coordinates with the match (the platform geocoder does); otherwise [PlaceSearch.locate] resolves
 * it (Google Places only returns a place id per suggestion).
 */
data class PlaceResult(
    val id: String,
    val title: String,
    val subtitle: String?,
    val point: GeoPoint?,
)

/**
 * Finds places by name for the register-ground map's search box -- the map jumps to the picked
 * result, and the fixed centre pin then reads the location. Two implementations, picked at build
 * time (see docs/PHASE2.md, "Ground map search"): the platform geocoder (default, no API/billing)
 * and Google Places autocomplete (Android only, needs the Places API on the Maps key).
 *
 * Both methods return empty / `null` on any failure rather than throwing -- search is a
 * convenience, the map is always pannable by hand.
 */
interface PlaceSearch {
    /** `true`: query on every (debounced) keystroke. `false`: only when the user presses search. */
    val searchesAsYouType: Boolean

    /** Up to a handful of matches for [query], ranked best first; [near] biases toward the league's area when known. */
    suspend fun search(query: String, near: GeoPoint?): List<PlaceResult>

    /** The coordinates of a [result] -- [PlaceResult.point] when present, otherwise a lookup. */
    suspend fun locate(result: PlaceResult): GeoPoint?
}

/**
 * [PlaceSearch.search] scoped to the league's area. Places takes [near] as a soft bias. The
 * geocoder has no bias, so "Rajaram College" alone can land in another state: it's asked for
 * "query, [areaName]" first and falls back to the bare query only when that finds nothing (the
 * ground may sit just outside the league's city).
 */
suspend fun PlaceSearch.searchNear(query: String, areaName: String?, near: GeoPoint?): List<PlaceResult> {
    if (query.isBlank()) return emptyList()
    if (searchesAsYouType || areaName.isNullOrBlank()) return search(query, near)
    return search("${query.trim()}, $areaName", near).ifEmpty { search(query, near) }
}

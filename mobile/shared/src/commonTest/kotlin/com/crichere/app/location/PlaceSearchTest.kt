package com.crichere.app.location

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceSearchTest {

    private class RecordingPlaceSearch(
        override val searchesAsYouType: Boolean,
        private val answers: Map<String, List<PlaceResult>> = emptyMap(),
    ) : PlaceSearch {
        val queries = mutableListOf<String>()
        val nears = mutableListOf<GeoPoint?>()

        override suspend fun search(query: String, near: GeoPoint?): List<PlaceResult> {
            queries += query
            nears += near
            return answers[query].orEmpty()
        }

        override suspend fun locate(result: PlaceResult): GeoPoint? = result.point
    }

    private val college = PlaceResult("1", "Rajaram College", "Kolhapur, Maharashtra", GeoPoint(16.70, 74.24))
    private val kolhapur = GeoPoint(16.70, 74.24)

    @Test
    fun `geocoder search tries the query scoped to the league's area first`() = runTest {
        val search = RecordingPlaceSearch(searchesAsYouType = false, answers = mapOf("Rajaram College, Kolhapur, Maharashtra" to listOf(college)))

        val results = search.searchNear("Rajaram College ", "Kolhapur, Maharashtra", near = null)

        assertEquals(listOf(college), results)
        assertEquals(listOf("Rajaram College, Kolhapur, Maharashtra"), search.queries)
    }

    @Test
    fun `geocoder search falls back to the bare query when the scoped one finds nothing`() = runTest {
        val search = RecordingPlaceSearch(searchesAsYouType = false, answers = mapOf("Wankhede Stadium" to listOf(college)))

        val results = search.searchNear("Wankhede Stadium", "Kolhapur, Maharashtra", near = null)

        assertEquals(listOf(college), results)
        assertEquals(listOf("Wankhede Stadium, Kolhapur, Maharashtra", "Wankhede Stadium"), search.queries)
    }

    @Test
    fun `as-you-type search sends the bare query once with the area centre as bias`() = runTest {
        val search = RecordingPlaceSearch(searchesAsYouType = true, answers = mapOf("Rajaram" to listOf(college)))

        val results = search.searchNear("Rajaram", "Kolhapur, Maharashtra", near = kolhapur)

        assertEquals(listOf(college), results)
        assertEquals(listOf("Rajaram"), search.queries)
        assertEquals(listOf<GeoPoint?>(kolhapur), search.nears)
    }

    @Test
    fun `no area name means a single bare query`() = runTest {
        val search = RecordingPlaceSearch(searchesAsYouType = false)

        search.searchNear("Shivaji Park", areaName = null, near = null)

        assertEquals(listOf("Shivaji Park"), search.queries)
    }

    @Test
    fun `a blank query searches nothing`() = runTest {
        val search = RecordingPlaceSearch(searchesAsYouType = false)

        assertTrue(search.searchNear("   ", "Kolhapur", near = null).isEmpty())
        assertTrue(search.queries.isEmpty())
    }
}

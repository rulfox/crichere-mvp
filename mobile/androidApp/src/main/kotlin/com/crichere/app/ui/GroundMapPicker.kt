package com.crichere.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

private val DEFAULT_CAMERA_POSITION = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 4f) // India, whole-country zoom

/**
 * New-ground registration's map-pin picker: a draggable [Marker] the user positions over the
 * ground's real location, backed by [GoogleMap] (`com.google.maps.android:maps-compose`) -- see
 * `AppModule.kt`'s doc and `androidApp/build.gradle.kts` for the still-unprovisioned API-key gate
 * this degrades gracefully under (renders a blank/attribution-only map, doesn't crash).
 *
 * [initialLatitude]/[initialLongitude] seed the marker (e.g. from [com.crichere.app.location.LocationProvider]'s
 * "use my location" fix, resolved by the caller before this composable is shown); every drag calls
 * [onPositionChanged] with the marker's new coordinates so [com.crichere.app.league.LeagueCreationViewModel.onNewGroundPositionChanged]
 * always reflects exactly where the pin sits.
 */
@Composable
internal fun GroundMapPicker(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onPositionChanged: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialLatLng = remember(initialLatitude, initialLongitude) {
        if (initialLatitude != null && initialLongitude != null) LatLng(initialLatitude, initialLongitude) else null
    }

    val cameraPositionState = rememberCameraPositionState {
        position = initialLatLng?.let { CameraPosition.fromLatLngZoom(it, 15f) } ?: DEFAULT_CAMERA_POSITION
    }
    val markerState = rememberMarkerState(position = initialLatLng ?: DEFAULT_CAMERA_POSITION.target)

    // A GPS fix that arrives after this composable is already up (e.g. "use my location" resolves
    // mid-registration) should move the existing pin/camera, not just seed the initial one.
    LaunchedEffect(initialLatLng) {
        if (initialLatLng != null && markerState.position != initialLatLng) {
            markerState.position = initialLatLng
            cameraPositionState.centerOn(initialLatLng)
        }
    }

    LaunchedEffect(markerState.position) {
        onPositionChanged(markerState.position.latitude, markerState.position.longitude)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Drag the pin to the ground's exact location")
        GoogleMap(
            modifier = Modifier.fillMaxWidth().height(240.dp),
            cameraPositionState = cameraPositionState,
        ) {
            Marker(
                state = markerState,
                draggable = true,
            )
        }
    }
}

private fun CameraPositionState.centerOn(latLng: LatLng) {
    position = CameraPosition.fromLatLngZoom(latLng, 15f)
}

package com.crichere.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crichere.app.R
import com.crichere.app.league.LeagueCreationState
import com.crichere.app.league.LeagueCreationViewModel
import com.crichere.app.ui.theme.CrichereErrorStrong
import com.crichere.app.ui.theme.JetBrainsMonoFamily
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import java.util.Locale

private val DEFAULT_CAMERA_POSITION = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 4f) // India, whole-country zoom

/**
 * Design I5/I11: registering a new ground, full screen. The map fills the screen with a draggable
 * pin; a sheet at the bottom takes the ground's name, shows the pin's coordinates and any error
 * (missing name inline, everything else in a banner), with Cancel / Register ground.
 */
@Composable
internal fun GroundRegisterOverlay(state: LeagueCreationState, viewModel: LeagueCreationViewModel) {
    val colors = MaterialTheme.colorScheme
    BackHandler(onBack = viewModel::onCancelRegisteringNewGround)

    Box(Modifier.fillMaxSize().background(colors.background).clickable(enabled = false) {}) {
        GroundMapPicker(
            initialLatitude = state.newGroundLatitude,
            initialLongitude = state.newGroundLongitude,
            onPositionChanged = viewModel::onNewGroundPositionChanged,
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 13.dp, end = 13.dp, top = 1.dp)
                .fillMaxWidth()
                .height(44.dp)
                .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                .background(Color.White, RoundedCornerShape(22.dp))
                .padding(start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_pan_tool), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Drag the pin to the ground's exact location", style = pText(13.sp, FontWeight.Medium, 15.6.sp), color = colors.onBackground)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.background, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 19.dp, end = 19.dp, top = 11.dp, bottom = 26.dp),
        ) {
            CrichereTextField(
                value = state.newGroundName,
                onValueChange = viewModel::onNewGroundNameChanged,
                label = "New ground name",
                look = FieldVariant.Form,
                error = if (state.newGroundNameError) "Enter the ground name" else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            Spacer(Modifier.height(12.dp))
            val lat = state.newGroundLatitude
            val lng = state.newGroundLongitude
            Text(
                if (lat != null && lng != null) String.format(Locale.US, "%.4f, %.4f", lat, lng) else "Pin not placed yet",
                style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 12.sp),
                color = colors.onSurfaceVariant,
            )
            val errorTitle = state.groundErrorTitle
            if (errorTitle != null) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = CrichereErrorStrong, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(errorTitle, style = pText(13.sp, FontWeight.SemiBold, 17.55.sp), color = CrichereErrorStrong)
                        state.groundErrorMessage?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(it, style = pText(12.sp, lineHeight = 16.8.sp), color = CrichereErrorStrong)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, colors.outline, RoundedCornerShape(22.dp))
                        .clickable(onClick = viewModel::onCancelRegisteringNewGround),
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", style = pText(13.5.sp, FontWeight.SemiBold), color = colors.primary) }
                Row(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.primary)
                        .clickable(enabled = !state.isRegisteringGround, onClick = viewModel::registerNewGround),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isRegisteringGround) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.isRegisteringGround) "Registering…" else "Register ground", style = pText(13.5.sp, FontWeight.SemiBold), color = Color.White)
                }
            }
        }
    }
}

/**
 * The map with a draggable green pin, backed by [GoogleMap] (`com.google.maps.android:maps-compose`)
 * -- without an API key it renders a blank map rather than crashing.
 *
 * [initialLatitude]/[initialLongitude] seed the pin; every drag calls [onPositionChanged] with the
 * pin's new coordinates so the ViewModel always reflects exactly where the pin sits.
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

    // A GPS fix that arrives after this composable is already up should move the existing
    // pin/camera, not just seed the initial one.
    LaunchedEffect(initialLatLng) {
        if (initialLatLng != null && markerState.position != initialLatLng) {
            markerState.position = initialLatLng
            cameraPositionState.centerOn(initialLatLng)
        }
    }

    // The very first emission fires on initial composition, before the user has done anything --
    // if there's no real seed yet, that first position is just DEFAULT_CAMERA_POSITION's
    // placeholder, not a location anyone chose. Reporting it would let "register" silently save
    // that placeholder if the user never drags the pin at all.
    var hasEmittedInitialPosition by remember { mutableStateOf(false) }
    LaunchedEffect(markerState.position) {
        val isFirstEmission = !hasEmittedInitialPosition
        hasEmittedInitialPosition = true
        if (isFirstEmission && initialLatLng == null) return@LaunchedEffect
        onPositionChanged(markerState.position.latitude, markerState.position.longitude)
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
    ) {
        MarkerComposable(state = markerState, draggable = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_location_on_filled), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Box(Modifier.size(14.dp, 5.dp).background(Color.Black.copy(alpha = 0.25f), CircleShape))
            }
        }
    }
}

private fun CameraPositionState.centerOn(latLng: LatLng) {
    position = CameraPosition.fromLatLngZoom(latLng, 15f)
}

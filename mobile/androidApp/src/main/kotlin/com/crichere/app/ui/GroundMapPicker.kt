package com.crichere.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crichere.app.BuildConfig
import com.crichere.app.R
import com.crichere.app.league.LeagueCreationState
import com.crichere.app.league.LeagueCreationViewModel
import com.crichere.app.location.GeoPoint
import com.crichere.app.location.GeocoderPlaceSearch
import com.crichere.app.location.GooglePlacesSearch
import com.crichere.app.location.PlaceResult
import com.crichere.app.location.PlaceSearch
import com.crichere.app.location.searchNear
import com.crichere.app.ui.theme.CrichereErrorStrong
import com.crichere.app.ui.theme.JetBrainsMonoFamily
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val DEFAULT_CAMERA_POSITION = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 4f) // India, whole-country zoom
private const val CITY_ZOOM = 13f
private const val GROUND_ZOOM = 16f
private const val AS_YOU_TYPE_MIN_CHARS = 3
private const val AS_YOU_TYPE_DEBOUNCE_MS = 300L

/**
 * Design I5/I11: registering a new ground, full screen. The map fills the screen under a fixed
 * centre pin -- the user moves the map, not the pin (owner decision 2026-10-02: Google Maps only
 * drags a marker after a long-press, which read as "the pin can't be moved"). A search box on top
 * jumps the map to a typed place; a sheet at the bottom takes the ground's name, shows the pin's
 * coordinates and any error (missing name inline, everything else in a banner), with Cancel /
 * Register ground.
 *
 * The location is reported to the ViewModel each time the map comes to rest, but only once the
 * user has actually placed it (a gesture, a search pick, or an existing seed) -- the camera's
 * starting point (India, or the league's city) is not a location anyone chose.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroundRegisterOverlay(state: LeagueCreationState, viewModel: LeagueCreationViewModel) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = viewModel::onCancelRegisteringNewGround)

    val placeSearch = remember { createPlaceSearch(context) }
    val seed = remember {
        val lat = state.newGroundLatitude
        val lng = state.newGroundLongitude
        if (lat != null && lng != null) LatLng(lat, lng) else null
    }
    val cameraPositionState = rememberCameraPositionState {
        position = seed?.let { CameraPosition.fromLatLngZoom(it, GROUND_ZOOM) } ?: DEFAULT_CAMERA_POSITION
    }
    var pinPlaced by remember { mutableStateOf(seed != null) }
    val areaName = listOfNotNull(state.city, state.district, state.state).joinToString(", ").ifBlank { null }
    var areaCentre by remember { mutableStateOf<GeoPoint?>(null) }

    // Open on the league's city rather than all of India. Always the free platform geocoder --
    // one lookup per open isn't worth a billed Places session.
    LaunchedEffect(Unit) {
        if (seed != null || areaName == null) return@LaunchedEffect
        val point = GeocoderPlaceSearch(context).search(areaName, near = null).firstOrNull()?.point ?: return@LaunchedEffect
        areaCentre = point
        if (!pinPlaced && !cameraPositionState.isMoving) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), CITY_ZOOM), 700)
        }
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) {
            if (cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) pinPlaced = true
        } else if (pinPlaced) {
            val target = cameraPositionState.position.target
            viewModel.onNewGroundPositionChanged(target.latitude, target.longitude)
        }
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var noMatch by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun runSearch(text: String, debounce: Boolean) {
        searchJob?.cancel()
        searchJob = scope.launch {
            if (debounce) delay(AS_YOU_TYPE_DEBOUNCE_MS)
            isSearching = true
            try {
                val found = placeSearch.searchNear(text, areaName, areaCentre)
                results = found
                noMatch = found.isEmpty()
            } finally {
                isSearching = false
            }
        }
    }

    fun pick(result: PlaceResult) {
        searchJob?.cancel()
        focusManager.clearFocus()
        results = emptyList()
        noMatch = false
        query = result.title
        scope.launch {
            val point = placeSearch.locate(result)
            if (point == null) {
                noMatch = true
                return@launch
            }
            pinPlaced = true
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), GROUND_ZOOM), 700)
        }
    }

    // The pin sits at the centre of the map area left between the search box and the sheet; the
    // map's content padding matches, so the camera target is exactly what the pin points at.
    var mapTopPx by remember { mutableIntStateOf(0) }
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    // U4 C1: while the ground name is being typed the sheet shrinks to a 68 dp bar (field + Register) and
    // the search box, hint and coordinates step aside, so the pin keeps most of the map. It follows the
    // keyboard: the bar shows while the name field has focus and the IME is up.
    var nameFocused by remember { mutableStateOf(false) }
    val compact = nameFocused && WindowInsets.isImeVisible
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val mapPadding = with(density) {
        PaddingValues(top = if (compact) statusBarTop else mapTopPx.toDp(), bottom = sheetHeightPx.toDp())
    }

    Box(Modifier.fillMaxSize().background(colors.background).clickable(enabled = false) {}) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentPadding = mapPadding,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
            onMapClick = { focusManager.clearFocus() },
        )

        CentrePin(lifted = cameraPositionState.isMoving, modifier = Modifier.fillMaxSize().padding(mapPadding))

        AnimatedVisibility(
            visible = !compact,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 13.dp, end = 13.dp, top = 1.dp)
                .fillMaxWidth(),
        ) {
            MapSearchBar(
                query = query,
                isSearching = isSearching,
                searchesAsYouType = placeSearch.searchesAsYouType,
                onQueryChange = { text ->
                    query = text
                    noMatch = false
                    results = emptyList()
                    searchJob?.cancel()
                    isSearching = false
                    if (placeSearch.searchesAsYouType && text.trim().length >= AS_YOU_TYPE_MIN_CHARS) runSearch(text, debounce = true)
                },
                onSearch = { if (query.isNotBlank()) runSearch(query, debounce = false) },
                onClear = {
                    searchJob?.cancel()
                    query = ""
                    results = emptyList()
                    noMatch = false
                    isSearching = false
                },
                modifier = Modifier.onGloballyPositioned { mapTopPx = (it.positionInRoot().y + it.size.height).toInt() },
            )
            Spacer(Modifier.height(8.dp))
            when {
                results.isNotEmpty() -> SearchResults(results, onPick = ::pick)
                noMatch -> SearchMessage("No match. Try a nearby landmark, or move the map by hand.")
                else -> MoveMapHint()
            }
        }
        }

        val sheetShape = RoundedCornerShape(topStart = if (compact) 20.dp else 24.dp, topEnd = if (compact) 20.dp else 24.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { sheetHeightPx = it.height }
                .then(if (compact) Modifier.shadow(14.dp, sheetShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f)) else Modifier)
                .background(colors.background, sheetShape)
                .navigationBarsPadding()
                .imePadding()
                // Compact: 10 dp around a 48 dp field; the field's own 7 dp notch room counts toward the top 10.
                .padding(start = if (compact) 12.dp else 19.dp, end = if (compact) 12.dp else 19.dp, top = if (compact) 3.dp else 11.dp, bottom = if (compact) 10.dp else 26.dp),
        ) {
            // One field in both layouts, so focus and the keyboard survive the switch.
            Row(verticalAlignment = Alignment.Bottom) {
                CrichereTextField(
                    value = state.newGroundName,
                    onValueChange = viewModel::onNewGroundNameChanged,
                    label = "New ground name",
                    look = if (compact) FieldVariant.Form.copy(height = 48.dp) else FieldVariant.Form,
                    error = if (state.newGroundNameError) "Enter the ground name" else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f).onFocusChanged { nameFocused = it.isFocused },
                )
                if (compact) {
                    Spacer(Modifier.width(8.dp))
                    val canRegister = state.newGroundName.isNotBlank() && !state.isRegisteringGround
                    Box(
                        Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (canRegister) colors.primary else CompactRegisterDisabled)
                            .clickable(enabled = canRegister, onClick = viewModel::registerNewGround)
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Register", style = pText(13.5.sp, FontWeight.SemiBold), color = if (canRegister) Color.White else CompactRegisterDisabledText)
                    }
                }
            }
            if (!compact) {
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
}

private val CompactRegisterDisabled = Color(0xFFDCE0D7)
private val CompactRegisterDisabledText = Color(0xFF8A948C)

/**
 * `PLACE_SEARCH_PROVIDER` in local.properties picks the search backend (see androidApp's
 * build.gradle.kts). "places" without a Maps key falls back to the geocoder rather than a
 * search box that silently never answers.
 */
private fun createPlaceSearch(context: Context): PlaceSearch =
    if (BuildConfig.PLACE_SEARCH_PROVIDER == "places" && BuildConfig.MAPS_API_KEY.isNotBlank()) {
        GooglePlacesSearch(context, BuildConfig.MAPS_API_KEY)
    } else {
        GeocoderPlaceSearch(context)
    }

/** The green pin, its tip on the centre of [modifier]'s area; it lifts while the map moves. */
@Composable
private fun CentrePin(lifted: Boolean, modifier: Modifier) {
    val lift by animateDpAsState(if (lifted) (-10).dp else 0.dp, label = "pinLift")
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.size(14.dp, 5.dp).background(Color.Black.copy(alpha = 0.25f), CircleShape))
        // ic_location_on's tip is at 22/24 of the icon's height: 20dp below its centre at 48dp.
        Icon(
            painterResource(R.drawable.ic_location_on_filled),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.offset(y = (-20).dp + lift).size(48.dp),
        )
    }
}

@Composable
private fun MapSearchBar(
    query: String,
    isSearching: Boolean,
    searchesAsYouType: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .background(Color.White, RoundedCornerShape(22.dp))
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    if (searchesAsYouType) "Search a ground, college or area" else "Search a ground, college or area, then press search",
                    style = pText(13.sp, lineHeight = 15.6.sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = pText(13.sp, FontWeight.Medium, 15.6.sp).copy(color = colors.onBackground),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            when {
                isSearching -> CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                query.isNotEmpty() -> Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = "Clear search",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClear).padding(7.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(results: List<PlaceResult>, onPick: (PlaceResult) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .background(Color.White, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
    ) {
        results.forEachIndexed { index, result ->
            if (index > 0) HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(result) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(result.title, style = pText(13.5.sp, FontWeight.SemiBold, 17.sp), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                result.subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = pText(12.sp, lineHeight = 15.sp), color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(message: String) {
    Text(
        message,
        style = pText(12.5.sp, lineHeight = 16.sp),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun MoveMapHint() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .shadow(6.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_pan_tool), contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Move the map to put the pin on the ground", style = pText(12.sp, FontWeight.Medium, 14.sp), color = colors.onBackground)
    }
}

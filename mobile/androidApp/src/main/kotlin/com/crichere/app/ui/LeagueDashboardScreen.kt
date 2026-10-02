package com.crichere.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.crichere.app.R
import com.crichere.app.league.LeagueDashboardState
import com.crichere.app.league.LeagueDashboardViewModel
import com.crichere.app.league.LeagueDto
import com.crichere.app.reference.CityDto
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.StateDto
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereInkSubtle
import com.crichere.app.ui.theme.CrichereOutlineDisabled
import com.crichere.app.ui.theme.InstrumentSansFamily
import com.crichere.app.ui.theme.JetBrainsMonoFamily
import com.crichere.app.ui.theme.LocalCrichereExtraColors
import org.koin.compose.viewmodel.koinViewModel

/** Resolves [LeagueDashboardViewModel] via Koin -- the app's post-login landing content (see `AuthNavHost`'s `Main` destination). */
@Composable
internal fun LeagueDashboardRoute(
    onOpenLeague: (String) -> Unit,
    onCreateLeague: () -> Unit,
    onOpenProfile: () -> Unit = {},
    viewModel: LeagueDashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var locationDenied by rememberSaveable { mutableStateOf(false) }

    // The ViewModel survives leaving and re-entering this tab (see its doc), so a fresh fetch on
    // every visit -- not just the ViewModel's own one-time init -- is what keeps a just-created or
    // just-completed league from looking like it never happened until the user taps Refresh.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        locationDenied = !granted
        if (granted) viewModel.onToggleNearMe()
    }

    LeagueDashboardScreen(
        state = state,
        showLocationOff = locationDenied,
        onStateSelected = viewModel::onStateSelected,
        onDistrictSelected = viewModel::onDistrictSelected,
        onCitySelected = viewModel::onCitySelected,
        onClearAreaFilters = viewModel::onClearAreaFilters,
        onToggleNearMe = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (granted || state.isNearMode) {
                viewModel.onToggleNearMe()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
        onOpenLocationSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            locationDenied = false
        },
        onDismissLocationOff = { locationDenied = false },
        onRefresh = viewModel::refresh,
        onOpenLeague = onOpenLeague,
        onCreateLeague = onCreateLeague,
        onOpenProfile = onOpenProfile,
    )
}

private enum class AreaLevel(val title: String, val search: String) {
    STATE("Choose state", "Search states"),
    DISTRICT("Choose district", "Search districts"),
    CITY("Choose city", "Search cities"),
}

/**
 * League Dashboard (design board screen D): announced leagues, filterable by State/District/City or
 * "nearest to me" (mutually exclusive -- see `LeagueDashboardViewModel`'s doc). Rendered inside
 * `AuthNavHost`'s `MainRoute` tab `Scaffold`, which owns the bottom navigation.
 */
@Composable
private fun LeagueDashboardScreen(
    state: LeagueDashboardState,
    showLocationOff: Boolean,
    onStateSelected: (StateDto) -> Unit,
    onDistrictSelected: (DistrictDto) -> Unit,
    onCitySelected: (CityDto) -> Unit,
    onClearAreaFilters: () -> Unit,
    onToggleNearMe: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onDismissLocationOff: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLeague: (String) -> Unit,
    onCreateLeague: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    var openPicker by remember { mutableStateOf<AreaLevel?>(null) }
    val hasAreaFilter = !state.isNearMode && (state.selectedState != null || state.selectedDistrict != null || state.selectedCity != null)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 19.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Leagues",
                        style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 28.sp, letterSpacing = (-0.84).sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    ViewerAvatar(name = state.viewerName, photoUrl = state.viewerPhotoUrl, onClick = onOpenProfile)
                }
                if (showLocationOff) {
                    Spacer(Modifier.height(10.dp))
                    LocationOffBanner(onOpenSettings = onOpenLocationSettings, onNotNow = onDismissLocationOff)
                    Spacer(Modifier.height(14.dp))
                } else {
                    Spacer(Modifier.height(16.dp))
                }
                NearMePill(isLocating = state.isLocating, isNearMode = state.isNearMode, onClick = onToggleNearMe)
                Spacer(Modifier.height(10.dp))
                // Area filters and "near me" are mutually exclusive: dim the chips while near-me owns the list.
                val areaLocked = state.isNearMode || state.isLocating
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).alpha(if (areaLocked) 0.5f else 1f),
                ) {
                    AreaChip("State", state.selectedState, enabled = !areaLocked) { openPicker = AreaLevel.STATE }
                    AreaChip("District", state.selectedDistrict, enabled = !areaLocked && state.selectedState != null) { openPicker = AreaLevel.DISTRICT }
                    AreaChip("City", state.selectedCity, enabled = !areaLocked && state.selectedDistrict != null) { openPicker = AreaLevel.CITY }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (hasAreaFilter) {
                        TextAction(R.drawable.ic_filter_alt_off, "Clear filters", onClearAreaFilters)
                    }
                    Spacer(Modifier.weight(1f))
                    TextAction(R.drawable.ic_refresh, "Refresh", onRefresh)
                }
                val error = state.errorMessage
                if (error != null) {
                    Text(
                        error,
                        style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 12.5.sp),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.outlineVariant, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                }
                state.leagues.isEmpty() -> EmptyState(showClearFilters = hasAreaFilter, onClearFilters = onClearAreaFilters)
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 10.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.leagues, key = { it.id }) { league ->
                        LeagueCard(league = league, onClick = { onOpenLeague(league.id) })
                    }
                }
            }
        }

        CreateLeagueFab(onClick = onCreateLeague, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 15.dp, bottom = 12.dp))
    }

    val level = openPicker
    val dismiss = { openPicker = null }
    when (level) {
        AreaLevel.STATE -> AreaPickerSheet(level.title, level.search, state.states, state.selectedState, StateDto::name, dismiss) {
            openPicker = null
            onStateSelected(it)
        }
        AreaLevel.DISTRICT -> AreaPickerSheet(level.title, level.search, state.districts, state.selectedDistrict, DistrictDto::name, dismiss) {
            openPicker = null
            onDistrictSelected(it)
        }
        AreaLevel.CITY -> AreaPickerSheet(level.title, level.search, state.cities, state.selectedCity, CityDto::name, dismiss) {
            openPicker = null
            onCitySelected(it)
        }
        null -> Unit
    }
}

@Composable
private fun ViewerAvatar(name: String?, photoUrl: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(name),
            style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        if (photoUrl != null) {
            AsyncImage(model = photoUrl, contentDescription = "My profile", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun NearMePill(isLocating: Boolean, isNearMode: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val active = isNearMode && !isLocating
    val shape = RoundedCornerShape(21.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(if (active) colors.primary else colors.surface)
            .border(1.dp, if (active) colors.primary else if (isLocating) CrichereOutlineDisabled else colors.outline, shape)
            .clickable(enabled = !isLocating, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
        if (isLocating) {
            CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Finding your location…", style = label, color = CrichereInkSubtle)
        } else {
            val tint = if (active) colors.onPrimary else colors.primary
            Icon(
                painterResource(if (active) R.drawable.ic_near_me_filled else R.drawable.ic_near_me),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (active) "Showing nearest leagues -- tap to clear" else "Show nearest to me", style = label, color = tint)
        }
    }
}

@Composable
private fun AreaChip(label: String, value: String?, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val selected = value != null
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, Color(0xFFD5DACE), shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value ?: label,
            style = TextStyle(
                fontFamily = InstrumentSansFamily,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 12.5.sp,
            ),
            color = colors.onBackground,
            maxLines = 1,
        )
        Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun TextAction(icon: Int, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LocationOffBanner(onOpenSettings: () -> Unit, onNotNow: () -> Unit) {
    val extra = LocalCrichereExtraColors.current
    val body = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 12.5.sp, lineHeight = 17.5.sp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(extra.warningContainer, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Icon(painterResource(R.drawable.ic_location_off), contentDescription = null, tint = extra.onWarning, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Location is off for Crichere", style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.5.sp), color = extra.onWarning)
            Spacer(Modifier.height(8.dp))
            Text("Allow location to see leagues near you, or pick your area below.", style = body, color = extra.onWarning)
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    "Open settings",
                    style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    color = extra.onWarning,
                    modifier = Modifier.clickable(onClick = onOpenSettings),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "Not now",
                    style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                    color = extra.onWarning,
                    modifier = Modifier.clickable(onClick = onNotNow),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(showClearFilters: Boolean, onClearFilters: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 88.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No leagues found.",
            style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp),
            color = Color(0xFF3E4A41),
        )
        if (showClearFilters) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .clickable(onClick = onClearFilters)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Clear filters", style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CreateLeagueFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .shadow(10.dp, shape, ambientColor = Color(0x801B5E20), spotColor = Color(0x801B5E20))
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .height(52.dp)
            .padding(start = 14.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text("Create a league", style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = Color.White)
    }
}

@Composable
private fun LeagueCard(league: LeagueDto, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tileColor(league.id)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                shortCode(league.name),
                style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp),
                color = Color.White,
            )
            if (league.logoUrl != null) {
                AsyncImage(model = league.logoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                league.name,
                style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 17.25.sp),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${league.city}, ${league.district}, ${league.state}",
                style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 12.sp, lineHeight = 14.4.sp),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                InfoChip("Starts ${shortDate(league.startsOn)}")
                league.format?.takeIf { it.isNotBlank() }?.let { InfoChip(it) }
                InfoChip(entryLabel(league.playerFee), highlighted = true)
            }
            Spacer(Modifier.height(10.dp))
            PlayerProgress(joined = league.players.size, required = league.playersRequired)
        }
    }
}

@Composable
private fun InfoChip(text: String, highlighted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .height(22.dp)
            .background(if (highlighted) colors.primaryContainer else colors.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = if (highlighted) colors.onPrimaryContainer else colors.onBackground,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayerProgress(joined: Int, required: Int?) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (required != null && required > 0) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((joined.toFloat() / required).coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(colors.primary),
                )
            }
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            if (required != null && required > 0) "$joined/$required" else "$joined joined",
            style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp),
            color = colors.onSurfaceVariant,
        )
    }
}

package com.crichere.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.crichere.app.R
import com.crichere.app.profile.BattingStyle
import com.crichere.app.profile.BowlingStyle
import com.crichere.app.profile.PlayingRole
import com.crichere.app.profile.ProfileField
import com.crichere.app.profile.ProfileSetupState
import com.crichere.app.profile.ProfileSetupViewModel
import com.crichere.app.reference.CityDto
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.StateDto
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereDisabledContainer
import com.crichere.app.ui.theme.CrichereErrorStrong
import com.crichere.app.ui.theme.CrichereInkSubtle
import com.crichere.app.ui.theme.CrichereOutlineDisabled
import com.crichere.app.ui.theme.InstrumentSansFamily
import com.crichere.app.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Profile Setup screen (design board screen C): first-time cricket-player onboarding (resumable,
 * per the field order name -> photo -> state -> district -> city -> role -> batting ->
 * bowling-if-applicable) and the "edit" entry point from Own Profile View, both driven by the same
 * [ProfileSetupViewModel] (see its `isEditMode` constructor parameter). Navigation on completion is
 * handled by the caller (`AuthNavHost`) via [ProfileSetupViewModel.navigationEvents].
 *
 * Photo: Android's Photo Picker (gallery only, no camera entry point) -> circle-crop preview
 * ([PhotoCropSheet], C3) -> the cropped 512px JPEG uploads with visible progress (C4), and a failed
 * upload offers Retry / Choose another (C6).
 */
@Composable
fun ProfileSetupScreen(viewModel: ProfileSetupViewModel, onNavigateToOwnProfile: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect {
            onNavigateToOwnProfile()
        }
    }

    var pickedPhoto by remember { mutableStateOf<PickedPhoto?>(null) }
    var localPhoto by remember { mutableStateOf<ImageBitmap?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch { pickedPhoto = withContext(Dispatchers.IO) { decodePickedPhoto(context, uri) } }
        }
    }
    val launchPicker = {
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.useMyLocation()
    }

    pickedPhoto?.let { photo ->
        PhotoCropSheet(
            photo = photo,
            title = "Preview",
            subtitle = "Profile photo · drag or pinch to adjust",
            onDismiss = { pickedPhoto = null },
            onChooseAnother = {
                pickedPhoto = null
                launchPicker()
            },
            onUsePhoto = { square ->
                pickedPhoto = null
                scope.launch {
                    val bytes = withContext(Dispatchers.Default) { encodeCrop(photo.bitmap, square) }
                    localPhoto = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    viewModel.uploadPhoto(bytes, "image/jpeg", photo.fileName)
                }
            },
        )
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    // One requester per resumable field, so the screen can scroll the first-missing (or, in edit
    // mode, the top) field into view on load -- see ProfileSetupState.initialFocusField's KDoc.
    val requesters = remember { ProfileField.entries.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(Unit) {
        requesters.getValue(state.initialFocusField).bringIntoView()
    }
    fun Modifier.field(field: ProfileField) = bringIntoViewRequester(requesters.getValue(field))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 21.dp, bottom = 20.dp),
        ) {
            Text(
                text = "Set up your profile",
                style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 26.4.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "So organizers know who's joining their league.",
                style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 13.sp, lineHeight = 18.2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Fields carry 7dp of top room for their notched label, so gaps below are design gap - 7.
            Spacer(Modifier.height(8.dp))

            CrichereTextField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = "Full name",
                error = state.nameError,
                reserveErrorSlot = true,
                modifier = Modifier.field(ProfileField.NAME),
            )
            Spacer(Modifier.height(14.dp))

            PhotoRow(
                state = state,
                localPhoto = localPhoto,
                onPick = launchPicker,
                onCancel = {
                    viewModel.cancelPhotoUpload()
                    localPhoto = null
                },
                onRetry = viewModel::retryPhotoUpload,
                modifier = Modifier.field(ProfileField.PHOTO),
            )
            Spacer(Modifier.height(14.dp))

            LocationPill(
                isLocating = state.isLocating,
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) viewModel.useMyLocation() else locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
            )
            Spacer(Modifier.height(7.dp))

            CrichereSelectField(
                label = "State",
                options = state.states,
                selected = state.states.firstOrNull { it.name == state.state },
                optionLabel = StateDto::name,
                onSelected = viewModel::onStateSelected,
                modifier = Modifier.field(ProfileField.STATE),
            )
            Spacer(Modifier.height(7.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CrichereSelectField(
                    label = "District",
                    options = state.districts,
                    selected = state.districts.firstOrNull { it.name == state.district },
                    optionLabel = DistrictDto::name,
                    onSelected = viewModel::onDistrictSelected,
                    enabled = state.state != null,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f).field(ProfileField.DISTRICT),
                )
                CrichereSelectField(
                    label = "City",
                    options = state.cities,
                    selected = state.cities.firstOrNull { it.name == state.city },
                    optionLabel = CityDto::name,
                    onSelected = viewModel::onCitySelected,
                    enabled = state.district != null,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f).field(ProfileField.CITY),
                )
            }
            Spacer(Modifier.height(7.dp))

            CrichereSelectField(
                label = "Playing role",
                options = PlayingRole.entries,
                selected = state.playingRole,
                optionLabel = PlayingRole::displayName,
                onSelected = viewModel::onRoleSelected,
                modifier = Modifier.field(ProfileField.ROLE),
            )
            Spacer(Modifier.height(7.dp))

            CrichereSelectField(
                label = "Batting style",
                options = BattingStyle.entries,
                selected = state.battingStyle,
                optionLabel = BattingStyle::displayName,
                onSelected = viewModel::onBattingStyleSelected,
                modifier = Modifier.field(ProfileField.BATTING),
            )

            if (state.isBowlingStyleApplicable) {
                Spacer(Modifier.height(7.dp))
                CrichereSelectField(
                    label = "Bowling style",
                    options = BowlingStyle.entries,
                    selected = state.bowlingStyle,
                    optionLabel = BowlingStyle::displayName,
                    onSelected = viewModel::onBowlingStyleSelected,
                    modifier = Modifier.field(ProfileField.BOWLING),
                )
            }
        }

        SaveBar(state = state, onSave = viewModel::save)
    }
}

@Composable
private fun SaveBar(state: ProfileSetupState, onSave: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 26.dp)) {
            val error = state.errorMessage
            if (error != null) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        painter = painterResource(R.drawable.ic_error),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp).size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        error,
                        style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.25.sp),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            CricherePrimaryButton(
                text = "Save",
                onClick = onSave,
                enabled = state.isSaveEnabled,
                loading = state.isSaving,
                loadingText = "Saving…",
            )
        }
    }
}

/** "Use my location" pill -- Profile setup (C) at the defaults, League creation (I9) at 18dp / 13.5sp. */
@Composable
internal fun LocationPill(isLocating: Boolean, onClick: () -> Unit, iconSize: Dp = 19.dp, fontSize: TextUnit = 14.sp) {
    val colors = MaterialTheme.colorScheme
    val label = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = fontSize)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(1.dp, if (isLocating) CrichereOutlineDisabled else colors.outline, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = !isLocating, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLocating) {
            CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(9.dp))
            Text("Finding your location…", style = label, color = CrichereInkSubtle)
        } else {
            Icon(painterResource(R.drawable.ic_my_location), contentDescription = null, tint = colors.primary, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(5.dp))
            Text("Use my location", style = label, color = colors.primary)
        }
    }
}

@Composable
private fun PhotoRow(
    state: ProfileSetupState,
    localPhoto: ImageBitmap?,
    onPick: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val failed = state.photoUploadErrorMessage != null && !state.isUploadingPhoto
    val hasImage = localPhoto != null || state.photoUrl != null
    val title = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 16.8.sp)
    val link = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !state.isUploadingPhoto && !failed, onClick = onPick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoCircle(state = state, localPhoto = localPhoto, failed = failed, hasImage = hasImage)
        Spacer(Modifier.width(14.dp))
        Column {
            when {
                state.isUploadingPhoto -> {
                    Text("Uploading photo…", style = title, color = colors.onBackground)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        uploadCaption(state),
                        style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Cancel",
                        style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onCancel),
                    )
                }
                failed -> {
                    Text("Couldn't upload the photo", style = title, color = CrichereErrorStrong)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text("Retry", style = link, color = colors.primary, modifier = Modifier.clickable(onClick = onRetry))
                        Spacer(Modifier.width(14.dp))
                        Text("Choose another", style = link, color = colors.onSurfaceVariant, modifier = Modifier.clickable(onClick = onPick))
                    }
                }
                hasImage -> {
                    Text("Profile photo", style = title, color = colors.onBackground)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap to change", style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 12.sp), color = colors.onSurfaceVariant)
                }
                else -> {
                    Text(
                        buildAnnotatedString {
                            append("Add a profile photo")
                            withStyle(SpanStyle(color = colors.error)) { append(" *") }
                        },
                        style = title,
                        color = colors.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Required to save · shown on rosters and auctions",
                        style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 12.sp, lineHeight = 15.6.sp),
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoCircle(state: ProfileSetupState, localPhoto: ImageBitmap?, failed: Boolean, hasImage: Boolean) {
    val colors = MaterialTheme.colorScheme
    if (!hasImage) {
        Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(76.dp)) {
                drawCircle(Color.White)
                drawCircle(
                    color = Color(0xFFB9C6B3),
                    radius = size.minDimension / 2f - 1.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            Icon(painterResource(R.drawable.ic_add_a_photo), contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
        }
        return
    }

    val progress = state.photoUploadProgress
    Box(
        modifier = Modifier
            .size(76.dp)
            .drawWithContent {
                drawContent()
                if (state.isUploadingPhoto) {
                    // Progress ring just outside the photo: track, then the green sweep from 12 o'clock.
                    val stroke = 4.dp.toPx()
                    val radius = size.minDimension / 2f + 1.5.dp.toPx()
                    val topLeft = Offset(center.x - radius, center.y - radius)
                    val arcSize = Size(radius * 2, radius * 2)
                    drawArc(CrichereDisabledContainer, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    drawArc(colors.primary, -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                }
            }
            .border(2.dp, if (failed) colors.error else colors.background, CircleShape)
            .padding(2.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (localPhoto != null) {
            Image(localPhoto, contentDescription = "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(model = state.photoUrl, contentDescription = "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        when {
            state.isUploadingPhoto -> Box(Modifier.fillMaxSize().background(Color(0x730E1A11)), contentAlignment = Alignment.Center) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    color = Color.White,
                )
            }
            failed -> Box(Modifier.fillMaxSize().background(Color(0x808C1D12)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

private fun uploadCaption(state: ProfileSetupState): String {
    val name = state.uploadingPhotoName ?: "profile-photo.jpg"
    val bytes = state.uploadingPhotoSizeBytes ?: return name
    val size = if (bytes >= 1_048_576) "%.1f MB".format(bytes / 1_048_576.0) else "${(bytes + 1023) / 1024} KB"
    return "$name · $size"
}

private fun PlayingRole.displayName(): String = when (this) {
    PlayingRole.BATSMAN -> "Batsman"
    PlayingRole.BOWLER -> "Bowler"
    PlayingRole.ALL_ROUNDER -> "All-rounder"
    PlayingRole.WICKETKEEPER -> "Wicketkeeper"
}

private fun BattingStyle.displayName(): String = when (this) {
    BattingStyle.RIGHT_HAND -> "Right-hand bat"
    BattingStyle.LEFT_HAND -> "Left-hand bat"
}

private fun BowlingStyle.displayName(): String = when (this) {
    BowlingStyle.RIGHT_ARM_FAST -> "Right-arm fast"
    BowlingStyle.RIGHT_ARM_MEDIUM -> "Right-arm medium"
    BowlingStyle.RIGHT_ARM_OFFBREAK -> "Right-arm offbreak"
    BowlingStyle.RIGHT_ARM_LEGBREAK -> "Right-arm legbreak"
    BowlingStyle.LEFT_ARM_FAST -> "Left-arm fast"
    BowlingStyle.LEFT_ARM_MEDIUM -> "Left-arm medium"
    BowlingStyle.LEFT_ARM_ORTHODOX -> "Left-arm orthodox"
    BowlingStyle.LEFT_ARM_CHINAMAN -> "Left-arm chinaman"
}

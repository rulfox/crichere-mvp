package com.crichere.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.crichere.app.R
import com.crichere.app.profile.BattingStyle
import com.crichere.app.profile.BowlingStyle
import com.crichere.app.profile.OwnProfileNavigationEvent
import com.crichere.app.profile.OwnProfileState
import com.crichere.app.profile.OwnProfileViewModel
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereErrorStrong
import com.crichere.app.ui.theme.CrichereGreenContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SheetHandle = Color(0xFFB9C1B4)
private val DialogSurface = Color(0xFFF1F4EE)
private val DialogBody = Color(0xFF3E4A41)

/**
 * Design N1-N5: photo (or initials) with a camera badge, name, the profile rows with humanized
 * labels, Edit profile and Log out (confirmed first). Tapping the photo opens the photo sheet
 * (View photo / Choose new photo -- no Remove, a photo is required); with no photo it goes
 * straight to the picker. Navigation is handled by the caller (`AuthNavHost`) via
 * [OwnProfileViewModel.navigationEvents].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnProfileScreen(
    viewModel: OwnProfileViewModel,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToPhoneEntry: () -> Unit,
    onViewPhoto: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPhotoSheet by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var pickedPhoto by remember { mutableStateOf<PickedPhoto?>(null) }
    // The cropped image, shown while it uploads so the change feels immediate.
    var localPhoto by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                OwnProfileNavigationEvent.NavigateToEditProfile -> onNavigateToEditProfile()
                OwnProfileNavigationEvent.NavigateToPhoneEntry -> onNavigateToPhoneEntry()
            }
        }
    }
    LaunchedEffect(state.isUploadingPhoto, state.photoError) {
        if (!state.isUploadingPhoto) localPhoto = null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { pickedPhoto = withContext(Dispatchers.IO) { decodePickedPhoto(context, uri) } }
    }
    val launchPicker = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val colors = MaterialTheme.colorScheme
    when {
        !state.hasProfile && state.isLoading -> Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primary)
        }
        !state.hasProfile -> Box(Modifier.fillMaxSize().background(colors.background)) {
            LoadError(onRetry = viewModel::retry, title = "Couldn't load your profile")
        }
        else -> Profile(
            state = state,
            localPhoto = localPhoto,
            onPhotoTap = { if (state.photoUrl != null) showPhotoSheet = true else launchPicker() },
            onEdit = viewModel::editProfile,
            onLogout = { showLogoutConfirm = true },
        )
    }

    if (showPhotoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { Box(Modifier.padding(top = 10.dp).size(32.dp, 4.dp).background(SheetHandle, RoundedCornerShape(2.dp))) },
        ) {
            PhotoSheet(
                photoUrl = state.photoUrl,
                name = state.name.orEmpty(),
                onView = {
                    showPhotoSheet = false
                    state.photoUrl?.let(onViewPhoto)
                },
                onChoose = {
                    showPhotoSheet = false
                    launchPicker()
                },
            )
        }
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
                    viewModel.changePhoto(bytes, "image/jpeg")
                }
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(horizontal = 23.dp),
            containerColor = DialogSurface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Log out of Crichere?", style = pText(19.sp, FontWeight.SemiBold, 22.8.sp), color = colors.onBackground) },
            text = { Text("You'll need an SMS code to sign back in.", style = pText(13.5.sp, lineHeight = 19.575.sp), color = DialogBody) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    viewModel.logout()
                }) { Text("Log out", style = pText(14.sp, FontWeight.SemiBold), color = CrichereErrorStrong) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel", style = pText(14.sp, FontWeight.SemiBold), color = colors.primary) }
            },
        )
    }
}

@Composable
private fun Profile(state: OwnProfileState, localPhoto: ImageBitmap?, onPhotoTap: () -> Unit, onEdit: () -> Unit, onLogout: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = state.name.orEmpty()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 19.dp, end = 19.dp, top = 19.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(104.dp).clickable(enabled = !state.isUploadingPhoto, onClick = onPhotoTap)) {
            ProfilePhoto(state.photoUrl, localPhoto, name, 104.dp)
            if (state.isUploadingPhoto) {
                CircularProgressIndicator(
                    progress = { state.photoUploadProgress.coerceIn(0.05f, 1f) },
                    color = colors.primary,
                    trackColor = Color.White.copy(alpha = 0.6f),
                    strokeWidth = 3.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
                    .background(colors.background, CircleShape)
                    .padding(3.dp)
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (state.photoUrl != null) R.drawable.ic_photo_camera else R.drawable.ic_add_a_photo),
                    contentDescription = if (state.photoUrl != null) "Profile photo options" else "Add a photo",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 24.2.sp),
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        if (state.photoUrl == null) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Add a photo so organizers recognise you",
                style = pText(13.sp, FontWeight.SemiBold, 13.sp),
                color = colors.primary,
                modifier = Modifier.clickable(enabled = !state.isUploadingPhoto, onClick = onPhotoTap),
            )
        }
        state.photoError?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = pText(12.5.sp, FontWeight.Medium, 16.sp), color = colors.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(16.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                .padding(1.dp),
        ) {
            val rows = listOfNotNull(
                "State" to state.state,
                "District" to state.district,
                "City" to state.city,
                "Playing role" to state.playingRole?.label(),
                "Batting style" to state.battingStyle?.label(),
                state.bowlingStyle?.let { "Bowling style" to it.label() },
            )
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.surfaceVariant)
                Row(Modifier.fillMaxWidth().height(37.5.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = pText(13.5.sp, lineHeight = 13.5.sp), color = colors.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        value ?: "--",
                        style = pText(13.5.sp, lineHeight = 13.5.sp),
                        color = colors.onBackground,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(colors.primary).clickable(onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) { Text("Edit profile", style = pText(14.5.sp, FontWeight.SemiBold, 14.5.sp), color = Color.White) }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).border(1.dp, colors.outline, RoundedCornerShape(24.dp)).clickable(onClick = onLogout),
            contentAlignment = Alignment.Center,
        ) { Text("Log out", style = pText(14.5.sp, FontWeight.SemiBold, 14.5.sp), color = CrichereErrorStrong) }
    }
}

/** N2. */
@Composable
private fun PhotoSheet(photoUrl: String?, name: String, onView: () -> Unit, onChoose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfilePhoto(photoUrl, null, name, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Profile photo", style = pText(16.sp, FontWeight.SemiBold, 16.sp), color = colors.onBackground)
                Spacer(Modifier.height(4.dp))
                Text("Visible to organizers and franchises", style = pText(12.sp, lineHeight = 12.sp), color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        SheetAction(R.drawable.ic_visibility, "View photo", onView)
        SheetAction(R.drawable.ic_photo_library, "Choose new photo", onChoose)
        Spacer(Modifier.height(8.dp))
        Text(
            "A photo is required for a complete profile, so it can be replaced but not removed.",
            style = pText(12.sp, lineHeight = 17.sp),
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }
}

@Composable
private fun SheetAction(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = pText(15.sp, FontWeight.Medium, 15.sp), color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Photo, the just-cropped local image while it uploads, or initials on green (N3). */
@Composable
private fun ProfilePhoto(url: String?, local: ImageBitmap?, name: String, size: Dp) {
    val initials: @Composable () -> Unit = {
        Box(Modifier.size(size).background(CrichereGreenContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                initials(name),
                style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.327f).sp, lineHeight = (size.value * 0.327f).sp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    when {
        local != null -> Image(local, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
        url == null -> initials()
        else -> SubcomposeAsyncImage(
            model = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            loading = { Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) },
            error = { initials() },
            modifier = Modifier.size(size).clip(CircleShape),
        )
    }
}

private fun BattingStyle.label(): String = when (this) {
    BattingStyle.RIGHT_HAND -> "Right-hand bat"
    BattingStyle.LEFT_HAND -> "Left-hand bat"
}

private fun BowlingStyle.label(): String = when (this) {
    BowlingStyle.RIGHT_ARM_FAST -> "Right-arm fast"
    BowlingStyle.RIGHT_ARM_MEDIUM -> "Right-arm medium"
    BowlingStyle.RIGHT_ARM_OFFBREAK -> "Right-arm offbreak"
    BowlingStyle.RIGHT_ARM_LEGBREAK -> "Right-arm legbreak"
    BowlingStyle.LEFT_ARM_FAST -> "Left-arm fast"
    BowlingStyle.LEFT_ARM_MEDIUM -> "Left-arm medium"
    BowlingStyle.LEFT_ARM_ORTHODOX -> "Left-arm orthodox"
    BowlingStyle.LEFT_ARM_CHINAMAN -> "Left-arm chinaman"
}

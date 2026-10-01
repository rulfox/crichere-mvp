package com.crichere.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.crichere.app.R
import com.crichere.app.ground.GroundDto
import com.crichere.app.league.AwardDraft
import com.crichere.app.league.LeagueCreationNavigationEvent
import com.crichere.app.league.LeagueCreationState
import com.crichere.app.league.LeagueCreationViewModel
import com.crichere.app.league.LeagueField
import com.crichere.app.reference.CityDto
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.StateDto
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereDisabledContainer
import com.crichere.app.ui.theme.CrichereErrorStrong
import com.crichere.app.ui.theme.CrichereInkSubtle
import com.crichere.app.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BannerEmpty = Color(0xFFF1F4EE)
private val BannerDash = Color(0xFFB9C6B3)
private val LogoEmpty = Color(0xFFE3E8DD)
private val PreviewPlaceholder = Color(0xFFA5ADA6)
private val DialogSurface = Color(0xFFF1F4EE)
private val DialogBody = Color(0xFF3E4A41)
private const val OtherFormat = "Other"

/** Resolves [LeagueCreationViewModel] via Koin, parameterized on [editingLeagueId] -- `null` is create mode. */
@Composable
internal fun LeagueCreationRoute(
    editingLeagueId: String?,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: LeagueCreationViewModel = koinViewModel(key = "league-creation:$editingLeagueId") { parametersOf(editingLeagueId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is LeagueCreationNavigationEvent.Saved -> onDone(event.leagueId)
            }
        }
    }

    LeagueCreationScreen(state = state, viewModel = viewModel, onCancel = onCancel)
}

private enum class ImageTarget { Logo, Banner }

/**
 * Create / edit league (design board screen I): one scrolling form -- the "How it will look" card
 * with logo and 16:9 banner crops, then Basics, Location, Ground, Schedule & format, Capacity, Fees
 * and Awards. Save sits in the top bar; it validates on tap (inline errors + a count banner, I7/I9),
 * and leaving an edited form asks first (I10). New grounds are registered on a full-screen map
 * (I5/I11). Images upload as part of Save -- see docs/DESIGN-REVIEW.md, screen I.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeagueCreationScreen(state: LeagueCreationState, viewModel: LeagueCreationViewModel, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(ImageTarget.Logo) }
    var pickedLogo by remember { mutableStateOf<PickedPhoto?>(null) }
    var pickedBanner by remember { mutableStateOf<PickedPhoto?>(null) }
    // Previews come from the ViewModel's pending bytes, so they survive recreation and clear on remove/upload.
    val logoBytes = viewModel.pendingLogoBytes()
    val bannerBytes = viewModel.pendingBannerBytes()
    val logoPreview = remember(logoBytes) { logoBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val bannerPreview = remember(bannerBytes) { bannerBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val pickedFor = target
            scope.launch {
                val photo = withContext(Dispatchers.IO) { decodePickedPhoto(context, uri) }
                if (pickedFor == ImageTarget.Logo) pickedLogo = photo else pickedBanner = photo
            }
        }
    }
    val choose = { forTarget: ImageTarget ->
        target = forTarget
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val requestClose = { if (state.isDirty) confirmDiscard = true else onCancel() }
    BackHandler(enabled = !state.isRegisteringNewGround) { requestClose() }

    val scrollState = rememberScrollState()
    val fieldTops = remember { mutableStateMapOf<LeagueField, Float>() }
    var formCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    // A Save tap with missing fields scrolls to the first one, just under the error banner.
    LaunchedEffect(state.validationAttempt) {
        if (state.validationAttempt == 0) return@LaunchedEffect
        withFrameNanos { }
        val first = state.fieldErrors.keys.firstOrNull() ?: return@LaunchedEffect
        val top = fieldTops[first] ?: return@LaunchedEffect
        scrollState.animateScrollTo((top - with(density) { 12.dp.toPx() }).toInt().coerceAtLeast(0))
    }
    // Each required field scrolls to its section's heading, as on the board (I7/I9).
    fun Modifier.trackSection(vararg fields: LeagueField) = onGloballyPositioned { coordinates ->
        val form = formCoordinates ?: return@onGloballyPositioned
        val top = coordinates.positionInRoot().y - form.positionInRoot().y
        fields.forEach { fieldTops[it] = top }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.statusBarsPadding()) {
                CreationTopBar(state = state, onClose = requestClose, onSave = viewModel::save)
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                state.loadFailed -> LoadError(onRetry = viewModel::retryLoad)
                else -> {
                    // The count banner stays pinned under the top bar while the form scrolls (I7/I9).
                    val errors = state.fieldErrors
                    if (errors.isNotEmpty()) {
                        Box(Modifier.padding(start = 19.dp, end = 19.dp, top = 2.dp, bottom = 4.dp)) { ErrorSummary(count = errors.size) }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .imePadding()
                            .verticalScroll(scrollState)
                            .onGloballyPositioned { formCoordinates = it }
                            .padding(start = 19.dp, end = 19.dp, top = 2.dp)
                            .navigationBarsPadding(),
                    ) {
                        val empty = state.name.isBlank() && logoPreview == null && bannerPreview == null && state.logoUrl == null && state.bannerUrl == null
                        if (empty) {
                            Text(
                                "HOW IT WILL LOOK",
                                style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 10.5.sp, letterSpacing = 0.63.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                        } else {
                            Spacer(Modifier.height(4.dp))
                        }
                        PreviewCard(
                            state = state,
                            logoPreview = logoPreview,
                            bannerPreview = bannerPreview,
                            onPickLogo = { choose(ImageTarget.Logo) },
                            onPickBanner = { choose(ImageTarget.Banner) },
                        )
                        ImageActions(
                            hasLogo = logoPreview != null || state.logoUrl != null,
                            hasBanner = bannerPreview != null || state.bannerUrl != null,
                            enabled = !state.isSaving,
                            onChangeLogo = { choose(ImageTarget.Logo) },
                            onRemoveLogo = viewModel::removeLogo,
                            onChangeBanner = { choose(ImageTarget.Banner) },
                            onRemoveBanner = viewModel::removeBanner,
                        )

                        SectionHeading("Basics", top = 16.dp, modifier = Modifier.trackSection(LeagueField.Name))
                        CrichereTextField(
                            value = state.name,
                            onValueChange = viewModel::onNameChanged,
                            label = "League name",
                            look = FieldVariant.Form,
                            error = errors[LeagueField.Name],
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        )
                        Spacer(Modifier.height(5.dp))
                        CrichereTextField(
                            value = state.description,
                            onValueChange = viewModel::onDescriptionChanged,
                            label = if (state.description.isEmpty()) "Description (optional)" else "Description",
                            look = FieldVariant.Form,
                            minLines = 3,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )

                        LocationSection(state = state, viewModel = viewModel, errors = errors, headingModifier = Modifier.trackSection(LeagueField.State, LeagueField.District, LeagueField.City))
                        GroundSection(state = state, viewModel = viewModel)

                        SectionHeading("Schedule & format", modifier = Modifier.trackSection(LeagueField.StartsOn))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CrichereTapField(
                                label = "Starts on",
                                value = state.startsOn?.let { formatDate(it, "d MMM yyyy") },
                                onClick = { showDatePicker = true },
                                look = FieldVariant.Form,
                                error = errors[LeagueField.StartsOn],
                                trailingIcon = R.drawable.ic_event,
                                modifier = Modifier.weight(1f),
                            )
                            val formatOptions = LeagueCreationViewModel.FORMAT_OPTIONS + OtherFormat
                            CrichereSelectField(
                                label = "Format",
                                options = formatOptions,
                                selected = if (state.isFormatOther) OtherFormat else state.format.takeIf { it in formatOptions },
                                optionLabel = { it },
                                onSelected = { viewModel.onFormatOptionSelected(it.takeIf { option -> option != OtherFormat }) },
                                look = FieldVariant.Form,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.isFormatOther) {
                            Spacer(Modifier.height(5.dp))
                            CrichereTextField(
                                value = state.format,
                                onValueChange = viewModel::onFormatChanged,
                                label = "Format name",
                                placeholder = "e.g. 8 overs",
                                look = FieldVariant.Form,
                            )
                        }

                        SectionHeading("Capacity")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CrichereTextField(
                                value = state.franchisesRequired,
                                onValueChange = { viewModel.onFranchisesRequiredChanged(it.filter(Char::isDigit)) },
                                label = "Franchises",
                                look = FieldVariant.Form,
                                mono = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            CrichereTextField(
                                value = state.playersRequired,
                                onValueChange = { viewModel.onPlayersRequiredChanged(it.filter(Char::isDigit)) },
                                label = "Players",
                                look = FieldVariant.Form,
                                mono = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        SectionHeading("Fees", modifier = Modifier.trackSection(LeagueField.UpiId))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CrichereTextField(
                                value = state.franchiseFee,
                                onValueChange = { viewModel.onFranchiseFeeChanged(amountInput(it)) },
                                label = "Franchise fee ₹",
                                look = FieldVariant.Form,
                                mono = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            CrichereTextField(
                                value = state.playerFee,
                                onValueChange = { viewModel.onPlayerFeeChanged(amountInput(it)) },
                                label = "Player fee ₹",
                                look = FieldVariant.Form,
                                mono = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        CrichereTextField(
                            value = state.organizerUpiId,
                            onValueChange = { viewModel.onOrganizerUpiIdChanged(it.trim()) },
                            label = "UPI ID for payments",
                            placeholder = "name@bank",
                            look = FieldVariant.Form,
                            mono = true,
                            error = errors[LeagueField.UpiId],
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        )

                        AwardsSection(awards = state.awards, viewModel = viewModel)
                        Spacer(Modifier.height(if (state.errorMessage != null) 96.dp else 32.dp))
                    }
                }
            }
        }

        val saveError = state.errorMessage
        if (saveError != null && !state.loadFailed) {
            CrichereSnackbar(
                message = saveError,
                actionLabel = "Retry",
                onAction = viewModel::save,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 13.dp, end = 13.dp, bottom = 25.dp),
            )
        }

        if (state.isRegisteringNewGround) {
            GroundRegisterOverlay(state = state, viewModel = viewModel)
        }
    }

    pickedLogo?.let { photo ->
        PhotoCropSheet(
            photo = photo,
            title = "Preview",
            subtitle = "League logo · drag to reposition",
            square = true,
            useLabel = "Use logo",
            onDismiss = { pickedLogo = null },
            onChooseAnother = {
                pickedLogo = null
                choose(ImageTarget.Logo)
            },
            onUsePhoto = { crop ->
                pickedLogo = null
                scope.launch {
                    val bytes = withContext(Dispatchers.Default) { encodeCrop(photo.bitmap, crop) }
                    viewModel.onLogoPicked(bytes, "image/jpeg")
                }
            },
        )
    }
    pickedBanner?.let { photo ->
        PhotoCropSheet(
            photo = photo,
            title = "Preview",
            subtitle = "League banner · drag to reposition",
            useLabel = "Use banner",
            onDismiss = { pickedBanner = null },
            onChooseAnother = {
                pickedBanner = null
                choose(ImageTarget.Banner)
            },
            onUsePhoto = {},
            onUseBanner = { region ->
                pickedBanner = null
                scope.launch {
                    val bytes = withContext(Dispatchers.Default) { encodeBannerCrop(photo.bitmap, region) }
                    viewModel.onBannerPicked(bytes, "image/jpeg")
                }
            },
        )
    }

    if (showDatePicker) {
        StartDatePicker(startsOn = state.startsOn, onDismiss = { showDatePicker = false }, onPicked = {
            viewModel.onStartsOnChanged(it)
            showDatePicker = false
        })
    }
    if (confirmDiscard) {
        DiscardDialog(
            editMode = state.isEditMode,
            onKeepEditing = { confirmDiscard = false },
            onDiscard = {
                confirmDiscard = false
                onCancel()
            },
        )
    }
}

/** Close (or, in edit mode, "Cancel"), the title, and the Save pill (design I1/I10/I12). */
@Composable
private fun CreationTopBar(state: LeagueCreationState, onClose: () -> Unit, onSave: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 3.dp, end = 13.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isEditMode) {
            Box(
                Modifier.padding(start = 9.dp).size(70.dp, 40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cancel", style = pText(14.sp, FontWeight.SemiBold), color = colors.primary)
            }
        } else {
            Box(Modifier.padding(start = 5.dp).size(48.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", tint = colors.onBackground, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            if (state.isEditMode) "Edit league" else "Create a league",
            style = pText(17.sp, FontWeight.SemiBold, 17.sp),
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        val enabled = state.isSaveEnabled
        Row(
            Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(if (enabled || state.isSaving) colors.primary else CrichereDisabledContainer)
                .clickable(enabled = enabled, onClick = onSave)
                .padding(start = if (state.isSaving) 11.dp else 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(color = Color.White, trackColor = Color.White.copy(alpha = 0.35f), strokeWidth = 2.dp, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
            }
            val progress = state.bannerUploadProgress ?: state.logoUploadProgress
            Text(
                when {
                    progress != null -> "Uploading ${(progress * 100).toInt()}%"
                    state.isSaving -> "Saving…"
                    else -> "Save"
                },
                style = pText(13.5.sp, FontWeight.SemiBold),
                color = if (enabled || state.isSaving) Color.White else CrichereInkSubtle,
            )
        }
    }
}

/** "N fields need attention" (design I7/I9), drawn 2dp wider than the form on each side. */
@Composable
private fun ErrorSummary(count: Int) {
    Row(
        Modifier
            .bleed(2.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = CrichereErrorStrong, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (count == 1) "1 field needs attention" else "$count fields need attention",
            style = pText(13.sp, FontWeight.SemiBold, 17.55.sp),
            color = CrichereErrorStrong,
        )
    }
}

/** Lays this out [amount] wider than its constraints on both sides. */
private fun Modifier.bleed(amount: Dp) = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-amount.roundToPx(), 0) }
}

@Composable
private fun SectionHeading(text: String, top: Dp = 16.dp, modifier: Modifier = Modifier) {
    Spacer(Modifier.height(top))
    Text(text, style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 15.sp), color = MaterialTheme.colorScheme.onBackground, modifier = modifier)
    Spacer(Modifier.height(5.dp))
}

/** "How it will look": the 16:9 banner, the logo tile and the name / city · date (design I1/I3). */
@Composable
private fun PreviewCard(
    state: LeagueCreationState,
    logoPreview: ImageBitmap?,
    bannerPreview: ImageBitmap?,
    onPickLogo: () -> Unit,
    onPickBanner: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, colors.outlineVariant, shape).padding(1.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clickable(enabled = !state.isSaving, onClick = onPickBanner)) {
                when {
                    bannerPreview != null -> Image(bannerPreview, contentDescription = "League banner", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    state.bannerUrl != null -> AsyncImage(model = state.bannerUrl, contentDescription = "League banner", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else -> EmptyBanner()
                }
                val progress = state.bannerUploadProgress
                if (progress != null) UploadProgress(progress)
            }
            Spacer(Modifier.height(52.dp))
        }
        Box(
            Modifier
                .offset(x = 14.dp, y = 154.dp)
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LogoEmpty)
                .border(4.dp, Color.White, RoundedCornerShape(16.dp))
                .clickable(enabled = !state.isSaving, onClick = onPickLogo),
            contentAlignment = Alignment.Center,
        ) {
            val inner = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
            when {
                logoPreview != null -> Image(logoPreview, contentDescription = "League logo", contentScale = ContentScale.Crop, modifier = inner)
                state.logoUrl != null -> AsyncImage(model = state.logoUrl, contentDescription = "League logo", contentScale = ContentScale.Crop, modifier = inner)
                else -> Icon(painterResource(R.drawable.ic_add_a_photo), contentDescription = "Add logo", tint = colors.primary, modifier = Modifier.size(24.dp))
            }
            if (state.logoUploadProgress != null) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 90.dp, end = 14.dp, bottom = 16.dp)) {
            Text(
                state.name.ifBlank { "League name" },
                style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 16.5.sp),
                color = if (state.name.isBlank()) PreviewPlaceholder else colors.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            val parts = listOfNotNull(state.city, state.startsOn?.let { formatDate(it, "d MMM") })
            Text(
                if (parts.isEmpty()) "City · start date" else parts.joinToString(" · "),
                style = pText(11.5.sp, lineHeight = 11.5.sp),
                color = if (parts.isEmpty()) PreviewPlaceholder else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyBanner() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(BannerEmpty)
            .padding(8.dp)
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    color = BannerDash,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_wallpaper), contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text("Add banner", style = pText(12.5.sp, FontWeight.SemiBold, 12.5.sp), color = colors.primary)
            Spacer(Modifier.height(4.dp))
            Text("16:9 · optional", style = pText(11.sp, lineHeight = 11.sp), color = colors.onSurfaceVariant)
        }
    }
}

/** I3: "Uploading 42%" chip plus a bar along the banner's bottom edge. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.UploadProgress(progress: Float) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = 8.dp, end = 8.dp)
            .height(24.dp)
            .background(Color(0xBF0E1A11), RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Uploading ${(progress * 100).toInt()}%",
            style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 10.5.sp),
            color = Color.White,
        )
    }
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.2f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(MaterialTheme.colorScheme.primary))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageActions(
    hasLogo: Boolean,
    hasBanner: Boolean,
    enabled: Boolean,
    onChangeLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
    onChangeBanner: () -> Unit,
    onRemoveBanner: () -> Unit,
) {
    if (!hasLogo && !hasBanner) return
    val colors = MaterialTheme.colorScheme
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        @Composable
        fun action(text: String, color: Color, onClick: () -> Unit) {
            Text(text, style = pText(12.sp, FontWeight.SemiBold, 12.sp), color = color, modifier = Modifier.clickable(enabled = enabled, onClick = onClick))
        }
        if (hasLogo) {
            action("Change logo", colors.primary, onChangeLogo)
            action("Remove logo", CrichereErrorStrong, onRemoveLogo)
        }
        if (hasBanner) {
            action("Change banner", colors.primary, onChangeBanner)
            action("Remove banner", CrichereErrorStrong, onRemoveBanner)
        }
    }
}

/** I9: helper line, "Use my location", then State / District / City. */
@Composable
private fun LocationSection(
    state: LeagueCreationState,
    viewModel: LeagueCreationViewModel,
    errors: Map<LeagueField, String>,
    headingModifier: Modifier,
) {
    SectionHeading("Location", modifier = headingModifier)
    Spacer(Modifier.height(3.dp))
    Text("Players near this place see the league first.", style = pText(12.5.sp, lineHeight = 17.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    LocationPill(isLocating = state.isLocating, onClick = viewModel::useMyLocation, iconSize = 18.dp, fontSize = 13.5.sp)
    Spacer(Modifier.height(5.dp))
    CrichereSelectField(
        label = "State",
        options = state.states,
        selected = state.states.firstOrNull { it.name == state.state },
        optionLabel = StateDto::name,
        onSelected = viewModel::onStateSelected,
        look = FieldVariant.Form,
        error = errors[LeagueField.State],
    )
    Spacer(Modifier.height(5.dp))
    CrichereSelectField(
        label = "District",
        options = state.districts,
        selected = state.districts.firstOrNull { it.name == state.district },
        optionLabel = DistrictDto::name,
        onSelected = viewModel::onDistrictSelected,
        enabled = state.state != null,
        look = FieldVariant.Form,
        error = errors[LeagueField.District],
    )
    Spacer(Modifier.height(5.dp))
    CrichereSelectField(
        label = "City",
        options = state.cities,
        selected = state.cities.firstOrNull { it.name == state.city },
        optionLabel = CityDto::name,
        onSelected = viewModel::onCitySelected,
        enabled = state.district != null,
        look = FieldVariant.Form,
        error = errors[LeagueField.City],
    )
}

/** I4: search existing grounds, or the selected one; "Register a new ground" opens the map (I5). */
@Composable
private fun GroundSection(state: LeagueCreationState, viewModel: LeagueCreationViewModel) {
    val colors = MaterialTheme.colorScheme
    SectionHeading("Ground (optional)")
    val selected = state.groundId
    if (selected != null) {
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(Color.White, RoundedCornerShape(14.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
                .padding(start = 15.dp, end = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Selected", style = pText(11.sp, FontWeight.Medium, 11.sp), color = colors.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(state.groundDisplayName ?: "Ground selected", style = pText(14.sp, FontWeight.SemiBold, 16.8.sp), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Clear", style = pText(13.sp, FontWeight.SemiBold, 13.sp), color = colors.primary, modifier = Modifier.clickable(onClick = viewModel::onClearGround))
        }
        return
    }
    CrichereTextField(
        value = state.groundSearchQuery,
        onValueChange = viewModel::onGroundSearchQueryChanged,
        label = "Search grounds",
        look = FieldVariant.Form,
        trailingIcon = R.drawable.ic_search,
    )
    val results = if (state.groundSearchQuery.isBlank()) emptyList() else state.groundSearchResults
    if (results.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(14.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
                .padding(1.dp),
        ) {
            results.forEachIndexed { index, ground -> GroundRow(ground, last = index == results.lastIndex, onClick = { viewModel.onGroundSelected(ground) }) }
        }
    }
    Spacer(Modifier.height(9.dp))
    Row(
        Modifier.fillMaxWidth().height(31.dp).clickable(onClick = viewModel::onStartRegisteringNewGround),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_add_location_alt), contentDescription = null, tint = colors.primary, modifier = Modifier.padding(start = 2.dp).size(19.dp))
        Spacer(Modifier.width(6.dp))
        Text("Can't find it? Register a new ground", style = pText(13.5.sp, FontWeight.SemiBold, 13.5.sp), color = colors.primary)
    }
}

@Composable
private fun GroundRow(ground: GroundDto, last: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(49.dp).padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_stadium), contentDescription = null, tint = colors.primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text("${ground.name} -- ${ground.city}", style = pText(13.5.sp, FontWeight.Medium, 16.2.sp), color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!last) HorizontalDivider(thickness = 1.dp, color = colors.surfaceVariant)
    }
}

/** I8: one card per award -- name, cash, trophy, Remove -- then "Add another award". */
@Composable
private fun AwardsSection(awards: List<AwardDraft>, viewModel: LeagueCreationViewModel) {
    val colors = MaterialTheme.colorScheme
    SectionHeading("Awards")
    Spacer(Modifier.height(3.dp))
    Text(
        "Shown on the league page. Leave the name blank to drop an award.",
        style = pText(12.5.sp, lineHeight = 17.5.sp),
        color = colors.onSurfaceVariant,
    )
    awards.forEachIndexed { index, award ->
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(14.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
                .padding(start = 13.dp, end = 13.dp, top = 6.dp, bottom = 13.dp),
        ) {
            CrichereTextField(
                value = award.name,
                onValueChange = { viewModel.onAwardNameChanged(index, it) },
                label = "Award name",
                look = FieldVariant.FormOnCard,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            Spacer(Modifier.height(3.dp))
            CrichereTextField(
                value = award.cashAmount,
                onValueChange = { viewModel.onAwardCashAmountChanged(index, amountInput(it)) },
                label = "Cash (optional)",
                look = FieldVariant.FormOnCard,
                mono = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable { viewModel.onAwardTrophyToggled(index) }, verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (award.hasTrophy) colors.primary else Color.White)
                            .border(if (award.hasTrophy) 0.dp else 2.dp, colors.outline, RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (award.hasTrophy) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Trophy", style = pText(14.sp, FontWeight.Medium, 14.sp), color = colors.onBackground)
                }
                Text("Remove", style = pText(13.sp, FontWeight.SemiBold, 13.sp), color = CrichereErrorStrong, modifier = Modifier.clickable { viewModel.onRemoveAward(index) })
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().height(27.dp).clickable(onClick = viewModel::onAddAward), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.primary, modifier = Modifier.padding(start = 2.dp).size(19.dp))
        Spacer(Modifier.width(6.dp))
        Text("Add another award", style = pText(13.5.sp, FontWeight.SemiBold, 13.5.sp), color = colors.primary)
    }
}

/** I10: leaving with unsaved edits. */
@Composable
private fun DiscardDialog(editMode: Boolean, onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        // The board's dialog is 314 wide on a 360 screen -- wider than the platform default.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 23.dp),
        containerColor = DialogSurface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Discard changes?", style = pText(19.sp, FontWeight.SemiBold, 22.8.sp), color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Text(
                if (editMode) "Your edits won't be saved. The league stays as it was." else "This league won't be created.",
                style = pText(13.5.sp, lineHeight = 19.575.sp),
                color = DialogBody,
            )
        },
        confirmButton = {
            TextButton(onClick = onDiscard) { Text("Discard", style = pText(14.sp, FontWeight.SemiBold), color = CrichereErrorStrong) }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text("Keep editing", style = pText(14.sp, FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary) }
        },
    )
}

/** I6: Material date picker in the board's colours. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDatePicker(startsOn: String?, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val initial = startsOn?.let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
    val colors = MaterialTheme.colorScheme
    val pickerColors = DatePickerDefaults.colors(
        containerColor = DialogSurface,
        titleContentColor = colors.onSurfaceVariant,
        headlineContentColor = colors.onBackground,
        weekdayContentColor = colors.onSurfaceVariant,
        selectedDayContainerColor = colors.primary,
        selectedDayContentColor = Color.White,
        todayDateBorderColor = colors.primary,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        colors = pickerColors,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()) else onDismiss()
            }) { Text("OK", style = pText(14.sp, FontWeight.SemiBold), color = colors.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = pText(14.sp, FontWeight.SemiBold), color = colors.primary) }
        },
    ) {
        DatePicker(
            state = pickerState,
            colors = pickerColors,
            showModeToggle = false,
            title = { Text("Select start date", style = pText(12.sp, FontWeight.Medium), color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, top = 20.dp)) },
            headline = {
                val selected = pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                Text(
                    selected?.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)) ?: "Pick a date",
                    style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 26.sp),
                    color = colors.onBackground,
                    modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
                )
            },
        )
    }
}

/** `2026-10-12` as e.g. "12 Oct 2026" ([pattern] `d MMM yyyy`); the raw value if it doesn't parse. */
private fun formatDate(iso: String, pattern: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)) }.getOrDefault(iso)

/** Keeps digits and a single decimal point -- fee and cash inputs. */
private fun amountInput(raw: String): String {
    val kept = raw.filter { it.isDigit() || it == '.' }
    val dot = kept.indexOf('.')
    return if (dot < 0) kept else kept.substring(0, dot + 1) + kept.substring(dot + 1).replace(".", "")
}

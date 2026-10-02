package com.crichere.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.R
import com.crichere.app.league.ClaimFranchiseState
import com.crichere.app.league.ClaimFranchiseViewModel
import com.crichere.app.league.LeagueDto
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereErrorStrong
import com.crichere.app.ui.theme.CrichereInkSubtle
import com.crichere.app.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Resolves [ClaimFranchiseViewModel] via Koin, parameterized on [leagueId] -- see `AppRoute.ClaimFranchise` (ui/navigation). */
@Composable
internal fun ClaimFranchiseRoute(
    leagueId: String,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ClaimFranchiseViewModel = koinViewModel(key = "claim-franchise:$leagueId") { parametersOf(leagueId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.retry() }
    LaunchedEffect(state.claimed) { if (state.claimed) onDone() }

    ClaimFranchiseScreen(state = state, viewModel = viewModel, onCancel = onCancel)
}

private enum class PickTarget { Logo, Screenshot }

/**
 * Claim a franchise (design board screen G): name + square logo (with crop preview), the
 * franchise fee with "Pay via UPI" and the payment screenshot, the free-franchise summary, the
 * claim-failure banner and the refunds/disputes disclaimer -- see docs/PHASE3.md.
 */
@Composable
private fun ClaimFranchiseScreen(state: ClaimFranchiseState, viewModel: ClaimFranchiseViewModel, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickTarget by remember { mutableStateOf(PickTarget.Logo) }
    var pickedLogo by remember { mutableStateOf<PickedPhoto?>(null) }
    var pickedScreenshot by remember { mutableStateOf<PickedPhoto?>(null) }
    var logoPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    var noUpiApp by remember { mutableStateOf(false) }
    var copiedTick by remember { mutableIntStateOf(0) }
    var showCopied by remember { mutableStateOf(false) }
    LaunchedEffect(copiedTick) {
        if (copiedTick > 0) {
            showCopied = true
            delay(3_000)
            showCopied = false
        }
    }
    // A failed logo upload drops the logo; drop the local preview with it.
    LaunchedEffect(state.isUploadingLogo, state.logoUrl) {
        if (!state.isUploadingLogo && state.logoUrl == null) logoPreview = null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val target = pickTarget
            scope.launch {
                val photo = withContext(Dispatchers.IO) { decodePickedPhoto(context, uri) }
                if (target == PickTarget.Logo) pickedLogo = photo else pickedScreenshot = photo
            }
        }
    }
    val choose = { target: PickTarget ->
        pickTarget = target
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val locked = state.isSubmitting

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.statusBarsPadding()) { FlowTopBar("Claim a franchise", onClose = onCancel) }
            val league = state.league
            when {
                league == null && state.loadFailed -> LoadError(onRetry = viewModel::retry)
                league == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                else -> {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 19.dp, end = 19.dp, top = 6.dp),
                    ) {
                        Box(Modifier.alpha(if (locked) 0.55f else 1f)) {
                            NameCard(
                                state = state,
                                logoPreview = logoPreview,
                                locked = locked,
                                onNameChanged = viewModel::onNameChanged,
                                onPickLogo = { choose(PickTarget.Logo) },
                                onRemoveLogo = {
                                    viewModel.removeLogo()
                                    logoPreview = null
                                },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        val fee = league.franchiseFee
                        if (fee != null) {
                            Box(Modifier.alpha(if (locked) 0.55f else 1f)) {
                                FeeCard(
                                    league = league,
                                    fee = fee,
                                    state = state,
                                    thumbnail = thumbnail,
                                    noUpiApp = noUpiApp,
                                    copied = copiedTick > 0,
                                    locked = locked,
                                    onPayViaUpi = {
                                        val upi = league.organizerUpiId ?: return@FeeCard
                                        noUpiApp = !launchUpiPayment(context, upi, league.name, fee, "Franchise fee for ${league.name}")
                                    },
                                    onCopyUpiId = {
                                        league.organizerUpiId?.let { copyToClipboard(context, "UPI ID", it) }
                                        copiedTick++
                                    },
                                    onChoose = { choose(PickTarget.Screenshot) },
                                    onCancelUpload = {
                                        viewModel.cancelUpload()
                                        thumbnail = null
                                    },
                                    onRetryUpload = viewModel::retryUpload,
                                    onRemove = {
                                        viewModel.removeScreenshot()
                                        thumbnail = null
                                    },
                                )
                            }
                        } else {
                            FreeFranchiseNote()
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                    if (league.franchiseFee != null && state.screenshotUrl != null && state.errorMessage == null && !locked) {
                        Text(
                            "Crichere doesn't process payment or handle refunds/disputes -- that's between you and the organizer directly.",
                            style = pText(11.5.sp, lineHeight = 16.675.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 19.dp, end = 19.dp, bottom = 12.dp),
                        )
                    }
                    ClaimBar(state, league, onClaim = viewModel::submit)
                }
            }
        }

        AnimatedVisibility(
            visible = showCopied,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 13.dp, end = 13.dp, bottom = 100.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("UPI ID copied", style = pText(14.sp, FontWeight.Medium), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    pickedLogo?.let { photo ->
        PhotoCropSheet(
            photo = photo,
            title = "Preview",
            subtitle = "Franchise logo · drag to reposition",
            onDismiss = { pickedLogo = null },
            onChooseAnother = {
                pickedLogo = null
                choose(PickTarget.Logo)
            },
            onUsePhoto = { crop ->
                pickedLogo = null
                scope.launch {
                    val bytes = withContext(Dispatchers.Default) { encodeCrop(photo.bitmap, crop) }
                    logoPreview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    viewModel.uploadLogo(bytes, "image/jpeg", "franchise-logo.jpg")
                }
            },
            square = true,
            useLabel = "Use logo",
        )
    }

    pickedScreenshot?.let { photo ->
        val league = state.league
        ScreenshotCheckSheet(
            bitmap = photo.bitmap,
            checklist = listOfNotNull(
                league?.franchiseFee?.let { "Amount ${rupees(it)}" },
                league?.organizerUpiId?.let { "UPI ID $it" },
                "Reference / UTR no.",
            ),
            onDismiss = { pickedScreenshot = null },
            onChooseAnother = {
                pickedScreenshot = null
                choose(PickTarget.Screenshot)
            },
            onUse = {
                pickedScreenshot = null
                thumbnail = photo.bitmap.asImageBitmap()
                scope.launch {
                    val bytes = withContext(Dispatchers.Default) { encodeScreenshot(photo.bitmap) }
                    viewModel.uploadScreenshot(bytes, "image/jpeg", photo.fileName.substringBeforeLast('.') + ".jpg")
                }
            },
        )
    }
}

/** Design G1/G3: square logo tile next to the franchise name. */
@Composable
private fun NameCard(
    state: ClaimFranchiseState,
    logoPreview: ImageBitmap?,
    locked: Boolean,
    onNameChanged: (String) -> Unit,
    onPickLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
) {
    val hasLogo = logoPreview != null || state.logoUrl != null
    FlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoTile(state.name, logoPreview, state.logoUrl, state.isUploadingLogo, enabled = !locked, onClick = onPickLogo)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                NameField(state.name, state.nameError, enabled = !locked, onNameChanged = onNameChanged)
                if (hasLogo && !locked) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Remove logo",
                        style = pText(12.sp, FontWeight.SemiBold),
                        color = CrichereErrorStrong,
                        modifier = Modifier.clickable(onClick = onRemoveLogo),
                    )
                }
            }
        }
    }
}

@Composable
private fun LogoTile(name: String, preview: ImageBitmap?, logoUrl: String?, uploading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val hasLogo = preview != null || logoUrl != null
    Box(
        Modifier.size(80.dp).clip(shape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            hasLogo -> {
                if (preview != null) {
                    Image(preview, contentDescription = "Franchise logo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    coil3.compose.AsyncImage(model = logoUrl, contentDescription = "Franchise logo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (uploading) {
                    Box(Modifier.fillMaxSize().background(Color(0x8C0E1A11)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                } else if (enabled) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(Color(0xB30E1A11)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = "Change logo", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            name.isNotBlank() -> Box(Modifier.fillMaxSize().background(tileColor(name.trim())), contentAlignment = Alignment.Center) {
                Text(
                    initials(name).take(2),
                    style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 25.6.sp),
                    color = Color.White,
                )
            }
            else -> {
                Canvas(Modifier.matchParentSize().background(Color(0xFFF1F4EE))) {
                    drawRoundRect(
                        color = Color(0xFFB9C6B3),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_add_photo_alternate), contentDescription = null, tint = colors.primary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Logo", style = pText(10.sp, FontWeight.SemiBold, 10.sp), color = colors.primary)
                }
            }
        }
    }
}

/**
 * Empty: a boxed field with a "Franchise name" placeholder (red when [error], design G1).
 * Filled: a label over the name in Archivo with an underline (design G3).
 */
@Composable
private fun NameField(name: String, error: Boolean, enabled: Boolean, onNameChanged: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val empty = name.isEmpty()
    BasicTextField(
        value = name,
        onValueChange = { onNameChanged(it.take(60)) },
        enabled = enabled,
        singleLine = true,
        interactionSource = interaction,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        textStyle = if (empty) {
            pText(14.sp, lineHeight = 14.sp).copy(color = colors.onBackground)
        } else {
            TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 18.sp, color = colors.onBackground)
        },
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            if (empty) {
                Column {
                    val borderColor = when {
                        error -> colors.error
                        focused -> colors.primary
                        else -> colors.outline
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(colors.surface, RoundedCornerShape(10.dp))
                            .border(if (error || focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("Franchise name", style = pText(14.sp, lineHeight = 14.sp), color = CrichereInkSubtle)
                        inner()
                    }
                    if (error) {
                        Spacer(Modifier.height(6.dp))
                        Text("Enter a franchise name", style = pText(11.5.sp, FontWeight.Medium, 11.5.sp), color = colors.error)
                    }
                }
            } else {
                Column {
                    Text("Franchise name", style = pText(11.5.sp, FontWeight.Medium, 11.5.sp), color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.TopStart) { inner() }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (enabled) colors.primary else colors.outline))
                }
            }
        },
    )
}

/** Design G1/G3: "Franchise fee ₹5,000", Pay via UPI, then the compact screenshot row. */
@Composable
private fun FeeCard(
    league: LeagueDto,
    fee: Double,
    state: ClaimFranchiseState,
    thumbnail: ImageBitmap?,
    noUpiApp: Boolean,
    copied: Boolean,
    locked: Boolean,
    onPayViaUpi: () -> Unit,
    onCopyUpiId: () -> Unit,
    onChoose: () -> Unit,
    onCancelUpload: () -> Unit,
    onRetryUpload: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val proof = when {
        state.isUploadingScreenshot -> ProofState.Uploading(state.uploadProgress, fileCaption(state.uploadFileName, state.uploadSizeBytes))
        state.uploadFailed -> ProofState.Failed
        state.screenshotUrl != null -> ProofState.Attached(sizeCaption(state.uploadSizeBytes))
        else -> ProofState.Empty
    }
    FlowCard {
        Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Franchise fee", style = pText(14.sp, FontWeight.SemiBold, 14.sp), color = colors.onBackground, modifier = Modifier.weight(1f))
            Text(rupees(fee), style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 20.sp), color = colors.primary)
        }
        Spacer(Modifier.height(10.dp))
        if (proof == ProofState.Empty) {
            val upiId = league.organizerUpiId
            when {
                upiId == null -> {
                    InfoNote(title = "The organizer hasn't added a UPI ID yet.", body = "Pay them directly, then attach the payment screenshot.")
                    Spacer(Modifier.height(10.dp))
                }
                noUpiApp -> {
                    InfoNote(title = null, body = "No UPI app found on this phone. Copy the UPI ID and pay from any app, or ask the organizer.")
                    Spacer(Modifier.height(10.dp))
                    UpiIdCopyRow(upiId, copied, onCopyUpiId)
                    Spacer(Modifier.height(10.dp))
                }
                else -> {
                    FlowPill("Pay via UPI", R.drawable.ic_open_in_new, filled = true, height = 42.dp, onClick = onPayViaUpi)
                    Spacer(Modifier.height(10.dp))
                }
            }
            AttachRow(onClick = onChoose)
        } else {
            ProofRow(proof, thumbnail, locked, onChoose, onCancelUpload, onRetryUpload, onRemove)
        }
    }
}

@Composable
private fun AttachRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFAFBF8)).clickable(onClick = onClick)) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                color = Color(0xFFB9C6B3),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_add_photo_alternate), contentDescription = null, tint = colors.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Attach payment screenshot", style = pText(13.5.sp, FontWeight.SemiBold, 13.5.sp), color = colors.primary)
                Spacer(Modifier.height(3.dp))
                Text("Required", style = pText(11.5.sp, lineHeight = 11.5.sp), color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Design G3: compact attached-screenshot row (also uploading / failed). */
@Composable
private fun ProofRow(
    proof: ProofState,
    thumbnail: ImageBitmap?,
    locked: Boolean,
    onChoose: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(90.dp).background(Color(0xFFF1F4EE), RoundedCornerShape(12.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 42.dp, height = 74.dp).background(Color.White)) {
            if (thumbnail != null) Image(thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            when (proof) {
                is ProofState.Uploading -> Box(Modifier.fillMaxSize().background(Color(0x8C0E1A11)), contentAlignment = Alignment.Center) {
                    Text("${(proof.progress * 100).toInt()}%", style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp), color = Color.White)
                }
                ProofState.Failed -> Box(Modifier.fillMaxSize().background(Color(0x8C8C1D12)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                else -> Unit
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            when (proof) {
                is ProofState.Uploading -> {
                    Text("Uploading…", style = pText(13.sp, FontWeight.SemiBold, 13.sp), color = colors.onBackground)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(proof.progress.coerceIn(0f, 1f)).height(4.dp).background(colors.primary))
                    }
                }
                ProofState.Failed -> {
                    Text("Upload failed", style = pText(13.sp, FontWeight.SemiBold, 13.sp), color = CrichereErrorStrong)
                    Spacer(Modifier.height(6.dp))
                    Text("Check your connection.", style = pText(11.5.sp, lineHeight = 11.5.sp), color = colors.onSurfaceVariant)
                }
                is ProofState.Attached -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_check_circle_filled), contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Screenshot attached", style = pText(13.sp, FontWeight.SemiBold, 13.sp), color = colors.primary)
                    }
                    if (proof.caption.isNotEmpty()) {
                        Spacer(Modifier.height(5.dp))
                        Text(proof.caption, style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 11.5.sp), color = colors.onSurfaceVariant)
                    }
                }
                ProofState.Empty -> Unit
            }
        }
        if (!locked) {
            when (proof) {
                is ProofState.Uploading -> RoundIcon(R.drawable.ic_close, "Cancel upload", colors.onSurfaceVariant, onCancel)
                ProofState.Failed -> {
                    RoundIcon(R.drawable.ic_refresh, "Retry upload", colors.primary, onRetry)
                    Spacer(Modifier.width(12.dp))
                    RoundIcon(R.drawable.ic_delete, "Remove screenshot", CrichereErrorStrong, onRemove)
                }
                is ProofState.Attached -> {
                    RoundIcon(R.drawable.ic_swap_horiz, "Replace screenshot", colors.primary, onChoose)
                    Spacer(Modifier.width(12.dp))
                    RoundIcon(R.drawable.ic_delete, "Remove screenshot", CrichereErrorStrong, onRemove)
                }
                ProofState.Empty -> Unit
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: Int, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** "388 KB" -- the G3 row shows only the size. */
private fun sizeCaption(bytes: Long?): String {
    val b = bytes ?: return ""
    return if (b >= 1_048_576) "%.1f MB".format(b / 1_048_576.0) else "${(b + 1023) / 1024} KB"
}

/** Design G4: free franchise -- no payment step. */
@Composable
private fun FreeFranchiseNote() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.primaryContainer, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_celebration), contentDescription = null, tint = colors.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("No franchise fee", style = pText(14.5.sp, FontWeight.Bold, 15.95.sp), color = colors.onPrimaryContainer)
            Spacer(Modifier.height(3.dp))
            Text("Your franchise is added as soon as you claim it.", style = pText(12.5.sp, lineHeight = 16.25.sp), color = Color(0xFF2E4A31))
        }
    }
}

/** Bottom bar: the claim-failure banner (G6) above the Claim button. */
@Composable
private fun ClaimBar(state: ClaimFranchiseState, league: LeagueDto, onClaim: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        Column(Modifier.padding(start = 19.dp, end = 19.dp, top = 12.dp, bottom = 24.dp)) {
            val message = state.errorMessage
            if (message != null) {
                Row(Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = CrichereErrorStrong, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        state.errorTitle?.let {
                            Text(it, style = pText(13.sp, FontWeight.SemiBold, 17.55.sp), color = CrichereErrorStrong)
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(message, style = pText(12.sp, lineHeight = 16.8.sp), color = CrichereErrorStrong)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            CricherePrimaryButton(
                text = "Claim Franchise",
                onClick = onClaim,
                enabled = state.name.isNotBlank() && !state.isUploadingLogo && (league.franchiseFee == null || state.screenshotUrl != null),
                loading = state.isSubmitting,
                loadingText = "Claiming…",
                modifier = Modifier.height(50.dp),
            )
        }
    }
}

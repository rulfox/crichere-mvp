package com.crichere.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crichere.app.R
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereErrorStrong
import com.crichere.app.ui.theme.InstrumentSansFamily
import com.crichere.app.ui.theme.JetBrainsMonoFamily
import com.crichere.app.ui.theme.LocalCrichereExtraColors
import java.io.ByteArrayOutputStream

// Shared pieces of the "pay the organizer, attach proof" flow -- Join as player (screen F) and
// Claim a franchise (screen G).

internal fun pText(size: TextUnit, weight: FontWeight = FontWeight.Normal, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = InstrumentSansFamily, fontWeight = weight, fontSize = size, lineHeight = lineHeight)

/** JPEG bytes of a picked screenshot -- already downsampled to <= 2048px by [decodePickedPhoto]. */
internal fun encodeScreenshot(bitmap: Bitmap): ByteArray =
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    }

/** Opens a UPI app for [upiId]/[amount]; `false` if none is installed (design F2). */
internal fun launchUpiPayment(context: Context, upiId: String, payeeName: String, amount: Double, note: String): Boolean {
    val uri = Uri.parse("upi://pay?pa=$upiId&pn=${Uri.encode(payeeName)}&am=$amount&tn=${Uri.encode(note)}&cu=INR")
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

internal fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}

/** White 16dp-rounded card with the board's hairline border. */
@Composable
internal fun FlowCard(modifier: Modifier = Modifier, borderColor: Color = MaterialTheme.colorScheme.outlineVariant, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(15.dp),
    ) { content() }
}

/** Close (X) + title row used by the full-screen flows. */
@Composable
internal fun FlowTopBar(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 5.dp, top = 3.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = pText(17.sp, FontWeight.SemiBold), color = MaterialTheme.colorScheme.onBackground)
    }
}

/** League tile + name + "Starts 12 Oct · T20" summary at the top of the flows. */
@Composable
internal fun LeagueSummaryRow(id: String, name: String, logoUrl: String?, startsOn: String, format: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(tileColor(id)), contentAlignment = Alignment.Center) {
            Text(shortCode(name), style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 12.8.sp), color = Color.White)
            if (logoUrl != null) {
                coil3.compose.AsyncImage(model = logoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, lineHeight = 15.95.sp), color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(3.dp))
            Text(
                "Starts ${shortDate(startsOn)}" + (format?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = pText(12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Fee card: amount, the organizer's UPI ID with "Pay via UPI" + copy (F1), the no-UPI-app
 * fallback (F2) and the organizer-has-no-UPI-ID notice (F9).
 */
@Composable
internal fun PayToCard(
    feeLabel: String,
    fee: Double,
    upiId: String?,
    noUpiApp: Boolean,
    copied: Boolean,
    onPayViaUpi: () -> Unit,
    onCopyUpiId: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    FlowCard {
        Text("$feeLabel: ${rupees(fee)}", style = pText(14.sp, FontWeight.SemiBold), color = colors.onBackground)
        Spacer(Modifier.height(10.dp))
        when {
            upiId == null -> {
                InfoNote(
                    title = "The organizer hasn't added a UPI ID yet.",
                    body = "Pay them directly, then attach the payment screenshot.",
                )
                Spacer(Modifier.height(10.dp))
                FlowPill("Pay via UPI", R.drawable.ic_open_in_new, filled = true, enabled = false, height = 42.dp, onClick = {})
            }
            noUpiApp -> {
                InfoNote(title = null, body = "No UPI app found on this phone. Copy the UPI ID and pay from any app, or ask the organizer.")
                Spacer(Modifier.height(10.dp))
                UpiIdCopyRow(upiId, copied, onCopyUpiId)
            }
            else -> {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFF1F4EE), RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Pay to", style = pText(11.sp), color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(upiId, style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp), color = colors.onBackground)
                    }
                    Text(rupees(fee), style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp), color = colors.primary)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FlowPill("Pay via UPI", R.drawable.ic_open_in_new, filled = true, height = 42.dp, modifier = Modifier.weight(1f), onClick = onPayViaUpi)
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).border(1.dp, colors.outline, CircleShape).clickable(onClick = onCopyUpiId),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_content_copy), contentDescription = "Copy UPI ID", tint = colors.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** The organizer's UPI ID with a Copy chip -- the no-UPI-app fallback (F2/G). */
@Composable
internal fun UpiIdCopyRow(upiId: String, copied: Boolean, onCopyUpiId: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(2.dp, colors.primary, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(upiId, style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = colors.onBackground, modifier = Modifier.weight(1f))
        Row(
            Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(colors.primaryContainer)
                .clickable(onClick = onCopyUpiId)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(if (copied) R.drawable.ic_check else R.drawable.ic_content_copy), null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (copied) "Copied" else "Copy", style = pText(12.5.sp, FontWeight.SemiBold), color = colors.primary)
        }
    }
}

@Composable
internal fun InfoNote(title: String?, body: String) {
    val extra = LocalCrichereExtraColors.current
    Row(Modifier.fillMaxWidth().background(extra.warningContainer, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = extra.onWarning, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            if (title != null) {
                Text(title, style = pText(12.5.sp, FontWeight.SemiBold, 17.sp), color = extra.onWarning)
                Spacer(Modifier.height(4.dp))
            }
            Text(body, style = pText(12.sp, lineHeight = 17.sp), color = extra.onWarning)
        }
    }
}

/** What the payment-screenshot card is showing. */
internal sealed interface ProofState {
    data object Empty : ProofState
    data class Uploading(val progress: Float, val caption: String) : ProofState
    data object Failed : ProofState
    data class Attached(val caption: String) : ProofState
}

/** Design F1/F5: dropzone, then uploading (progress), failed (Retry / Remove) or attached (Replace / Remove). */
@Composable
internal fun PaymentProofCard(
    state: ProofState,
    thumbnail: ImageBitmap?,
    onChoose: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onView: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    FlowCard(borderColor = if (state is ProofState.Failed) Color(0xFFF0C7C0) else colors.outlineVariant) {
        if (state is ProofState.Empty) {
            Text("Payment screenshot", style = pText(14.sp, FontWeight.SemiBold), color = colors.onBackground)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(134.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFAFBF8))
                    .clickable(onClick = onChoose),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawRoundRect(
                        color = Color(0xFFB9C6B3),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(painterResource(R.drawable.ic_add_photo_alternate), null, tint = colors.primary, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Choose screenshot", style = pText(13.5.sp, FontWeight.SemiBold), color = colors.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Amount, UPI ID and reference no. should be visible",
                        style = pText(11.5.sp, lineHeight = 16.1.sp),
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@FlowCard
        }
        Row {
            Box(
                Modifier
                    .size(width = 70.dp, height = 124.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFDDE1D6), RoundedCornerShape(8.dp))
                    .background(colors.surfaceVariant)
                    .clickable(enabled = state is ProofState.Attached, onClick = onView),
            ) {
                if (thumbnail != null) Image(thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                when (state) {
                    is ProofState.Uploading -> Box(Modifier.fillMaxSize().background(Color(0x8C0E1A11)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xD90E1A11)), contentAlignment = Alignment.Center) {
                            Text("${(state.progress * 100).toInt()}%", style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp), color = Color.White)
                        }
                    }
                    ProofState.Failed -> Box(Modifier.fillMaxSize().background(Color(0x8C8C1D12)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_error), null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    is ProofState.Attached -> Box(
                        Modifier.align(Alignment.BottomEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Color(0xB30E1A11)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_open_in_full), "View screenshot", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    else -> Unit
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).height(124.dp)) {
                when (state) {
                    is ProofState.Uploading -> {
                        Text("Uploading…", style = pText(13.5.sp, FontWeight.SemiBold), color = colors.onBackground)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceVariant)) {
                            Box(Modifier.fillMaxWidth(state.progress.coerceIn(0f, 1f)).height(4.dp).background(colors.primary))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(state.caption, style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.5.sp), color = colors.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("Cancel", style = pText(12.5.sp, FontWeight.SemiBold), color = colors.onSurfaceVariant, modifier = Modifier.clickable(onClick = onCancel))
                    }
                    ProofState.Failed -> {
                        Text("Upload failed", style = pText(13.5.sp, FontWeight.SemiBold), color = CrichereErrorStrong)
                        Spacer(Modifier.height(6.dp))
                        Text("Check your connection.", style = pText(12.sp, lineHeight = 16.8.sp), color = colors.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SmallAction("Retry", filled = true, onClick = onRetry)
                            Text("Remove", style = pText(12.5.sp, FontWeight.SemiBold), color = colors.onSurfaceVariant, modifier = Modifier.clickable(onClick = onRemove).padding(8.dp))
                        }
                    }
                    is ProofState.Attached -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_check_circle_filled), null, tint = colors.primary, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Attached", style = pText(13.5.sp, FontWeight.SemiBold), color = colors.primary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(state.caption, style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.5.sp), color = colors.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            SmallAction("Replace", filled = false, icon = R.drawable.ic_swap_horiz, onClick = onChoose)
                            Text("Remove", style = pText(12.5.sp, FontWeight.SemiBold), color = CrichereErrorStrong, modifier = Modifier.clickable(onClick = onRemove).padding(8.dp))
                        }
                    }
                    ProofState.Empty -> Unit
                }
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, filled: Boolean, icon: Int? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .height(32.dp)
            .clip(shape)
            .background(if (filled) colors.primary else Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(1.dp, colors.outline, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = colors.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = pText(12.5.sp, FontWeight.SemiBold), color = if (filled) colors.onPrimary else colors.primary)
    }
}

@Composable
internal fun FlowPill(
    text: String,
    icon: Int?,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(height / 2)
    val container = when {
        !enabled -> Color(0xFFDCE0D7)
        filled -> colors.primary
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> Color(0xFF8A948C)
        filled -> colors.onPrimary
        else -> colors.primary
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(container)
            .then(if (!filled && enabled) Modifier.border(1.dp, colors.outline, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = pText(13.5.sp, FontWeight.SemiBold), color = content)
    }
}

/**
 * Design F3/F4: full preview of the picked screenshot with a "tick what you can see" checklist;
 * "Use this" stays disabled until every item is ticked (decided instead of automatic receipt
 * detection, which the app can't do).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenshotCheckSheet(
    bitmap: Bitmap,
    checklist: List<String>,
    onDismiss: () -> Unit,
    onChooseAnother: () -> Unit,
    onUse: () -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val ticked = remember(bitmap) { mutableStateListOf(*Array(checklist.size) { false }) }
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        scrimColor = Color(0x800A120C),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(width = 32.dp, height = 4.dp).background(Color(0xFFB9C1B4), RoundedCornerShape(2.dp))) },
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = 15.dp, end = 15.dp, bottom = 24.dp)) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = colors.onBackground, modifier = Modifier.padding(top = 4.dp).size(22.dp).clickable(onClick = onDismiss))
                Spacer(Modifier.width(6.dp))
                Column {
                    Text("Preview", style = pText(17.sp, FontWeight.SemiBold, 17.sp), color = colors.onBackground)
                    Spacer(Modifier.height(4.dp))
                    Text("Payment screenshot · sent to the organizer as-is", style = pText(12.sp, lineHeight = 12.sp), color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF101410)).padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(image, contentDescription = "Payment screenshot", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(12.dp))
            Text("Tick what you can see in the screenshot", style = pText(12.sp, FontWeight.SemiBold), color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(4.dp))
            checklist.forEachIndexed { i, item ->
                Row(
                    Modifier.fillMaxWidth().height(38.dp).clickable { ticked[i] = !ticked[i] },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = ticked[i],
                        onCheckedChange = { ticked[i] = it },
                        colors = CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = Color(0xFF8A948C)),
                    )
                    Text(item, style = pText(13.5.sp, FontWeight.Medium), color = colors.onBackground)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                val label = pText(14.5.sp, FontWeight.SemiBold)
                OutlinedButton(
                    onClick = onChooseAnother,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, colors.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Choose another", style = label) }
                Button(
                    onClick = onUse,
                    enabled = ticked.all { it },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        disabledContainerColor = Color(0xFFDCE0D7),
                        disabledContentColor = Color(0xFF8A948C),
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Use this", style = label) }
            }
        }
    }
}

/** "payment-proof.jpg · 412 KB". */
internal fun fileCaption(name: String?, bytes: Long?): String {
    val n = name ?: "payment-proof.jpg"
    val b = bytes ?: return n
    val size = if (b >= 1_048_576) "%.1f MB".format(b / 1_048_576.0) else "${(b + 1023) / 1024} KB"
    return "$n · $size"
}

package com.crichere.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crichere.app.R
import com.crichere.app.ui.theme.InstrumentSansFamily
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A photo the user picked, decoded and ready to crop. */
class PickedPhoto(val bitmap: Bitmap, val fileName: String)

/** The square region of [PickedPhoto.bitmap] (in bitmap pixels) the circle currently covers. */
data class CropSquare(val left: Float, val top: Float, val side: Float)

private const val MaxDecodeDimension = 2048
private const val OutputSize = 512
private const val MaxZoom = 5f
private val CropCircle = 230.dp
private val CropSquareSide = 220.dp

/** Decodes [uri] (EXIF-rotated on API 28+), downsampled so its longest side is at most 2048px. */
fun decodePickedPhoto(context: Context, uri: Uri): PickedPhoto? = runCatching {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MaxDecodeDimension) {
                val scale = MaxDecodeDimension.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MaxDecodeDimension) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } ?: return null
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "photo.jpg"
    PickedPhoto(bitmap, name)
}.getOrNull()

/** Crops [square] out of [bitmap] and encodes it as a [OutputSize]px JPEG. */
fun encodeCrop(bitmap: Bitmap, square: CropSquare): ByteArray {
    val left = square.left.roundToInt().coerceIn(0, bitmap.width - 1)
    val top = square.top.roundToInt().coerceIn(0, bitmap.height - 1)
    val side = square.side.roundToInt().coerceAtMost(min(bitmap.width - left, bitmap.height - top)).coerceAtLeast(1)
    val cropped = Bitmap.createBitmap(bitmap, left, top, side, side)
    val scaled = Bitmap.createScaledBitmap(cropped, OutputSize, OutputSize, true)
    return ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        out.toByteArray()
    }
}

/** Draws [square] of [image] scaled into this DrawScope's full bounds. */
private fun DrawScope.drawCrop(image: ImageBitmap, square: CropSquare) {
    drawImage(
        image = image,
        srcOffset = IntOffset(square.left.roundToInt(), square.top.roundToInt()),
        srcSize = IntSize(square.side.roundToInt(), square.side.roundToInt()),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    )
}

/**
 * Design screen C3: circle-crop preview shown after picking a photo, before anything uploads. Drag
 * or pinch to position; "Use photo" hands back the JPEG bytes of the crop. [square] switches to the
 * rounded-square window used for franchise logos (design G2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCropSheet(
    photo: PickedPhoto,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onChooseAnother: () -> Unit,
    onUsePhoto: (CropSquare) -> Unit,
    square: Boolean = false,
    useLabel: String = "Use photo",
) {
    val image = remember(photo) { photo.bitmap.asImageBitmap() }
    val bitmapW = photo.bitmap.width.toFloat()
    val bitmapH = photo.bitmap.height.toFloat()
    val circlePx = with(LocalDensity.current) { (if (square) CropSquareSide else CropCircle).toPx() }
    val windowRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }
    val baseScale = circlePx / min(bitmapW, bitmapH)

    var zoom by remember(photo) { mutableFloatStateOf(1f) }
    var pan by remember(photo) { mutableStateOf(Offset.Zero) }

    fun clampPan(p: Offset, z: Float): Offset {
        val s = baseScale * z
        val maxX = (bitmapW * s - circlePx) / 2f
        val maxY = (bitmapH * s - circlePx) / 2f
        return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
    }

    val crop = run {
        val s = baseScale * zoom
        val side = circlePx / s
        CropSquare(
            left = bitmapW / 2f - pan.x / s - side / 2f,
            top = bitmapH / 2f - pan.y / s - side / 2f,
            side = side,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = Color(0x800A120C),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(Color(0xFFB9C1B4), RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(start = 15.dp, end = 15.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 12.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp).size(22.dp).clickable(onClick = onDismiss),
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        title,
                        style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 12.sp, lineHeight = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF101410))
                    .pointerInput(photo) {
                        detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                            val newZoom = (zoom * gestureZoom).coerceIn(1f, MaxZoom)
                            zoom = newZoom
                            pan = clampPan(pan + gesturePan, newZoom)
                        }
                    },
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val s = baseScale * zoom
                    val drawW = bitmapW * s
                    val drawH = bitmapH * s
                    val topLeft = Offset(center.x - drawW / 2f + pan.x, center.y - drawH / 2f + pan.y)
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                        dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                    )
                    val window = Rect(center, circlePx / 2f)
                    val hole = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        if (square) addRoundRect(RoundRect(window, CornerRadius(windowRadiusPx))) else addOval(window)
                    }
                    drawPath(hole, Color(0x9E0A0E0A))
                    if (square) {
                        drawRoundRect(Color.White, window.topLeft, window.size, CornerRadius(windowRadiusPx), style = Stroke(2.dp.toPx()))
                    } else {
                        drawCircle(Color.White, radius = circlePx / 2f, center = center, style = Stroke(2.dp.toPx()))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            if (square) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Canvas(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))) { drawCrop(image, crop) }
                    Spacer(Modifier.width(10.dp))
                    Canvas(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) { drawCrop(image, crop) }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "At auction and roster sizes",
                        style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 12.sp, lineHeight = 16.2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                ) { drawCrop(image, crop) }
                Spacer(Modifier.width(16.dp))
                Text(
                    "How it will look on rosters and the auction screen",
                    style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 12.5.sp, lineHeight = 16.9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(if (square) 14.dp else 19.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                val label = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                OutlinedButton(
                    onClick = onChooseAnother,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Choose another", style = label) }
                Button(
                    onClick = { onUsePhoto(crop) },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text(useLabel, style = label) }
            }
        }
    }
}

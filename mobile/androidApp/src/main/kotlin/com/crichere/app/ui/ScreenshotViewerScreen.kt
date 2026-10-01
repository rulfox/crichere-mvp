package com.crichere.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import com.crichere.app.R
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereAuctionBg
import com.crichere.app.ui.theme.CrichereAuctionGold
import com.crichere.app.ui.theme.CrichereAuctionMuted
import com.crichere.app.ui.theme.JetBrainsMonoFamily
import java.util.Locale

private val ViewerBg = Color(0xFF050805)
private val ViewerIconMuted = Color(0xFF5E7064)
private const val MaxZoom = 5f

/**
 * Design screen H: full-screen, pinch-zoomable view of a payment-proof screenshot (H1). Zooming in
 * hides the top bar and Back for a zoom chip and a minimap of the visible area (H2); a failed load
 * offers Retry (H3).
 */
@Composable
internal fun ScreenshotViewerRoute(imageUrl: String, onBack: () -> Unit) {
    // The backend already restricts this field to https:// (see LeaguePlayerJoinRequest/
    // LeagueFranchiseClaimRequest), but this is a second, independent check right at the point the
    // URL is actually opened -- a `file://`/`content://` value here would otherwise read local
    // storage on whichever device views this "screenshot".
    val allowed = imageUrl.startsWith("https://")
    var attempt by remember(imageUrl) { mutableIntStateOf(0) }
    val context = LocalContext.current
    // A new request per attempt makes Retry reload; failures aren't cached. Full size: the viewer
    // draws only once the image is in, so there is no layout size to resolve against yet.
    val request = remember(imageUrl, attempt) {
        ImageRequest.Builder(context).data(if (allowed) imageUrl else null).size(CoilSize.ORIGINAL).build()
    }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsStateWithLifecycle()

    LightStatusBarIcons(enabled = true, navigationBar = true)
    ScreenshotViewerScreen(
        painter = painter,
        loaded = state is AsyncImagePainter.State.Success,
        failed = !allowed || state is AsyncImagePainter.State.Error,
        onRetry = { attempt++ },
        onBack = onBack,
    )
}

@Composable
internal fun ScreenshotViewerScreen(painter: Painter, loaded: Boolean, failed: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(Size.Zero) }
    var imageBounds by remember { mutableStateOf(Rect.Zero) }
    val zoomed = loaded && scale > 1.01f

    Box(
        Modifier
            .fillMaxSize()
            .background(if (failed) CrichereAuctionBg else ViewerBg)
            .onSizeChanged { rootSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(loaded) {
                if (!loaded) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, MaxZoom)
                    offset = clampPan(offset + pan, scale, imageBounds, rootSize)
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.statusBarsPadding().alpha(if (zoomed) 0f else 1f)) { ViewerTopBar(onBack = onBack, enabled = !zoomed) }
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 29.dp, bottom = 35.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (loaded) {
                    val intrinsic = painter.intrinsicSize
                    val density = LocalDensity.current
                    // ContentScale.Fit by hand, so the rounded corners sit on the image itself.
                    val fit = with(density) {
                        val boxW = maxWidth.toPx()
                        val boxH = maxHeight.toPx()
                        val ratio = if (intrinsic.isUnspecified() || intrinsic.height == 0f) boxW / boxH else intrinsic.width / intrinsic.height
                        if (ratio > boxW / boxH) Size(boxW, boxW / ratio) else Size(boxH * ratio, boxH)
                    }
                    Image(
                        painter = painter,
                        contentDescription = "Payment screenshot",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .size(with(density) { fit.width.toDp() }, with(density) { fit.height.toDp() })
                            .onGloballyPositioned { imageBounds = it.boundsInRoot() }
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                }
            }
            Box(
                Modifier
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, bottom = 28.dp)
                    .alpha(if (zoomed) 0f else 1f),
            ) {
                BackPill(onBack = onBack, enabled = !zoomed)
            }
        }

        when {
            failed -> LoadFailed(onRetry = onRetry, modifier = Modifier.align(Alignment.Center))
            !loaded -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        if (zoomed) {
            Box(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 18.dp, top = 6.dp)) {
                Box(
                    Modifier.height(30.dp).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(15.dp)).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        String.format(Locale.US, "%.1f×", scale),
                        style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 12.sp),
                        color = Color.White,
                    )
                }
            }
            Minimap(
                painter = painter,
                visible = visibleFraction(scale, offset, imageBounds, rootSize),
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun ViewerTopBar(onBack: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text("Payment screenshot", style = pText(15.sp, FontWeight.SemiBold, 15.sp), color = Color.White)
    }
}

@Composable
private fun BackPill(onBack: () -> Unit, enabled: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Text("Back", style = pText(14.5.sp, FontWeight.SemiBold, 14.5.sp), color = Color.White)
    }
}

/** H3: the screenshot didn't load. */
@Composable
private fun LoadFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(painterResource(R.drawable.ic_broken_image), contentDescription = null, tint = ViewerIconMuted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            "Couldn't load this screenshot.",
            style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 20.4.sp),
            color = Color.White,
        )
        Spacer(Modifier.height(10.dp))
        Text("Check your connection and try again.", style = pText(13.sp, lineHeight = 18.85.sp), color = CrichereAuctionMuted)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(CrichereAuctionGold).clickable(onClick = onRetry).padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Retry", style = pText(13.5.sp, FontWeight.SemiBold), color = CrichereAuctionBg)
        }
    }
}

/** H2: the whole screenshot in miniature, with the part on screen outlined. */
@Composable
private fun Minimap(painter: Painter, visible: Rect, modifier: Modifier = Modifier) {
    val intrinsic = painter.intrinsicSize
    val ratio = if (intrinsic.isUnspecified() || intrinsic.height == 0f) 56f / 102f else intrinsic.width / intrinsic.height
    val innerHeight = (56f / ratio).coerceIn(32f, 120f)
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black)
            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(2.dp),
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.size(56.dp, innerHeight.dp).alpha(0.7f),
        )
        Canvas(Modifier.size(56.dp, innerHeight.dp)) {
            val stroke = 2.dp.toPx()
            drawRect(
                color = CrichereAuctionGold,
                topLeft = Offset(visible.left * size.width + stroke / 2, visible.top * size.height + stroke / 2),
                size = Size(visible.width * size.width - stroke, visible.height * size.height - stroke),
                style = Stroke(stroke),
            )
        }
    }
}

private fun Size.isUnspecified() = width.isNaN() || height.isNaN() || width <= 0f || height <= 0f

/**
 * Keeps a zoomed image covering the screen: it can pan until its edge meets the screen edge, and
 * stays centred on an axis where it is still narrower than the screen.
 */
internal fun clampPan(offset: Offset, scale: Float, image: Rect, screen: Size): Offset {
    if (image.isEmpty || screen.width == 0f) return Offset.Zero
    fun axis(value: Float, length: Float, center: Float, screenLength: Float): Float {
        val scaled = length * scale
        if (scaled <= screenLength) return 0f
        // Centre may move between screenLength - scaled/2 and scaled/2 (screen coordinates).
        return (center + value).coerceIn(screenLength - scaled / 2, scaled / 2) - center
    }
    return Offset(
        axis(offset.x, image.width, image.center.x, screen.width),
        axis(offset.y, image.height, image.center.y, screen.height),
    )
}

/** The part of the image on screen, as fractions of the image (0..1 on each axis). */
internal fun visibleFraction(scale: Float, offset: Offset, image: Rect, screen: Size): Rect {
    if (image.isEmpty) return Rect(0f, 0f, 1f, 1f)
    val center = image.center + offset
    val halfW = image.width * scale / 2
    val halfH = image.height * scale / 2
    val shown = Rect(center.x - halfW, center.y - halfH, center.x + halfW, center.y + halfH)
    val onScreen = shown.intersect(Rect(0f, 0f, screen.width, screen.height))
    return Rect(
        ((onScreen.left - shown.left) / shown.width).coerceIn(0f, 1f),
        ((onScreen.top - shown.top) / shown.height).coerceIn(0f, 1f),
        ((onScreen.right - shown.left) / shown.width).coerceIn(0f, 1f),
        ((onScreen.bottom - shown.top) / shown.height).coerceIn(0f, 1f),
    )
}

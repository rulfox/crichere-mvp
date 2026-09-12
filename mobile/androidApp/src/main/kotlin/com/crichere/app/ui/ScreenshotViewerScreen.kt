package com.crichere.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Full-screen, pinch-zoomable view of a payment-proof screenshot -- see docs/PHASE3.md ("a
 * real-viewer for the organizer to actually check it"). No image-loading library (e.g. Coil)
 * exists anywhere in this app yet -- adding one is real new scope for a single screen, so this
 * loads the JPEG directly via [URL]/[BitmapFactory] on [Dispatchers.IO], the same standard-library
 * approach the rest of this app uses for network work it doesn't yet have a library for.
 */
@Composable
internal fun ScreenshotViewerRoute(imageUrl: String, onBack: () -> Unit) {
    var bitmap by remember(imageUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var errorMessage by remember(imageUrl) { mutableStateOf<String?>(null) }

    LaunchedEffect(imageUrl) {
        runCatching {
            // The backend already restricts this field to https:// (see LeaguePlayerJoinRequest/
            // LeagueFranchiseClaimRequest), but this is a second, independent check right at the
            // point the URL is actually opened -- a `file://`/`content://` value here would
            // otherwise read local storage on whichever device views this "screenshot".
            require(imageUrl.startsWith("https://")) { "Unsupported image URL" }
            withContext(Dispatchers.IO) {
                URL(imageUrl).openStream().use { BitmapFactory.decodeStream(it) }
            }
        }.onSuccess { bitmap = it }
            .onFailure { errorMessage = it.message ?: "Couldn't load this screenshot." }
    }

    ScreenshotViewerScreen(bitmap = bitmap, errorMessage = errorMessage, onBack = onBack)
}

@Composable
private fun ScreenshotViewerScreen(bitmap: android.graphics.Bitmap?, errorMessage: String?, onBack: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Payment screenshot",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
            )

            errorMessage != null -> Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )

            else -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.padding(16.dp)) { Text("Back") }
    }
}

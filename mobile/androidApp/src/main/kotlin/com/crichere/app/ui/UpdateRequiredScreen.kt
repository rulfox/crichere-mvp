package com.crichere.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.R
import com.crichere.app.account.AppUpdateNotices
import com.crichere.app.account.AppUpdateRequired
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereGreenContainer
import com.crichere.app.ui.theme.CrichereOnWarning
import com.crichere.app.ui.theme.CrichereWarningContainer
import com.crichere.app.ui.theme.JetBrainsMonoFamily
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import org.koin.compose.koinInject

private const val PLAY_STORE_FALLBACK = "market://details?id=com.crichere.app"
private const val IMMEDIATE_UPDATE_REQUEST = 4231

/**
 * Covers the whole app once the backend says this build is too old (docs/PHASE23.md). Installed from
 * Play, Google's own immediate-update screen is tried first; otherwise (or if it's dismissed) design U1
 * stays, and nothing behind it can be reached -- Back does nothing.
 */
@Composable
fun UpdateRequiredGate(notices: AppUpdateNotices = koinInject()) {
    val required by notices.required.collectAsStateWithLifecycle()
    val notice = required ?: return
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val activity = context as? Activity ?: return@LaunchedEffect
        runCatching {
            val manager = AppUpdateManagerFactory.create(context)
            val info = manager.requestAppUpdateInfo()
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                manager.startUpdateFlowForResult(info, activity, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(), IMMEDIATE_UPDATE_REQUEST)
            }
        } // Not installed from Play (debug, sideloaded) or Play unavailable: our own screen is enough.
    }
    BackHandler(enabled = true) { /* The only way on is updating. */ }
    UpdateRequiredScreen(notice)
}

/** Design U1 (Android) and U2 (the store link couldn't open). */
@Composable
fun UpdateRequiredScreen(notice: AppUpdateRequired) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var openFailed by rememberSaveable { mutableStateOf(false) }
    val open = {
        val url = notice.updateUrl?.takeIf { it.isNotBlank() } ?: PLAY_STORE_FALLBACK
        // U2: nothing on the phone can open the link.
        openFailed = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            false
        } catch (_: ActivityNotFoundException) {
            true
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            // Swallows taps so nothing behind the screen can be reached.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            UpdateArt()
            Spacer(Modifier.height(32.dp))
            Text(
                "Update Crichere to continue",
                style = pText(26.sp, FontWeight.ExtraBold, lineHeight = 32.sp).copy(fontFamily = ArchivoFamily, letterSpacing = (-0.26).sp),
                color = colors.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "This version of the app is no longer supported. Update to keep joining leagues and bidding in auctions.",
                style = pText(15.sp, lineHeight = 22.sp),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
            val current = notice.currentVersion
            val needed = notice.minVersion
            if (current != null && needed != null) {
                Spacer(Modifier.height(22.dp))
                Text(
                    buildAnnotatedString {
                        val mono = SpanStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                        append("You have ")
                        withStyle(mono) { append(current) }
                        append("  ·  Needed ")
                        withStyle(mono) { append(needed) }
                    },
                    style = pText(13.sp),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape).background(colors.surfaceVariant).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (openFailed) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CrichereWarningContainer)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Icon(painterResource(R.drawable.ic_info), null, tint = CrichereOnWarning, modifier = Modifier.size(18.dp))
                    Text(
                        "Couldn’t open Google Play. Open the Play Store yourself and update Crichere.",
                        style = pText(13.5.sp, lineHeight = 19.sp),
                        color = CrichereOnWarning,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(onClick = open),
            ) {
                if (openFailed) Icon(painterResource(R.drawable.ic_refresh), null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(if (openFailed) "Try again" else "Update on Google Play", style = pText(16.sp, FontWeight.SemiBold), color = Color.White)
            }
            Text(
                "Your leagues, bids and profile are saved. Nothing is lost.",
                style = pText(12.5.sp, lineHeight = 18.sp),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** U1's art: a phone with an up arrow in a green circle. */
@Composable
private fun UpdateArt() {
    val green = MaterialTheme.colorScheme.primary
    Box(Modifier.size(132.dp).clip(CircleShape).background(CrichereGreenContainer)) {
        Box(
            Modifier
                .offset(x = 38.dp, y = 22.dp)
                .size(width = 56.dp, height = 88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(2.5.dp, green, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = green, modifier = Modifier.size(26.dp).rotate(-90f))
        }
    }
}

package com.crichere.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties
import com.crichere.app.ui.theme.CrichereErrorStrong
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.crichere.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereDisabledContainer
import com.crichere.app.ui.theme.CrichereInk
import com.crichere.app.ui.theme.CrichereInkSubtle
import com.crichere.app.ui.theme.InstrumentSansFamily

private val PillShape = RoundedCornerShape(26.dp)
private val PillLabel = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

/** Compact green wordmark used as the header on auth screens once the hero is gone. */
@Composable
fun CrichereWordmark(modifier: Modifier = Modifier) {
    Text(
        text = "Crichere",
        style = TextStyle(
            fontFamily = ArchivoFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.84).sp,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/**
 * Full-width 52dp green pill. While [loading] it stays green (at 85% opacity, per the design's
 * in-flight state) instead of greying out; callers' ViewModels already ignore repeat taps.
 */
@Composable
fun CricherePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String = text,
) {
    Button(
        onClick = onClick,
        enabled = enabled || loading,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = CrichereDisabledContainer,
            disabledContentColor = CrichereInkSubtle,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (loading) 0.85f else 1f),
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
                Text(loadingText, style = PillLabel)
            }
        } else {
            Text(text, style = PillLabel)
        }
    }
}

/** Full-width 52dp outlined pill (secondary action). */
@Composable
fun CrichereOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = PillShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(text, style = PillLabel)
    }
}

/** Icon + title + body callout (error / warning banners). */
@Composable
fun NoticeCard(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    containerColor: Color,
    titleColor: Color,
    bodyColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = titleColor,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 16.8.sp),
                color = titleColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 12.5.sp, lineHeight = 17.5.sp),
                color = bodyColor,
            )
        }
    }
}

private val SnackbarAction = Color(0xFFA8D5A0)

/** U5 L21: the I12 bar inverted for the dark auction screen. */
private val InverseSnackbar = Color(0xFFF5F6F1)

/**
 * The board's dark bar (I12, J4, J5): message plus an optional green action. Placement (bottom
 * offset, nav-bar padding) is the caller's [modifier] -- each screen sits it above its own bar.
 */
@Composable
fun CrichereSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    inverse: Boolean = false,
) {
    val shadow = Color.Black.copy(alpha = if (inverse) 0.4f else 0.25f)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(10.dp), ambientColor = shadow, spotColor = shadow)
            .background(if (inverse) InverseSnackbar else CrichereInk, RoundedCornerShape(10.dp))
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 18.225.sp),
            color = if (inverse) CrichereInk else Color.White,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                actionLabel,
                style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 13.5.sp),
                color = if (inverse) MaterialTheme.colorScheme.primary else SnackbarAction,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}

/** Back arrow + 16sp title, 52dp under the status bar (boards J, K). */
@Composable
fun BackTitleBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 5.dp).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 19.2.sp),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** A message for [SnackHost]. A `null` [durationMs] stays until its action or a swipe (U4 E12). */
class Snack(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null, val durationMs: Long? = 4_000)

/** Where [SnackHost] sits: 24 dp above the navigation bar, or 12 dp above a docked panel it's placed over (U5 L21). */
enum class SnackPlacement { ABOVE_NAV_BAR, ABOVE_DOCK }

/**
 * The I12 snackbar at the bottom of a screen: 12 dp in from the sides, 24 dp above the navigation bar,
 * swipe sideways to dismiss. The caller owns [snack] and its timing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnackHost(
    snack: Snack?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    inverse: Boolean = false,
    placement: SnackPlacement = SnackPlacement.ABOVE_NAV_BAR,
) {
    var shown by remember { mutableStateOf(snack) }
    if (snack != null) shown = snack
    val placed = when (placement) {
        SnackPlacement.ABOVE_NAV_BAR -> modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 24.dp)
        SnackPlacement.ABOVE_DOCK -> modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
    }
    AnimatedVisibility(visible = snack != null, enter = fadeIn(), exit = fadeOut(), modifier = placed) {
        val current = shown ?: return@AnimatedVisibility
        key(current) {
            val dismissState = rememberSwipeToDismissBoxState()
            LaunchedEffect(dismissState.currentValue) {
                if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) onDismiss()
            }
            SwipeToDismissBox(state = dismissState, backgroundContent = {}) {
                CrichereSnackbar(current.message, actionLabel = current.actionLabel, onAction = current.onAction, inverse = inverse)
            }
        }
    }
}

/**
 * The N4 / K3 confirmation shell for an irreversible action (U4 E10/E11, K9): the confirm button is in
 * the destructive red. While [submitting], it shows a spinner and [submittingLabel], Cancel fades to 38%
 * and stops responding, and the dialog can't be dismissed.
 */
@Composable
fun DestructiveConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    submittingLabel: String,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val buttonText = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 14.sp)
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !submitting, dismissOnClickOutside = !submitting),
        modifier = Modifier.padding(horizontal = 23.dp),
        containerColor = DialogSurface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(title, style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 22.8.sp), color = CrichereInk)
        },
        text = {
            Text(body, style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 13.5.sp, lineHeight = 19.575.sp), color = DialogBody)
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !submitting) {
                if (submitting) {
                    CircularProgressIndicator(
                        color = CrichereErrorStrong,
                        trackColor = CrichereErrorStrong.copy(alpha = 0.25f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (submitting) submittingLabel else confirmLabel, style = buttonText, color = CrichereErrorStrong)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting, modifier = Modifier.alpha(if (submitting) 0.38f else 1f)) {
                Text("Cancel", style = buttonText, color = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

private val DialogSurface = Color(0xFFF1F4EE)
private val DialogBody = Color(0xFF3E4A41)

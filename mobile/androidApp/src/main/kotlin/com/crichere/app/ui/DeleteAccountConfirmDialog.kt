package com.crichere.app.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.crichere.app.R
import com.crichere.app.account.DeleteAccountDialogState
import com.crichere.app.ui.theme.CrichereErrorStrong
import com.crichere.app.ui.theme.CrichereInk
import com.crichere.app.ui.theme.CrichereInkSubtle
import com.crichere.app.ui.theme.InstrumentSansFamily
import com.crichere.app.ui.theme.JetBrainsMonoFamily

private val DialogSurface = Color(0xFFF1F4EE)
private val DialogBody = Color(0xFF3E4A41)
private val Disabled = CrichereInk.copy(alpha = 0.38f)

private fun dialogText(size: TextUnit, weight: FontWeight = FontWeight.Normal, lineHeight: TextUnit = size) =
    TextStyle(fontFamily = InstrumentSansFamily, fontWeight = weight, fontSize = size, lineHeight = lineHeight)

/**
 * D6a–e: the typed-DELETE confirmation. Confirm is enabled only for exactly `DELETE`
 * ([DeleteAccountDialogState.canConfirm]); any other input shows the D6b helper. While submitting
 * (D6d) nothing in it responds and back / the scrim can't close it. A failure (D6e) keeps it open.
 */
@Composable
internal fun DeleteAccountConfirmDialog(
    state: DeleteAccountDialogState,
    erasesOn: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val submitting = state.isSubmitting
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !submitting, dismissOnClickOutside = !submitting),
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .background(DialogSurface, RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(painterResource(R.drawable.ic_delete_forever), contentDescription = null, tint = CrichereErrorStrong, modifier = Modifier.size(26.dp))
            Text("Delete your account?", style = dialogText(19.sp, FontWeight.SemiBold, 22.8.sp), color = CrichereInk)
            Text(
                "You'll be signed out on all your devices. Your account is erased on $erasesOn unless you sign in before then.",
                style = dialogText(13.5.sp, lineHeight = 19.575.sp),
                color = DialogBody,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Type DELETE to confirm", style = dialogText(12.5.sp, FontWeight.SemiBold), color = DialogBody)
                ConfirmField(state, onInputChange)
                if (state.showMismatch && !submitting) {
                    Text("Type it in capital letters: DELETE", style = dialogText(12.5.sp, lineHeight = 16.875.sp), color = colors.error)
                }
            }
            if (state.failed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_error_filled), contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
                    Text(
                        "Couldn't delete your account. Check your connection and try again.",
                        style = dialogText(13.sp, lineHeight = 18.2.sp),
                        color = colors.error,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancel",
                    style = dialogText(14.sp, FontWeight.SemiBold),
                    color = colors.primary,
                    modifier = Modifier
                        .alpha(if (submitting) 0.38f else 1f)
                        .clickable(enabled = !submitting, onClick = onDismiss)
                        .padding(4.dp),
                )
                Spacer(Modifier.width(14.dp))
                Row(
                    Modifier.clickable(enabled = state.canConfirm, onClick = onConfirm).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            color = CrichereErrorStrong,
                            trackColor = CrichereErrorStrong.copy(alpha = 0.25f),
                            strokeWidth = 1.6.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        if (submitting) "Deleting…" else "Delete account",
                        style = dialogText(14.sp, FontWeight.SemiBold),
                        color = if (state.canConfirm || submitting) CrichereErrorStrong else Disabled,
                    )
                }
            }
        }
    }
}

/**
 * The 56 dp field: mono capitals, no autocorrect. Border green while focused (D6a/c), red with an
 * error icon on a mismatch (D6b), plain outline once it loses focus (D6e), greyed while submitting (D6d).
 */
@Composable
private fun ConfirmField(state: DeleteAccountDialogState, onInputChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val submitting = state.isSubmitting
    val (borderWidth, borderColor) = when {
        submitting -> 1.dp to colors.outlineVariant
        state.showMismatch -> 1.6.dp to colors.error
        focused -> 1.6.dp to colors.primary
        else -> 1.dp to colors.outline
    }
    val shape = RoundedCornerShape(12.dp)
    val mono = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 16.sp, letterSpacing = 1.28.sp)
    BasicTextField(
        value = state.input,
        onValueChange = onInputChange,
        enabled = !submitting,
        singleLine = true,
        textStyle = mono.copy(color = if (submitting) CrichereInkSubtle else CrichereInk),
        cursorBrush = SolidColor(colors.primary),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = {}),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(if (submitting) colors.background else colors.surface, shape)
                    .border(borderWidth, borderColor, shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (state.input.isEmpty()) Text("DELETE", style = dialogText(15.sp), color = CrichereInkSubtle)
                    inner()
                }
                when {
                    submitting -> Unit
                    state.showMismatch -> Icon(painterResource(R.drawable.ic_error_filled), contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
                    // D6e: once focus is gone the field is plain -- no check.
                    state.canConfirm && focused -> Icon(painterResource(R.drawable.ic_check_circle_filled), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                }
            }
        },
    )
}

package com.crichere.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.R
import com.crichere.app.auth.OtpVerifyState
import com.crichere.app.auth.OtpVerifyViewModel
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereErrorBody
import com.crichere.app.ui.theme.CrichereErrorField
import com.crichere.app.ui.theme.CrichereErrorStrong
import com.crichere.app.ui.theme.CrichereFieldDisabled
import com.crichere.app.ui.theme.CrichereInkDisabled
import com.crichere.app.ui.theme.CrichereInkSubtle
import com.crichere.app.ui.theme.CrichereOutlineDisabled
import com.crichere.app.ui.theme.InstrumentSansFamily
import com.crichere.app.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay

/**
 * OTP Verify screen (design board screen B): 6-digit code entry, 60s resend cooldown with a visible
 * countdown, max 3 resends, max 5 wrong attempts (all enforced by [OtpVerifyViewModel], not this
 * composable -- this screen only renders [OtpVerifyViewModel.state] and forwards actions).
 * Navigation (Profile Setup / Own Profile / back-to-Phone-Entry) is handled by the caller
 * (`AuthNavHost`) via [OtpVerifyViewModel.navigationEvents]; back and "Edit" both go through
 * [OtpVerifyViewModel.startOver].
 */
@Composable
fun OtpVerifyScreen(viewModel: OtpVerifyViewModel, phoneNumber: String) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    var showResentSnackbar by remember { mutableStateOf(false) }
    LaunchedEffect(state.resendsUsed) {
        if (state.resendsUsed > 0) {
            showResentSnackbar = true
            delay(4_000)
            showResentSnackbar = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        IconButton(
            onClick = viewModel::startOver,
            enabled = !state.isVerifying,
            modifier = Modifier.padding(start = 4.dp, top = 5.dp).size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint = if (state.isVerifying) CrichereInkDisabled else colors.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 30.dp),
        ) {
            Text(
                text = "Enter verification code",
                style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 26.4.sp),
                color = colors.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            SentToText(phoneNumber = phoneNumber, showEdit = !state.isVerifying, onEdit = viewModel::startOver)
            Spacer(Modifier.height(18.dp))

            CodeField(state = state, onCodeChanged = viewModel::onCodeChanged)

            if (state.errorMessage != null && !state.resendsExhausted) {
                Spacer(Modifier.height(10.dp))
                ErrorRow(state.errorMessage.orEmpty())
            }

            if (state.resendsExhausted) {
                Spacer(Modifier.height(20.dp))
                NoticeCard(
                    icon = R.drawable.ic_block,
                    title = "No more codes available for this number.",
                    body = "You can still enter the last code sent, or start over.",
                    containerColor = colors.errorContainer,
                    titleColor = CrichereErrorStrong,
                    bodyColor = CrichereErrorBody,
                )
            } else {
                Spacer(Modifier.height(16.dp))
                ResendRow(state = state, onResend = viewModel::resendCode)
            }

            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.weight(1f))

            AnimatedVisibility(visible = showResentSnackbar, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    ResentSnackbar(phoneNumber)
                    Spacer(Modifier.height(22.dp))
                }
            }
            if (state.resendsExhausted) {
                CrichereOutlinedButton(text = "Request a new code", onClick = viewModel::startOver)
                Spacer(Modifier.height(20.dp))
            }
            CricherePrimaryButton(
                text = "Verify",
                onClick = viewModel::verifyCode,
                enabled = state.code.length == OtpVerifyViewModel.OTP_LENGTH,
                loading = state.isVerifying,
                loadingText = "Verifying…",
            )
        }
    }
}

@Composable
private fun SentToText(phoneNumber: String, showEdit: Boolean, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val text = buildAnnotatedString {
        append("We sent a 6-digit code by SMS to ")
        withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, color = colors.onBackground)) { append(phoneNumber) }
        append(".")
        if (showEdit) {
            append(" ")
            withLink(
                LinkAnnotation.Clickable(
                    tag = "edit",
                    styles = TextLinkStyles(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.primary)),
                ) { onEdit() },
            ) { append("Edit") }
        }
    }
    Text(
        text = text,
        style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 13.5.sp, lineHeight = 18.9.sp),
        color = colors.onSurfaceVariant,
    )
}

@Composable
private fun CodeField(state: OtpVerifyState, onCodeChanged: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isError = state.errorMessage != null && !state.resendsExhausted

    val look = when {
        state.isVerifying -> FieldLook(1.dp, CrichereOutlineDisabled, CrichereFieldDisabled, colors.onSurfaceVariant)
        isError -> FieldLook(2.dp, colors.error, CrichereErrorField, colors.error)
        isFocused -> FieldLook(2.dp, colors.primary, colors.surface, colors.primary)
        else -> FieldLook(1.dp, colors.outline, colors.surface, colors.onSurfaceVariant)
    }
    val shape = RoundedCornerShape(12.dp)

    BasicTextField(
        value = state.code,
        onValueChange = { value -> onCodeChanged(value.filter(Char::isDigit).take(OtpVerifyViewModel.OTP_LENGTH)) },
        enabled = !state.isVerifying,
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            letterSpacing = 6.sp,
            color = if (isError) CrichereErrorStrong else colors.onBackground,
        ),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.padding(top = 6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(look.fill, shape)
                        .border(look.border, look.borderColor, shape)
                        .padding(horizontal = 16.dp + look.border),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) { innerTextField() }
                    Text(
                        text = "${state.code.length}/${OtpVerifyViewModel.OTP_LENGTH}",
                        style = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp),
                        color = CrichereInkSubtle,
                    )
                }
                // Notched label: its background matches the screen so it masks the top border.
                Text(
                    text = "6-digit code",
                    style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 11.5.sp),
                    color = look.label,
                    modifier = Modifier
                        .offset(x = 14.dp, y = (-6).dp)
                        .background(colors.background)
                        .padding(horizontal = 4.dp),
                )
            }
        },
    )
}

private data class FieldLook(val border: Dp, val borderColor: Color, val fill: Color, val label: Color)

@Composable
private fun ErrorRow(message: String) {
    // Icon pinned to the first line so longer (wrapping) messages stay fully visible.
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.5.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = message,
            style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.9.sp),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ResendRow(state: OtpVerifyState, onResend: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 13.sp)
    if (state.canResend) {
        val text = buildAnnotatedString {
            withLink(
                LinkAnnotation.Clickable(
                    tag = "resend",
                    styles = TextLinkStyles(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.primary)),
                ) { if (!state.isResending) onResend() },
            ) { append("Resend code (${state.resendsUsed}/${state.maxResends} used)") }
        }
        Text(text = text, style = style)
    } else {
        val seconds = state.cooldownSecondsRemaining
        val text = buildAnnotatedString {
            append("Resend in ")
            withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, color = colors.onBackground)) {
                append("%d:%02d".format(seconds / 60, seconds % 60))
            }
            if (state.resendsUsed > 0) append(" · ${state.resendsUsed}/${state.maxResends} used")
        }
        Text(text = text, style = style, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun ResentSnackbar(phoneNumber: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "New code sent to ${formatIndianNumber(phoneNumber)}",
            style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.2.sp),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** "+919876543210" -> "+91 98765 43210"; anything else is shown as typed. */
private fun formatIndianNumber(number: String): String =
    if (number.length == 13 && number.startsWith("+91") && number.drop(1).all(Char::isDigit)) {
        "+91 ${number.substring(3, 8)} ${number.substring(8)}"
    } else {
        number
    }

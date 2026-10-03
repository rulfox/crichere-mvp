package com.crichere.app.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.R
import com.crichere.app.auth.PhoneEntryState
import com.crichere.app.auth.PhoneEntryViewModel
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereFieldDisabled
import com.crichere.app.ui.theme.CrichereInkSubtle
import com.crichere.app.ui.theme.InstrumentSansFamily
import com.crichere.app.ui.theme.LocalCrichereExtraColors
import com.crichere.app.ui.theme.JetBrainsMonoFamily

/**
 * Phone Entry screen (design board screen A). Calls the real
 * `FirebasePhoneAuthClient.sendVerificationCode` (via `PhoneEntryViewModel`/`AuthRepository`) --
 * navigation to OTP Verify on success is handled by the caller (`AuthNavHost`) via
 * [PhoneEntryViewModel.navigationEvents], not by this composable.
 *
 * The green hero only shows on first launch (empty, unfocused field); once the user engages the
 * field it collapses to a compact wordmark header so the form fits above the keyboard (A1 vs A2-A4).
 * [showLockoutNotice] is the design's B6: the user was bounced here after 5 wrong OTP attempts.
 */
@Composable
fun PhoneEntryScreen(viewModel: PhoneEntryViewModel, showLockoutNotice: Boolean = false) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val showHero = state.phoneNumber.isEmpty() && !isFocused && !showLockoutNotice

    LightStatusBarIcons(enabled = showHero)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        if (showHero) Hero()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .then(if (showHero) Modifier.padding(top = 26.dp) else Modifier.statusBarsPadding().padding(top = 41.dp))
                .navigationBarsPadding()
                .padding(bottom = 30.dp),
        ) {
            if (!showHero) {
                CrichereWordmark()
                Spacer(Modifier.height(14.dp))
            }
            if (showLockoutNotice) {
                NoticeCard(
                    icon = R.drawable.ic_lock_clock,
                    title = "Too many incorrect attempts",
                    body = "For your security, request a new code to continue.",
                    containerColor = LocalCrichereExtraColors.current.warningContainer,
                    titleColor = LocalCrichereExtraColors.current.onWarning,
                    bodyColor = LocalCrichereExtraColors.current.onWarning,
                )
                Spacer(Modifier.height(14.dp))
            }
            Text(
                text = "Sign in",
                style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 24.2.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            if (!showLockoutNotice) {
                Text(
                    text = "Enter your phone number to receive a verification code.",
                    style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 13.5.sp, lineHeight = 18.9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
            }

            PhoneField(state = state, onValueChange = viewModel::onPhoneNumberChanged, interactionSource = interactionSource)

            if (state.errorMessage != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.errorMessage.orEmpty(),
                    style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.5.sp),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.weight(1f))

            CricherePrimaryButton(
                text = "Send code",
                onClick = viewModel::requestCode,
                enabled = state.phoneNumber.isNotBlank(),
                loading = state.isSubmitting,
                loadingText = "Sending code…",
            )
        }
    }
}

@Composable
private fun Hero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .clipToBounds(),
    ) {
        // Decorative ring, positioned relative to the hero's top edge (design: 240dp circle at 178,40).
        Box(
            modifier = Modifier
                .offset(x = 178.dp, y = 40.dp)
                .size(240.dp)
                .border(BorderStroke(34.dp, Color.White.copy(alpha = 0.06f)), CircleShape),
        )
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(298.dp)
                    .padding(start = 26.dp, end = 26.dp, top = 56.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Crichere",
                    style = TextStyle(
                        fontFamily = ArchivoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 40.sp,
                        lineHeight = 40.sp,
                        letterSpacing = (-1.2).sp,
                    ),
                    color = Color.White,
                )
                Text(
                    text = "Local leagues, franchise auctions and your cricket profile.",
                    style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 25.sp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.widthIn(max = 250.dp),
                )
            }
        }
    }
}

@Composable
private fun PhoneField(
    state: PhoneEntryState,
    onValueChange: (String) -> Unit,
    interactionSource: MutableInteractionSource,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val isError = state.errorMessage != null
    val (borderWidth, borderColor) = when {
        isError -> 2.dp to colors.error
        state.isSubmitting -> 1.dp to colors.outline
        isFocused -> 2.dp to colors.primary
        else -> 1.dp to colors.outline
    }
    val shape = RoundedCornerShape(12.dp)

    BasicTextField(
        value = state.phoneNumber,
        onValueChange = onValueChange,
        enabled = !state.isSubmitting,
        singleLine = true,
        textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = colors.onBackground),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(if (state.isSubmitting) CrichereFieldDisabled else colors.surface, shape)
                    .border(borderWidth, borderColor, shape)
                    .padding(horizontal = 16.dp + borderWidth),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (state.phoneNumber.isEmpty()) {
                        Text(
                            text = "10-digit mobile number",
                            style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 15.sp),
                            color = CrichereInkSubtle,
                        )
                    }
                    innerTextField()
                }
                if (isError) {
                    Icon(
                        painter = painterResource(R.drawable.ic_error),
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
    )
}

/**
 * White system-bar icons over a dark or green surface (status bar, and the navigation bar too when
 * [navigationBar] is set); restores dark icons for every other screen on exit.
 */
@Composable
internal fun LightStatusBarIcons(enabled: Boolean, navigationBar: Boolean = false) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(enabled, navigationBar) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !enabled
        if (navigationBar) controller?.isAppearanceLightNavigationBars = !enabled
        onDispose {
            controller?.isAppearanceLightStatusBars = true
            if (navigationBar) controller?.isAppearanceLightNavigationBars = true
        }
    }
}

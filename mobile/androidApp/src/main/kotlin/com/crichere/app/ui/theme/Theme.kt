package com.crichere.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens Material3's [androidx.compose.material3.ColorScheme] has no slot for: the pressed-state
 * green, the warning (not error) container pair, and the auction screen's own dark palette (the
 * live auction screen uses a dedicated dark surface regardless of the app's light theme -- see
 * assets/README.md).
 */
data class CrichereExtraColors(
    val primaryPressed: Color,
    val warningContainer: Color,
    val onWarning: Color,
    val auctionBg: Color,
    val auctionSurface: Color,
    val auctionGold: Color,
    val auctionAlert: Color,
    val auctionMuted: Color,
)

private val LightExtraColors =
    CrichereExtraColors(
        primaryPressed = CrichereGreenPressed,
        warningContainer = CrichereWarningContainer,
        onWarning = CrichereOnWarning,
        auctionBg = CrichereAuctionBg,
        auctionSurface = CrichereAuctionSurface,
        auctionGold = CrichereAuctionGold,
        auctionAlert = CrichereAuctionAlert,
        auctionMuted = CrichereAuctionMuted,
    )

val LocalCrichereExtraColors = staticCompositionLocalOf { LightExtraColors }

private val CrichereLightColorScheme =
    lightColorScheme(
        primary = CrichereGreen,
        onPrimary = Color.White,
        primaryContainer = CrichereGreenContainer,
        onPrimaryContainer = CrichereOnGreenContainer,
        secondary = CrichereOutline,
        onSecondary = Color.White,
        tertiary = CrichereAuctionGold,
        onTertiary = CrichereAuctionBg,
        background = CrichereBackground,
        onBackground = CrichereInk,
        surface = CrichereSurface,
        onSurface = CrichereInk,
        surfaceVariant = CrichereSurfaceVariant,
        onSurfaceVariant = CrichereInkMuted,
        outline = CrichereOutline,
        outlineVariant = CrichereOutlineVariant,
        error = CrichereError,
        onError = Color.White,
        errorContainer = CrichereErrorContainer,
        onErrorContainer = CrichereError,
    )

/** Not currently reachable -- [CrichereTheme] defaults `darkTheme` to false; see its doc comment. */
private val CrichereDarkColorScheme = CrichereLightColorScheme

@Composable
fun CrichereTheme(
    // The design system is light-only for now (dark mode isn't in the reviewed design board
    // beyond the auction screen's own always-dark palette, which is applied directly via
    // LocalCrichereExtraColors regardless of this flag) -- defaulting to light avoids Compose's
    // dynamic/dark-follows-system behavior producing an unreviewed, unintended color scheme.
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) CrichereDarkColorScheme else CrichereLightColorScheme
    CompositionLocalProvider(LocalCrichereExtraColors provides LightExtraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CrichereTypography,
            content = content,
        )
    }
}

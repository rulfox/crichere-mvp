import SwiftUI

/// Every colour the Android app uses, under the same names (androidApp `ui/theme/Color.kt`, plus the
/// private colours its components declare inline). The iOS screens must use these, never iOS system
/// colours, so the two apps match (docs/IOS-REDESIGN-PLAN.md).
enum CrichereColor {
    // Color.kt
    static let green = Color(hex: 0x1B5E20)
    static let greenPressed = Color(hex: 0x154A19)
    static let greenContainer = Color(hex: 0xD7EBD2)
    static let onGreenContainer = Color(hex: 0x0B2E10)

    static let background = Color(hex: 0xF5F6F1)
    static let surface = Color(hex: 0xFFFFFF)
    static let surfaceVariant = Color(hex: 0xEEF1EA)
    static let outline = Color(hex: 0x9FAE9A)
    static let outlineVariant = Color(hex: 0xE2E5DC)

    static let ink = Color(hex: 0x15201A)
    static let inkMuted = Color(hex: 0x5A665D)
    static let inkSubtle = Color(hex: 0x8A948C)
    static let inkDisabled = Color(hex: 0x9AA39C)

    static let disabledContainer = Color(hex: 0xDCE0D7)
    static let fieldDisabled = Color(hex: 0xF1F3EE)
    static let outlineDisabled = Color(hex: 0xDDE1D6)

    static let error = Color(hex: 0xB3261E)
    static let errorContainer = Color(hex: 0xFBE3DF)
    static let errorStrong = Color(hex: 0x8C1D12)
    static let errorBody = Color(hex: 0x6B2A22)
    static let errorField = Color(hex: 0xFFF8F7)

    static let warningContainer = Color(hex: 0xFFF3D6)
    static let onWarning = Color(hex: 0x6E4700)

    static let auctionBg = Color(hex: 0x0B140D)
    static let auctionSurface = Color(hex: 0x13211A)
    static let auctionGold = Color(hex: 0xF2B544)
    static let auctionAlert = Color(hex: 0xFF6B4A)
    static let auctionMuted = Color(hex: 0x8FA094)

    // Material3 colour-scheme slots as Theme.kt maps them, so a port of
    // `MaterialTheme.colorScheme.X` reads the same.
    static let primary = green
    static let onPrimary = Color.white
    static let primaryContainer = greenContainer
    static let onPrimaryContainer = onGreenContainer
    static let onBackground = ink
    static let onSurface = ink
    static let onSurfaceVariant = inkMuted
    static let onError = Color.white
    static let onErrorContainer = error

    // Inline colours from the shared components (CrichereComponents.kt, CrichereFields.kt).
    static let dialogSurface = Color(hex: 0xF1F4EE)
    static let dialogBody = Color(hex: 0x3E4A41)
    static let snackbarAction = Color(hex: 0xA8D5A0)
    static let inverseSnackbar = Color(hex: 0xF5F6F1)
    static let fieldDisabledFill = Color(hex: 0xEFF1EC)
    static let fieldDisabledBorder = Color(hex: 0xDDE1D6)
    static let fieldDisabledText = Color(hex: 0xB0B8B1)
}

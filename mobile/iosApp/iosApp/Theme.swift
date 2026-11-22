import SwiftUI

// The Crichere design system for iOS lives in Design/ and mirrors androidApp `ui/theme` and
// `ui/CrichereComponents.kt` one-to-one (docs/IOS-REDESIGN-PLAN.md): Tokens.swift (colours),
// Typography.swift (Instrument Sans / Archivo / JetBrains Mono with Android's line heights),
// LeagueUi.swift (tiles, money and dates), Navigation.swift (system bar hidden, swipe-back kept),
// Components/ (pills, fields, dialogs, sheets, payment proof, crop sheet, date/time dialogs), and
// Debug/ (the DEBUG parity gallery, `-parity <board>`). Android is the source of truth; every
// screen draws its own top bar like the Android app does.

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}

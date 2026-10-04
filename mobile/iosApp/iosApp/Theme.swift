import SwiftUI
import UIKit

/// Design update #5 C (docs/PHASE15.md 8.4): native iOS structure, Crichere colours and brand type. The
/// live auction stays a custom dark surface; everything else is system controls tinted Crichere.
/// **Authored but unverified** (no Xcode here), like every Swift file in this target.

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

/// Light screens: tint primary, grouped background #F5F6F1 instead of systemGroupedBackground, white cells.
enum Brand {
    static let primary = Color(hex: 0x1B5E20)
    static let background = Color(hex: 0xF5F6F1)
    static let ink = Color(hex: 0x15201A)
    static let inkMuted = Color(hex: 0x5A665D)
    static let outlineVariant = Color(hex: 0xE2E5DC)
    static let avatar = Color(hex: 0xE3E8DD)
    static let error = Color(hex: 0xB3261E)
    /// The Live Auction / Auction results capsule on the league page (E16).
    static let auctionButton = Color(hex: 0x0E1A11)
}

/// The live auction's dark broadcast tokens (same values as Android's `CrichereAuction*`).
enum AuctionPalette {
    static let background = Color(hex: 0x0B140D)
    static let surface = Color(hex: 0x13211A)
    static let dock = Color(hex: 0x101B14)
    static let gold = Color(hex: 0xF2B544)
    static let coral = Color(hex: 0xFF8B70)
    static let liveGreen = Color(hex: 0x7BC47F)
    static let muted = Color(hex: 0x8FA094)
    static let soft = Color(hex: 0xD5DDD6)
    static let dim = Color(hex: 0xB7C2B9)
    static let hairline = Color.white.opacity(0.08)
    /// "Waiting for next player" / disabled gold: gold at .35 over the dock.
    static let goldDisabled = Color(hex: 0x5C4A22)
}

/// Bundled brand faces. Both files are variable fonts; weights resolve to their named instances.
enum BrandFont {
    static func archivo(_ size: CGFloat, _ weight: UIFont.Weight) -> Font {
        Font(uiArchivo(size, weight))
    }

    static func mono(_ size: CGFloat, _ weight: UIFont.Weight = .regular) -> Font {
        Font(uiFont(family: "JetBrains Mono", size: size, weight: weight, fallback: .monospacedSystemFont(ofSize: size, weight: weight)))
    }

    static func uiArchivo(_ size: CGFloat, _ weight: UIFont.Weight) -> UIFont {
        uiFont(family: "Archivo", size: size, weight: weight, fallback: .systemFont(ofSize: size, weight: weight))
    }

    private static func uiFont(family: String, size: CGFloat, weight: UIFont.Weight, fallback: UIFont) -> UIFont {
        let descriptor = UIFontDescriptor(fontAttributes: [
            .family: family,
            .traits: [UIFontDescriptor.TraitKey.weight: weight],
        ])
        let font = UIFont(descriptor: descriptor, size: size)
        return font.familyName == family ? font : fallback
    }
}

/// Call once at launch: large titles in Archivo 800 34 (−.02em), inline titles stay SF 17 semibold.
func applyCrichereAppearance() {
    let appearance = UINavigationBarAppearance()
    appearance.configureWithTransparentBackground()
    appearance.backgroundColor = UIColor(Brand.background)
    appearance.largeTitleTextAttributes = [
        .font: BrandFont.uiArchivo(34, .heavy),
        .foregroundColor: UIColor(Brand.ink),
        .kern: -0.68,
    ]
    appearance.titleTextAttributes = [
        .font: UIFont.systemFont(ofSize: 17, weight: .semibold),
        .foregroundColor: UIColor(Brand.ink),
    ]
    let navigationBar = UINavigationBar.appearance()
    navigationBar.standardAppearance = appearance
    navigationBar.scrollEdgeAppearance = appearance
    navigationBar.compactAppearance = appearance
}

extension View {
    /// E16 section header: Archivo 700 20/25 ink, sentence case.
    func brandSectionHeader() -> some View {
        font(BrandFont.archivo(20, .bold))
            .foregroundColor(Brand.ink)
            .textCase(nil)
    }
}

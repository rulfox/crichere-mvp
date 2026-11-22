import SwiftUI
import UIKit

/// The three brand faces (androidApp `ui/theme/Type.kt`): Archivo for headlines, Instrument Sans for
/// all UI text, JetBrains Mono for money, phone numbers and OTP digits. All three are variable fonts;
/// each weight is set on the `wght` axis explicitly (like Type.kt's `FontVariation.weight`), otherwise
/// iOS renders the default instance and fakes bold.
enum CrichereFamily {
    case archivo, instrumentSans, jetBrainsMono

    fileprivate var postScriptName: String {
        switch self {
        case .archivo: "ArchivoRoman-Regular"
        case .instrumentSans: "InstrumentSans-Regular"
        case .jetBrainsMono: "JetBrainsMono-Regular"
        }
    }

    fileprivate var weightRange: ClosedRange<Int> {
        switch self {
        case .archivo: 100...900
        case .instrumentSans: 400...700
        case .jetBrainsMono: 100...800
        }
    }
}

/// Compose `FontWeight` values.
enum CrichereWeight: Int {
    case normal = 400, medium = 500, semiBold = 600, bold = 700, extraBold = 800

    fileprivate var system: UIFont.Weight {
        switch self {
        case .normal: .regular
        case .medium: .medium
        case .semiBold: .semibold
        case .bold: .bold
        case .extraBold: .heavy
        }
    }
}

enum CrichereFonts {
    /// OpenType tag 'wght' as CoreText's variation-axis identifier.
    private static let weightAxis = 0x7767_6874
    private static var cache: [String: UIFont] = [:]

    static func uiFont(_ family: CrichereFamily, _ weight: CrichereWeight, _ size: CGFloat) -> UIFont {
        let key = "\(family)-\(weight.rawValue)-\(size)"
        if let cached = cache[key] { return cached }
        let font: UIFont
        if let base = UIFont(name: family.postScriptName, size: size) {
            let value = min(max(weight.rawValue, family.weightRange.lowerBound), family.weightRange.upperBound)
            let descriptor = base.fontDescriptor.addingAttributes([
                UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [weightAxis: value],
            ])
            font = UIFont(descriptor: descriptor, size: size)
        } else {
            // Only if the bundled file failed to register (UIAppFonts) -- visible in the gallery.
            font = family == .jetBrainsMono
                ? .monospacedSystemFont(ofSize: size, weight: weight.system)
                : .systemFont(ofSize: size, weight: weight.system)
        }
        cache[key] = font
        return font
    }
}

/// A Compose `TextStyle`: family, weight, size (sp = pt), line height and letter spacing (sp = pt).
/// A nil [lineHeight] keeps the font's own, like a Compose style without one.
struct CrichereTextStyle {
    var family: CrichereFamily
    var weight: CrichereWeight = .normal
    var size: CGFloat
    var lineHeight: CGFloat? = nil
    var tracking: CGFloat = 0

    var uiFont: UIFont { CrichereFonts.uiFont(family, weight, size) }
    var font: Font { Font(uiFont) }

    static func archivo(_ size: CGFloat, _ weight: CrichereWeight = .bold, lineHeight: CGFloat? = nil, tracking: CGFloat = 0) -> Self {
        Self(family: .archivo, weight: weight, size: size, lineHeight: lineHeight, tracking: tracking)
    }

    static func sans(_ size: CGFloat, _ weight: CrichereWeight = .normal, lineHeight: CGFloat? = nil, tracking: CGFloat = 0) -> Self {
        Self(family: .instrumentSans, weight: weight, size: size, lineHeight: lineHeight, tracking: tracking)
    }

    static func mono(_ size: CGFloat, _ weight: CrichereWeight = .medium, lineHeight: CGFloat? = nil, tracking: CGFloat = 0) -> Self {
        Self(family: .jetBrainsMono, weight: weight, size: size, lineHeight: lineHeight, tracking: tracking)
    }

    func weight(_ weight: CrichereWeight) -> Self { var copy = self; copy.weight = weight; return copy }
    func size(_ size: CGFloat, lineHeight: CGFloat? = nil) -> Self { var copy = self; copy.size = size; copy.lineHeight = lineHeight; return copy }
}

/// Material3's default type scale with Type.kt's families and weights (`CrichereTypography`), for
/// ports of `MaterialTheme.typography.X`.
enum CrichereType {
    static let displayLarge = CrichereTextStyle.archivo(57, .extraBold, lineHeight: 64, tracking: -0.25)
    static let displayMedium = CrichereTextStyle.archivo(45, .extraBold, lineHeight: 52)
    static let displaySmall = CrichereTextStyle.archivo(36, .bold, lineHeight: 44)
    static let headlineLarge = CrichereTextStyle.archivo(32, .extraBold, lineHeight: 40)
    static let headlineMedium = CrichereTextStyle.archivo(28, .bold, lineHeight: 36)
    static let headlineSmall = CrichereTextStyle.archivo(24, .bold, lineHeight: 32)
    static let titleLarge = CrichereTextStyle.archivo(22, .bold, lineHeight: 28)
    static let titleMedium = CrichereTextStyle.sans(16, .semiBold, lineHeight: 24, tracking: 0.15)
    static let titleSmall = CrichereTextStyle.sans(14, .semiBold, lineHeight: 20, tracking: 0.1)
    static let bodyLarge = CrichereTextStyle.sans(16, .normal, lineHeight: 24, tracking: 0.5)
    static let bodyMedium = CrichereTextStyle.sans(14, .normal, lineHeight: 20, tracking: 0.25)
    static let bodySmall = CrichereTextStyle.sans(12, .normal, lineHeight: 16, tracking: 0.4)
    static let labelLarge = CrichereTextStyle.sans(14, .medium, lineHeight: 20, tracking: 0.1)
    static let labelMedium = CrichereTextStyle.sans(12, .medium, lineHeight: 16, tracking: 0.5)
    static let labelSmall = CrichereTextStyle.sans(11, .medium, lineHeight: 16, tracking: 0.5)
    /// `CrichereMonoTextStyle`: money, phone numbers, OTP digits.
    static let mono = CrichereTextStyle.mono(14, .medium)
}

private struct CrichereTextModifier: ViewModifier {
    let style: CrichereTextStyle

    func body(content: Content) -> some View {
        let natural = style.uiFont.lineHeight
        let extra = (style.lineHeight ?? natural) - natural
        // Compose sizes a text line to its lineHeight, centred; SwiftUI uses the font's own line
        // height. Line spacing covers the gaps between lines, half the difference above and below.
        content
            .font(style.font)
            .tracking(style.tracking)
            .lineSpacing(max(0, extra))
            .padding(.vertical, extra / 2)
    }
}

extension View {
    /// Applies a [CrichereTextStyle] the way Compose lays it out (size, weight, line height, spacing).
    func crichere(_ style: CrichereTextStyle) -> some View {
        modifier(CrichereTextModifier(style: style))
    }

    /// Text style plus colour -- the common `Text(style = …, color = …)` port.
    func crichere(_ style: CrichereTextStyle, _ color: Color) -> some View {
        modifier(CrichereTextModifier(style: style)).foregroundStyle(color)
    }
}

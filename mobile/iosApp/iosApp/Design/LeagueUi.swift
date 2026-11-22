import SwiftUI
import Shared

// Display helpers shared by the league screens -- the iOS twin of androidApp `ui/LeagueUi.kt`, with
// the same outputs so a league looks the same on both apps (tile colour, short code, dates, money).

/// Logo-tile colours for leagues without a logo, picked stably per league id.
private let tilePalette: [Color] = [Color(hex: 0x1B5E20), Color(hex: 0x2B5FB5), Color(hex: 0x7A5B12), Color(hex: 0xB5462B), Color(hex: 0x5B2A86)]

/// Java's `String.hashCode()` (UTF-16 units, 32-bit wrap), so [tileColor] picks Android's colour.
func javaHashCode(_ string: String) -> Int32 {
    var hash: Int32 = 0
    for unit in string.utf16 { hash = hash &* 31 &+ Int32(unit) }
    return hash
}

func tileColor(_ id: String) -> Color {
    let hash = javaHashCode(id)
    // Kotlin's abs(Int.MIN_VALUE) stays negative; magnitude keeps the index valid either way.
    return tilePalette[Int(hash.magnitude % UInt32(tilePalette.count))]
}

/// First letters of the first three words that start with a letter: "Kolhapur Premier League 2026" -> "KPL".
func shortCode(_ name: String) -> String {
    let code = name.split(separator: " ").filter { $0.first?.isLetter == true }.prefix(3).map { String($0.first!).uppercased() }.joined()
    return code.isEmpty ? String(name.prefix(2)).uppercased() : code
}

/// Up to two initials for an avatar: "Aarav Pawar" -> "AP".
func initials(_ name: String?) -> String {
    guard let name else { return "" }
    return name.split(separator: " ").filter { !$0.isEmpty }.prefix(2).map { String($0.first!).uppercased() }.joined()
}

private let indianGrouping: NumberFormatter = {
    let formatter = NumberFormatter()
    formatter.locale = Locale(identifier: "en_IN")
    formatter.numberStyle = .decimal
    formatter.maximumFractionDigits = 0
    formatter.roundingMode = .halfEven
    return formatter
}()

/// "₹5,000" / "₹1,25,000" -- Indian digit grouping, no decimals (Android's `rupees()`).
func rupees(_ amount: Double) -> String {
    "₹" + (indianGrouping.string(from: NSNumber(value: amount)) ?? String(Int(amount)))
}

func entryLabel(_ fee: Double?) -> String {
    guard let fee, fee > 0 else { return "Free" }
    return "\(rupees(fee)) entry"
}

private let isoDate: DateFormatter = {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.timeZone = TimeZone(identifier: "UTC")
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter
}()

private func dateFormatter(_ pattern: String) -> DateFormatter {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_GB")
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.timeZone = TimeZone(identifier: "UTC")
    formatter.dateFormat = pattern
    return formatter
}

private let shortDateFormat = dateFormatter("d MMM")
private let longDateFormat = dateFormatter("d MMM yyyy")

/// "2026-10-12" -> "12 Oct" (or the raw string if it isn't an ISO date).
func shortDate(_ iso: String) -> String {
    guard let date = isoDate.date(from: iso) else { return iso }
    return shortDateFormat.string(from: date)
}

/// "2026-10-12" -> "12 Oct 2026".
func longDate(_ iso: String) -> String {
    guard let date = isoDate.date(from: iso) else { return iso }
    return longDateFormat.string(from: date)
}

func yearOf(_ iso: String) -> String? {
    guard let date = isoDate.date(from: iso) else { return nil }
    return String(Calendar(identifier: .gregorian).component(.year, from: date))
}

extension PlayingRole {
    var label: String {
        switch self {
        case .batsman: "Batsman"
        case .bowler: "Bowler"
        case .allRounder: "All-rounder"
        case .wicketkeeper: "Wicketkeeper"
        }
    }
}

/// "payment-proof.jpg · 412 KB".
func fileCaption(_ name: String?, _ bytes: Int64?) -> String {
    let file = name ?? "payment-proof.jpg"
    guard let bytes else { return file }
    let size = bytes >= 1_048_576 ? String(format: "%.1f MB", Double(bytes) / 1_048_576) : "\((bytes + 1023) / 1024) KB"
    return "\(file) · \(size)"
}

/// The deleted-account avatar tokens (AccountDeletionUi.kt), also the R12a neutral logo.
enum DeletedAvatar {
    static let background = Color(hex: 0xE3E8DD)
    static let icon = Color(hex: 0x8A948C)
}

/// Board R12a: a removed league or franchise logo. No label, so nobody is publicly flagged.
struct NeutralLogo: View {
    let size: CGFloat
    let iconSize: CGFloat
    let corner: CGFloat

    var body: some View {
        CrIcon("ic_shield", size: iconSize, tint: DeletedAvatar.icon)
            .frame(width: size, height: size)
            .background(DeletedAvatar.background, in: RoundedRectangle(cornerRadius: corner, style: .continuous))
    }
}

/// A league's logo, or its short code in Archivo 800 on its tile colour.
struct LeagueTile: View {
    let id: String
    let name: String
    let logoUrl: String?
    var size: CGFloat = 40
    var corner: CGFloat = 10
    var fontSize: CGFloat = 12.8
    var logoRemoved: Bool = false

    var body: some View {
        if logoRemoved {
            NeutralLogo(size: size, iconSize: size / 2, corner: corner)
        } else {
            ZStack {
                tileColor(id)
                Text(shortCode(name)).crichere(.archivo(fontSize, .extraBold), .white)
                RemoteImage(url: logoUrl)
            }
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
        }
    }
}

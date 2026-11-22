import SwiftUI

// Date / time pickers in Android's Material dialog style (androidApp `LeagueCreationScreen.kt`
// StartDatePicker, `AuctionSettingsScreen.kt` J10/J11): the board's #F1F4EE surface, 28 pt corners,
// a small title, an Archivo headline, then -- kept native per docs/IOS-REDESIGN-PLAN.md -- the iOS
// picker tinted green, and Cancel / OK text buttons.

/// The calendar every date picker works in: plain dates (`2026-10-12`) carry no time zone.
let utcCalendar: Calendar = {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!
    return calendar
}()

/// A UTC date formatter with [pattern], English month names.
func utcDateFormatter(_ pattern: String) -> DateFormatter {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(identifier: "UTC")
    formatter.dateFormat = pattern
    return formatter
}

private let isoDay = utcDateFormatter("yyyy-MM-dd")

/// `2026-10-12` → a UTC midnight `Date`, or nil.
func parseIsoDay(_ iso: String?) -> Date? { iso.flatMap { isoDay.date(from: $0) } }
/// A UTC `Date` → `2026-10-12`.
func isoDayString(_ date: Date) -> String { isoDay.string(from: date) }

/// `2026-10-12` as e.g. "12 Oct 2026" ([pattern] `d MMM yyyy`); the raw value if it doesn't parse.
func formatIsoDay(_ iso: String, _ pattern: String) -> String {
    guard let date = isoDay.date(from: iso) else { return iso }
    return utcDateFormatter(pattern).string(from: date)
}

/// Material `DatePickerDialog`: title ("Select start date"), the picked day as an Archivo headline
/// ("Mon, 12 Oct", or "Pick a date"), a divider, the graphical picker, Cancel / OK.
struct CrichereDatePickerDialog: View {
    let title: String
    let initial: Date?
    var minimum: Date? = nil
    let onDismiss: () -> Void
    let onPicked: (Date) -> Void

    @State private var selection: Date?

    init(title: String, initial: Date?, minimum: Date? = nil, onDismiss: @escaping () -> Void, onPicked: @escaping (Date) -> Void) {
        self.title = title
        self.initial = initial
        self.minimum = minimum
        self.onDismiss = onDismiss
        self.onPicked = onPicked
        _selection = State(initialValue: initial)
    }

    var body: some View {
        PickerDialogShell(title: title, headline: selection.map { utcDateFormatter("EEE, d MMM").string(from: $0) } ?? "Pick a date",
                          onDismiss: onDismiss, onConfirm: { if let selection { onPicked(selection) } else { onDismiss() } }) {
            DatePicker("", selection: Binding(get: { selection ?? initial ?? minimum ?? Date() }, set: { selection = $0 }),
                       in: (minimum ?? .distantPast)..., displayedComponents: .date)
                .datePickerStyle(.graphical)
                .labelsHidden()
                .tint(CrichereColor.primary)
                .environment(\.calendar, utcCalendar)
                .environment(\.timeZone, TimeZone(identifier: "UTC")!)
                .padding(.horizontal, 12)
        }
    }
}

/// Material `TimePicker` dialog: title, the picked time as the headline, the native wheel, Cancel / OK.
/// [selection] is a wall-clock time in the device's zone.
struct CrichereTimePickerDialog: View {
    let title: String
    let initial: Date
    let onDismiss: () -> Void
    let onPicked: (Date) -> Void

    @State private var selection: Date

    init(title: String, initial: Date, onDismiss: @escaping () -> Void, onPicked: @escaping (Date) -> Void) {
        self.title = title
        self.initial = initial
        self.onDismiss = onDismiss
        self.onPicked = onPicked
        _selection = State(initialValue: initial)
    }

    var body: some View {
        let headline: DateFormatter = {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = "h:mm a"
            return formatter
        }()
        PickerDialogShell(title: title, headline: headline.string(from: selection), onDismiss: onDismiss, onConfirm: { onPicked(selection) }) {
            DatePicker("", selection: $selection, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .tint(CrichereColor.primary)
                .frame(maxWidth: .infinity)
        }
    }
}

private struct PickerDialogShell<Picker: View>: View {
    let title: String
    let headline: String
    let onDismiss: () -> Void
    let onConfirm: () -> Void
    @ViewBuilder let picker: () -> Picker

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).crichere(.sans(12, .medium), CrichereColor.onSurfaceVariant)
                .padding(.leading, 24)
                .padding(.top, 20)
            Text(headline).crichere(.archivo(26, .bold, lineHeight: 26), CrichereColor.onBackground)
                .padding(.leading, 24)
                .padding(.top, 36)
                .padding(.bottom, 12)
            Rectangle().fill(CrichereColor.outlineVariant).frame(height: 1)
            picker()
            HStack(spacing: 8) {
                Spacer(minLength: 0)
                textButton("Cancel", action: onDismiss)
                textButton("OK", action: onConfirm)
            }
            .padding(.trailing, 6)
            .padding(.bottom, 8)
        }
        .frame(maxWidth: 360)
        .background(CrichereColor.dialogSurface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
    }

    private func textButton(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).crichere(.sans(14, .semiBold), CrichereColor.primary).padding(.horizontal, 12).frame(minHeight: 40)
        }
        .buttonStyle(PressHighlight(shape: Capsule(), color: CrichereColor.primary.opacity(0.1)))
    }
}

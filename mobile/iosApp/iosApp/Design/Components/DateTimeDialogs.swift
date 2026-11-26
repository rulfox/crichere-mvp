import SwiftUI
import UIKit

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

/// How a date dialog's header reads: Create league's I6 (Archivo 26) or the auction's J10 (sans 30/36).
struct DateDialogStyle {
    var titleColor: Color = CrichereColor.onSurfaceVariant
    var headline: CrichereTextStyle = .archivo(26, .bold, lineHeight: 26)
    var headlineTop: CGFloat = 36
    var headlineBottom: CGFloat = 12
    /// "Pick a date" in this colour until a day is tapped (nil: the ink colour either way).
    var emptyHeadlineColor: Color? = nil
    var confirmLabel: String = "OK"
    /// U5 J13: the confirm button stays at 38% until a day is picked.
    var confirmNeedsPick: Bool = false

    static let league = DateDialogStyle()
    static let auction = DateDialogStyle(titleColor: Color(hex: 0x3E4A41), headline: .sans(30, lineHeight: 36), headlineTop: 22,
                                         headlineBottom: 14, emptyHeadlineColor: CrichereColor.onSurfaceVariant, confirmLabel: "Next",
                                         confirmNeedsPick: true)
}

/// Material `DatePickerDialog`: title, the picked day as the headline ("Mon, 12 Oct", or "Pick a
/// date"), a divider, the graphical picker (days before [minimum] disabled), Cancel / OK.
/// The inline calendar (the `UIDatePicker` SwiftUI's `.graphical` style wraps) with a tap recognizer that
/// runs alongside the picker's own. A SwiftUI `simultaneousGesture` on the DatePicker swallowed the day
/// taps instead, so every pick became today.
private struct InlineCalendar: UIViewRepresentable {
    let date: Date
    let minimum: Date?
    let onChange: (Date) -> Void
    let onTap: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeUIView(context: Context) -> UIDatePicker {
        let picker = UIDatePicker()
        picker.datePickerMode = .date
        picker.preferredDatePickerStyle = .inline
        picker.calendar = utcCalendar
        picker.timeZone = TimeZone(identifier: "UTC")
        picker.tintColor = UIColor(CrichereColor.primary)
        picker.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .valueChanged)
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tapped))
        tap.cancelsTouchesInView = false
        tap.delegate = context.coordinator
        picker.addGestureRecognizer(tap)
        return picker
    }

    func updateUIView(_ picker: UIDatePicker, context: Context) {
        context.coordinator.parent = self
        picker.minimumDate = minimum
        if picker.date != date { picker.setDate(date, animated: false) }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UIDatePicker, context: Context) -> CGSize? {
        let width = proposal.width ?? 320
        let fitted = uiView.systemLayoutSizeFitting(CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
                                                    withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel)
        return CGSize(width: width, height: fitted.height)
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: InlineCalendar
        init(parent: InlineCalendar) { self.parent = parent }
        @objc func changed(_ picker: UIDatePicker) { parent.onChange(picker.date) }
        @objc func tapped() { parent.onTap() }
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
    }
}

struct CrichereDatePickerDialog: View {
    let title: String
    let initial: Date?
    var minimum: Date? = nil
    var style: DateDialogStyle = .league
    let onDismiss: () -> Void
    let onPicked: (Date) -> Void

    @State private var selection: Date?

    init(title: String, initial: Date?, minimum: Date? = nil, style: DateDialogStyle = .league, onDismiss: @escaping () -> Void,
         onPicked: @escaping (Date) -> Void) {
        self.title = title
        self.initial = initial
        self.minimum = minimum
        self.style = style
        self.onDismiss = onDismiss
        self.onPicked = onPicked
        _selection = State(initialValue: initial)
    }

    var body: some View {
        let picked = selection != nil
        VStack(alignment: .leading, spacing: 0) {
            Text(title).crichere(.sans(12, .medium, lineHeight: 12), style.titleColor)
                .padding(.leading, 24)
                .padding(.top, 20)
            Text(selection.map { utcDateFormatter("EEE, d MMM").string(from: $0) } ?? "Pick a date")
                .crichere(style.headline, picked ? CrichereColor.onBackground : (style.emptyHeadlineColor ?? CrichereColor.onBackground))
                .padding(.leading, 24)
                .padding(.top, style.headlineTop)
                .padding(.bottom, style.headlineBottom)
            Rectangle().fill(CrichereColor.outlineVariant).frame(height: 1)
            // iOS's calendar always shows a selected day and doesn't report a tap on it, so any tap
            // in the calendar counts as the pick (J13's "Pick a date" until then).
            InlineCalendar(date: selection ?? initial ?? minimum ?? Date(), minimum: minimum,
                           onChange: { selection = $0 },
                           onTap: { if selection == nil { selection = initial ?? minimum ?? utcCalendar.startOfDay(for: Date()) } })
                .padding(.horizontal, 12)
            HStack(spacing: 8) {
                Spacer(minLength: 0)
                DialogTextButton(label: "Cancel", action: onDismiss)
                DialogTextButton(label: style.confirmLabel, enabled: picked || !style.confirmNeedsPick) {
                    if let selection { onPicked(selection) } else { onDismiss() }
                }
            }
            .padding(.trailing, 6)
            .padding(.bottom, 8)
        }
        .frame(maxWidth: 360)
        .background(CrichereColor.dialogSurface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
    }
}

/// J11: a Material AlertDialog around the native time wheel -- [title] ("Auction time · 12 Oct 2026"),
/// Back (to the date) / OK. Hours and minutes are wall-clock time in the phone's zone.
struct CrichereTimePickerDialog: View {
    let title: String
    let initial: Date
    let onBack: () -> Void
    let onPicked: (Date) -> Void

    @State private var selection: Date

    init(title: String, initial: Date, onBack: @escaping () -> Void, onPicked: @escaping (Date) -> Void) {
        self.title = title
        self.initial = initial
        self.onBack = onBack
        self.onPicked = onPicked
        _selection = State(initialValue: initial)
    }

    var body: some View {
        CrichereDialog(title: title, titleStyle: .sans(12, .medium, lineHeight: 12), titleColor: Color(hex: 0x3E4A41), buttons: [
            DialogButton(label: "Back", action: onBack),
            DialogButton(label: "OK") { onPicked(selection) },
        ]) {
            DatePicker("", selection: $selection, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .tint(CrichereColor.primary)
                .frame(maxWidth: .infinity)
        }
        // Android's AlertDialog is platformDialogWidth wide, but the native wheel needs more room than that leaves.
        .frame(maxWidth: 360)
    }
}

/// Material `TextButton` in a dialog: SemiBold 14 green, 38% when disabled.
struct DialogTextButton: View {
    let label: String
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label).crichere(.sans(14, .semiBold), CrichereColor.primary.opacity(enabled ? 1 : 0.38))
                .padding(.horizontal, 12).frame(minHeight: 40)
        }
        .buttonStyle(PressHighlight(shape: Capsule(), color: CrichereColor.primary.opacity(0.1)))
        .disabled(!enabled)
    }
}

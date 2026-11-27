import SwiftUI
import Shared

private let rowDivider = Color(hex: 0xDCE2D8)
private let errorRowFill = Color(hex: 0xFDF3F2)
private let cardOutline = Color(hex: 0xC5CEC0)

/// A section's bold heading ("When", "Franchises", "Bidding").
struct SectionTitle: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text).crichere(.archivo(15, .bold, lineHeight: 15), CrichereColor.onBackground)
            .accessibilityAddTraits(.isHeader)
    }
}

/// A small grey line under a field.
struct FieldHelp: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text).crichere(.sans(12, lineHeight: 16), CrichereColor.onSurfaceVariant).padding(.leading, 14)
    }
}

/// The Bidding section (Auction Settings redesign; androidApp `ui/BidStepsSection.kt`): the starting price,
/// then "How bids go up" as two example cards -- "Same amount always" (one "Each bid adds" amount) or
/// "Changes with price" (rows that read "₹1,000 to [3,000] add [₹500]", ending "Above ₹3,000 add […]") --
/// and a preview of the bids. A row with a mistake turns pink and says what's wrong, with amounts to tap.
struct BiddingSection: View {
    let state: AuctionSettingsState
    let actions: AuctionSettingsActions

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionTitle("Bidding")
            VStack(alignment: .leading, spacing: 2) {
                CrichereTextField(text: Binding(get: { state.basePrice }, set: actions.onBasePrice), label: "Starting price (₹)", look: .form,
                                  error: state.errorFor(field: .basePrice), placeholder: "e.g. 250", mono: true, readOnly: state.isSaving, keyboard: .numberPad)
                FieldHelp("Every player’s bidding opens at this price.")
            }

            Text("How bids go up").crichere(.sans(13, .semiBold, lineHeight: 17.55), CrichereColor.onBackground).padding(.top, 4)
            HStack(alignment: .top, spacing: 10) {
                ModeCard(title: "Same amount always", example: state.sameExample, selected: state.bidMode == .sameAlways, enabled: !state.isSaving) {
                    actions.onBidMode(.sameAlways)
                }
                ModeCard(title: "Changes with price", example: "e.g. +250 till 1,000, then +500", selected: state.bidMode == .changesWithPrice,
                         enabled: !state.isSaving) {
                    actions.onBidMode(.changesWithPrice)
                }
            }
            .fixedSize(horizontal: false, vertical: true)

            if state.bidMode == .sameAlways {
                CrichereTextField(text: Binding(get: { state.bidIncrement }, set: actions.onBidIncrement), label: "Each bid adds (₹)", look: .form,
                                  error: state.errorFor(field: .bidIncrement), placeholder: "e.g. 250", mono: true, readOnly: state.isSaving, keyboard: .numberPad)
            } else {
                PriceRanges(state: state, actions: actions)
            }

            if let preview = state.ladderPreview {
                LadderPreview(lines: preview)
            } else {
                Text(state.previewPlaceholder)
                    .crichere(.sans(13, lineHeight: 18), CrichereColor.onSurfaceVariant)
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(CrichereColor.outline, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
            }
        }
    }
}

/// One "How bids go up" card: a radio, its name and a short example.
private struct ModeCard: View {
    let title: String
    let example: String
    let selected: Bool
    let enabled: Bool
    let onSelect: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    CrIcon(selected ? "ic_radio_button_checked" : "ic_radio_button_unchecked", size: 20,
                           tint: selected ? CrichereColor.primary : CrichereColor.onSurfaceVariant)
                    Text(title).crichere(.sans(13.5, .semiBold, lineHeight: 17), CrichereColor.onBackground)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Text(example).crichere(.mono(12, .medium, lineHeight: 17), CrichereColor.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(CrichereColor.surface, in: shape)
            .overlay(shape.strokeBorder(selected ? CrichereColor.primary : cardOutline, lineWidth: selected ? 2 : 1))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

/// "Changes with price": an example sentence, the rows, and "+ Add another price range".
private struct PriceRanges: View {
    let state: AuctionSettingsState
    let actions: AuctionSettingsActions

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        VStack(alignment: .leading, spacing: 8) {
            (Text("Bids go up by bigger amounts as the price rises. Example: ")
                + Text("+₹250 till ₹1,000, then +₹500 till ₹3,000, then +₹1,000.").foregroundColor(CrichereColor.onBackground))
                .crichere(.sans(12.5, lineHeight: 17.5), CrichereColor.onSurfaceVariant)
            VStack(spacing: 0) {
                ForEach(Array(state.ranges.enumerated()), id: \.offset) { index, row in
                    if index > 0 { rowDivider.frame(height: 1) }
                    RangeRow(state: state, index: Int32(index), row: row, actions: actions)
                }
                rowDivider.frame(height: 1)
                AboveRow(state: state, actions: actions)
            }
            .background(CrichereColor.surface)
            .clipShape(shape)
            .overlay(shape.strokeBorder(rowDivider, lineWidth: 1))
            if state.canAddRange {
                Button(action: actions.onAddRange) {
                    HStack(spacing: 6) {
                        CrIcon("ic_add", size: 18, tint: CrichereColor.primary)
                        Text("Add another price range").crichere(.sans(13.5, .semiBold, lineHeight: 13.5), CrichereColor.primary)
                    }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(state.isSaving)
            }
        }
    }
}

private struct RangeRow: View {
    let state: AuctionSettingsState
    let index: Int32
    let row: BidRangeRow
    let actions: AuctionSettingsActions

    var body: some View {
        let error = state.rangeErrorFor(index: index)
        let start = state.rangeStartText(index: index) ?? "—"
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Text(start).crichere(.mono(13, .semiBold), CrichereColor.onBackground).frame(width: 54, alignment: .leading)
                    .minimumScaleFactor(0.8).lineLimit(1)
                Text("to").crichere(.sans(13), CrichereColor.onSurfaceVariant)
                CompactAmountField(text: Binding(get: { row.upTo }, set: { actions.onRangeUpTo(index, $0) }),
                                   description: "Range from \(start) ends at", isError: error?.field == .upTo, readOnly: state.isSaving)
                Text("add").crichere(.sans(13), CrichereColor.onSurfaceVariant)
                CompactAmountField(text: Binding(get: { row.step }, set: { actions.onRangeStep(index, $0) }),
                                   description: "Range from \(start) adds", isError: error?.field == .step, readOnly: state.isSaving, rupee: true)
                    .frame(width: 84)
                Button { actions.onRemoveRange(index) } label: {
                    CrIcon("ic_close", size: 18, tint: CrichereColor.onSurfaceVariant).frame(width: 36, height: 48)
                }
                .buttonStyle(.plain)
                .disabled(state.isSaving)
                .accessibilityLabel("Remove range from \(start)")
            }
            if let error {
                RowError(message: error.message, suggestions: error.suggestions.map { $0.int64Value }, enabled: !state.isSaving) { amount in
                    actions.onRangeSuggestion(index, amount)
                }
            }
        }
        .padding(.leading, 12)
        .padding(.trailing, 4)
        .padding(.vertical, 10)
        .background(error != nil ? errorRowFill : CrichereColor.surface)
    }
}

/// The last row, "Above ₹3,000 add […]": the league's bid increment.
private struct AboveRow: View {
    let state: AuctionSettingsState
    let actions: AuctionSettingsActions

    var body: some View {
        let error = state.errorFor(field: .bidIncrement)
        let above = state.aboveStartText ?? "—"
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                (Text("Above ") + Text(above).font(CrichereTextStyle.mono(13, .semiBold).font))
                    .crichere(.sans(13.5, lineHeight: 18), CrichereColor.onBackground)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("add").crichere(.sans(13), CrichereColor.onSurfaceVariant)
                CompactAmountField(text: Binding(get: { state.bidIncrement }, set: actions.onBidIncrement),
                                   description: "Above \(above) adds", isError: error != nil, readOnly: state.isSaving, rupee: true)
                    .frame(width: 84)
                Color.clear.frame(width: 36, height: 1)
            }
            if let error { RowError(message: error, suggestions: [], enabled: false) { _ in } }
        }
        .padding(.leading, 12)
        .padding(.trailing, 4)
        .padding(.vertical, 10)
        .background(error != nil ? errorRowFill : CrichereColor.surface)
    }
}

/// A 44 pt boxed number for the range rows; [rupee] puts a ₹ in front.
private struct CompactAmountField: View {
    @Binding var text: String
    let description: String
    let isError: Bool
    let readOnly: Bool
    var rupee = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 10, style: .continuous)
        HStack(spacing: 3) {
            if rupee { Text("₹").crichere(.mono(14, .semiBold), CrichereColor.onSurfaceVariant) }
            TextField("", text: $text)
                .font(CrichereTextStyle.mono(14.5, .medium).font)
                .foregroundColor(CrichereColor.onBackground)
                .keyboardType(.numberPad)
                .disabled(readOnly)
                .accessibilityLabel(description)
        }
        .padding(.horizontal, 9)
        .frame(height: 52)
        .background(CrichereColor.surface, in: shape)
        .overlay(shape.strokeBorder(isError ? CrichereColor.error : CrichereColor.outline, lineWidth: isError ? 2 : 1))
    }
}

/// A row's mistake in plain words, and the amounts that would work ("Try ₹400 ₹500").
private struct RowError: View {
    let message: String
    let suggestions: [Int64]
    let enabled: Bool
    let onPick: (Int64) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 6) {
                CrIcon("ic_error", size: 16, tint: CrichereColor.error).padding(.top, 1)
                Text(message).crichere(.sans(13, .medium, lineHeight: 18), CrichereColor.error)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if !suggestions.isEmpty {
                HStack(spacing: 8) {
                    Text("Try").crichere(.sans(13), CrichereColor.onSurfaceVariant)
                    ForEach(suggestions, id: \.self) { amount in
                        Button { onPick(amount) } label: {
                            Text("₹" + indianGrouping(amount)).crichere(.mono(13, .semiBold), CrichereColor.primary)
                                .padding(.horizontal, 14)
                                .frame(minHeight: 36)
                                .background(CrichereColor.surface, in: Capsule())
                                .overlay(Capsule().strokeBorder(CrichereColor.primary, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                        .disabled(!enabled)
                    }
                }
                .padding(.leading, 22)
            }
        }
    }
}

/// `150000` -> `1,50,000` for the suggestion chips.
private func indianGrouping(_ amount: Int64) -> String {
    let digits = String(amount)
    guard digits.count > 3 else { return digits }
    let last3 = String(digits.suffix(3))
    var rest = String(digits.dropLast(3))
    var groups: [String] = []
    while rest.count > 2 {
        groups.insert(String(rest.suffix(2)), at: 0)
        rest = String(rest.dropLast(2))
    }
    if !rest.isEmpty { groups.insert(rest, at: 0) }
    return (groups + [last3]).joined(separator: ",")
}

/// "Bids will be": a step pill and the bids it adds, per range; each line filled to the width.
private struct LadderPreview: View {
    let lines: [LadderPreviewLine]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Bids will be").crichere(.sans(12.5, lineHeight: 17), CrichereColor.onSurfaceVariant)
            if lines.count == 1, let line = lines.first {
                FillingLine(options: line.options, size: 13)
            } else {
                Grid(alignment: .leading, horizontalSpacing: 8, verticalSpacing: 6) {
                    ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                        GridRow(alignment: .center) {
                            Text(line.step).crichere(.mono(11, .bold, lineHeight: 20), CrichereColor.onGreenContainer)
                                .padding(.horizontal, 8)
                                .background(Capsule().fill(CrichereColor.greenContainer))
                            FillingLine(options: line.options, size: 12.5)
                        }
                    }
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(CrichereColor.surfaceVariant))
    }
}

/// The longest of [options] (longest first) that fits on one line, so a trailing "…" ends the row.
private struct FillingLine: View {
    let options: [String]
    let size: CGFloat

    var body: some View {
        ViewThatFits(in: .horizontal) {
            ForEach(options, id: \.self) { option in
                Text(option).crichere(.mono(size, .medium, lineHeight: 20), CrichereColor.onBackground).lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Once the auction has started, settings are read-only.
struct LockedAuctionNote: View {
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            CrIcon("ic_lock", size: 18, tint: CrichereColor.onWarning)
            Text("The auction has started. These settings can’t be changed now.")
                .crichere(.sans(13.5, .medium, lineHeight: 19), CrichereColor.onWarning)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(CrichereColor.warningContainer))
    }
}

/// One read-only group: a title over label / value rows in a white card, plus optional price ranges.
struct LockedGroup: View {
    let title: String
    let rows: [(String, String)]
    var ranges: [LadderPreviewLine] = []

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        VStack(alignment: .leading, spacing: 10) {
            SectionTitle(title)
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                    if index > 0 { rowDivider.frame(height: 1) }
                    HStack(alignment: .top, spacing: 12) {
                        Text(row.0).crichere(.sans(14), CrichereColor.onSurfaceVariant)
                        Spacer(minLength: 0)
                        Text(row.1).crichere(.sans(14, .semiBold), CrichereColor.onBackground).multilineTextAlignment(.trailing)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 13)
                    .accessibilityElement(children: .combine)
                }
                if !ranges.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(Array(ranges.enumerated()), id: \.offset) { _, range in
                            VStack(spacing: 0) {
                                Line().stroke(rowDivider, style: StrokeStyle(lineWidth: 1, dash: [4, 3])).frame(height: 1)
                                HStack {
                                    Text(range.values).crichere(.mono(13, .semiBold), CrichereColor.onBackground)
                                    Spacer(minLength: 8)
                                    Text(range.step).crichere(.mono(12, .bold, lineHeight: 20), CrichereColor.onGreenContainer)
                                        .padding(.horizontal, 8)
                                        .background(Capsule().fill(CrichereColor.greenContainer))
                                }
                                .padding(.vertical, 9)
                                .accessibilityElement(children: .combine)
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.bottom, 4)
                }
            }
            .background(CrichereColor.surface)
            .clipShape(shape)
            .overlay(shape.strokeBorder(rowDivider, lineWidth: 1))
        }
    }
}

/// A horizontal line across the frame (for the dashed range dividers).
private struct Line: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.width, y: rect.midY))
        return path
    }
}

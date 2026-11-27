import SwiftUI
import Shared

/// Auction Settings' "Bid steps" (docs/PHASE22.md, design S1-S3; androidApp `ui/BidStepsSection.kt`):
/// optional ranges -- each starts where the row above ended, so the organizer only types "Up to" and
/// "Step" -- then the required "and above" step (the league's bid increment), and a preview of how
/// bids will go. A broken row shows its error under the whole row; the preview hides until it's fixed.
struct BidStepsSection: View {
    let state: AuctionSettingsState
    let actions: AuctionSettingsActions

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Rectangle().fill(CrichereColor.outlineVariant).frame(height: 1).padding(.top, 6)
            VStack(alignment: .leading, spacing: 3) {
                Text("Bid steps").crichere(.sans(15, .semiBold, lineHeight: 20), CrichereColor.onBackground)
                Text("How much each new bid must go up by. Add ranges to use bigger steps at higher prices.")
                    .crichere(.sans(12.5, lineHeight: 17), CrichereColor.onSurfaceVariant)
            }
            ForEach(Array(state.ranges.enumerated()), id: \.offset) { index, row in
                RangeRow(state: state, index: Int32(index), row: row, actions: actions)
            }
            if state.canAddRange {
                Button(action: actions.onAddRange) {
                    HStack(spacing: 6) {
                        CrIcon("ic_add", size: 18, tint: CrichereColor.primary)
                        Text("Add range").crichere(.sans(13.5, .semiBold, lineHeight: 16), CrichereColor.primary)
                    }
                    .padding(.horizontal, 16)
                    .frame(height: 40)
                    .overlay(Capsule().strokeBorder(CrichereColor.outline, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .disabled(state.isSaving)
            }
            VStack(alignment: .leading, spacing: 2) {
                caption(start: state.aboveStartText, suffix: " and above")
                CrichereTextField(text: Binding(get: { state.bidIncrement }, set: actions.onBidIncrement), label: "Step", look: .form,
                                  error: state.errorFor(field: .bidIncrement), mono: true, readOnly: state.isSaving, keyboard: .numberPad)
                    .padding(.trailing, 50)
            }
            if let preview = state.ladderPreview {
                LadderPreview(lines: preview)
            } else {
                Text("Fix the steps above to see how bids will go.")
                    .crichere(.sans(12.5, lineHeight: 17), CrichereColor.onSurfaceVariant)
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(CrichereColor.outline, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
            }
        }
    }
}

/// "From ₹1,000" / "₹15,000 and above" over a row; the amount sits in mono ink.
@ViewBuilder func caption(start: String?, prefix: String = "", suffix: String = "") -> some View {
    (Text(prefix).font(CrichereTextStyle.sans(12, .medium).font)
        + Text(start ?? "—").font(CrichereTextStyle.mono(12, .semiBold).font).foregroundColor(CrichereColor.onBackground)
        + Text(suffix).font(CrichereTextStyle.sans(12, .medium).font))
        .foregroundColor(CrichereColor.onSurfaceVariant)
        .padding(.leading, 2)
}

private struct RangeRow: View {
    let state: AuctionSettingsState
    let index: Int32
    let row: BidRangeRow
    let actions: AuctionSettingsActions

    var body: some View {
        let error = state.rangeErrorFor(index: index)
        VStack(alignment: .leading, spacing: 2) {
            caption(start: state.rangeStartText(index: index), prefix: "From ")
            HStack(alignment: .top, spacing: 10) {
                CrichereTextField(text: Binding(get: { row.upTo }, set: { actions.onRangeUpTo(index, $0) }), label: "Up to", look: .form,
                                  error: error?.field == .upTo ? "" : nil, mono: true, readOnly: state.isSaving, keyboard: .numberPad)
                CrichereTextField(text: Binding(get: { row.step }, set: { actions.onRangeStep(index, $0) }), label: "Step", look: .form,
                                  error: error?.field == .step ? "" : nil, mono: true, readOnly: state.isSaving, keyboard: .numberPad)
                Button { actions.onRemoveRange(index) } label: {
                    CrIcon("ic_close", size: 20, tint: CrichereColor.onSurfaceVariant).frame(width: 40, height: 44)
                }
                .buttonStyle(.plain)
                .padding(.top, 7)
                .disabled(state.isSaving)
                .accessibilityLabel("Remove range from \(state.rangeStartText(index: index) ?? "")")
            }
            if let error {
                HStack(alignment: .top, spacing: 6) {
                    CrIcon("ic_error", size: 15, tint: CrichereColor.error).padding(.top, 1)
                    Text(error.message).crichere(.sans(12.5, .medium, lineHeight: 17), CrichereColor.error)
                }
                .padding(.leading, 2)
            }
        }
    }
}

/// Design S1's "Bids will go" box: a step pill and the bids it adds, per range.
private struct LadderPreview: View {
    let lines: [LadderPreviewLine]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("BIDS WILL GO").crichere(.sans(11.5, .semiBold, lineHeight: 14, tracking: 0.3), CrichereColor.onSurfaceVariant)
            Grid(alignment: .leading, horizontalSpacing: 10, verticalSpacing: 6) {
                ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                    GridRow(alignment: .top) {
                        Text(line.step).crichere(.mono(11, .bold, lineHeight: 20), CrichereColor.onGreenContainer)
                            .padding(.horizontal, 8)
                            .background(Capsule().fill(CrichereColor.greenContainer))
                        // The longest version that fits on one line, so a trailing "…" ends the row.
                        ViewThatFits(in: .horizontal) {
                            ForEach(line.options, id: \.self) { option in
                                Text(option).crichere(.mono(12, .medium, lineHeight: 20), CrichereColor.onBackground).lineLimit(1)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(CrichereColor.surfaceVariant))
    }
}

/// Design S4: once the auction has started, a lock note and the steps as a read-only table.
struct LockedAuctionNote: View {
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            CrIcon("ic_lock", size: 18, tint: CrichereColor.onWarning)
            Text("The auction has started. Settings can’t be changed now.")
                .crichere(.sans(13, .medium, lineHeight: 18), CrichereColor.onWarning)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(CrichereColor.warningContainer))
    }
}

struct LockedBidSteps: View {
    let rows: [LadderPreviewLine]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Bid steps").crichere(.sans(15, .semiBold, lineHeight: 20), CrichereColor.onBackground)
            VStack(spacing: 0) {
                tableRow("Bid range", "Step", header: true)
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    Rectangle().fill(CrichereColor.outlineVariant).frame(height: 1)
                    tableRow(row.values, row.step, header: false)
                }
            }
            .background(CrichereColor.surface)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(CrichereColor.outlineVariant))
        }
    }

    private func tableRow(_ range: String, _ step: String, header: Bool) -> some View {
        let style: CrichereTextStyle = header ? .sans(12, .semiBold) : .mono(13, .medium)
        let color = header ? CrichereColor.onSurfaceVariant : CrichereColor.onBackground
        return HStack {
            Text(range).crichere(style, color)
            Spacer()
            Text(step).crichere(style, color)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(header ? CrichereColor.surfaceVariant : Color.clear)
    }
}

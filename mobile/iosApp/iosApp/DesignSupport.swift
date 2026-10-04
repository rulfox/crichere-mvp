import SwiftUI
import Shared

/// Shared bits for the design update #4 ports (docs/PHASE15.md section 7). **Authored but unverified**
/// (no Xcode here), like every Swift file in this target.

private let indianGrouping: NumberFormatter = {
    let formatter = NumberFormatter()
    formatter.locale = Locale(identifier: "en_IN")
    formatter.numberStyle = .decimal
    formatter.maximumFractionDigits = 2
    return formatter
}()

/// "₹15,500" / "₹1,25,000" -- Indian digit grouping, same as Android's `rupees()`.
func rupees(_ amount: Double) -> String {
    "₹" + (indianGrouping.string(from: NSNumber(value: amount)) ?? String(amount))
}

/// A one-off message at the bottom of a screen: the I12 snackbar (#15201A, radius 10, 12 pt from the
/// sides, 24 pt above the bottom). A `nil` duration stays until its action or a tap on the message.
struct Notice: Equatable {
    let id = UUID()
    let message: String
    var actionLabel: String? = nil
    var durationSeconds: Double? = 4

    static func == (lhs: Notice, rhs: Notice) -> Bool { lhs.id == rhs.id }
}

struct NoticeBanner: View {
    let notice: Notice
    var onAction: (() -> Void)? = nil
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Text(notice.message)
                .font(.system(size: 13.5, weight: .medium))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let label = notice.actionLabel, let onAction {
                Button(label, action: onAction)
                    .font(.system(size: 13.5, weight: .semibold))
                    .foregroundColor(Color(red: 0xA8 / 255, green: 0xD5 / 255, blue: 0xA0 / 255))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(minHeight: 48)
        .background(Color(red: 0x15 / 255, green: 0x20 / 255, blue: 0x1A / 255), in: RoundedRectangle(cornerRadius: 10))
        .shadow(color: .black.opacity(0.25), radius: 9, y: 6)
        .padding(.horizontal, 12)
        .padding(.bottom, 24)
        .onTapGesture { if notice.actionLabel == nil { onDismiss() } }
        .task(id: notice.id) {
            guard let seconds = notice.durationSeconds else { return }
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            onDismiss()
        }
        .transition(.opacity)
    }
}

extension View {
    /// Shows [notice] as a [NoticeBanner] over the bottom of this view.
    func noticeBanner(_ notice: Binding<Notice?>, onAction: (() -> Void)? = nil) -> some View {
        overlay(alignment: .bottom) {
            if let current = notice.wrappedValue {
                NoticeBanner(notice: current, onAction: onAction, onDismiss: { notice.wrappedValue = nil })
            }
        }
        .animation(.easeOut(duration: 0.2), value: notice.wrappedValue)
    }
}

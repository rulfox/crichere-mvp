import SwiftUI
import UIKit

// Shared pieces of the "pay the organizer, attach proof" flow -- Join as player (screen F) and Claim a
// franchise (screen G). The iOS twin of androidApp `ui/PaymentProof.kt`.

/// Opens a UPI app for [upiId]/[amount]; calls back `false` if none is installed (design F2).
func launchUpiPayment(upiId: String, payeeName: String, amount: Double, note: String, completion: @escaping (Bool) -> Void) {
    var components = URLComponents()
    components.scheme = "upi"
    components.host = "pay"
    components.queryItems = [
        URLQueryItem(name: "pa", value: upiId),
        URLQueryItem(name: "pn", value: payeeName),
        URLQueryItem(name: "am", value: String(amount)),
        URLQueryItem(name: "tn", value: note),
        URLQueryItem(name: "cu", value: "INR"),
    ]
    guard let url = components.url else { return completion(false) }
    UIApplication.shared.open(url, options: [:]) { opened in completion(opened) }
}

func copyToClipboard(_ text: String) {
    UIPasteboard.general.string = text
}

/// White 16 pt-rounded card with the board's hairline border, 15 pt padding.
struct FlowCard<Content: View>: View {
    var borderColor: Color = CrichereColor.outlineVariant
    @ViewBuilder let content: () -> Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        VStack(alignment: .leading, spacing: 0, content: content)
            .padding(15)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(CrichereColor.surface, in: shape)
            .overlay(shape.strokeBorder(borderColor, lineWidth: 1))
    }
}

/// Close (X) + title row used by the full-screen flows.
struct FlowTopBar: View {
    let title: String
    let onClose: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            BackArrowButton(icon: "ic_close", label: "Close", action: onClose)
            Text(title).crichere(.sans(17, .semiBold), CrichereColor.onBackground).accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
        }
        .padding(.leading, 5)
        .frame(height: 48)
        .padding(.top, 3)
    }
}

/// League tile + name + "Starts 12 Oct · T20" summary at the top of the flows.
struct LeagueSummaryRow: View {
    let id: String
    let name: String
    let logoUrl: String?
    let startsOn: String
    let format: String?
    var logoRemoved: Bool = false

    var body: some View {
        HStack(spacing: 10) {
            LeagueTile(id: id, name: name, logoUrl: logoUrl, size: 40, corner: 10, fontSize: 12.8, logoRemoved: logoRemoved)
            VStack(alignment: .leading, spacing: 3) {
                Text(name).crichere(.archivo(14.5, .bold, lineHeight: 15.95), CrichereColor.onBackground)
                Text("Starts \(shortDate(startsOn))" + (format.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : " · \($0)" } ?? ""))
                    .crichere(.sans(12), CrichereColor.onSurfaceVariant)
            }
            Spacer(minLength: 0)
        }
    }
}

/// Fee card: amount, the organizer's UPI ID with "Pay via UPI" + copy (F1), the no-UPI-app fallback
/// (F2) and the organizer-has-no-UPI-ID notice (F9).
struct PayToCard: View {
    let feeLabel: String
    let fee: Double
    let upiId: String?
    let noUpiApp: Bool
    let copied: Bool
    let onPayViaUpi: () -> Void
    let onCopyUpiId: () -> Void

    var body: some View {
        FlowCard {
            Text("\(feeLabel): \(rupees(fee))").crichere(.sans(14, .semiBold), CrichereColor.onBackground)
            Spacer().frame(height: 10)
            if let upiId {
                if noUpiApp {
                    InfoNote(title: nil, message: "No UPI app found on this phone. Copy the UPI ID and pay from any app, or ask the organizer.")
                    Spacer().frame(height: 10)
                    UpiIdCopyRow(upiId: upiId, copied: copied, onCopyUpiId: onCopyUpiId)
                } else {
                    HStack(alignment: .center) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Pay to").crichere(.sans(11), CrichereColor.onSurfaceVariant)
                            Text(upiId).crichere(.mono(13, .semiBold), CrichereColor.onBackground)
                        }
                        Spacer(minLength: 8)
                        Text(rupees(fee)).crichere(.mono(22, .bold), CrichereColor.primary)
                    }
                    .padding(12)
                    .background(CrichereColor.dialogSurface, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    Spacer().frame(height: 10)
                    HStack(spacing: 8) {
                        FlowPill(text: "Pay via UPI", icon: "ic_open_in_new", filled: true, height: 42, action: onPayViaUpi)
                        Button(action: onCopyUpiId) {
                            CrIcon("ic_content_copy", size: 18, tint: CrichereColor.primary)
                                .frame(width: 42, height: 42)
                                .overlay(Circle().strokeBorder(CrichereColor.outline, lineWidth: 1))
                        }
                        .buttonStyle(.pressCircle)
                        .accessibilityLabel("Copy UPI ID")
                    }
                }
            } else {
                InfoNote(title: "The organizer hasn't added a UPI ID yet.", message: "Pay them directly, then attach the payment screenshot.")
                Spacer().frame(height: 10)
                FlowPill(text: "Pay via UPI", icon: "ic_open_in_new", filled: true, enabled: false, height: 42) {}
            }
        }
    }
}

/// The organizer's UPI ID with a Copy chip -- the no-UPI-app fallback (F2/G).
struct UpiIdCopyRow: View {
    let upiId: String
    let copied: Bool
    let onCopyUpiId: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Text(upiId).crichere(.mono(14, .semiBold), CrichereColor.onBackground).frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onCopyUpiId) {
                HStack(spacing: 4) {
                    CrIcon(copied ? "ic_check" : "ic_content_copy", size: 16, tint: CrichereColor.primary)
                    Text(copied ? "Copied" : "Copy").crichere(.sans(12.5, .semiBold), CrichereColor.primary)
                }
                .padding(.horizontal, 12)
                .frame(height: 34)
                .background(CrichereColor.primaryContainer, in: Capsule())
            }
            .buttonStyle(PressHighlight(shape: Capsule()))
        }
        .padding(.leading, 14)
        .padding(.trailing, 6)
        .frame(height: 48)
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(CrichereColor.primary, lineWidth: 2))
    }
}

/// Amber info callout: 18 pt info icon, optional SemiBold title, body.
struct InfoNote: View {
    let title: String?
    let message: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            CrIcon("ic_info", size: 18, tint: CrichereColor.onWarning).padding(.top, 1)
            VStack(alignment: .leading, spacing: 4) {
                if let title { Text(title).crichere(.sans(12.5, .semiBold, lineHeight: 17), CrichereColor.onWarning) }
                Text(message).crichere(.sans(12, lineHeight: 17), CrichereColor.onWarning)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(CrichereColor.warningContainer, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

/// What the payment-screenshot card is showing.
enum ProofState: Equatable {
    case empty
    case uploading(progress: Double, caption: String)
    case failed
    case attached(caption: String)
}

/// Design F1/F5: dropzone, then uploading (progress), failed (Retry / Remove) or attached (Replace / Remove).
struct PaymentProofCard: View {
    let state: ProofState
    let thumbnail: UIImage?
    let onChoose: () -> Void
    let onCancel: () -> Void
    let onRetry: () -> Void
    let onRemove: () -> Void
    let onView: () -> Void

    var body: some View {
        FlowCard(borderColor: state == .failed ? Color(hex: 0xF0C7C0) : CrichereColor.outlineVariant) {
            if state == .empty {
                dropzone
            } else {
                HStack(alignment: .top, spacing: 12) {
                    thumbnailTile
                    VStack(alignment: .leading, spacing: 0) { details }
                        .frame(maxWidth: .infinity, minHeight: 124, maxHeight: 124, alignment: .topLeading)
                }
            }
        }
    }

    @ViewBuilder private var dropzone: some View {
        Text("Payment screenshot").crichere(.sans(14, .semiBold), CrichereColor.onBackground)
        Spacer().frame(height: 10)
        Button(action: onChoose) {
            VStack(spacing: 6) {
                CrIcon("ic_add_photo_alternate", size: 30, tint: CrichereColor.primary)
                Text("Choose screenshot").crichere(.sans(13.5, .semiBold), CrichereColor.primary)
                Text("Amount, UPI ID and reference no. should be visible")
                    .crichere(.sans(11.5, lineHeight: 16.1), CrichereColor.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, minHeight: 134, maxHeight: 134)
            .background(Color(hex: 0xFAFBF8), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(Color(hex: 0xB9C6B3), style: StrokeStyle(lineWidth: 2, dash: [6, 4])))
        }
        .buttonStyle(PressHighlight(shape: RoundedRectangle(cornerRadius: 12, style: .continuous)))
    }

    private var thumbnailTile: some View {
        let shape = RoundedRectangle(cornerRadius: 8, style: .continuous)
        return ZStack {
            CrichereColor.surfaceVariant
            // Pinned to the tile, or a fill-cropped image grows the stack and the badge lands off-tile.
            if let thumbnail { Image(uiImage: thumbnail).resizable().scaledToFill().frame(width: 70, height: 124) }
            switch state {
            case let .uploading(progress, _):
                Color(hex: 0x0E1A11, opacity: 0x8C / 255.0)
                Text("\(Int(progress * 100 + 0.0001))%").crichere(.mono(10, .bold), .white)
                    .frame(width: 32, height: 32)
                    .background(Color(hex: 0x0E1A11, opacity: 0xD9 / 255.0), in: Circle())
            case .failed:
                Color(hex: 0x8C1D12, opacity: 0x8C / 255.0)
                CrIcon("ic_error", size: 28, tint: .white)
            case .attached:
                CrIcon("ic_open_in_full", size: 14, tint: .white)
                    .frame(width: 22, height: 22)
                    .background(Color(hex: 0x0E1A11, opacity: 0xB3 / 255.0), in: Circle())
                    .padding(4)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            case .empty:
                EmptyView()
            }
        }
        .frame(width: 70, height: 124)
        .clipShape(shape)
        .overlay(shape.strokeBorder(Color(hex: 0xDDE1D6), lineWidth: 1))
        .contentShape(shape)
        .onTapGesture { if case .attached = state { onView() } }
        .accessibilityLabel(state.isAttached ? "View screenshot" : "Payment screenshot")
    }

    @ViewBuilder private var details: some View {
        switch state {
        case let .uploading(progress, caption):
            Text("Uploading…").crichere(.sans(13.5, .semiBold), CrichereColor.onBackground)
            Spacer().frame(height: 8)
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    CrichereColor.surfaceVariant
                    CrichereColor.primary.frame(width: proxy.size.width * min(max(progress, 0), 1))
                }
            }
            .frame(height: 4)
            .clipShape(RoundedRectangle(cornerRadius: 2))
            Spacer().frame(height: 8)
            Text(caption).crichere(.mono(11.5, .medium), CrichereColor.onSurfaceVariant)
            Spacer(minLength: 0)
            Button(action: onCancel) { Text("Cancel").crichere(.sans(12.5, .semiBold), CrichereColor.onSurfaceVariant) }
                .buttonStyle(.plain)
        case .failed:
            Text("Upload failed").crichere(.sans(13.5, .semiBold), CrichereColor.errorStrong)
            Spacer().frame(height: 6)
            Text("Check your connection.").crichere(.sans(12, lineHeight: 16.8), CrichereColor.onSurfaceVariant)
            Spacer(minLength: 0)
            HStack(spacing: 8) {
                SmallAction(text: "Retry", filled: true, action: onRetry)
                Button(action: onRemove) { Text("Remove").crichere(.sans(12.5, .semiBold), CrichereColor.onSurfaceVariant).padding(8) }
                    .buttonStyle(.plain)
            }
        case let .attached(caption):
            HStack(spacing: 5) {
                CrIcon("ic_check_circle_filled", size: 17, tint: CrichereColor.primary)
                Text("Attached").crichere(.sans(13.5, .semiBold), CrichereColor.primary)
            }
            Spacer().frame(height: 6)
            Text(caption).crichere(.mono(11.5, .medium), CrichereColor.onSurfaceVariant)
            Spacer(minLength: 0)
            HStack(spacing: 4) {
                SmallAction(text: "Replace", filled: false, icon: "ic_swap_horiz", action: onChoose)
                Button(action: onRemove) { Text("Remove").crichere(.sans(12.5, .semiBold), CrichereColor.errorStrong).padding(8) }
                    .buttonStyle(.plain)
            }
        case .empty:
            EmptyView()
        }
    }
}

private extension ProofState {
    var isAttached: Bool { if case .attached = self { return true } else { return false } }
}

/// 32 pt pill: filled green, or outlined with an optional 15 pt icon.
struct SmallAction: View {
    let text: String
    let filled: Bool
    var icon: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let icon { CrIcon(icon, size: 15, tint: CrichereColor.primary) }
                Text(text).crichere(.sans(12.5, .semiBold), filled ? CrichereColor.onPrimary : CrichereColor.primary)
            }
            .padding(.horizontal, 12)
            .frame(height: 32)
        }
        .buttonStyle(PillButtonStyle(fill: filled ? CrichereColor.primary : .clear,
                                     pressedFill: filled ? CrichereColor.greenPressed : CrichereColor.primary.opacity(0.1),
                                     content: filled ? CrichereColor.onPrimary : CrichereColor.primary,
                                     border: filled ? nil : CrichereColor.outline, radius: 16))
    }
}

/// Full-width pill of [height] (default 48): filled green, outlined, or disabled grey, with an
/// optional 18 pt leading icon.
struct FlowPill: View {
    let text: String
    let icon: String?
    let filled: Bool
    var enabled: Bool = true
    var height: CGFloat = 48
    let action: () -> Void

    var body: some View {
        let container: Color = !enabled ? CrichereColor.disabledContainer : (filled ? CrichereColor.primary : .clear)
        let content: Color = !enabled ? CrichereColor.inkSubtle : (filled ? CrichereColor.onPrimary : CrichereColor.primary)
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon { CrIcon(icon, size: 18, tint: content) }
                Text(text).crichere(.sans(13.5, .semiBold), content)
            }
            .frame(maxWidth: .infinity, minHeight: height, maxHeight: height)
        }
        .buttonStyle(PillButtonStyle(fill: container,
                                     pressedFill: filled ? CrichereColor.greenPressed : CrichereColor.primary.opacity(0.1),
                                     content: content,
                                     border: (!filled && enabled) ? CrichereColor.outline : nil, radius: height / 2))
        .disabled(!enabled)
    }
}

/// Design F3/F4: full preview of the picked screenshot with a "tick what you can see" checklist; "Use
/// this" stays disabled until every item is ticked (instead of automatic receipt detection, which the
/// app can't do). Present inside `crichereSheet`.
struct ScreenshotCheckSheet: View {
    let image: UIImage
    let checklist: [String]
    let onDismiss: () -> Void
    let onChooseAnother: () -> Void
    let onUse: () -> Void

    @State private var ticked: [Bool]

    init(image: UIImage, checklist: [String], onDismiss: @escaping () -> Void, onChooseAnother: @escaping () -> Void, onUse: @escaping () -> Void) {
        self.image = image
        self.checklist = checklist
        self.onDismiss = onDismiss
        self.onChooseAnother = onChooseAnother
        self.onUse = onUse
        _ticked = State(initialValue: Array(repeating: false, count: checklist.count))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 6) {
                Button(action: onDismiss) { CrIcon("ic_arrow_back", size: 22, tint: CrichereColor.onBackground) }
                    .buttonStyle(.plain)
                    .padding(.top, 4)
                    .accessibilityLabel("Back")
                VStack(alignment: .leading, spacing: 4) {
                    Text("Preview").crichere(.sans(17, .semiBold, lineHeight: 17), CrichereColor.onBackground)
                    Text("Payment screenshot · sent to the organizer as-is").crichere(.sans(12, lineHeight: 12), CrichereColor.onSurfaceVariant)
                }
            }
            .padding(.top, 12)
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(10)
                .frame(height: 300)
                .background(Color(hex: 0x101410), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .padding(.top, 12)
                .accessibilityLabel("Payment screenshot")
            Text("Tick what you can see in the screenshot")
                .crichere(.sans(12, .semiBold), CrichereColor.onSurfaceVariant)
                .padding(.horizontal, 4)
                .padding(.top, 12)
            Spacer().frame(height: 4)
            ForEach(checklist.indices, id: \.self) { index in
                HStack(spacing: 0) {
                    CrichereCheckbox(checked: ticked[index], label: checklist[index]) { ticked[index] = $0 }
                    Text(checklist[index]).crichere(.sans(13.5, .medium), CrichereColor.onBackground)
                    Spacer(minLength: 0)
                }
                .frame(height: 38)
                .contentShape(Rectangle())
                .onTapGesture { ticked[index].toggle() }
            }
            HStack(spacing: 10) {
                Button(action: onChooseAnother) {
                    Text("Choose another").crichere(.sans(14.5, .semiBold)).frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
                }
                .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.primary.opacity(0.1), content: CrichereColor.primary, border: CrichereColor.outline, radius: 24))
                let ready = ticked.allSatisfy { $0 }
                Button(action: onUse) {
                    Text("Use this").crichere(.sans(14.5, .semiBold)).frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
                }
                .buttonStyle(PillButtonStyle(fill: ready ? CrichereColor.primary : CrichereColor.disabledContainer, pressedFill: CrichereColor.greenPressed,
                                             content: ready ? CrichereColor.onPrimary : CrichereColor.inkSubtle, radius: 24))
                .disabled(!ready)
            }
            .padding(.horizontal, 4)
            .padding(.top, 14)
        }
        .padding(.horizontal, 15)
        .padding(.bottom, 24)
    }
}

/// "UPI ID copied" on Join (F2) and Claim (G3): a 48 pt ink bar, 100 pt up so it clears the bottom button bar.
struct UpiCopiedBar: View {
    let message: String

    var body: some View {
        Text(message).crichere(.sans(14, .medium), CrichereColor.onPrimary)
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48, alignment: .leading)
            .background(CrichereColor.onBackground, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .padding(.horizontal, 13)
            .padding(.bottom, 100)
    }
}

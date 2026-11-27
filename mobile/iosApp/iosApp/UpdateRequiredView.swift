import SwiftUI
import Shared

/// Observes `AppUpdateNotices` (docs/PHASE23.md): set once the backend says this build is too old.
@MainActor
final class AppUpdateNoticesWrapper: ObservableObject {
    @Published var required: AppUpdateRequired?

    init() {
        let notices = KoinHelper().appUpdateNotices
        Task { [weak self] in
            for await value in notices.required { self?.required = value }
        }
    }
}

/// Design U1 (iOS) and U2 (the link couldn't open), androidApp `ui/UpdateRequiredScreen.kt`. Covers the
/// whole app; the only way on is updating. The button opens the backend's link -- TestFlight while
/// testing, the App Store once live -- and its label follows the link.
struct UpdateRequiredView: View {
    let notice: AppUpdateRequired

    @State private var openFailed = false
    @Environment(\.openURL) private var openURL

    private var isTestFlight: Bool { notice.updateUrl?.lowercased().contains("testflight") == true }
    private var buttonLabel: String { isTestFlight ? "Update in TestFlight" : "Update on the App Store" }
    private var failedNote: String {
        isTestFlight
            ? "Couldn’t open TestFlight. Open the TestFlight app yourself and tap Update next to Crichere."
            : "Couldn’t open the App Store. Open the App Store yourself and update Crichere."
    }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                Spacer()
                art
                Spacer().frame(height: 32)
                Text("Update Crichere to continue")
                    .crichere(.archivo(26, .extraBold, lineHeight: 32, tracking: -0.26), CrichereColor.onBackground)
                    .multilineTextAlignment(.center)
                Spacer().frame(height: 12)
                Text("This version of the app is no longer supported. Update to keep joining leagues and bidding in auctions.")
                    .crichere(.sans(15, lineHeight: 22), CrichereColor.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 300)
                if let current = notice.currentVersion, let needed = notice.minVersion {
                    Spacer().frame(height: 22)
                    (Text("You have ") + Text(current).font(CrichereTextStyle.mono(13, .semiBold).font).foregroundColor(CrichereColor.onBackground)
                        + Text("  ·  Needed ") + Text(needed).font(CrichereTextStyle.mono(13, .semiBold).font).foregroundColor(CrichereColor.onBackground))
                        .crichere(.sans(13), CrichereColor.onSurfaceVariant)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(Capsule().fill(CrichereColor.surfaceVariant))
                }
                Spacer()
            }
            VStack(spacing: 12) {
                if openFailed {
                    HStack(alignment: .top, spacing: 10) {
                        CrIcon("ic_info", size: 18, tint: CrichereColor.onWarning)
                        Text(failedNote)
                            .crichere(.sans(13.5, lineHeight: 19), CrichereColor.onWarning)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 14).fill(CrichereColor.warningContainer))
                    .accessibilityAddTraits(.updatesFrequently)
                }
                Button(action: open) {
                    HStack(spacing: 8) {
                        if openFailed { CrIcon("ic_refresh", size: 20, tint: .white) }
                        Text(openFailed ? "Try again" : buttonLabel)
                            .crichere(.sans(16, .semiBold, lineHeight: 16), .white)
                    }
                    .frame(maxWidth: .infinity, minHeight: 52, maxHeight: 52)
                }
                .buttonStyle(PillButtonStyle(fill: CrichereColor.primary, pressedFill: CrichereColor.greenPressed, content: .white, radius: 26))
                Text("Your leagues, bids and profile are saved. Nothing is lost.")
                    .crichere(.sans(12.5, lineHeight: 18), CrichereColor.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(CrichereColor.background.ignoresSafeArea())
        // Nothing behind the screen can be reached.
        .contentShape(Rectangle())
    }

    /// U1's art: a phone with an up arrow in a green circle.
    private var art: some View {
        ZStack {
            Circle().fill(CrichereColor.greenContainer).frame(width: 132, height: 132)
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.white)
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(CrichereColor.primary, lineWidth: 2.5))
                .frame(width: 56, height: 88)
            CrIcon("ic_arrow_forward", size: 26, tint: CrichereColor.primary).rotationEffect(.degrees(-90))
        }
        .accessibilityHidden(true)
    }

    private func open() {
        guard let text = notice.updateUrl, let url = URL(string: text) else {
            openFailed = true
            return
        }
        openURL(url) { accepted in openFailed = !accepted }
    }
}

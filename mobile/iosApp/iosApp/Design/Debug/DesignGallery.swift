#if DEBUG
import SwiftUI

/// DEBUG-only component gallery: launch with `-gallery` (`xcrun simctl launch booted com.crichere.app
/// -gallery`). Every Design/ component in its states, to compare with the Android components and to
/// catch a font that didn't register or an icon that didn't convert.
struct DesignGallery: View {
    @State private var name = ""
    @State private var filled = "Aswin Kumar"
    @State private var amount = "15000"
    @State private var role: String? = "All-rounder"
    @State private var snack: Snack?
    @State private var discard = false
    @State private var destructive = false
    @State private var submitting = false
    @State private var tab = 0
    @State private var cropPhoto: PickedPhoto?
    @State private var cropMode = 0
    @State private var areaPicker = false
    @State private var district: String? = "Kolhapur"

    private let icons = ["ic_arrow_back", "ic_add", "ic_search", "ic_near_me", "ic_location_on", "ic_edit", "ic_delete",
                         "ic_check_circle", "ic_error", "ic_info", "ic_gavel", "ic_groups", "ic_photo_camera", "ic_close",
                         "ic_more_vert", "ic_refresh", "ic_logout", "ic_content_copy"]

    /// `-gallery 2` opens on the second page (fields, cards, dialogs) -- simctl can't scroll.
    private var page: Int {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-gallery"), i + 1 < args.count else { return 1 }
        return Int(args[i + 1]) ?? 1
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if page == 1 { pageOne } else if page == 2 { pageTwo } else { pageThree }
            }
            .padding(20)
        }
        .background(CrichereColor.background)
        .safeAreaInset(edge: .top) { BackTitleBar(title: "Design gallery") {}.background(CrichereColor.background) }
        .snackHost($snack)
        .crichereDialog(isPresented: $discard) {
            DiscardChangesDialog(message: "Your league stays as it was.", onKeepEditing: { discard = false }, onDiscard: { discard = false })
        }
        .crichereDialog(isPresented: $destructive, dismissable: !submitting) {
            DestructiveConfirmDialog(title: "Remove co-organizer?", message: "They lose access to this league.", confirmLabel: "Remove",
                                     submittingLabel: "Removing…", submitting: submitting,
                                     onDismiss: { destructive = false }, onConfirm: { submitting = true })
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if page == 3 {
                CrichereTabBar(tabs: [
                    CrichereTab(id: 0, label: "Dashboard", icon: "ic_sports_cricket", selectedIcon: "ic_sports_cricket_filled"),
                    CrichereTab(id: 1, label: "My Leagues", icon: "ic_groups", selectedIcon: "ic_groups_filled"),
                    CrichereTab(id: 2, label: "My Profile", icon: "ic_account_circle", selectedIcon: "ic_account_circle_filled"),
                ], selected: tab) { tab = $0 }
            }
        }
        .crichereSheet(item: $cropPhoto) { photo in
            PhotoCropSheet(photo: photo, title: cropMode == 2 ? "League banner" : "Profile photo",
                           subtitle: "Drag or pinch to position", onDismiss: { cropPhoto = nil }, onChooseAnother: {},
                           onUsePhoto: { _ in cropPhoto = nil }, square: cropMode == 1,
                           onUseBanner: cropMode == 2 ? { _ in cropPhoto = nil } : nil)
        }
        .crichereSheet(isPresented: $areaPicker) {
            AreaPickerSheet(title: "District", searchHint: "Search districts", options: Self.districts, selectedName: district,
                            optionLabel: { $0 }) { district = $0; areaPicker = false }
        }
        .onAppear {
            let args = ProcessInfo.processInfo.arguments
            if args.contains("-gallery-dialog") { destructive = true }
            if args.contains("-gallery-crop") { cropPhoto = Self.samplePhoto }
            if args.contains("-gallery-crop-banner") { cropMode = 2; cropPhoto = Self.samplePhoto }
            if args.contains("-gallery-area") { areaPicker = true }
            if args.contains("-gallery-snack") { snack = Snack(message: "League saved", actionLabel: "View", onAction: {}, durationSeconds: nil) }
        }
    }

    @ViewBuilder private var pageOne: some View {
                section("Type")
                Text("Archivo 800 28 Crichere").crichere(.archivo(28, .extraBold), CrichereColor.primary)
                Text("Archivo 700 22 League detail").crichere(.archivo(22, .bold), CrichereColor.ink)
                ForEach([CrichereWeight.normal, .medium, .semiBold, .bold], id: \.rawValue) { weight in
                    Text("Instrument Sans \(weight.rawValue) — Join as player").crichere(.sans(16, weight), CrichereColor.ink)
                }
                Text("JetBrains Mono ₹1,25,000 +91 98765").crichere(.mono(16, .medium), CrichereColor.ink)
                Text("Fallback check: \(CrichereFonts.uiFont(.instrumentSans, .normal, 12).familyName) / \(CrichereFonts.uiFont(.archivo, .bold, 12).familyName) / \(CrichereFonts.uiFont(.jetBrainsMono, .normal, 12).familyName)")
                    .crichere(.sans(12), CrichereColor.inkMuted)

                section("Icons")
                LazyVGrid(columns: Array(repeating: GridItem(.fixed(36)), count: 9), spacing: 8) {
                    ForEach(icons, id: \.self) { CrIcon($0, size: 24, tint: CrichereColor.ink) }
                }

                section("Buttons")
                CrichereWordmark()
                CricherePrimaryButton("Send code") {}
                CricherePrimaryButton("Send code", loading: true, loadingText: "Sending…") {}
                CricherePrimaryButton("Send code", enabled: false) {}
                CrichereOutlinedButton("Try again") {}
    }

    @ViewBuilder private var pageTwo: some View {
                section("Fields")
                CrichereTextField(text: $name, label: "Full name")
                CrichereTextField(text: $filled, label: "Full name")
                CrichereTextField(text: $amount, label: "Base price", error: "Enter at least ₹100", mono: true)
                CrichereTextField(text: $name, label: "UPI ID", placeholder: "name@bank")
                CrichereTextField(text: $name, label: "District", enabled: false)
                CrichereSelectField(label: "Playing role", options: ["Batsman", "Bowler", "All-rounder", "Wicketkeeper"], selected: role,
                                    optionLabel: { $0 }, onSelected: { role = $0 })
                CrichereTapField(label: "Start date", value: "12 Oct 2026", trailingIcon: "ic_event") {}

                section("Cards")
                NoticeCard(icon: "ic_error", title: "Couldn't send the code", message: "Check your connection and try again.",
                           containerColor: CrichereColor.errorContainer, titleColor: CrichereColor.errorStrong, bodyColor: CrichereColor.errorBody)
                NoticeCard(icon: "ic_info", title: "Squad limits", message: "4 franchises × 11 players needs 44 players; 38 joined.",
                           containerColor: CrichereColor.warningContainer, titleColor: CrichereColor.onWarning, bodyColor: CrichereColor.onWarning)
                CrichereSnackbar(message: "Couldn't save. Try again.", actionLabel: "Retry", onAction: {})

                section("Dialogs")
                CrichereOutlinedButton("Discard changes dialog") { discard = true }
                CrichereOutlinedButton("Destructive dialog") { destructive = true }
                CrichereOutlinedButton("Show snackbar") { snack = Snack(message: "League saved", actionLabel: "View", onAction: {}) }
    }

    @ViewBuilder private var pageThree: some View {
                section("Payment proof")
                LeagueSummaryRow(id: "league-1", name: "Kolhapur Premier League", logoUrl: nil, startsOn: "2026-10-12", format: "T20")
                PayToCard(feeLabel: "Entry fee", fee: 500, upiId: "organizer@okaxis", noUpiApp: false, copied: false, onPayViaUpi: {}, onCopyUpiId: {})
                PayToCard(feeLabel: "Franchise fee", fee: 5000, upiId: "organizer@okaxis", noUpiApp: true, copied: true, onPayViaUpi: {}, onCopyUpiId: {})
                PaymentProofCard(state: .empty, thumbnail: nil, onChoose: {}, onCancel: {}, onRetry: {}, onRemove: {}, onView: {})
                PaymentProofCard(state: .uploading(progress: 0.42, caption: "payment-proof.jpg · 412 KB"), thumbnail: Self.samplePhoto.image,
                                 onChoose: {}, onCancel: {}, onRetry: {}, onRemove: {}, onView: {})
                PaymentProofCard(state: .attached(caption: "payment-proof.jpg · 412 KB"), thumbnail: Self.samplePhoto.image,
                                 onChoose: {}, onCancel: {}, onRetry: {}, onRemove: {}, onView: {})
                CrichereOutlinedButton("Crop sheet (circle)") { cropMode = 0; cropPhoto = Self.samplePhoto }
                CrichereOutlinedButton("Area picker") { areaPicker = true }
    }

    static let districts = ["Ahmednagar", "Akola", "Amravati", "Aurangabad", "Beed", "Bhandara", "Buldhana", "Chandrapur",
                            "Dhule", "Gadchiroli", "Gondia", "Hingoli", "Jalgaon", "Jalna", "Kolhapur", "Latur", "Mumbai City"]

    /// A generated test image (gradient + grid) -- no bundled photo needed.
    static let samplePhoto: PickedPhoto = {
        let size = CGSize(width: 1200, height: 900)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
            let colors = [UIColor(red: 0.11, green: 0.37, blue: 0.13, alpha: 1).cgColor, UIColor(red: 0.95, green: 0.71, blue: 0.27, alpha: 1).cgColor]
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray, locations: [0, 1])!
            context.cgContext.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: size.width, y: size.height), options: [])
            UIColor.white.withAlphaComponent(0.35).setStroke()
            for x in stride(from: 0, through: size.width, by: 100) { context.stroke(CGRect(x: x, y: 0, width: 1, height: size.height)) }
            for y in stride(from: 0, through: size.height, by: 100) { context.stroke(CGRect(x: 0, y: y, width: size.width, height: 1)) }
        }
        return PickedPhoto(image: image, fileName: "sample.jpg")
    }()

    private func section(_ title: String) -> some View {
        Text(title).crichere(.archivo(20, .bold, lineHeight: 25), CrichereColor.ink).padding(.top, 8)
    }
}
#endif

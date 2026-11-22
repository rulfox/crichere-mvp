import SwiftUI
import Shared
import PhotosUI

/// Mirrors `ClaimFranchiseViewModel`'s shared `StateFlow<ClaimFranchiseState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents.
@MainActor
final class ClaimFranchiseViewModelWrapper: ObservableObject {
    @Published var state: ClaimFranchiseState

    private let viewModel: ClaimFranchiseViewModel

    init(leagueId: String) {
        let viewModel = KoinHelper().claimFranchiseViewModel(leagueId: leagueId)
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func retry() { viewModel.retry() }
    func onNameChanged(_ name: String) { viewModel.onNameChanged(name: name) }
    func submit() { viewModel.submit() }
    func uploadLogo(data: Data) {
        viewModel.uploadLogo(bytes: toKotlinByteArray(data), contentType: "image/jpeg", filename: "franchise-logo.jpg")
    }
    func removeLogo() { viewModel.removeLogo() }
    func uploadScreenshot(data: Data, fileName: String) {
        viewModel.uploadScreenshot(bytes: toKotlinByteArray(data), contentType: "image/jpeg", filename: fileName)
    }
    func retryUpload() { viewModel.retryUpload() }
    func cancelUpload() { viewModel.cancelUpload() }
    func removeScreenshot() { viewModel.removeScreenshot() }
}

private enum PickTarget { case logo, screenshot }

/// Claim a franchise (design board screen G, androidApp `ui/ClaimFranchiseScreen.kt`): name + square
/// logo (with crop preview), the franchise fee with "Pay via UPI" and the payment screenshot, the
/// free-franchise summary (G4), the claim-failure banner (G6) and the refunds/disputes disclaimer.
struct ClaimFranchiseView: View {
    let leagueId: String
    let onDone: () -> Void
    var onCancel: (() -> Void)? = nil

    @StateObject private var wrapper: ClaimFranchiseViewModelWrapper
    @State private var pickTarget = PickTarget.logo
    @State private var pickerShown = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var pickedLogo: PickedPhoto?
    @State private var pickedScreenshot: PickedPhoto?
    @State private var logoPreview: UIImage?
    @State private var thumbnail: UIImage?
    @State private var noUpiApp = false
    @State private var copied = false
    @State private var snack: Snack?

    init(leagueId: String, onDone: @escaping () -> Void, onCancel: (() -> Void)? = nil) {
        self.leagueId = leagueId
        self.onDone = onDone
        self.onCancel = onCancel
        _wrapper = StateObject(wrappedValue: ClaimFranchiseViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        ClaimFranchiseContent(
            state: wrapper.state,
            logoPreview: logoPreview,
            noUpiApp: noUpiApp,
            copied: copied,
            thumbnail: thumbnail,
            actions: ClaimFranchiseActions(
                flow: PaymentFlowActions(
                    onClose: onCancel ?? onDone,
                    onRetryLoad: wrapper.retry,
                    onPayViaUpi: payViaUpi,
                    onCopyUpiId: {
                        if let upi = wrapper.state.league?.organizerUpiId { copyToClipboard(upi) }
                        copied = true
                        snack = Snack(message: "UPI ID copied", durationSeconds: 3)
                    },
                    onChoose: { choose(.screenshot) },
                    onCancelUpload: { wrapper.cancelUpload(); thumbnail = nil },
                    onRetryUpload: wrapper.retryUpload,
                    onRemove: { wrapper.removeScreenshot(); thumbnail = nil },
                    onSubmit: wrapper.submit
                ),
                onNameChanged: wrapper.onNameChanged,
                onPickLogo: { choose(.logo) },
                onRemoveLogo: { wrapper.removeLogo(); logoPreview = nil }
            )
        )
        .crichereScreen()
        .overlay(alignment: .bottom) { copiedBar }
        .animation(.easeOut(duration: 0.2), value: snack)
        .onAppear { wrapper.retry() }
        .onChange(of: wrapper.state.claimed) { _, claimed in if claimed { onDone() } }
        // A failed logo upload drops the logo; drop the local preview with it.
        .onChange(of: wrapper.state.isUploadingLogo) { _, uploading in
            if !uploading && wrapper.state.logoUrl == nil { logoPreview = nil }
        }
        .photosPicker(isPresented: $pickerShown, selection: $pickerItem, matching: .images)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            pickerItem = nil
            let target = pickTarget
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self) else { return }
                if target == .logo {
                    pickedLogo = decodePickedPhoto(data, fileName: "franchise-logo.jpg")
                } else {
                    pickedScreenshot = decodePickedPhoto(data, fileName: "payment-proof.jpg")
                }
            }
        }
        .crichereSheet(item: $pickedLogo) { photo in
            PhotoCropSheet(
                photo: photo, title: "Preview", subtitle: "Franchise logo · drag to reposition",
                onDismiss: { pickedLogo = nil },
                onChooseAnother: { pickedLogo = nil; choose(.logo) },
                onUsePhoto: { square in
                    pickedLogo = nil
                    guard let jpeg = encodeCrop(photo, square) else { return }
                    logoPreview = UIImage(data: jpeg)
                    wrapper.uploadLogo(data: jpeg)
                },
                square: true,
                useLabel: "Use logo"
            )
        }
        .crichereSheet(item: $pickedScreenshot) { photo in
            let league = wrapper.state.league
            ScreenshotCheckSheet(
                image: photo.image,
                checklist: [league?.franchiseFee.map { "Amount \(rupees($0.doubleValue))" }, league?.organizerUpiId.map { "UPI ID \($0)" }, "Reference / UTR no."]
                    .compactMap { $0 },
                onDismiss: { pickedScreenshot = nil },
                onChooseAnother: { pickedScreenshot = nil; choose(.screenshot) },
                onUse: {
                    pickedScreenshot = nil
                    thumbnail = photo.image
                    if let jpeg = photo.image.jpegData(compressionQuality: 0.9) { wrapper.uploadScreenshot(data: jpeg, fileName: photo.fileName) }
                }
            )
        }
    }

    /// Android places "UPI ID copied" 100 pt up, clear of the Claim bar.
    @ViewBuilder private var copiedBar: some View {
        if let snack {
            Text(snack.message).crichere(.sans(14, .medium), CrichereColor.onPrimary)
                .padding(.horizontal, 16)
                .frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48, alignment: .leading)
                .background(CrichereColor.onBackground, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                .padding(.horizontal, 13)
                .padding(.bottom, 100)
                .transition(.opacity)
                .task(id: snack.id) {
                    try? await Task.sleep(nanoseconds: 3_000_000_000)
                    self.snack = nil
                }
        }
    }

    private func choose(_ target: PickTarget) {
        pickTarget = target
        pickerShown = true
    }

    private func payViaUpi() {
        guard let league = wrapper.state.league, let upi = league.organizerUpiId, let fee = league.franchiseFee?.doubleValue else { return }
        launchUpiPayment(upiId: upi, payeeName: league.name, amount: fee, note: "Franchise fee for \(league.name)") { opened in noUpiApp = !opened }
    }
}

/// What the Claim screen does: the shared pay-flow actions plus the name and logo.
struct ClaimFranchiseActions {
    var flow = PaymentFlowActions()
    var onNameChanged: (String) -> Void = { _ in }
    var onPickLogo: () -> Void = {}
    var onRemoveLogo: () -> Void = {}
}

/// The screen, stateless for the DEBUG parity gallery.
struct ClaimFranchiseContent: View {
    let state: ClaimFranchiseState
    var logoPreview: UIImage? = nil
    var noUpiApp: Bool = false
    var copied: Bool = false
    var thumbnail: UIImage? = nil
    var actions = ClaimFranchiseActions()

    private var locked: Bool { state.isSubmitting }

    var body: some View {
        VStack(spacing: 0) {
            FlowTopBar(title: "Claim a franchise", onClose: actions.flow.onClose)
            if let league = state.league {
                ScrollView { form(league) }
                if league.franchiseFee != nil && state.screenshotUrl != nil && state.errorMessage == nil && !locked {
                    Text("Crichere doesn't process payment or handle refunds/disputes -- that's between you and the organizer directly.")
                        .crichere(.sans(11.5, lineHeight: 16.675), CrichereColor.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 19)
                        .padding(.bottom, 12)
                }
                ClaimBar(state: state, league: league, onClaim: actions.flow.onSubmit)
            } else if state.loadFailed {
                LoadErrorView(onRetry: actions.flow.onRetryLoad)
            } else {
                CrichereSpinner(color: CrichereColor.primary, lineWidth: 4, size: 40).frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(CrichereColor.background.ignoresSafeArea())
    }

    private func form(_ league: LeagueDto) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            NameCard(state: state, logoPreview: logoPreview, locked: locked, actions: actions)
                .opacity(locked ? 0.55 : 1)
            Spacer().frame(height: 12)
            if let fee = league.franchiseFee?.doubleValue {
                FeeCard(upiId: league.organizerUpiId, fee: fee, proof: proofState, thumbnail: thumbnail, noUpiApp: noUpiApp,
                        copied: copied, locked: locked, actions: actions.flow)
                    .opacity(locked ? 0.55 : 1)
            } else {
                FreeFranchiseNote()
            }
        }
        .padding(.horizontal, 19)
        .padding(.top, 6)
        .padding(.bottom, 20)
    }

    private var proofState: ProofState {
        if state.isUploadingScreenshot {
            return .uploading(progress: Double(state.uploadProgress * 100) / 100, caption: fileCaption(state.uploadFileName, state.uploadSizeBytes?.int64Value))
        }
        if state.uploadFailed { return .failed }
        if state.screenshotUrl != nil { return .attached(caption: sizeCaption(state.uploadSizeBytes?.int64Value)) }
        return .empty
    }
}

/// "388 KB" -- the G3 row shows only the size.
private func sizeCaption(_ bytes: Int64?) -> String {
    guard let bytes else { return "" }
    return bytes >= 1_048_576 ? String(format: "%.1f MB", Double(bytes) / 1_048_576) : "\((bytes + 1023) / 1024) KB"
}

private let dashedOutline = Color(hex: 0xB9C6B3)

// MARK: - Name card

/// Design G1/G3: square logo tile next to the franchise name.
private struct NameCard: View {
    let state: ClaimFranchiseState
    let logoPreview: UIImage?
    let locked: Bool
    let actions: ClaimFranchiseActions

    var body: some View {
        let hasLogo = logoPreview != nil || state.logoUrl != nil
        FlowCard {
            HStack(spacing: 14) {
                LogoTile(name: state.name, preview: logoPreview, logoUrl: state.logoUrl, uploading: state.isUploadingLogo,
                         enabled: !locked, onClick: actions.onPickLogo)
                VStack(alignment: .leading, spacing: 0) {
                    NameField(name: state.name, error: state.nameError, enabled: !locked, onNameChanged: actions.onNameChanged)
                    if hasLogo && !locked {
                        Spacer().frame(height: 6)
                        Button(action: actions.onRemoveLogo) {
                            Text("Remove logo").crichere(.sans(12, .semiBold), CrichereColor.errorStrong)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

private struct LogoTile: View {
    let name: String
    let preview: UIImage?
    let logoUrl: String?
    let uploading: Bool
    let enabled: Bool
    let onClick: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        Button(action: onClick) {
            tile.frame(width: 80, height: 80).clipShape(shape).contentShape(shape)
        }
        .buttonStyle(PressHighlight(shape: shape))
        .disabled(!enabled)
        .accessibilityLabel(preview != nil || logoUrl != nil ? "Change logo" : "Add logo")
    }

    @ViewBuilder private var tile: some View {
        if preview != nil || logoUrl != nil {
            ZStack(alignment: .bottomTrailing) {
                if let preview {
                    Image(uiImage: preview).resizable().scaledToFill().frame(width: 80, height: 80)
                } else {
                    RemoteImage(url: logoUrl)
                }
                if uploading {
                    Color(hex: 0x0E1A11, opacity: 0x8C / 255.0)
                    CrichereSpinner(color: .white, track: .clear, lineWidth: 2, size: 24).frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if enabled {
                    CrIcon("ic_edit", size: 14, tint: .white)
                        .frame(width: 24, height: 24)
                        .background(Color(hex: 0x0E1A11, opacity: 0.7), in: Circle())
                        .padding(4)
                }
            }
        } else if !name.trimmingCharacters(in: .whitespaces).isEmpty {
            Text(String(initials(name).prefix(2)))
                .crichere(.archivo(25.6, .extraBold), .white)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(tileColor(name.trimmingCharacters(in: .whitespaces)))
        } else {
            VStack(spacing: 4) {
                CrIcon("ic_add_photo_alternate", size: 26, tint: CrichereColor.primary)
                Text("Logo").crichere(.sans(10, .semiBold, lineHeight: 10), CrichereColor.primary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(hex: 0xF1F4EE))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(dashedOutline, style: StrokeStyle(lineWidth: 2, dash: [6, 4])))
        }
    }
}

/// Empty: a boxed field with a "Franchise name" placeholder (red when [error], design G1).
/// Filled: a label over the name in Archivo with an underline (design G3). One TextField throughout,
/// so typing the first letter keeps focus.
private struct NameField: View {
    let name: String
    let error: Bool
    let enabled: Bool
    let onNameChanged: (String) -> Void

    @FocusState private var focused: Bool

    var body: some View {
        let empty = name.isEmpty
        let box = RoundedRectangle(cornerRadius: 10, style: .continuous)
        let border = error ? CrichereColor.error : focused ? CrichereColor.primary : CrichereColor.outline
        VStack(alignment: .leading, spacing: 0) {
            if !empty {
                Text("Franchise name").crichere(.sans(11.5, .medium, lineHeight: 11.5), CrichereColor.onSurfaceVariant)
                Spacer().frame(height: 6)
            }
            ZStack(alignment: empty ? .leading : .topLeading) {
                if empty {
                    Text("Franchise name").crichere(.sans(14, lineHeight: 14), CrichereColor.inkSubtle)
                }
                TextField("", text: Binding(get: { name }, set: { onNameChanged(String($0.prefix(60))) }))
                    .font(empty ? CrichereTextStyle.sans(14).font : CrichereTextStyle.archivo(18, .bold).font)
                    .foregroundStyle(CrichereColor.onBackground)
                    .tint(CrichereColor.primary)
                    .textFieldStyle(.plain)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .focused($focused)
                    .disabled(!enabled)
            }
            .padding(.horizontal, empty ? 14 : 0)
            .frame(maxWidth: .infinity, minHeight: empty ? 48 : 26, maxHeight: empty ? 48 : 26, alignment: empty ? .leading : .topLeading)
            .background(empty ? CrichereColor.surface : .clear, in: box)
            .overlay(box.strokeBorder(empty ? border : .clear, lineWidth: error || focused ? 2 : 1))
            if !empty {
                Rectangle().fill(enabled ? CrichereColor.primary : CrichereColor.outline).frame(height: 2)
            } else if error {
                Spacer().frame(height: 6)
                Text("Enter a franchise name").crichere(.sans(11.5, .medium, lineHeight: 11.5), CrichereColor.error)
            }
        }
    }
}

// MARK: - Fee card

/// Design G1/G3: "Franchise fee ₹5,000", Pay via UPI, then the compact screenshot row.
private struct FeeCard: View {
    let upiId: String?
    let fee: Double
    let proof: ProofState
    let thumbnail: UIImage?
    let noUpiApp: Bool
    let copied: Bool
    let locked: Bool
    let actions: PaymentFlowActions

    var body: some View {
        FlowCard {
            HStack {
                Text("Franchise fee").crichere(.sans(14, .semiBold, lineHeight: 14), CrichereColor.onBackground)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(rupees(fee)).crichere(.mono(20, .bold, lineHeight: 20), CrichereColor.primary)
            }
            .frame(height: 20)
            Spacer().frame(height: 10)
            if proof == .empty {
                payStep
                AttachRow(onClick: actions.onChoose)
            } else {
                ProofRow(proof: proof, thumbnail: thumbnail, locked: locked, actions: actions)
            }
        }
    }

    @ViewBuilder private var payStep: some View {
        if let upiId {
            if noUpiApp {
                InfoNote(title: nil, message: "No UPI app found on this phone. Copy the UPI ID and pay from any app, or ask the organizer.")
                Spacer().frame(height: 10)
                UpiIdCopyRow(upiId: upiId, copied: copied, onCopyUpiId: actions.onCopyUpiId)
                Spacer().frame(height: 10)
            } else {
                FlowPill(text: "Pay via UPI", icon: "ic_open_in_new", filled: true, height: 42, action: actions.onPayViaUpi)
                Spacer().frame(height: 10)
            }
        } else {
            InfoNote(title: "The organizer hasn't added a UPI ID yet.", message: "Pay them directly, then attach the payment screenshot.")
            Spacer().frame(height: 10)
        }
    }
}

private struct AttachRow: View {
    let onClick: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        Button(action: onClick) {
            HStack(spacing: 12) {
                CrIcon("ic_add_photo_alternate", size: 26, tint: CrichereColor.primary)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Attach payment screenshot").crichere(.sans(13.5, .semiBold, lineHeight: 13.5), CrichereColor.primary)
                    Text("Required").crichere(.sans(11.5, lineHeight: 11.5), CrichereColor.onSurfaceVariant)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, minHeight: 64, maxHeight: 64)
            .background(Color(hex: 0xFAFBF8), in: shape)
            .overlay(shape.strokeBorder(dashedOutline, style: StrokeStyle(lineWidth: 2, dash: [6, 4])))
        }
        .buttonStyle(PressHighlight(shape: shape))
    }
}

/// Design G3: compact attached-screenshot row (also uploading / failed).
private struct ProofRow: View {
    let proof: ProofState
    let thumbnail: UIImage?
    let locked: Bool
    let actions: PaymentFlowActions

    var body: some View {
        HStack(spacing: 0) {
            thumb
            Spacer().frame(width: 12)
            VStack(alignment: .leading, spacing: 0) { details }
                .frame(maxWidth: .infinity, alignment: .leading)
            if !locked { buttons }
        }
        .padding(8)
        .frame(maxWidth: .infinity, minHeight: 90, maxHeight: 90)
        .background(Color(hex: 0xF1F4EE), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var thumb: some View {
        ZStack {
            Color.white
            if let thumbnail { Image(uiImage: thumbnail).resizable().scaledToFill().frame(width: 42, height: 74) }
            switch proof {
            case let .uploading(progress, _):
                Color(hex: 0x0E1A11, opacity: 0x8C / 255.0)
                Text("\(Int(progress * 100))%").crichere(.mono(10, .bold), .white)
            case .failed:
                Color(hex: 0x8C1D12, opacity: 0x8C / 255.0)
                CrIcon("ic_error", size: 20, tint: .white)
            default:
                EmptyView()
            }
        }
        .frame(width: 42, height: 74)
        .clipped()
    }

    @ViewBuilder private var details: some View {
        switch proof {
        case let .uploading(progress, _):
            Text("Uploading…").crichere(.sans(13, .semiBold, lineHeight: 13), CrichereColor.onBackground)
            Spacer().frame(height: 8)
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    CrichereColor.surfaceVariant
                    CrichereColor.primary.frame(width: proxy.size.width * min(max(progress, 0), 1))
                }
            }
            .frame(height: 4)
            .clipShape(RoundedRectangle(cornerRadius: 2))
        case .failed:
            Text("Upload failed").crichere(.sans(13, .semiBold, lineHeight: 13), CrichereColor.errorStrong)
            Spacer().frame(height: 6)
            Text("Check your connection.").crichere(.sans(11.5, lineHeight: 11.5), CrichereColor.onSurfaceVariant)
        case let .attached(caption):
            HStack(spacing: 4) {
                CrIcon("ic_check_circle_filled", size: 16, tint: CrichereColor.primary)
                Text("Screenshot attached").crichere(.sans(13, .semiBold, lineHeight: 13), CrichereColor.primary)
            }
            if !caption.isEmpty {
                Spacer().frame(height: 5)
                Text(caption).crichere(.mono(11.5, .medium, lineHeight: 11.5), CrichereColor.onSurfaceVariant)
            }
        case .empty:
            EmptyView()
        }
    }

    @ViewBuilder private var buttons: some View {
        switch proof {
        case .uploading:
            RoundIcon(icon: "ic_close", label: "Cancel upload", tint: CrichereColor.onSurfaceVariant, action: actions.onCancelUpload)
        case .failed:
            HStack(spacing: 12) {
                RoundIcon(icon: "ic_refresh", label: "Retry upload", tint: CrichereColor.primary, action: actions.onRetryUpload)
                RoundIcon(icon: "ic_delete", label: "Remove screenshot", tint: CrichereColor.errorStrong, action: actions.onRemove)
            }
        case .attached:
            HStack(spacing: 12) {
                RoundIcon(icon: "ic_swap_horiz", label: "Replace screenshot", tint: CrichereColor.primary, action: actions.onChoose)
                RoundIcon(icon: "ic_delete", label: "Remove screenshot", tint: CrichereColor.errorStrong, action: actions.onRemove)
            }
        case .empty:
            EmptyView()
        }
    }
}

private struct RoundIcon: View {
    let icon: String
    let label: String
    let tint: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            CrIcon(icon, size: 20, tint: tint).frame(width: 38, height: 38)
        }
        .buttonStyle(.pressCircle)
        .accessibilityLabel(label)
    }
}

/// Design G4: free franchise -- no payment step.
private struct FreeFranchiseNote: View {
    var body: some View {
        HStack(spacing: 12) {
            CrIcon("ic_celebration", size: 26, tint: CrichereColor.primary)
            VStack(alignment: .leading, spacing: 3) {
                Text("No franchise fee").crichere(.sans(14.5, .bold, lineHeight: 15.95), CrichereColor.onPrimaryContainer)
                Text("Your franchise is added as soon as you claim it.").crichere(.sans(12.5, lineHeight: 16.25), Color(hex: 0x2E4A31))
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(CrichereColor.primaryContainer, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// Bottom bar: the claim-failure banner (G6) above the Claim button.
private struct ClaimBar: View {
    let state: ClaimFranchiseState
    let league: LeagueDto
    let onClaim: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(CrichereColor.outlineVariant).frame(height: 1)
            VStack(spacing: 0) {
                if let message = state.errorMessage {
                    HStack(alignment: .top, spacing: 10) {
                        CrIcon("ic_error", size: 20, tint: CrichereColor.errorStrong)
                        VStack(alignment: .leading, spacing: 2) {
                            if let title = state.errorTitle {
                                Text(title).crichere(.sans(13, .semiBold, lineHeight: 17.55), CrichereColor.errorStrong)
                            }
                            Text(message).crichere(.sans(12, lineHeight: 16.8), CrichereColor.errorStrong)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(12)
                    .background(CrichereColor.errorContainer, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    Spacer().frame(height: 10)
                }
                CricherePrimaryButton(
                    "Claim Franchise",
                    enabled: !state.name.trimmingCharacters(in: .whitespaces).isEmpty && !state.isUploadingLogo
                        && (league.franchiseFee == nil || state.screenshotUrl != nil),
                    loading: state.isSubmitting,
                    loadingText: "Claiming…",
                    height: 50,
                    action: onClaim
                )
            }
            .padding(.horizontal, 19)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
    }
}

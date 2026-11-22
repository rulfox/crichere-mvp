import SwiftUI
import Shared
import PhotosUI

/// Mirrors `JoinLeagueViewModel`'s shared `StateFlow<JoinLeagueState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents.
@MainActor
final class JoinLeagueViewModelWrapper: ObservableObject {
    @Published var state: JoinLeagueState

    private let viewModel: JoinLeagueViewModel

    init(leagueId: String) {
        let viewModel = KoinHelper().joinLeagueViewModel(leagueId: leagueId)
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func retry() { viewModel.retry() }
    func submit() { viewModel.submit() }
    func uploadScreenshot(data: Data, fileName: String) {
        viewModel.uploadScreenshot(bytes: toKotlinByteArray(data), contentType: "image/jpeg", filename: fileName)
    }
    func retryUpload() { viewModel.retryUpload() }
    func cancelUpload() { viewModel.cancelUpload() }
    func removeScreenshot() { viewModel.removeScreenshot() }
}


/// Join as player (design board screen F, androidApp `ui/JoinLeagueScreen.kt`): fee + "Pay via UPI" (with
/// the no-UPI-app and no-UPI-ID fallbacks), the payment screenshot with a tick-the-checklist preview and
/// upload progress, the free-league summary (F6), and the refunds disclaimer.
struct JoinLeagueView: View {
    let leagueId: String
    let onDone: () -> Void
    var onCancel: (() -> Void)? = nil

    @StateObject private var wrapper: JoinLeagueViewModelWrapper
    @State private var pickerShown = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var picked: PickedPhoto?
    @State private var thumbnail: UIImage?
    @State private var noUpiApp = false
    @State private var copied = false
    @State private var snack: Snack?

    init(leagueId: String, onDone: @escaping () -> Void, onCancel: (() -> Void)? = nil) {
        self.leagueId = leagueId
        self.onDone = onDone
        self.onCancel = onCancel
        _wrapper = StateObject(wrappedValue: JoinLeagueViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        JoinLeagueContent(
            state: wrapper.state,
            noUpiApp: noUpiApp,
            copied: copied,
            thumbnail: thumbnail,
            actions: PaymentFlowActions(
                onClose: onCancel ?? onDone,
                onRetryLoad: wrapper.retry,
                onPayViaUpi: payViaUpi,
                onCopyUpiId: {
                    if let upi = wrapper.state.league?.organizerUpiId { copyToClipboard(upi) }
                    copied = true
                    snack = Snack(message: "UPI ID copied", durationSeconds: 3)
                },
                onChoose: { pickerShown = true },
                onCancelUpload: { wrapper.cancelUpload(); thumbnail = nil },
                onRetryUpload: wrapper.retryUpload,
                onRemove: { wrapper.removeScreenshot(); thumbnail = nil },
                onSubmit: wrapper.submit
            )
        )
        .crichereScreen()
        // Android places "UPI ID copied" 100 pt up, clear of the Join bar.
        .overlay(alignment: .bottom) {
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
        .animation(.easeOut(duration: 0.2), value: snack)
        .onAppear { wrapper.retry() }
        .onChange(of: wrapper.state.joined) { _, joined in if joined { onDone() } }
        .photosPicker(isPresented: $pickerShown, selection: $pickerItem, matching: .images)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            pickerItem = nil
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self) else { return }
                picked = decodePickedPhoto(data, fileName: "payment-proof.jpg")
            }
        }
        .crichereSheet(item: $picked) { photo in
            let league = wrapper.state.league
            ScreenshotCheckSheet(
                image: photo.image,
                checklist: [league?.playerFee.map { "Amount \(rupees($0.doubleValue))" }, league?.organizerUpiId.map { "UPI ID \($0)" }, "Reference / UTR no."]
                    .compactMap { $0 },
                onDismiss: { picked = nil },
                onChooseAnother: { picked = nil; pickerShown = true },
                onUse: {
                    picked = nil
                    thumbnail = photo.image
                    if let jpeg = photo.image.jpegData(compressionQuality: 0.9) { wrapper.uploadScreenshot(data: jpeg, fileName: photo.fileName) }
                }
            )
        }
    }

    private func payViaUpi() {
        guard let league = wrapper.state.league, let upi = league.organizerUpiId, let fee = league.playerFee?.doubleValue else { return }
        launchUpiPayment(upiId: upi, payeeName: league.name, amount: fee, note: "Player fee for \(league.name)") { opened in noUpiApp = !opened }
    }
}

/// What the pay-and-attach flows (Join F, Claim G) do.
struct PaymentFlowActions {
    var onClose: () -> Void = {}
    var onRetryLoad: () -> Void = {}
    var onPayViaUpi: () -> Void = {}
    var onCopyUpiId: () -> Void = {}
    var onChoose: () -> Void = {}
    var onCancelUpload: () -> Void = {}
    var onRetryUpload: () -> Void = {}
    var onRemove: () -> Void = {}
    var onSubmit: () -> Void = {}
}

/// The screen, stateless for the DEBUG parity gallery.
struct JoinLeagueContent: View {
    let state: JoinLeagueState
    var noUpiApp: Bool = false
    var copied: Bool = false
    var thumbnail: UIImage? = nil
    var actions = PaymentFlowActions()

    var body: some View {
        VStack(spacing: 0) {
            FlowTopBar(title: "Join as player", onClose: actions.onClose)
            if let league = state.league {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        if let fee = league.playerFee?.doubleValue {
                            LeagueSummaryRow(id: league.id, name: league.name, logoUrl: league.logoUrl, startsOn: league.startsOn,
                                             format: league.format, logoRemoved: league.logoRemoved)
                            Spacer().frame(height: 12)
                            PayToCard(feeLabel: "Player fee", fee: fee, upiId: league.organizerUpiId, noUpiApp: noUpiApp, copied: copied,
                                      onPayViaUpi: actions.onPayViaUpi, onCopyUpiId: actions.onCopyUpiId)
                            Spacer().frame(height: 12)
                            PaymentProofCard(state: proofState, thumbnail: thumbnail, onChoose: actions.onChoose, onCancel: actions.onCancelUpload,
                                             onRetry: actions.onRetryUpload, onRemove: actions.onRemove, onView: {})
                            Spacer().frame(height: 12)
                            Text("Crichere doesn't process payment or handle refunds/disputes -- that's between you and the organizer directly.")
                                .crichere(.sans(11.5, lineHeight: 16.675), CrichereColor.onSurfaceVariant)
                        } else {
                            FreeLeagueSummary(league: league, state: state)
                        }
                    }
                    .padding(.horizontal, 19)
                    .padding(.top, 6)
                    .padding(.bottom, 20)
                }
                PaymentFlowBar(errorTitle: state.errorTitle, errorMessage: state.errorMessage, label: "Join", loadingLabel: "Joining…",
                               enabled: league.playerFee == nil || state.screenshotUrl != nil, loading: state.isSubmitting, action: actions.onSubmit)
            } else if state.loadFailed {
                LoadErrorView(onRetry: actions.onRetryLoad)
            } else {
                CrichereSpinner(color: CrichereColor.primary, lineWidth: 4, size: 40).frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(CrichereColor.background.ignoresSafeArea())
    }

    private var proofState: ProofState {
        let caption = fileCaption(state.uploadFileName, state.uploadSizeBytes?.int64Value)
        if state.isUploadingScreenshot { return .uploading(progress: Double(state.uploadProgress * 100) / 100, caption: caption) }
        if state.uploadFailed { return .failed }
        if state.screenshotUrl != nil { return .attached(caption: caption) }
        return .empty
    }
}

/// Design F6: free league -- no payment step, just who's joining.
private struct FreeLeagueSummary: View {
    let league: LeagueDto
    let state: JoinLeagueState

    var body: some View {
        let role = [state.viewerRole?.label, state.viewerBatting.map { $0 == .rightHand ? "Right-hand" : "Left-hand" }].compactMap { $0 }.joined(separator: " · ")
        let card = RoundedRectangle(cornerRadius: 12, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            Text(league.name).crichere(.archivo(18, .bold), CrichereColor.onBackground)
            Spacer().frame(height: 14)
            HStack(spacing: 12) {
                CrIcon("ic_celebration", size: 26, tint: CrichereColor.primary)
                VStack(alignment: .leading, spacing: 3) {
                    Text("No entry fee").crichere(.sans(14.5, .semiBold), CrichereColor.onPrimaryContainer)
                    Text("You'll be added straight to the player pool.").crichere(.sans(12.5, lineHeight: 16.25), Color(hex: 0x2E4A31))
                }
                Spacer(minLength: 0)
            }
            .padding(14)
            .background(CrichereColor.primaryContainer, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            Spacer().frame(height: 14)
            VStack(spacing: 0) {
                summaryRow("Joining as", state.viewerName ?? "You")
                Rectangle().fill(CrichereColor.surfaceVariant).frame(height: 1)
                summaryRow("Role", role.isEmpty ? "Not set" : role)
            }
            .background(CrichereColor.surface, in: card)
            .overlay(card.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
        }
    }

    private func summaryRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).crichere(.sans(13.5), CrichereColor.onSurfaceVariant).frame(maxWidth: .infinity, alignment: .leading)
            Text(value).crichere(.sans(13.5), CrichereColor.onBackground)
        }
        .padding(.horizontal, 14)
        .frame(height: 40)
    }
}

/// Bottom bar of the pay flows: hairline, the failure banner (F7 / G6), then the main pill.
struct PaymentFlowBar: View {
    let errorTitle: String?
    let errorMessage: String?
    let label: String
    let loadingLabel: String
    let enabled: Bool
    let loading: Bool
    let action: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(CrichereColor.outlineVariant).frame(height: 1)
            VStack(spacing: 0) {
                if let errorMessage {
                    HStack(alignment: .top, spacing: 10) {
                        CrIcon("ic_error", size: 18, tint: CrichereColor.error).padding(.top, 1)
                        VStack(alignment: .leading, spacing: 3) {
                            if let errorTitle { Text(errorTitle).crichere(.sans(13.5, .semiBold), CrichereColor.errorStrong) }
                            Text(errorMessage).crichere(.sans(12.5, lineHeight: 16.25), CrichereColor.errorStrong)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(12)
                    .background(CrichereColor.errorContainer, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    Spacer().frame(height: 10)
                }
                CricherePrimaryButton(label, enabled: enabled, loading: loading, loadingText: loadingLabel, action: action)
            }
            .padding(.horizontal, 19)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
    }
}

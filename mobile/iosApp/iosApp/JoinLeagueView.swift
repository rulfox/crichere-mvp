import SwiftUI
import Shared
import PhotosUI

/// Mirrors `JoinLeagueViewModel`'s shared `StateFlow<JoinLeagueState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents. **Authored but unverified, not wired into any
/// navigation** -- see docs/PHASE2.md Section 5 / docs/PHASE3.md.
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
    func uploadScreenshot(data: Data) {
        viewModel.uploadScreenshot(bytes: toKotlinByteArray(data), contentType: "image/jpeg", filename: "payment-proof.jpg")
    }
}

/// iOS equivalent of `androidApp/.../ui/JoinLeagueScreen.kt`: fee/UPI display, screenshot attach
/// (no "Pay via UPI" deep-link button here -- that's Android-specific `upi://pay` intent chrome
/// with no iOS equivalent app ecosystem to target the same way), refunds/disputes disclaimer,
/// submit. Screenshot-only proof, no UPI auto-capture (see docs/PHASE3.md's Decisions Made).
struct JoinLeagueView: View {
    let leagueId: String
    let onDone: () -> Void

    @StateObject private var wrapper: JoinLeagueViewModelWrapper
    @State private var pickerItem: PhotosPickerItem?

    init(leagueId: String, onDone: @escaping () -> Void) {
        self.leagueId = leagueId
        self.onDone = onDone
        _wrapper = StateObject(wrappedValue: JoinLeagueViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        Group {
            if wrapper.state.isLoading {
                ProgressView()
            } else if let league = wrapper.state.league {
                Form {
                    Text("Join \(league.name) as a Player").font(.headline)

                    if let fee = league.playerFee {
                        Text("Player fee: \(fee)")
                        if let upiId = league.organizerUpiId {
                            Text("Pay to: \(upiId)")
                        }
                        PhotosPicker("Attach payment screenshot", selection: $pickerItem, matching: .images)
                        if wrapper.state.screenshotUrl != nil {
                            Text("Screenshot attached")
                        }
                        Text("Crichere doesn't process payment or handle refunds/disputes -- that's between you and the organizer directly.")
                            .font(.caption)
                    }

                    if let error = wrapper.state.errorMessage {
                        Text(error).foregroundColor(.red)
                    }

                    Button(wrapper.state.isSubmitting ? "Joining..." : "Join") { wrapper.submit() }
                        .disabled(wrapper.state.isSubmitting)
                }
            } else {
                Text(wrapper.state.errorMessage ?? "Couldn't load this league.").foregroundColor(.red)
            }
        }
        .onAppear { wrapper.retry() }
        .onChange(of: wrapper.state.joined) { _, joined in if joined { onDone() } }
        .task(id: pickerItem) {
            if let item = pickerItem, let data = try? await item.loadTransferable(type: Data.self) {
                wrapper.uploadScreenshot(data: data)
            }
        }
    }
}

import SwiftUI
import Shared
import PhotosUI

/// Mirrors `ClaimFranchiseViewModel`'s shared `StateFlow<ClaimFranchiseState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents. **Authored but unverified, not wired into any
/// navigation** -- see docs/PHASE2.md Section 5 / docs/PHASE3.md.
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
    func uploadScreenshot(data: Data) {
        viewModel.uploadScreenshot(bytes: toKotlinByteArray(data), contentType: "image/jpeg", filename: "payment-proof.jpg")
    }
}

/// iOS equivalent of `androidApp/.../ui/ClaimFranchiseScreen.kt`: required name field, optional
/// logo, fee/screenshot handling same shape as [JoinLeagueView].
struct ClaimFranchiseView: View {
    let leagueId: String
    let onDone: () -> Void

    @StateObject private var wrapper: ClaimFranchiseViewModelWrapper
    @State private var logoPickerItem: PhotosPickerItem?
    @State private var screenshotPickerItem: PhotosPickerItem?

    init(leagueId: String, onDone: @escaping () -> Void) {
        self.leagueId = leagueId
        self.onDone = onDone
        _wrapper = StateObject(wrappedValue: ClaimFranchiseViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        Group {
            if wrapper.state.isLoading {
                ProgressView()
            } else if let league = wrapper.state.league {
                Form {
                    Text("Claim a Franchise in \(league.name)").font(.headline)

                    TextField("Franchise name", text: Binding(
                        get: { wrapper.state.name },
                        set: { wrapper.onNameChanged($0) }
                    ))

                    PhotosPicker(wrapper.state.logoUrl != nil ? "Logo selected" : "Choose a logo (optional)", selection: $logoPickerItem, matching: .images)

                    if let fee = league.franchiseFee {
                        Text("Franchise fee: \(fee)")
                        if let upiId = league.organizerUpiId {
                            Text("Pay to: \(upiId)")
                        }
                        PhotosPicker("Attach payment screenshot", selection: $screenshotPickerItem, matching: .images)
                        if wrapper.state.screenshotUrl != nil {
                            Text("Screenshot attached")
                        }
                        Text("Crichere doesn't process payment or handle refunds/disputes -- that's between you and the organizer directly.")
                            .font(.caption)
                    }

                    if let error = wrapper.state.errorMessage {
                        Text(error).foregroundColor(.red)
                    }

                    Button(wrapper.state.isSubmitting ? "Claiming..." : "Claim Franchise") { wrapper.submit() }
                        .disabled(wrapper.state.isSubmitting)
                }
            } else {
                Text(wrapper.state.errorMessage ?? "Couldn't load this league.").foregroundColor(.red)
            }
        }
        .onAppear { wrapper.retry() }
        .onChange(of: wrapper.state.claimed) { _, claimed in if claimed { onDone() } }
        .task(id: logoPickerItem) {
            if let item = logoPickerItem, let data = try? await item.loadTransferable(type: Data.self) {
                wrapper.uploadLogo(data: data)
            }
        }
        .task(id: screenshotPickerItem) {
            if let item = screenshotPickerItem, let data = try? await item.loadTransferable(type: Data.self) {
                wrapper.uploadScreenshot(data: data)
            }
        }
    }
}

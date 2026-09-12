import SwiftUI
import Shared

/// Mirrors `AuctionSettingsViewModel`'s shared `StateFlow<AuctionSettingsState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents. **Authored but unverified, not wired into any
/// navigation** -- see docs/PHASE2.md Section 5 / docs/PHASE4.md.
@MainActor
final class AuctionSettingsViewModelWrapper: ObservableObject {
    @Published var state: AuctionSettingsState

    private let viewModel: AuctionSettingsViewModel

    init(leagueId: String) {
        let viewModel = KoinHelper().auctionSettingsViewModel(leagueId: leagueId)
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func retry() { viewModel.retry() }
    func onBasePriceChanged(_ value: String) { viewModel.onBasePriceChanged(value: value) }
    func onPurseChanged(_ value: String) { viewModel.onPurseChanged(value: value) }
    func onSquadMinChanged(_ value: String) { viewModel.onSquadMinChanged(value: value) }
    func onSquadMaxChanged(_ value: String) { viewModel.onSquadMaxChanged(value: value) }
    func onBidIncrementChanged(_ value: String) { viewModel.onBidIncrementChanged(value: value) }
    func submit() { viewModel.submit() }
}

/// iOS equivalent of `androidApp/.../ui/AuctionSettingsScreen.kt`: five editable fields plus a
/// read-only auction pool/purse view rendered from the same loaded league -- no second fetch.
struct AuctionSettingsView: View {
    let leagueId: String

    @StateObject private var wrapper: AuctionSettingsViewModelWrapper

    init(leagueId: String) {
        self.leagueId = leagueId
        _wrapper = StateObject(wrappedValue: AuctionSettingsViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        Group {
            if wrapper.state.isLoading {
                ProgressView()
            } else if let league = wrapper.state.league {
                Form {
                    Section("Settings") {
                        TextField("Base price", text: Binding(get: { wrapper.state.basePrice }, set: { wrapper.onBasePriceChanged($0) }))
                        TextField("Purse per franchise", text: Binding(get: { wrapper.state.purse }, set: { wrapper.onPurseChanged($0) }))
                        TextField("Squad size (min)", text: Binding(get: { wrapper.state.squadMin }, set: { wrapper.onSquadMinChanged($0) }))
                        TextField("Squad size (max)", text: Binding(get: { wrapper.state.squadMax }, set: { wrapper.onSquadMaxChanged($0) }))
                        TextField("Bid increment", text: Binding(get: { wrapper.state.bidIncrement }, set: { wrapper.onBidIncrementChanged($0) }))
                    }

                    if league.auctionSquadMaxWarning {
                        Text("This may be impossible to satisfy: squad max times the number of franchises required exceeds players required.")
                            .foregroundColor(.red)
                    }

                    if let error = wrapper.state.errorMessage {
                        Text(error).foregroundColor(.red)
                    }

                    Button(wrapper.state.isSaving ? "Saving..." : "Save") { wrapper.submit() }
                        .disabled(wrapper.state.isSaving)

                    Section("Auction pool") {
                        Text("\(league.players.count) player(s) joined")
                        ForEach(league.franchises, id: \.id) { franchise in
                            Text(franchise.name)
                        }
                    }
                }
            } else {
                Text(wrapper.state.errorMessage ?? "Couldn't load this league.").foregroundColor(.red)
            }
        }
        .navigationTitle("Auction Settings")
        .onAppear { wrapper.retry() }
    }
}

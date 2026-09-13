import SwiftUI
import Shared

/// Mirrors `AuctionViewModel`'s shared `StateFlow<AuctionState>` -- same pattern
/// `AuctionSettingsViewModelWrapper` documents. **Authored but unverified, not wired into any
/// navigation** -- see docs/PHASE2.md Section 5 / docs/PHASE5.md. Real-time SSE consumption on
/// iOS is untested in this environment (no device/simulator here); this view is authored for
/// compile-time parity only, matching this project's standing iOS posture.
@MainActor
final class AuctionViewModelWrapper: ObservableObject {
    @Published var state: AuctionState

    private let viewModel: AuctionViewModel

    init(leagueId: String) {
        let viewModel = KoinHelper().auctionViewModel(leagueId: leagueId)
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func retry() { viewModel.retry() }
    func onBidAmountChanged(_ value: String) { viewModel.onBidAmountChanged(value: value) }
    func placeBid() { viewModel.placeBid() }
    func start() { viewModel.start() }
    func nextPlayer() { viewModel.nextPlayer() }
    func sold() { viewModel.sold() }
    func unsold() { viewModel.unsold() }
    func undo() { viewModel.undo() }
    func end() { viewModel.end() }
    func toggleExceedPurse(_ allow: Bool) { viewModel.toggleExceedPurse(allow: allow) }
}

/// iOS equivalent of `androidApp/.../ui/AuctionLiveScreen.kt`: organizer controls and a bidding
/// form for the caller's own franchise render independently (dual roles are allowed, see
/// docs/PHASE3.md), driven entirely by the ViewModel's own SSE subscription -- no manual refresh.
struct AuctionLiveView: View {
    let leagueId: String

    @StateObject private var wrapper: AuctionViewModelWrapper

    init(leagueId: String) {
        self.leagueId = leagueId
        _wrapper = StateObject(wrappedValue: AuctionViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        Group {
            if wrapper.state.isLoading {
                ProgressView()
            } else if wrapper.state.league != nil {
                Form {
                    Section("Status") {
                        Text("\(wrapper.state.auction?.auctionStatus ?? AuctionStatus.notStarted)")
                        if let auction = wrapper.state.auction, auction.currentPlayerId != nil {
                            Text(auction.currentPlayerName ?? "Current player")
                            Text("Current bid: \(auction.currentBidAmount.map { "\($0)" } ?? "none yet")")
                        }
                    }

                    if wrapper.state.myFranchiseId != nil, wrapper.state.auction?.currentPlayerId != nil {
                        Section("Your bid") {
                            TextField("Amount", text: Binding(get: { wrapper.state.bidAmountInput }, set: { wrapper.onBidAmountChanged($0) }))
                            Button(wrapper.state.isBidding ? "Placing..." : "Place Bid") { wrapper.placeBid() }
                                .disabled(wrapper.state.isBidding)
                        }
                    }

                    if wrapper.state.isOrganizer {
                        Section("Organizer controls") {
                            switch wrapper.state.auction?.auctionStatus ?? AuctionStatus.notStarted {
                            case AuctionStatus.notStarted:
                                Button("Start Auction") { wrapper.start() }.disabled(wrapper.state.isActing)
                            case AuctionStatus.inProgress:
                                Button("Next Player") { wrapper.nextPlayer() }.disabled(wrapper.state.isActing)
                                Button("Sold") { wrapper.sold() }.disabled(wrapper.state.isActing)
                                Button("Unsold") { wrapper.unsold() }.disabled(wrapper.state.isActing)
                                Button("Undo") { wrapper.undo() }.disabled(wrapper.state.isActing)
                                Button("End Auction") { wrapper.end() }.disabled(wrapper.state.isActing)
                                Toggle(
                                    "Allow exceeding purse",
                                    isOn: Binding(get: { wrapper.state.auction?.allowExceedPurse ?? false }, set: { wrapper.toggleExceedPurse($0) }),
                                )
                                .disabled(wrapper.state.isActing)
                            default:
                                Text("This auction has ended.")
                            }
                        }
                    }

                    if let results = wrapper.state.results {
                        Section("Results") {
                            ForEach(results.franchises, id: \.franchiseId) { franchise in
                                VStack(alignment: .leading) {
                                    Text(franchise.franchiseName)
                                    Text("\(franchise.playersWon.count) player(s) -- spent \(franchise.purseSpent)")
                                    if franchise.belowSquadMin {
                                        Text("Below minimum squad size").foregroundColor(.red)
                                    }
                                }
                            }
                        }
                    }

                    if let error = wrapper.state.errorMessage {
                        Text(error).foregroundColor(.red)
                    }
                }
            } else {
                Text(wrapper.state.errorMessage ?? "Couldn't load this league.").foregroundColor(.red)
            }
        }
        .navigationTitle("Live Auction")
        .onAppear { wrapper.retry() }
    }
}

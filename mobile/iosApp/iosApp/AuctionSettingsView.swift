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
    /// `nil` clears it; otherwise stored as an ISO-8601 instant (docs/PHASE11.md D3).
    func onScheduledAtChanged(_ value: Date?) {
        viewModel.onScheduledAtChanged(value: value.map { ISO8601DateFormatter().string(from: $0) })
    }
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
                        field("Base price", wrapper.state.basePrice, .basePrice) { wrapper.onBasePriceChanged($0) }
                        field("Purse per franchise", wrapper.state.purse, .purse) { wrapper.onPurseChanged($0) }
                        field("Squad size (min)", wrapper.state.squadMin, .squadMin) { wrapper.onSquadMinChanged($0) }
                        field("Squad size (max)", wrapper.state.squadMax, .squadMax) { wrapper.onSquadMaxChanged($0) }
                        field("Bid increment", wrapper.state.bidIncrement, .bidIncrement) { wrapper.onBidIncrementChanged($0) }
                    }

                    Section("Auction date & time (optional)") {
                        if let scheduled = scheduledDate {
                            DatePicker("Bidding opens", selection: Binding(get: { scheduled }, set: { wrapper.onScheduledAtChanged($0) }))
                            Button("Clear", role: .destructive) { wrapper.onScheduledAtChanged(nil) }
                        } else {
                            Button("Set a time") { wrapper.onScheduledAtChanged(Date()) }
                        }
                    }

                    if let warning = wrapper.state.squadWarning {
                        Text("Squad max × franchises (\(warning.squadMax) × \(warning.franchises) = \(warning.total)) is more than players required (\(warning.playersRequired)). Some squads may not fill.")
                            .foregroundColor(.orange)
                    }

                    if let error = wrapper.state.saveError {
                        Text(error.message).foregroundColor(.red)
                    } else if wrapper.state.showSavedNotice {
                        Text("Auction settings saved")
                    }

                    Button(wrapper.state.isSaving ? "Saving…" : "Save") { wrapper.submit() }
                        .disabled(!wrapper.state.canSave)

                    Section("Auction pool") {
                        Text("\(league.players.count) player(s) joined")
                        if league.franchises.isEmpty {
                            Text("No franchises yet. They appear here once owners claim them.")
                        }
                        ForEach(league.franchises, id: \.id) { franchise in
                            Text(franchise.name)
                        }
                    }
                }
            } else {
                Text("Couldn't load auction settings. Check your connection and try again.").foregroundColor(.red)
            }
        }
        .navigationTitle("Auction Settings")
        .onAppear { wrapper.retry() }
    }

    private var scheduledDate: Date? {
        wrapper.state.scheduledAt.flatMap { ISO8601DateFormatter().date(from: $0) }
    }

    private func field(_ label: String, _ value: String, _ key: AuctionField, onChange: @escaping (String) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            TextField(label, text: Binding(get: { value }, set: onChange))
                .keyboardType(.decimalPad)
            if let error = wrapper.state.errorFor(field: key) {
                Text(error).font(.caption).foregroundColor(.red)
            }
        }
    }
}

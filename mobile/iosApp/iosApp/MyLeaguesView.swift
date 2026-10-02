import SwiftUI
import Shared

/// Mirrors `MyLeaguesViewModel`'s shared `StateFlow<MyLeaguesState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents. **Authored but unverified, not wired into any
/// navigation** -- see docs/PHASE2.md Section 5 / docs/PHASE3.md.
@MainActor
final class MyLeaguesViewModelWrapper: ObservableObject {
    @Published var state: MyLeaguesState

    private let viewModel: MyLeaguesViewModel

    init() {
        let viewModel = KoinHelper().myLeaguesViewModel
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func retry() { viewModel.retry() }
}

/// iOS equivalent of `androidApp/.../ui/MyLeaguesScreen.kt`: four sections -- Organizing / Playing
/// / Franchise owner / Following -- see docs/PHASE3.md's Screens section.
struct MyLeaguesView: View {
    let onOpenLeague: (String) -> Void

    @StateObject private var wrapper = MyLeaguesViewModelWrapper()

    var body: some View {
        Group {
            if wrapper.state.isLoading {
                ProgressView()
            } else if let data = wrapper.state.data, data.organizing.isEmpty, data.playing.isEmpty, data.franchiseOwner.isEmpty, data.following.isEmpty {
                // Design M2 (app fix: was a blank list).
                VStack(spacing: 10) {
                    Text("No leagues yet").font(.headline)
                    Text("Join one as a player, claim a franchise, follow one, or start your own.")
                        .font(.footnote).multilineTextAlignment(.center).foregroundColor(.secondary)
                }
                .padding(40)
            } else if let data = wrapper.state.data {
                List {
                    section("Organizing", data.organizing)
                    section("Playing", data.playing)
                    section("Franchise owner", data.franchiseOwner)
                    section("Following", data.following)
                }
            } else {
                VStack {
                    Text(wrapper.state.errorMessage ?? "Couldn't load your leagues.").foregroundColor(.red)
                    Button("Retry") { wrapper.retry() }
                }
            }
        }
        .navigationTitle("My Leagues")
        .onAppear { wrapper.retry() }
    }

    @ViewBuilder
    private func section(_ title: String, _ leagues: [LeagueSummaryDto]) -> some View {
        if !leagues.isEmpty {
            Section(title) {
                ForEach(leagues, id: \.id) { league in
                    Button {
                        onOpenLeague(league.id)
                    } label: {
                        VStack(alignment: .leading) {
                            Text(league.name)
                            Text("\(league.city), \(league.state) -- \(league.startsOn)").font(.caption)
                        }
                    }
                }
            }
        }
    }
}

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
                        // U4 M5: the name may take 2 lines; only the place truncates, never the date.
                        VStack(alignment: .leading, spacing: 4) {
                            Text(league.name).lineLimit(2)
                            HStack(spacing: 0) {
                                Text("\(league.city), \(league.state)").lineLimit(1).truncationMode(.tail)
                                Text(" · \(rowDate(league.startsOn))").lineLimit(1).fixedSize()
                            }
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .accessibilityElement(children: .combine)
                        }
                    }
                }
            }
        }
    }
}

/// "2026-10-10" -> "10 Oct 2026" in the device locale (U4 D1), or the raw string if it isn't an ISO date.
private func rowDate(_ iso: String) -> String {
    let parser = DateFormatter()
    parser.locale = Locale(identifier: "en_US_POSIX")
    parser.dateFormat = "yyyy-MM-dd"
    guard let date = parser.date(from: iso) else { return iso }
    let formatter = DateFormatter()
    formatter.setLocalizedDateFormatFromTemplate("d MMM yyyy")
    return formatter.string(from: date)
}

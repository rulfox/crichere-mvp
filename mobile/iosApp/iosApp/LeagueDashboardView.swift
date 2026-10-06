import SwiftUI
import Shared

/// Mirrors `LeagueDashboardViewModel`'s shared `StateFlow<LeagueDashboardState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents. **Authored but unverified** -- see docs/PHASE9.md.
@MainActor
final class LeagueDashboardViewModelWrapper: ObservableObject {
    @Published var state: LeagueDashboardState

    private let viewModel: LeagueDashboardViewModel

    init() {
        let viewModel = KoinHelper().leagueDashboardViewModel
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func refresh() { viewModel.refresh() }
    func onStateSelected(_ stateDto: StateDto) { viewModel.onStateSelected(stateDto: stateDto) }
    func onDistrictSelected(_ districtDto: DistrictDto) { viewModel.onDistrictSelected(districtDto: districtDto) }
    func onClearAreaFilters() { viewModel.onClearAreaFilters() }
    func onToggleNearMe() { viewModel.onToggleNearMe() }
}

/// iOS equivalent of `androidApp/.../ui/LeagueDashboardScreen.kt`: leagues filterable by
/// State/District or "nearest to me" (mutually exclusive, see docs/PHASE2.md's Decisions
/// Made), tap a row to open League Detail, "Create a league" button. The app's post-login landing
/// tab, hosted by `AppRootView`'s `MainTabView`.
struct LeagueDashboardView: View {
    let onOpenLeague: (String) -> Void
    let onCreateLeague: () -> Void

    @StateObject private var wrapper = LeagueDashboardViewModelWrapper()
    @StateObject private var locationPermission = LocationPermissionRequester()

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Leagues").font(.title2.bold()).padding(.horizontal)

            Button("Create a league", action: onCreateLeague)
                .padding(.horizontal)

            Form {
                Section {
                    Button(wrapper.state.isLocating ? "Finding your location..." : (wrapper.state.isNearMode ? "Showing leagues near you" : "Show leagues near me")) {
                        Task {
                            await locationPermission.requestWhenInUseAuthorization()
                            wrapper.onToggleNearMe()
                        }
                    }
                    .disabled(wrapper.state.isLocating)

                    if !wrapper.state.isNearMode {
                        Picker("State", selection: Binding(
                            get: { wrapper.state.states.first { $0.name == wrapper.state.selectedState } },
                            set: { newValue in if let newValue { wrapper.onStateSelected(newValue) } }
                        )) {
                            Text("Any").tag(Optional<StateDto>.none)
                            ForEach(wrapper.state.states, id: \.code) { stateDto in
                                Text(stateDto.name).tag(Optional(stateDto))
                            }
                        }

                        Picker("District", selection: Binding(
                            get: { wrapper.state.districts.first { $0.name == wrapper.state.selectedDistrict } },
                            set: { newValue in if let newValue { wrapper.onDistrictSelected(newValue) } }
                        )) {
                            Text("Any").tag(Optional<DistrictDto>.none)
                            ForEach(wrapper.state.districts, id: \.id) { districtDto in
                                Text(districtDto.name).tag(Optional(districtDto))
                            }
                        }
                        .disabled(wrapper.state.selectedState == nil)


                        if wrapper.state.selectedState != nil {
                            Button("Clear filters") { wrapper.onClearAreaFilters() }
                        }
                    }
                }

                if let error = wrapper.state.errorMessage {
                    Text(error).foregroundColor(.red)
                }

                if wrapper.state.isLoading {
                    ProgressView()
                } else if wrapper.state.leagues.isEmpty {
                    Text("No leagues found.")
                } else {
                    Section {
                        ForEach(wrapper.state.leagues, id: \.id) { league in
                            Button {
                                onOpenLeague(league.id)
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(league.name).font(.headline)
                                    Text("\(league.groundName) · \(league.district)")
                                        .font(.subheadline).foregroundColor(.secondary)
                                    Text("Starts \(league.startsOn)" + (league.format.map { " -- \($0)" } ?? ""))
                                        .font(.caption).foregroundColor(.secondary)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                Button("Refresh") { wrapper.refresh() }
            }
        }
        .onAppear { wrapper.refresh() }
    }
}

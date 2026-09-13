import SwiftUI
import Shared

/// Mirrors `ManageRolesViewModel`'s shared `StateFlow<ManageRolesState>` -- same pattern
/// `AuctionSettingsViewModelWrapper` documents. **Authored but unverified, not wired into any
/// navigation** -- see docs/PHASE2.md Section 5 / docs/PHASE7.md.
@MainActor
final class ManageRolesViewModelWrapper: ObservableObject {
    @Published var state: ManageRolesState

    private let viewModel: ManageRolesViewModel

    init(leagueId: String) {
        let viewModel = KoinHelper().manageRolesViewModel(leagueId: leagueId)
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func retry() { viewModel.retry() }
    func onPhoneNumberChanged(_ value: String) { viewModel.onPhoneNumberChanged(value: value) }
    func lookup() { viewModel.lookup() }
    func grant() { viewModel.grant() }
    func revoke(_ roleId: String) { viewModel.revoke(roleId: roleId) }
}

/// iOS equivalent of `androidApp/.../ui/ManageRolesScreen.kt`: look up another registered user by
/// phone number, confirm, then grant them full organizer authority over this league; below that,
/// the current co-organizers with a per-row revoke.
struct ManageRolesView: View {
    let leagueId: String

    @StateObject private var wrapper: ManageRolesViewModelWrapper
    @State private var showGrantConfirm = false

    init(leagueId: String) {
        self.leagueId = leagueId
        _wrapper = StateObject(wrappedValue: ManageRolesViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        Group {
            if wrapper.state.isLoading {
                ProgressView()
            } else if let league = wrapper.state.league {
                Form {
                    Section("Grant access") {
                        Text("A co-organizer can do everything you can do for this league.")
                            .font(.footnote)
                        TextField("Phone number", text: Binding(get: { wrapper.state.phoneNumberInput }, set: { wrapper.onPhoneNumberChanged($0) }))
                        Button(wrapper.state.isLookingUp ? "Looking up..." : "Look up") { wrapper.lookup() }
                            .disabled(wrapper.state.isLookingUp || wrapper.state.phoneNumberInput.isEmpty)

                        if wrapper.state.lookupAttempted {
                            if let result = wrapper.state.lookupResult {
                                Text("Found: \(result.name ?? "Unnamed user")")
                                Button(wrapper.state.isGranting ? "Granting..." : "Grant") { showGrantConfirm = true }
                                    .disabled(wrapper.state.isGranting)
                                    .confirmationDialog(
                                        "Grant co-organizer access to \(result.name ?? "this user")?",
                                        isPresented: $showGrantConfirm,
                                        titleVisibility: .visible,
                                    ) {
                                        Button("Grant", role: .destructive) { wrapper.grant() }
                                        Button("Cancel", role: .cancel) {}
                                    } message: {
                                        Text("They will be able to do everything you can do for this league, including editing it and running the auction.")
                                    }
                            } else {
                                Text("No user found with that phone number.").foregroundColor(.red)
                            }
                        }
                    }

                    if let error = wrapper.state.errorMessage {
                        Text(error).foregroundColor(.red)
                    }

                    Section("Current co-organizers") {
                        if league.coOrganizers.isEmpty {
                            Text("No co-organizers yet.")
                        } else {
                            ForEach(league.coOrganizers, id: \.id) { role in
                                HStack {
                                    Text(role.name ?? "Unnamed user")
                                    Spacer()
                                    Button(wrapper.state.revokingRoleIds.contains(role.id) ? "Revoking..." : "Revoke") {
                                        wrapper.revoke(role.id)
                                    }
                                    .disabled(wrapper.state.revokingRoleIds.contains(role.id))
                                }
                            }
                        }
                    }
                }
            } else {
                Text(wrapper.state.errorMessage ?? "Couldn't load this league.").foregroundColor(.red)
            }
        }
        .navigationTitle("Manage Co-Organizers")
        .onAppear { wrapper.retry() }
    }
}

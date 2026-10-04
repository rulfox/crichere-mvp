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
    /// The signed-in co-organizer removed themselves (design update #4 K10): leave, telling the league page which league.
    let onAccessRevoked: (String) -> Void

    @StateObject private var wrapper: ManageRolesViewModelWrapper
    @State private var showGrantConfirm = false
    @State private var pendingRevoke: LeagueRoleDto?
    @State private var pendingSelfRevoke: LeagueRoleDto?

    init(leagueId: String, onAccessRevoked: @escaping (String) -> Void = { _ in }) {
        self.leagueId = leagueId
        self.onAccessRevoked = onAccessRevoked
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
                        TextField("10-digit mobile number", text: Binding(get: { wrapper.state.phoneNumberInput }, set: { wrapper.onPhoneNumberChanged($0) }))
                        Button(wrapper.state.isLookingUp ? "Looking up..." : "Look up") { wrapper.lookup() }
                            .disabled(wrapper.state.isLookingUp || wrapper.state.phoneNumberInput.isEmpty)

                        if let lookupError = wrapper.state.lookupError {
                            Text(lookupError).foregroundColor(.red)
                        }
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
                        }
                    }

                    if let error = wrapper.state.grantError ?? wrapper.state.revokeError {
                        Text("\(error.title) \(error.message)").foregroundColor(.red)
                    }

                    Section("Current co-organizers") {
                        if league.coOrganizers.isEmpty {
                            Text("No co-organizers yet.")
                        } else {
                            ForEach(league.coOrganizers, id: \.id) { role in
                                let isMe = role.userId == wrapper.state.currentUserId
                                let revoking = wrapper.state.revokingRoleIds.contains(role.id)
                                HStack(spacing: 6) {
                                    Text(role.name ?? "Unnamed user").lineLimit(1)
                                    if isMe {
                                        // U4 K9: the signed-in user's own row.
                                        Text("You")
                                            .font(.system(size: 10.5, weight: .semibold))
                                            .foregroundColor(Color(red: 0x0B / 255, green: 0x2E / 255, blue: 0x10 / 255))
                                            .padding(.horizontal, 6)
                                            .frame(height: 18)
                                            .background(Color(red: 0xD7 / 255, green: 0xEB / 255, blue: 0xD2 / 255), in: Capsule())
                                    }
                                    Spacer()
                                    Button(revoking ? (isMe ? "Removing…" : "Revoking…") : "Revoke") {
                                        if isMe { pendingSelfRevoke = role } else { pendingRevoke = role }
                                    }
                                    .disabled(revoking)
                                }
                            }
                        }
                    }
                }
            } else {
                Text("Couldn't load this league. Check your connection and try again.").foregroundColor(.red)
            }
        }
        .alert(
            "Revoke \(pendingRevoke?.name ?? "this co-organizer")?",
            isPresented: Binding(get: { pendingRevoke != nil }, set: { if !$0 { pendingRevoke = nil } })
        ) {
            Button("Cancel", role: .cancel) { pendingRevoke = nil }
            Button("Revoke", role: .destructive) {
                if let role = pendingRevoke { wrapper.revoke(role.id) }
                pendingRevoke = nil
            }
        } message: {
            Text("They'll lose co-organizer access to this league right away.")
        }
        .alert(
            "Remove your own access?",
            isPresented: Binding(get: { pendingSelfRevoke != nil }, set: { if !$0 { pendingSelfRevoke = nil } })
        ) {
            Button("Cancel", role: .cancel) { pendingSelfRevoke = nil }
            Button("Remove me", role: .destructive) {
                if let role = pendingSelfRevoke { wrapper.revoke(role.id) }
                pendingSelfRevoke = nil
            }
        } message: {
            Text("You'll stop being a co-organizer of \(wrapper.state.league?.name.trimmingCharacters(in: .whitespaces) ?? "this league") right away. You won't be able to edit the league or run the auction. Only an organizer can add you back.")
        }
        .onChange(of: wrapper.state.accessRevoked) { _, revoked in
            if revoked { onAccessRevoked(wrapper.state.league?.name.trimmingCharacters(in: .whitespaces) ?? "") }
        }
        .navigationTitle("Manage Co-Organizers")
        .onAppear { wrapper.retry() }
    }
}

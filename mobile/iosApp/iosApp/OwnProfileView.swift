import SwiftUI
import Shared

/// Mirrors `OwnProfileViewModel`'s shared `StateFlow<OwnProfileState>` and `navigationEvents`
/// into SwiftUI-observable state -- same pattern `ProfileSetupViewModelWrapper` documents.
///
/// **Authored but unverified** -- see task-7-report.md.
@MainActor
final class OwnProfileViewModelWrapper: ObservableObject {
    @Published var state: OwnProfileState
    @Published var navigationEvent: OwnProfileNavigationEvent?

    private let viewModel: OwnProfileViewModel

    init() {
        let viewModel = KoinHelper().ownProfileViewModel
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
        Task { [weak self] in
            for await event in viewModel.navigationEvents {
                self?.navigationEvent = event
            }
        }
    }

    func editProfile() { viewModel.editProfile() }
    func logout() { viewModel.logout() }
}

/// iOS equivalent of `androidApp/.../ui/OwnProfileScreen.kt`: the signed-in user's complete
/// cricket-player profile, with "edit" and "logout" actions.
struct OwnProfileView: View {
    let onNavigateToEditProfile: () -> Void
    let onNavigateToPhoneEntry: () -> Void

    @StateObject private var wrapper = OwnProfileViewModelWrapper()

    var body: some View {
        Group {
            if wrapper.state.isLoading {
                ProgressView()
            } else {
                List {
                    if let error = wrapper.state.errorMessage {
                        Text(error).foregroundColor(.red)
                    }
                    LabeledContent("Name", value: wrapper.state.name ?? "--")
                    LabeledContent("State", value: wrapper.state.state ?? "--")
                    LabeledContent("City", value: wrapper.state.city ?? "--")
                    LabeledContent("Role", value: wrapper.state.playingRole?.name ?? "--")
                    LabeledContent("Batting style", value: wrapper.state.battingStyle?.name ?? "--")
                    if let bowlingStyle = wrapper.state.bowlingStyle {
                        LabeledContent("Bowling style", value: bowlingStyle.name)
                    }

                    Button("Edit profile") { wrapper.editProfile() }
                    Button("Log out", role: .destructive) { wrapper.logout() }
                }
            }
        }
        .navigationTitle("Your profile")
        .onChange(of: wrapper.navigationEvent) { _, event in
            switch event {
            case .navigateToEditProfile: onNavigateToEditProfile()
            case .navigateToPhoneEntry: onNavigateToPhoneEntry()
            case nil: break
            }
        }
    }
}

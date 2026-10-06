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

    func retry() { viewModel.retry() }
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
            if wrapper.state.isLoading && !wrapper.state.hasProfile {
                ProgressView()
            } else if wrapper.state.loadFailed {
                VStack(spacing: 10) {
                    Text("Couldn't load your profile").font(.headline)
                    Button("Retry") { wrapper.retry() }
                }
            } else {
                List {
                    LabeledContent("Name", value: wrapper.state.name ?? "--")
                    LabeledContent("State", value: wrapper.state.state ?? "--")
                    LabeledContent("District", value: wrapper.state.district ?? "--")
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
        .onAppear { wrapper.retry() }
        .onChange(of: wrapper.navigationEvent) { _, event in
            switch event {
            case .navigateToEditProfile: onNavigateToEditProfile()
            case .navigateToPhoneEntry: onNavigateToPhoneEntry()
            case nil: break
            }
        }
    }
}

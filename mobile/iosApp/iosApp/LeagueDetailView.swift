import SwiftUI
import Shared

/// Mirrors `LeagueDetailViewModel`'s shared `StateFlow<LeagueDetailState>` into SwiftUI-observable
/// state -- same pattern `OwnProfileViewModelWrapper` documents.
///
/// **Authored but unverified, and not wired into any navigation** -- this is the first iOS League
/// Detail screen of any kind (Phase 2 never authored one either, see docs/PHASE2.md Section 5),
/// so it covers both Phase 2's read view (ground/schedule/format/capacity/fees/awards) and Phase
/// 3's additions (Join/Claim/Follow/Share, Players/Franchises rosters, organizer Remove/approve-
/// leave) in this one file rather than there being an earlier version to extend.
@MainActor
final class LeagueDetailViewModelWrapper: ObservableObject {
    @Published var state: LeagueDetailState

    private let viewModel: LeagueDetailViewModel

    init(leagueId: String) {
        let viewModel = KoinHelper().leagueDetailViewModel(leagueId: leagueId)
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func retry() { viewModel.retry() }
    func markCompleted() { viewModel.markCompleted() }
    func toggleFollow() { viewModel.toggleFollow() }
    func removePlayer(playerId: String) { viewModel.removePlayer(playerId: playerId) }
    func removeFranchise(franchiseId: String) { viewModel.removeFranchise(franchiseId: franchiseId) }
    func approvePlayerLeave(playerId: String) { viewModel.approvePlayerLeave(playerId: playerId) }
    func dismissPlayerLeave(playerId: String) { viewModel.dismissPlayerLeave(playerId: playerId) }
    func approveFranchiseLeave(franchiseId: String) { viewModel.approveFranchiseLeave(franchiseId: franchiseId) }
    func dismissFranchiseLeave(franchiseId: String) { viewModel.dismissFranchiseLeave(franchiseId: franchiseId) }
    func requestLeaveAsPlayer(playerId: String) { viewModel.requestLeaveAsPlayer(playerId: playerId) }
    func requestLeaveAsFranchise(franchiseId: String) { viewModel.requestLeaveAsFranchise(franchiseId: franchiseId) }
}

/// iOS equivalent of `androidApp/.../ui/LeagueDetailScreen.kt`.
struct LeagueDetailView: View {
    let leagueId: String
    let onEditLeague: (String) -> Void
    let onJoinLeague: (String) -> Void
    let onClaimFranchise: (String) -> Void
    let onViewScreenshot: (String) -> Void
    // Phase 9 (iOS wiring, see docs/PHASE9.md) -- organizer entry points this file never had a
    // way to reach before: Auction Settings/Live and Manage Co-Organizers, matching Android's
    // LeagueDetailScreen.kt onAuctionSettings/onAuctionLive/onManageRoles.
    let onAuctionSettings: (String) -> Void
    let onAuctionLive: (String) -> Void
    let onManageRoles: (String) -> Void

    @StateObject private var wrapper: LeagueDetailViewModelWrapper

    init(
        leagueId: String,
        onEditLeague: @escaping (String) -> Void,
        onJoinLeague: @escaping (String) -> Void,
        onClaimFranchise: @escaping (String) -> Void,
        onViewScreenshot: @escaping (String) -> Void,
        onAuctionSettings: @escaping (String) -> Void,
        onAuctionLive: @escaping (String) -> Void,
        onManageRoles: @escaping (String) -> Void
    ) {
        self.leagueId = leagueId
        self.onEditLeague = onEditLeague
        self.onJoinLeague = onJoinLeague
        self.onClaimFranchise = onClaimFranchise
        self.onViewScreenshot = onViewScreenshot
        self.onAuctionSettings = onAuctionSettings
        self.onAuctionLive = onAuctionLive
        self.onManageRoles = onManageRoles
        _wrapper = StateObject(wrappedValue: LeagueDetailViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        Group {
            if wrapper.state.isLoading {
                ProgressView()
            } else if let league = wrapper.state.league {
                List {
                    Section {
                        Text(league.name).font(.title2)
                        Text("\(league.city), \(league.district), \(league.state)")
                        Text("Starts \(league.startsOn)")
                        Text(league.status == .completed ? "Completed" : "Announced")
                    }

                    if !wrapper.state.isOrganizer {
                        Section {
                            if league.status == .completed {
                                Text("League completed")
                            } else if let myRow = league.players.first(where: { $0.userId == wrapper.state.currentUserId }) {
                                Button(myRow.leaveRequestedAt != nil ? "Leave requested" : "Request to leave") {
                                    wrapper.requestLeaveAsPlayer(playerId: myRow.id)
                                }
                            } else {
                                Button("Join as Player") { onJoinLeague(leagueId) }
                            }
                            Button("Claim a Franchise") { onClaimFranchise(leagueId) }
                        }
                    }

                    Section {
                        Button(league.isFollowing ? "Following" : "Follow") { wrapper.toggleFollow() }
                    }

                    if !league.players.isEmpty {
                        Section("Players") {
                            ForEach(league.players, id: \.id) { player in
                                VStack(alignment: .leading) {
                                    Text(player.name ?? player.userId)
                                    if let url = player.paymentScreenshotUrl {
                                        Button("View payment screenshot") { onViewScreenshot(url) }
                                    }
                                    if wrapper.state.isOrganizer {
                                        if player.leaveRequestedAt != nil {
                                            Text("Requested to leave")
                                            Button("Approve") { wrapper.approvePlayerLeave(playerId: player.id) }
                                            Button("Dismiss") { wrapper.dismissPlayerLeave(playerId: player.id) }
                                        }
                                        Button("Remove", role: .destructive) { wrapper.removePlayer(playerId: player.id) }
                                    }
                                }
                            }
                        }
                    }

                    if !league.franchises.isEmpty {
                        Section("Franchises") {
                            ForEach(league.franchises, id: \.id) { franchise in
                                VStack(alignment: .leading) {
                                    Text(franchise.name)
                                    Text("Owner: \(franchise.ownerName ?? franchise.ownerUserId)")
                                    if let url = franchise.paymentScreenshotUrl {
                                        Button("View payment screenshot") { onViewScreenshot(url) }
                                    }
                                    if wrapper.state.isOrganizer {
                                        if franchise.leaveRequestedAt != nil {
                                            Text("Requested to leave")
                                            Button("Approve") { wrapper.approveFranchiseLeave(franchiseId: franchise.id) }
                                            Button("Dismiss") { wrapper.dismissFranchiseLeave(franchiseId: franchise.id) }
                                        }
                                        Button("Remove", role: .destructive) { wrapper.removeFranchise(franchiseId: franchise.id) }
                                    }
                                }
                            }
                        }
                    }

                    if wrapper.state.isOrganizer {
                        Section {
                            Button("Edit league") { onEditLeague(leagueId) }
                            if league.status != .completed {
                                Button("Mark completed") { wrapper.markCompleted() }
                            }
                            Button("Auction settings") { onAuctionSettings(leagueId) }
                            Button("Live auction") { onAuctionLive(leagueId) }
                            Button("Manage co-organizers") { onManageRoles(leagueId) }
                        }
                    }

                    if let error = wrapper.state.errorMessage {
                        Text(error).foregroundColor(.red)
                    }
                }
            } else {
                VStack {
                    Text(wrapper.state.errorMessage ?? "Couldn't load this league.").foregroundColor(.red)
                    Button("Retry") { wrapper.retry() }
                }
            }
        }
        .navigationTitle("League")
        .onAppear { wrapper.retry() }
    }
}

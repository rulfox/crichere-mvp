import SwiftUI
import Shared

/// Mirrors `LeagueDetailViewModel`'s shared `StateFlow<LeagueDetailState>` into SwiftUI-observable
/// state -- same pattern `OwnProfileViewModelWrapper` documents. **Authored but unverified** (no Xcode
/// here, docs/PHASE15.md 8.4).
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
    func clearCompletionNotice() { viewModel.clearCompletionNotice() }
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

/// The public web viewer the share text links to (same as Android's `webViewerBaseUrl` in release).
private let webViewerBaseUrl = "https://crichere.com"

/// iOS equivalent of `androidApp/.../ui/LeagueDetailScreen.kt`, restyled to design update #5 E16 / E17:
/// native inset-grouped list on #F5F6F1, Archivo large title and section headers, the Live Auction capsule
/// above the first section, Remove / Approve leave as swipe actions and in the row's action sheet.
struct LeagueDetailView: View {
    let leagueId: String
    let onEditLeague: (String) -> Void
    let onJoinLeague: (String) -> Void
    let onClaimFranchise: (String) -> Void
    let onViewScreenshot: (String) -> Void
    // Phase 9 (iOS wiring, see docs/PHASE9.md) -- organizer entry points, matching Android's
    // LeagueDetailScreen.kt onAuctionSettings/onAuctionLive/onManageRoles.
    let onAuctionSettings: (String) -> Void
    let onAuctionLive: (String) -> Void
    let onManageRoles: (String) -> Void
    /// A one-off message to show on arrival (design update #4 K10), cleared through [onNoticeShown].
    let notice: String?
    let onNoticeShown: () -> Void

    @StateObject private var wrapper: LeagueDetailViewModelWrapper
    @State private var showCompleteConfirm = false
    @State private var banner: Notice?
    /// What the banner's action does -- it differs per message (Retry vs Open auction).
    @State private var bannerAction: (() -> Void)?
    @State private var playerActions: LeaguePlayerDto?
    @State private var franchiseActions: LeagueFranchiseDto?

    init(
        leagueId: String,
        onEditLeague: @escaping (String) -> Void,
        onJoinLeague: @escaping (String) -> Void,
        onClaimFranchise: @escaping (String) -> Void,
        onViewScreenshot: @escaping (String) -> Void,
        onAuctionSettings: @escaping (String) -> Void,
        onAuctionLive: @escaping (String) -> Void,
        onManageRoles: @escaping (String) -> Void,
        notice: String? = nil,
        onNoticeShown: @escaping () -> Void = {}
    ) {
        self.notice = notice
        self.onNoticeShown = onNoticeShown
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

    private var state: LeagueDetailState { wrapper.state }

    var body: some View {
        Group {
            if state.isLoading {
                ProgressView()
            } else if let league = state.league {
                content(league)
            } else {
                VStack(spacing: 12) {
                    Text(state.errorMessage ?? "Couldn't load this league.").foregroundColor(Brand.error)
                    Button("Retry") { wrapper.retry() }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Brand.background.ignoresSafeArea())
        .navigationTitle(state.league?.name.trimmingCharacters(in: .whitespaces) ?? "League")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            if let league = state.league {
                ToolbarItem(placement: .navigationBarTrailing) {
                    ShareLink(item: URL(string: "\(webViewerBaseUrl)/leagues/\(league.id)")!,
                              message: Text("Join \(league.name.trimmingCharacters(in: .whitespaces)) on Crichere · \(league.district.trimmingCharacters(in: .whitespaces)) · watch the player auction live")) {
                        Image(systemName: "square.and.arrow.up")
                    }
                    .accessibilityLabel("Share league")
                }
            }
        }
        .onAppear { wrapper.retry() }
        .alert(
            "Mark \(state.league?.name.trimmingCharacters(in: .whitespaces) ?? "this league") as completed?",
            isPresented: $showCompleteConfirm
        ) {
            Button("Cancel", role: .cancel) {}
            Button("Mark completed", role: .destructive) { wrapper.markCompleted() }
        } message: {
            Text("This can't be undone. Rosters, auction results and awards stay visible to everyone. Nobody can join, edit the league or run the auction again.")
        }
        .onChange(of: state.completionNotice) { _, notice in
            guard let notice else { return }
            showNotice(notice)
            wrapper.clearCompletionNotice()
        }
        .onAppear {
            if let notice {
                banner = Notice(message: notice)
                bannerAction = nil
                onNoticeShown()
            }
        }
        .noticeBanner($banner, onAction: {
            banner = nil
            bannerAction?()
        })
        .confirmationDialog(playerActions?.name ?? "Player", isPresented: Binding(get: { playerActions != nil }, set: { if !$0 { playerActions = nil } }), titleVisibility: .visible, presenting: playerActions) { player in
            playerActionButtons(player)
        }
        .confirmationDialog(franchiseActions?.name ?? "Franchise", isPresented: Binding(get: { franchiseActions != nil }, set: { if !$0 { franchiseActions = nil } }), titleVisibility: .visible, presenting: franchiseActions) { franchise in
            franchiseActionButtons(franchise)
        }
    }

    // MARK: - list

    private func content(_ league: LeagueDto) -> some View {
        let completed = league.status == .completed
        let organizer = state.isOrganizer
        return List {
            // E16: subline, then the Live Auction capsule as the list's header view (not inside a cell).
            Section {
                EmptyView()
            } header: {
                VStack(alignment: .leading, spacing: 0) {
                    subline(league)
                    auctionButton(league)
                        .padding(.top, 12)
                    if completed {
                        Text("League completed. Rosters and awards stay visible.")
                            .font(.footnote)
                            .foregroundColor(Brand.inkMuted)
                            .padding(.top, 8)
                            .padding(.horizontal, 4)
                    }
                }
                .textCase(nil)
                .listRowInsets(EdgeInsets())
                .padding(.bottom, 4)
            }

            if !organizer {
                memberSection(league)
            }

            // U4 E10/E13: the organizer section goes once the league is completed (nothing in it can change).
            if organizer && !completed {
                organizerSection
            }

            if completed {
                franchisesSection(league, completed: true)
                playersSection(league, completed: true)
            } else {
                playersSection(league, completed: false)
                franchisesSection(league, completed: false)
            }

            if let error = state.errorMessage {
                Section { Text(error).foregroundColor(Brand.error) }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(Brand.background)
        .environment(\.defaultMinListRowHeight, 44)
    }

    /// "{ground} · {district} · {format} · {status}" -- status 600, Announced in primary, Completed in ink.
    private func subline(_ league: LeagueDto) -> some View {
        let completed = league.status == .completed
        let parts = [league.groundName.trimmingCharacters(in: .whitespaces), league.district.trimmingCharacters(in: .whitespaces), league.format].compactMap { $0 }.filter { !$0.isEmpty }
        return (Text(parts.map { $0 + " · " }.joined())
                + Text(completed ? "Completed" : "Announced").fontWeight(.semibold).foregroundColor(completed ? Brand.ink : Brand.primary))
            .font(.system(size: 15))
            .foregroundColor(Brand.inkMuted)
            .padding(.horizontal, 4)
    }

    /// E16 capsule: 50 tall, #0E1A11, gold SF 17 semibold, 20 pt icon. U5 E14: while the auction runs it
    /// reads "Live Auction · in progress" with a static gold dot.
    private func auctionButton(_ league: LeagueDto) -> some View {
        Button { onAuctionLive(leagueId) } label: {
            HStack(spacing: 8) {
                if league.status == .completed {
                    Image(systemName: "chart.bar.fill").font(.system(size: 18))
                    Text("Auction results")
                } else if state.isAuctionLive {
                    Circle().fill(AuctionPalette.gold).frame(width: 7, height: 7)
                    Text("Live Auction · in progress")
                } else {
                    Image(systemName: "hammer.fill").font(.system(size: 18))
                    Text("Live Auction")
                }
            }
            .font(.system(size: 17, weight: .semibold))
            .foregroundColor(AuctionPalette.gold)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(Capsule().fill(Brand.auctionButton))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func memberSection(_ league: LeagueDto) -> some View {
        Section {
            if league.status == .completed {
                Text("League completed").foregroundColor(Brand.inkMuted)
            } else if let myRow = league.players.first(where: { $0.userId == state.currentUserId }) {
                Button(myRow.leaveRequestedAt != nil ? "Leave requested" : "Request to leave") {
                    wrapper.requestLeaveAsPlayer(playerId: myRow.id)
                }
                .disabled(myRow.leaveRequestedAt != nil || state.isLeaveRequesting)
            } else {
                Button("Join as Player") { onJoinLeague(leagueId) }
            }
            if league.status != .completed {
                Button("Claim a Franchise") { onClaimFranchise(leagueId) }
            }
            Button(league.isFollowing ? "Following" : "Follow") { wrapper.toggleFollow() }
                .disabled(state.isTogglingFollow)
        }
    }

    // MARK: - organizer rows (E16)

    private var organizerSection: some View {
        Section {
            organizerRow("pencil", "Edit league") { onEditLeague(leagueId) }
            organizerRow("slider.horizontal.3", "Auction settings") { onAuctionSettings(leagueId) }
            organizerRow("person.badge.shield.checkmark", "Manage co-organizers") { onManageRoles(leagueId) }
            markCompletedRow
        } header: {
            Text("Organizer").brandSectionHeader()
        }
    }

    private func organizerRow(_ symbol: String, _ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: symbol).font(.system(size: 20)).foregroundColor(Brand.primary).frame(width: 28)
                Text(title).font(.system(size: 17)).foregroundColor(Brand.ink)
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundColor(Color(UIColor.tertiaryLabel))
            }
        }
    }

    /// U4 E11 on iOS: progress on the row. U5 E14: disabled with its reason while the auction runs.
    @ViewBuilder
    private var markCompletedRow: some View {
        if state.isAuctionLive {
            // Icon and label at 38%, the reason at full strength; not tappable, still read by VoiceOver.
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "checkmark.seal").font(.system(size: 20)).foregroundColor(Brand.primary.opacity(0.38)).frame(width: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Mark completed").font(.system(size: 17)).foregroundColor(Brand.ink.opacity(0.38))
                    Text("Available once the auction has ended").font(.footnote).foregroundColor(Brand.inkMuted)
                }
            }
            .padding(.vertical, 4)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isButton)
            .accessibilityHint("Unavailable")
        } else {
            Button {
                showCompleteConfirm = true
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "checkmark.seal").font(.system(size: 20)).foregroundColor(Brand.primary).frame(width: 28)
                    Text(state.isCompleting ? "Completing…" : "Mark completed").font(.system(size: 17)).foregroundColor(Brand.ink)
                    Spacer()
                    if state.isCompleting {
                        ProgressView()
                    } else {
                        Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundColor(Color(UIColor.tertiaryLabel))
                    }
                }
            }
            .disabled(state.isCompleting)
        }
    }

    // MARK: - rosters (E16 person rows, E17 franchises first)

    @ViewBuilder
    private func playersSection(_ league: LeagueDto, completed: Bool) -> some View {
        if !league.players.isEmpty {
            Section {
                ForEach(league.players, id: \.id) { player in
                    playerRow(player, editable: state.isOrganizer && !completed)
                }
            } header: {
                Text("Players · \(league.players.count)").brandSectionHeader()
            }
        }
    }

    @ViewBuilder
    private func franchisesSection(_ league: LeagueDto, completed: Bool) -> some View {
        if !league.franchises.isEmpty {
            Section {
                ForEach(league.franchises, id: \.id) { franchise in
                    franchiseRow(franchise, editable: state.isOrganizer && !completed)
                }
            } header: {
                Text("Franchises · \(league.franchises.count)").brandSectionHeader()
            }
        }
    }

    private func playerRow(_ player: LeaguePlayerDto, editable: Bool) -> some View {
        let name = player.name ?? "Player"
        let busy = state.removingIds.contains(player.id) || state.respondingToLeaveRequestIds.contains(player.id)
        let tappable = editable || player.paymentScreenshotUrl != nil
        return Button {
            if tappable { playerActions = player }
        } label: {
            personRow(
                avatar: AnyView(initialsAvatar(name)),
                name: name,
                detail: player.leaveRequestedAt != nil && editable
                    ? Text("Leave requested").foregroundColor(Brand.error)
                    : Text(playerDetail(player)),
                chevron: tappable,
                busy: busy
            )
        }
        .disabled(busy)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            if editable {
                Button("Remove", role: .destructive) { wrapper.removePlayer(playerId: player.id) }
                if player.leaveRequestedAt != nil {
                    Button("Approve leave") { wrapper.approvePlayerLeave(playerId: player.id) }.tint(Brand.primary)
                }
            }
        }
    }

    private func franchiseRow(_ franchise: LeagueFranchiseDto, editable: Bool) -> some View {
        let busy = state.removingIds.contains(franchise.id) || state.respondingToLeaveRequestIds.contains(franchise.id)
        let tappable = editable || franchise.paymentScreenshotUrl != nil
        return Button {
            if tappable { franchiseActions = franchise }
        } label: {
            personRow(
                avatar: AnyView(FranchiseBadge(name: franchise.name, size: 36, radius: 10, fontSize: 12)),
                name: franchise.name,
                detail: franchise.leaveRequestedAt != nil && editable
                    ? Text("Leave requested").foregroundColor(Brand.error)
                    : Text("Owner: \(franchise.ownerName ?? "Unknown")"),
                chevron: tappable,
                busy: busy
            )
        }
        .disabled(busy)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            if editable {
                Button("Remove", role: .destructive) { wrapper.removeFranchise(franchiseId: franchise.id) }
                if franchise.leaveRequestedAt != nil {
                    Button("Approve leave") { wrapper.approveFranchiseLeave(franchiseId: franchise.id) }.tint(Brand.primary)
                }
            }
        }
    }

    /// Min 60, avatar 36, name 17/22, detail 15/20 ink-muted.
    private func personRow(avatar: AnyView, name: String, detail: Text, chevron: Bool, busy: Bool) -> some View {
        HStack(spacing: 12) {
            avatar
            VStack(alignment: .leading, spacing: 2) {
                Text(name).font(.system(size: 17)).foregroundColor(Brand.ink).lineLimit(1)
                detail.font(.system(size: 15)).foregroundColor(Brand.inkMuted).lineLimit(1)
            }
            Spacer(minLength: 8)
            if busy {
                ProgressView()
            } else if chevron {
                Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundColor(Color(UIColor.tertiaryLabel))
            }
        }
        .frame(minHeight: 60 - 16)
        .padding(.vertical, 8)
    }

    private func initialsAvatar(_ name: String) -> some View {
        let initials = name.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
        return Text(initials.isEmpty ? "?" : initials)
            .font(BrandFont.archivo(12, .bold))
            .foregroundColor(Brand.primary)
            .frame(width: 36, height: 36)
            .background(Circle().fill(Brand.avatar))
            .accessibilityHidden(true)
    }

    private func playerDetail(_ player: LeaguePlayerDto) -> String {
        let role: String? = {
            switch player.playingRole {
            case .batsman: return "Batsman"
            case .bowler: return "Bowler"
            case .allRounder: return "All-rounder"
            case .wicketkeeper: return "Wicketkeeper"
            default: return nil
            }
        }()
        let paid = player.paymentScreenshotUrl != nil ? "Payment screenshot" : nil
        return [role, paid].compactMap { $0 }.joined(separator: " · ").ifEmpty("Joined")
    }

    @ViewBuilder
    private func playerActionButtons(_ player: LeaguePlayerDto) -> some View {
        if let url = player.paymentScreenshotUrl {
            Button("View payment screenshot") { onViewScreenshot(url) }
        }
        if state.isOrganizer && state.league?.status != .completed {
            if player.leaveRequestedAt != nil {
                Button("Approve leave") { wrapper.approvePlayerLeave(playerId: player.id) }
                Button("Dismiss leave request") { wrapper.dismissPlayerLeave(playerId: player.id) }
            }
            Button("Remove", role: .destructive) { wrapper.removePlayer(playerId: player.id) }
        }
        Button("Cancel", role: .cancel) {}
    }

    @ViewBuilder
    private func franchiseActionButtons(_ franchise: LeagueFranchiseDto) -> some View {
        if let url = franchise.paymentScreenshotUrl {
            Button("View payment screenshot") { onViewScreenshot(url) }
        }
        if state.isOrganizer && state.league?.status != .completed {
            if franchise.leaveRequestedAt != nil {
                Button("Approve leave") { wrapper.approveFranchiseLeave(franchiseId: franchise.id) }
                Button("Dismiss leave request") { wrapper.dismissFranchiseLeave(franchiseId: franchise.id) }
            }
            Button("Remove", role: .destructive) { wrapper.removeFranchise(franchiseId: franchise.id) }
        }
        Button("Cancel", role: .cancel) {}
    }

    // MARK: - notices (U4 E12/E13, U5 E15)

    private func showNotice(_ notice: CompletionNotice) {
        switch notice {
        case .completed:
            banner = Notice(message: "League marked completed")
            bannerAction = nil
        case .failed:
            banner = Notice(message: "Couldn't complete the league. Check your connection and try again.", actionLabel: "Retry", durationSeconds: nil)
            bannerAction = { showCompleteConfirm = true }
        case .auctionInProgress:
            // The ViewModel reloads the league, so the row turns into the disabled E14 row.
            banner = Notice(message: "The auction is running. End it before marking the league completed.", actionLabel: "Open auction", durationSeconds: nil)
            bannerAction = { onAuctionLive(leagueId) }
        case .refused:
            banner = Notice(message: "Couldn't complete the league right now. Try again later.")
            bannerAction = nil
        default:
            break
        }
    }
}

private extension String {
    func ifEmpty(_ fallback: String) -> String { isEmpty ? fallback : self }
}

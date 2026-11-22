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
    func dismissHandoverNotice() { viewModel.dismissHandoverNotice() }
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

/// League detail (design board screen E, androidApp `ui/LeagueDetailScreen.kt`). Players see the league page
/// (E1/E3/E5/E6); the organizer or a co-organizer also gets the management view (E4): menu, rosters with
/// Remove and leave-request Approve / Dismiss. Report and block (R1-R9) are wired here too.
struct LeagueDetailView: View {
    let leagueId: String
    let onEditLeague: (String) -> Void
    let onJoinLeague: (String) -> Void
    let onClaimFranchise: (String) -> Void
    let onViewScreenshot: (String) -> Void
    let onAuctionSettings: (String) -> Void
    let onAuctionLive: (String) -> Void
    let onManageRoles: (String) -> Void
    /// A one-off message to show on arrival (design update #4 K10), cleared through [onNoticeShown].
    let notice: String?
    let onNoticeShown: () -> Void
    let onHome: () -> Void

    @StateObject private var wrapper: LeagueDetailViewModelWrapper
    @StateObject private var block = BlockViewModelWrapper()
    @StateObject private var report = ReportViewModelWrapper()
    @State private var snack: Snack?
    @State private var showCompleteDialog = false
    @State private var leaveTarget: LeaveTarget?

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
        onNoticeShown: @escaping () -> Void = {},
        onHome: @escaping () -> Void
    ) {
        self.leagueId = leagueId
        self.onEditLeague = onEditLeague
        self.onJoinLeague = onJoinLeague
        self.onClaimFranchise = onClaimFranchise
        self.onViewScreenshot = onViewScreenshot
        self.onAuctionSettings = onAuctionSettings
        self.onAuctionLive = onAuctionLive
        self.onManageRoles = onManageRoles
        self.notice = notice
        self.onNoticeShown = onNoticeShown
        self.onHome = onHome
        _wrapper = StateObject(wrappedValue: LeagueDetailViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        LeagueDetailContent(
            state: wrapper.state,
            blocked: block.blockedIds,
            actions: actions
        )
        .crichereScreen()
        .snackHost($snack)
        .blockConfirm(block, coOrganizerIds: Set((wrapper.state.league?.coOrganizers ?? []).map(\.userId)))
        .blockToast(block)
        .reportSheet(report)
        .crichereDialog(isPresented: $showCompleteDialog, dismissable: !wrapper.state.isCompleting) {
            DestructiveConfirmDialog(
                title: "Mark \((wrapper.state.league?.name ?? "").trimmingCharacters(in: .whitespaces)) as completed?",
                message: "This can't be undone. Rosters, auction results and awards stay visible to everyone. Nobody can join, edit the league or run the auction again.",
                confirmLabel: "Mark completed",
                submittingLabel: "Completing…",
                submitting: wrapper.state.isCompleting,
                onDismiss: { showCompleteDialog = false },
                onConfirm: wrapper.markCompleted
            )
        }
        .crichereDialog(isPresented: Binding(get: { leaveTarget != nil }, set: { if !$0 { leaveTarget = nil } })) {
            // Design E2: confirm before asking the organizer to let you leave.
            CrichereDialog(
                title: "Request to leave?",
                message: "The organizer can approve or dismiss it. Any refund is between you and the organizer; Crichere doesn't handle payments.",
                icon: "ic_logout", iconTint: CrichereColor.errorStrong, iconSize: 26,
                buttons: [
                    DialogButton(label: "Cancel") { leaveTarget = nil },
                    DialogButton(label: "Send request", color: CrichereColor.errorStrong) {
                        let target = leaveTarget
                        leaveTarget = nil
                        switch target {
                        case let .player(id): wrapper.requestLeaveAsPlayer(playerId: id)
                        case let .franchise(id): wrapper.requestLeaveAsFranchise(franchiseId: id)
                        case nil: break
                        }
                    },
                ]
            )
        }
        // The ViewModel survives leaving and re-entering, so a fresh fetch on every visit shows edits.
        .onAppear {
            wrapper.retry()
            if let notice {
                snack = Snack(message: notice)
                onNoticeShown()
            }
        }
        .onChange(of: wrapper.state.completionNotice) { _, notice in
            guard let notice else { return }
            showNotice(notice)
            wrapper.clearCompletionNotice()
        }
    }

    private var actions: LeagueDetailActions {
        LeagueDetailActions(
            onBack: onHome,
            onRetry: wrapper.retry,
            onEditLeague: { onEditLeague(leagueId) },
            onMarkCompleted: { showCompleteDialog = true },
            onAuctionSettings: { onAuctionSettings(leagueId) },
            onAuctionLive: { onAuctionLive(leagueId) },
            onManageRoles: { onManageRoles(leagueId) },
            onJoinLeague: { onJoinLeague(leagueId) },
            onClaimFranchise: { onClaimFranchise(leagueId) },
            onToggleFollow: wrapper.toggleFollow,
            onViewScreenshot: onViewScreenshot,
            onRequestLeave: { leaveTarget = $0 },
            onRemovePlayer: { wrapper.removePlayer(playerId: $0) },
            onRemoveFranchise: { wrapper.removeFranchise(franchiseId: $0) },
            onApprovePlayerLeave: { wrapper.approvePlayerLeave(playerId: $0) },
            onDismissPlayerLeave: { wrapper.dismissPlayerLeave(playerId: $0) },
            onApproveFranchiseLeave: { wrapper.approveFranchiseLeave(franchiseId: $0) },
            onDismissFranchiseLeave: { wrapper.dismissFranchiseLeave(franchiseId: $0) },
            onDismissHandoverNotice: wrapper.dismissHandoverNotice,
            onShare: share,
            onCopyLink: { league in
                // Same link as Share, straight to the clipboard (docs/PHASE6.md) -- for pasting into a group.
                copyToClipboard("\(webViewerBaseUrl)/leagues/\(league.id)")
                snack = Snack(message: "Watch link copied", durationSeconds: 3)
            },
            onReportPerson: { userId, name, role in
                report.open(ReportSubject(type: .user, id: userId, name: name, context: "\(role) in \(wrapper.state.league?.name ?? "")",
                                          contextLeagueId: wrapper.state.league?.id))
            },
            onReportLeague: {
                guard let league = wrapper.state.league else { return }
                report.open(ReportSubject(type: .league, id: league.id, name: league.name, context: "Name, logo, banner and description", contextLeagueId: nil))
            },
            onBlock: { block.askToBlock(userId: $0, name: $1) },
            onUnblock: { block.unblock(userId: $0, name: $1) }
        )
    }

    /// The https watch link (docs/PHASE13.md): chat apps render a preview card from its Open Graph tags.
    private func share(_ league: LeagueDto) {
        let name = league.name.trimmingCharacters(in: .whitespaces)
        let link = "\(webViewerBaseUrl)/leagues/\(league.id)"
        presentShareSheet(text: "Join \(name) on Crichere · \(league.district.trimmingCharacters(in: .whitespaces)) · watch the player auction live\n\(link)")
    }

    /// U4 E12/E13 and U5 E15: the dialog closes either way; success confirms, failure offers Retry.
    private func showNotice(_ notice: CompletionNotice) {
        showCompleteDialog = false
        switch notice {
        case .completed:
            snack = Snack(message: "League marked completed")
        case .failed:
            snack = Snack(message: "Couldn't complete the league. Check your connection and try again.", actionLabel: "Retry",
                          onAction: { snack = nil; showCompleteDialog = true }, durationSeconds: nil)
        case .auctionInProgress:
            snack = Snack(message: "The auction is running. End it before marking the league completed.", actionLabel: "Open auction",
                          onAction: { snack = nil; onAuctionLive(leagueId) }, durationSeconds: nil)
        case .refused:
            snack = Snack(message: "Couldn't complete the league right now. Try again later.")
        }
    }
}

/// What a "Request to leave" confirmation would send, once the user confirms the E2 dialog.
enum LeaveTarget: Equatable {
    case player(String)
    case franchise(String)
}

struct LeagueDetailActions {
    var onBack: () -> Void = {}
    var onRetry: () -> Void = {}
    var onEditLeague: () -> Void = {}
    var onMarkCompleted: () -> Void = {}
    var onAuctionSettings: () -> Void = {}
    var onAuctionLive: () -> Void = {}
    var onManageRoles: () -> Void = {}
    var onJoinLeague: () -> Void = {}
    var onClaimFranchise: () -> Void = {}
    var onToggleFollow: () -> Void = {}
    var onViewScreenshot: (String) -> Void = { _ in }
    var onRequestLeave: (LeaveTarget) -> Void = { _ in }
    var onRemovePlayer: (String) -> Void = { _ in }
    var onRemoveFranchise: (String) -> Void = { _ in }
    var onApprovePlayerLeave: (String) -> Void = { _ in }
    var onDismissPlayerLeave: (String) -> Void = { _ in }
    var onApproveFranchiseLeave: (String) -> Void = { _ in }
    var onDismissFranchiseLeave: (String) -> Void = { _ in }
    var onDismissHandoverNotice: () -> Void = {}
    var onShare: (LeagueDto) -> Void = { _ in }
    var onCopyLink: (LeagueDto) -> Void = { _ in }
    var onReportPerson: (_ userId: String, _ name: String, _ role: String) -> Void = { _, _, _ in }
    var onReportLeague: () -> Void = {}
    var onBlock: (_ userId: String, _ name: String) -> Void = { _, _ in }
    var onUnblock: (_ userId: String, _ name: String) -> Void = { _, _ in }
}

private func organizerName(_ league: LeagueDto) -> String { league.organizerName ?? "this organizer" }

/// Boards R3e-R3g. A co-organizer can block the main organizer, so this doesn't use `isOrganizer`.
private func canBlockOrganizer(_ league: LeagueDto, me: String, blocked: Set<String>) -> Bool {
    me != league.organizerUserId && !league.organizerDeleted && !blocked.contains(league.organizerUserId)
}

/// The screen, stateless for the DEBUG parity gallery.
struct LeagueDetailContent: View {
    let state: LeagueDetailState
    var blocked: Set<String> = []
    var actions = LeagueDetailActions()

    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top
            Group {
                if let league = state.league {
                    if blocked.contains(league.organizerUserId) && !state.isOrganizer
                        && !league.players.contains(where: { $0.userId == state.currentUserId })
                        && !league.franchises.contains(where: { $0.ownerUserId == state.currentUserId }) {
                        VStack(spacing: 0) {
                            CompactHeader(topInset: top, onBack: actions.onBack)
                            BlockedOrganizerNotice(
                                organizerName: organizerName(league),
                                onUnblock: { actions.onUnblock(league.organizerUserId, organizerName(league)) },
                                onHome: actions.onBack
                            )
                        }
                    } else {
                        LeaguePage(league: league, state: state, blocked: blocked, actions: actions, topInset: top)
                    }
                } else {
                    VStack(spacing: 0) {
                        CompactHeader(topInset: top, onBack: actions.onBack)
                        if state.isLoading {
                            CrichereSpinner(color: CrichereColor.primary, lineWidth: 4, size: 40).frame(maxWidth: .infinity, maxHeight: .infinity)
                        } else {
                            LoadErrorView(onRetry: actions.onRetry)
                        }
                    }
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height + top + proxy.safeAreaInsets.bottom, alignment: .top)
            .ignoresSafeArea(.container, edges: .vertical)
        }
        .background(CrichereColor.background.ignoresSafeArea())
    }
}

// MARK: - League page (E1-E6)

/// One page for everyone. Players and visitors get the join / claim / follow block (E1), a "You're
/// registered" card with Request to leave once joined (E2), or the leave-requested notice (E3).
/// Organizers and co-organizers see the same league plus the E4 management block.
private struct LeaguePage: View {
    let league: LeagueDto
    let state: LeagueDetailState
    let blocked: Set<String>
    let actions: LeagueDetailActions
    let topInset: CGFloat

    private var me: String? { state.currentUserId }
    private var organizer: Bool { state.isOrganizer }
    private var myPlayer: LeaguePlayerDto? { organizer ? nil : league.players.first { $0.userId == me } }
    private var myFranchise: LeagueFranchiseDto? { organizer ? nil : league.franchises.first { $0.ownerUserId == me } }
    private var completed: Bool { league.status == .completed }
    private var playersFull: Bool { league.playersRequired.map { league.players.count >= Int(truncating: $0) } ?? false }
    private var franchisesFull: Bool { league.franchisesRequired.map { league.franchises.count >= Int(truncating: $0) } ?? false }
    private var leaveRequested: Bool { myPlayer?.leaveRequestedAt != nil || myFranchise?.leaveRequestedAt != nil }
    /// Board R9b: a member keeps the league, without the blocked organizer's uploads.
    private var organizerBlocked: Bool { blocked.contains(league.organizerUserId) }
    private var bannerUrl: String? { organizerBlocked ? nil : league.bannerUrl }
    private var bannerRemoved: Bool { league.bannerRemoved || (organizerBlocked && league.bannerUrl != nil) }
    private var logoUrl: String? { organizerBlocked ? nil : league.logoUrl }
    private var logoRemoved: Bool { league.logoRemoved || (organizerBlocked && league.logoUrl != nil) }
    private var hasBanner: Bool { bannerUrl != nil || bannerRemoved }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if hasBanner {
                    BannerHeader(league: league, bannerUrl: bannerUrl, bannerRemoved: bannerRemoved, logoUrl: logoUrl, logoRemoved: logoRemoved,
                                 topInset: topInset, onBack: actions.onBack)
                } else {
                    CompactHeader(topInset: topInset, onBack: actions.onBack)
                }
                VStack(alignment: .leading, spacing: 0) {
                    Spacer().frame(height: hasBanner ? 8 : 2)
                    Text(league.name).crichere(.archivo(21, .extraBold, lineHeight: 23.1), CrichereColor.onBackground)
                        .accessibilityAddTraits(.isHeader)
                    Spacer().frame(height: 4)
                    LeagueFacts(league: league, description: organizerBlocked ? nil : league.description_)
                    statusBlocks
                    if league.franchisesRequired != nil || league.playersRequired != nil || league.franchiseFee != nil || league.playerFee != nil {
                        CapacityFeesCard(league: league).padding(.top, 10)
                    }
                    actionsBlock.padding(.top, 10)
                    AwardsSection(awards: league.awards)
                    rosters
                    if let me {
                        LeagueEndLinks(
                            onReportLeague: organizer ? nil : actions.onReportLeague,
                            onBlockOrganizer: canBlockOrganizer(league, me: me, blocked: blocked)
                                ? { actions.onBlock(league.organizerUserId, organizerName(league)) } : nil
                        )
                    }
                }
                .padding(.horizontal, 19)
            }
            .padding(.bottom, 32)
        }
    }

    @ViewBuilder private var statusBlocks: some View {
        if organizerBlocked {
            BlockedOrganizerBanner(organizerName: organizerName(league),
                                   onUnblock: { actions.onUnblock(league.organizerUserId, organizerName(league)) })
                .padding(.top, 12)
        }
        if leaveRequested {
            NoticeCard(icon: "ic_hourglass_top", title: "Requested to leave", message: "You stay listed until the organizer approves or dismisses it.",
                       containerColor: CrichereColor.warningContainer, titleColor: CrichereColor.onWarning, bodyColor: CrichereColor.onWarning)
                .padding(.top, 12)
        } else if !completed && (myPlayer != nil || myFranchise != nil) {
            VStack(spacing: 13) {
                if let myPlayer {
                    RegisteredCard(title: "You're registered",
                                   line: registrationLine(myPlayer.paymentScreenshotUrl, league.playerFee?.doubleValue, myPlayer.joinedAt))
                } else if let myFranchise {
                    RegisteredCard(title: "You own \(myFranchise.name)",
                                   line: registrationLine(myFranchise.paymentScreenshotUrl, league.franchiseFee?.doubleValue, myFranchise.joinedAt))
                }
                let target: LeaveTarget? = myPlayer.map { .player($0.id) } ?? myFranchise.map { .franchise($0.id) }
                if let target {
                    LeaguePill(text: "Request to leave", icon: "ic_logout", style: .danger, enabled: !state.isLeaveRequesting) {
                        actions.onRequestLeave(target)
                    }
                }
            }
            .padding(.top, 12)
        }
    }

    @ViewBuilder private var actionsBlock: some View {
        VStack(alignment: .leading, spacing: 10) {
            if completed {
                Text("League completed. Rosters and awards stay visible.").crichere(.sans(13), CrichereColor.onSurfaceVariant)
            }
            // Design D9: only the new organizer sees it (the server decides), above their menu.
            if league.showHandoverNotice {
                DismissibleNotice(icon: "ic_admin_panel_settings",
                                  text: "The previous organizer deleted their account. You're now the organizer of \(league.name).",
                                  container: CrichereColor.primaryContainer, ink: CrichereColor.onPrimaryContainer,
                                  onDismiss: actions.onDismissHandoverNotice)
            }
            // U4 E13: a completed league can't change, so the organizer card goes rather than showing dead rows.
            if organizer && !completed {
                OrganizerMenu(actions: actions, auctionLive: state.isAuctionLive)
            }
            if organizer {
                if let error = state.errorMessage { Text(error).crichere(.sans(12.5, .medium), CrichereColor.error) }
            } else if !completed && myPlayer == nil && myFranchise == nil {
                if playersFull {
                    LeaguePill(text: "Registration full", icon: "ic_person_off", style: .disabled, height: 50) {}
                } else {
                    LeaguePill(text: "Join as Player", icon: "ic_person_add", style: .primary, action: actions.onJoinLeague)
                }
                HStack(spacing: 8) {
                    if !franchisesFull {
                        LeaguePill(text: "Claim a Franchise", icon: "ic_shield", style: .outlined, action: actions.onClaimFranchise)
                    }
                    FollowPill(league: league, state: state, registrationClosed: playersFull && franchisesFull, onToggle: actions.onToggleFollow)
                }
            } else if !completed {
                FollowPill(league: league, state: state, registrationClosed: false, onToggle: actions.onToggleFollow)
            }
            HStack(spacing: 8) {
                LeaguePill(text: "Share", icon: "ic_share", style: .outlined) { actions.onShare(league) }
                LeaguePill(text: "Copy watch link", icon: "ic_link", style: .outlined) { actions.onCopyLink(league) }
            }
            // Public like the rest of the league (docs/PHASE5.md). Completed: results only (U4 E13).
            if completed {
                LeaguePill(text: "Auction results", icon: "ic_leaderboard", style: .auction, action: actions.onAuctionLive)
            } else if state.isAuctionLive {
                LiveInProgressPill(action: actions.onAuctionLive)
            } else {
                LeaguePill(text: "Live Auction", icon: "ic_gavel", style: .auction, action: actions.onAuctionLive)
            }
        }
    }

    @ViewBuilder private var rosters: some View {
        if organizer {
            SectionHeader(text: "Players · \(league.players.count)")
            if league.players.isEmpty {
                Text("No players yet.").crichere(.sans(13), CrichereColor.onSurfaceVariant)
            } else {
                RosterCard {
                    ForEach(Array(league.players.enumerated()), id: \.element.id) { index, player in
                        if index > 0 { RowDivider() }
                        playerRow(player, organizerActions: organizerActions(id: player.id, player: true))
                    }
                }
            }
            SectionHeader(text: "Franchises · \(league.franchises.count)")
            if league.franchises.isEmpty {
                Text("No franchises yet.").crichere(.sans(13), CrichereColor.onSurfaceVariant)
            } else {
                RosterCard {
                    ForEach(Array(league.franchises.enumerated()), id: \.element.id) { index, franchise in
                        if index > 0 { RowDivider() }
                        franchiseRow(franchise, organizerActions: organizerActions(id: franchise.id, player: false))
                    }
                }
            }
        } else {
            if !league.franchises.isEmpty {
                SectionHeader(text: "Franchises")
                RosterCard {
                    ForEach(Array(league.franchises.enumerated()), id: \.element.id) { index, franchise in
                        if index > 0 { RowDivider() }
                        franchiseRow(franchise, organizerActions: nil)
                    }
                }
            }
            if !league.players.isEmpty {
                SectionHeader(text: "Players")
                RosterCard {
                    ForEach(Array(league.players.enumerated()), id: \.element.id) { index, player in
                        if index > 0 { RowDivider() }
                        playerRow(player, organizerActions: nil)
                    }
                }
            }
        }
    }

    private func organizerActions(id: String, player: Bool) -> OrganizerRowActions {
        OrganizerRowActions(
            readOnly: completed,
            isRemoving: state.removingIds.contains(id),
            isResponding: state.respondingToLeaveRequestIds.contains(id),
            onViewScreenshot: actions.onViewScreenshot,
            onRemove: { player ? actions.onRemovePlayer(id) : actions.onRemoveFranchise(id) },
            onApproveLeave: { player ? actions.onApprovePlayerLeave(id) : actions.onApproveFranchiseLeave(id) },
            onDismissLeave: { player ? actions.onDismissPlayerLeave(id) : actions.onDismissFranchiseLeave(id) }
        )
    }

    private func playerRow(_ player: LeaguePlayerDto, organizerActions: OrganizerRowActions?) -> some View {
        let isMe = player.userId == me
        let paid = player.paymentScreenshotUrl != nil ? league.playerFee.map { "paid \(rupees($0.doubleValue))" } : nil
        let isBlocked = blocked.contains(player.userId)
        let subtitle = [player.playingRole?.label, paid, isBlocked ? "Blocked" : nil].compactMap { $0 }.joined(separator: " · ")
        return RosterRow(
            avatarText: initials(player.name ?? "?"), avatarUrl: player.photoUrl, deleted: player.deleted,
            neutral: isBlocked || player.photoRemoved, neutralLogo: false,
            title: (player.name ?? "Player") + (isMe ? " (you)" : ""), subtitle: subtitle.isEmpty ? nil : subtitle,
            leaveRequested: player.leaveRequestedAt != nil, screenshotUrl: player.paymentScreenshotUrl, organizer: organizerActions,
            menu: (me != nil && !isMe && !player.deleted)
                ? RowMenuSpec(userId: player.userId, name: player.name ?? "Player", role: "Player", blocked: isBlocked) : nil,
            actions: actions
        )
    }

    private func franchiseRow(_ franchise: LeagueFranchiseDto, organizerActions: OrganizerRowActions?) -> some View {
        let isMe = franchise.ownerUserId == me
        let paid = franchise.paymentScreenshotUrl != nil ? league.franchiseFee.map { "paid \(rupees($0.doubleValue))" } : nil
        let owner = "Owner: \(franchise.ownerName ?? "Unknown")" + (isMe ? " (you)" : "")
        let isBlocked = blocked.contains(franchise.ownerUserId)
        return RosterRow(
            avatarText: initials(franchise.name), avatarUrl: franchise.logoUrl, deleted: false,
            neutral: isBlocked || franchise.logoRemoved, neutralLogo: true,
            title: franchise.name, subtitle: [owner, paid, isBlocked ? "Blocked" : nil].compactMap { $0 }.joined(separator: " · "),
            leaveRequested: franchise.leaveRequestedAt != nil, screenshotUrl: franchise.paymentScreenshotUrl, organizer: organizerActions,
            menu: (me != nil && !isMe && !franchise.ownerDeleted)
                ? RowMenuSpec(userId: franchise.ownerUserId, name: franchise.ownerName ?? franchise.name, role: "Franchise owner", blocked: isBlocked) : nil,
            actions: actions
        )
    }
}

private let joinedParser: ISO8601DateFormatter = {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return formatter
}()

/// "Paid ₹500 · proof sent 26 Sep" when a screenshot was attached, else "Joined 26 Sep" (local date).
private func registrationLine(_ screenshotUrl: String?, _ fee: Double?, _ joinedAt: String) -> String {
    let date = joinedParser.date(from: joinedAt) ?? ISO8601DateFormatter().date(from: joinedAt)
    let label: String = date.map { date in
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_GB")
        formatter.dateFormat = "d MMM"
        return formatter.string(from: date)
    } ?? ""
    if screenshotUrl != nil, let fee { return "Paid \(rupees(fee)) · proof sent \(label)" }
    return "Joined \(label)"
}

// MARK: - Headers and facts

/// Back arrow, 48 pt row 3 pt under the status bar.
private struct CompactHeader: View {
    let topInset: CGFloat
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            BackArrowButton(action: onBack)
            Spacer(minLength: 0)
        }
        .padding(.leading, 5)
        .padding(.trailing, 19)
        .frame(height: 48)
        .padding(.top, topInset + 3)
    }
}

/// The 118 pt banner under the status bar (removed: a quiet image glyph keeps its slot -- R12a), the
/// 38 pt white back circle, and the 64 pt logo tile hanging 28 pt below the banner.
private struct BannerHeader: View {
    let league: LeagueDto
    let bannerUrl: String?
    let bannerRemoved: Bool
    let logoUrl: String?
    let logoRemoved: Bool
    let topInset: CGFloat
    let onBack: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            VStack(spacing: 0) {
                ZStack {
                    if bannerRemoved {
                        DeletedAvatar.background
                        CrIcon("ic_image", size: 28, tint: DeletedAvatar.icon)
                    } else {
                        CrichereColor.primaryContainer
                        RemoteImage(url: bannerUrl)
                    }
                }
                .frame(height: topInset + 118)
                .frame(maxWidth: .infinity)
                .clipped()
                Spacer().frame(height: 28) // the part of the logo tile that hangs below the banner
            }
            Button(action: onBack) {
                CrIcon("ic_arrow_back", size: 21, tint: CrichereColor.onBackground)
                    .frame(width: 38, height: 38)
                    .background(Color.white.opacity(0.92), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            .padding(.leading, 11)
            .padding(.top, topInset + 9)
            ZStack {
                if logoRemoved {
                    DeletedAvatar.background
                    CrIcon("ic_shield", size: 24, tint: DeletedAvatar.icon)
                } else {
                    tileColor(league.id)
                    Text(shortCode(league.name)).crichere(.archivo(17.9, .extraBold), .white)
                }
                RemoteImage(url: logoUrl)
            }
            .frame(width: 56, height: 56)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .padding(4)
            .background(CrichereColor.background, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding(.leading, 19)
            .frame(maxHeight: .infinity, alignment: .bottomLeading)
        }
        .frame(height: topInset + 118 + 28)
    }
}

/// E1/E5 (design update #6): the ground, "District, State", "Starts 12 Oct 2026 · T20 · Announced", description.
private struct LeagueFacts: View {
    let league: LeagueDto
    let description: String?

    var body: some View {
        let style = CrichereTextStyle.sans(12.5, lineHeight: 16.875)
        VStack(alignment: .leading, spacing: 4) {
            Text(league.groundName).crichere(style, CrichereColor.onSurfaceVariant)
            Text("\(league.district), \(league.state)").crichere(style, CrichereColor.onSurfaceVariant)
            (Text("Starts \(longDate(league.startsOn))"
                  + (league.format.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : " · \($0)" } ?? "") + " · ")
             + Text(league.status == .completed ? "Completed" : "Announced")
                .font(CrichereTextStyle.sans(12.5, .semiBold).font).foregroundColor(CrichereColor.onBackground))
                .crichere(style, CrichereColor.onSurfaceVariant)
            if let description, !description.trimmingCharacters(in: .whitespaces).isEmpty {
                Text(description).crichere(.sans(13, lineHeight: 18), CrichereColor.onBackground).padding(.top, 2)
            }
        }
    }
}

private struct CapacityFeesCard: View {
    let league: LeagueDto

    var body: some View {
        let capacity = [league.franchisesRequired.map { "Franchises: \($0)" }, league.playersRequired.map { "Players: \($0)" }]
            .compactMap { $0 }.joined(separator: " · ")
        let fees = [league.franchiseFee.map { "Franchise \(rupees($0.doubleValue))" }, league.playerFee.map { "Player \(rupees($0.doubleValue))" }]
            .compactMap { $0 }.joined(separator: " · ")
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        HStack(alignment: .top, spacing: 21) {
            factColumn("Capacity", capacity.isEmpty ? "Open" : capacity)
            factColumn("Fees", fees.isEmpty ? "Free" : fees)
        }
        .padding(11)
        .background(CrichereColor.surface, in: shape)
        .overlay(shape.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }

    private func factColumn(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).crichere(.sans(10.5, .medium), CrichereColor.onSurfaceVariant)
            Text(value).crichere(.sans(12.5, .semiBold, lineHeight: 16.25), CrichereColor.onBackground)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct RegisteredCard: View {
    let title: String
    let line: String

    var body: some View {
        HStack(spacing: 12) {
            CrIcon("ic_check_circle_filled", size: 26, tint: CrichereColor.primary)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).crichere(.sans(14.5, .bold, lineHeight: 15.95), CrichereColor.onPrimaryContainer)
                Text(line).crichere(.sans(12.5, lineHeight: 16.25), Color(hex: 0x2E4A31))
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(CrichereColor.primaryContainer, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

// MARK: - Organizer menu and pills

private struct OrganizerMenu: View {
    let actions: LeagueDetailActions
    let auctionLive: Bool

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        VStack(spacing: 0) {
            MenuRow(icon: "ic_edit", text: "Edit league", action: actions.onEditLeague)
            MenuRow(icon: "ic_tune", text: "Auction settings", action: actions.onAuctionSettings)
            MenuRow(icon: "ic_admin_panel_settings", text: "Manage co-organizers", action: actions.onManageRoles)
            // Not red: the dialog carries the warning (U4 E10). Refused while the auction runs (U5 E14).
            if auctionLive {
                MarkCompletedUnavailableRow()
            } else {
                MenuRow(icon: "ic_task_alt", text: "Mark completed", action: actions.onMarkCompleted)
            }
        }
        .padding(5)
        .background(CrichereColor.surface, in: shape)
        .overlay(shape.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }
}

private struct MenuRow: View {
    let icon: String
    let text: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                CrIcon(icon, size: 20, tint: CrichereColor.primary)
                Text(text).crichere(.sans(13.5, .medium), CrichereColor.onBackground).frame(maxWidth: .infinity, alignment: .leading)
                CrIcon("ic_chevron_right", size: 18, tint: CrichereColor.inkDisabled)
            }
            .padding(.horizontal, 10)
            .frame(height: 40)
        }
        .buttonStyle(PressHighlight(shape: RoundedRectangle(cornerRadius: 10, style: .continuous)))
    }
}

/// U5 E14: 56 pt, icon and label at 38%, the reason at full strength; not tappable.
private struct MarkCompletedUnavailableRow: View {
    var body: some View {
        HStack(spacing: 12) {
            CrIcon("ic_task_alt", size: 20, tint: CrichereColor.primary.opacity(0.38))
            VStack(alignment: .leading, spacing: 2) {
                Text("Mark completed").crichere(.sans(13.5, .medium, lineHeight: 18), CrichereColor.onBackground.opacity(0.38))
                Text("Available once the auction has ended").crichere(.sans(12, lineHeight: 16), CrichereColor.onSurfaceVariant)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 10)
        .frame(height: 56)
        .accessibilityElement(children: .combine)
    }
}

/// U5 E14: "Live Auction · in progress" with a 7 pt static gold dot in place of the gavel.
private struct LiveInProgressPill: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Circle().fill(CrichereColor.auctionGold).frame(width: 7, height: 7)
                Text("Live Auction · in progress").crichere(.sans(13.5, .semiBold), CrichereColor.auctionGold).lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
        }
        .buttonStyle(PillButtonStyle(fill: Color(hex: 0x0E1A11), pressedFill: Color(hex: 0x1A2A1E), content: CrichereColor.auctionGold, radius: 22))
    }
}

private struct FollowPill: View {
    let league: LeagueDto
    let state: LeagueDetailState
    let registrationClosed: Bool
    let onToggle: () -> Void

    var body: some View {
        let label = league.isFollowing ? "Following" : (registrationClosed ? "Follow for updates" : "Follow")
        LeaguePill(text: label, icon: league.isFollowing ? "ic_notifications_active" : "ic_notifications", style: .outlined,
                   enabled: !state.isTogglingFollow, action: onToggle)
    }
}

private enum LeaguePillStyle { case primary, outlined, auction, danger, disabled }

/// League detail's pills (44 pt default): primary green, outlined, the dark auction capsule with gold,
/// danger outlined, and disabled grey (50 pt, 14.5).
private struct LeaguePill: View {
    let text: String
    let icon: String
    let style: LeaguePillStyle
    var height: CGFloat = 44
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        let (container, content, border): (Color, Color, Color?) = {
            switch style {
            case .primary: (CrichereColor.primary, CrichereColor.onPrimary, nil)
            case .outlined: (.clear, CrichereColor.primary, CrichereColor.outline)
            case .auction: (Color(hex: 0x0E1A11), CrichereColor.auctionGold, nil)
            case .danger: (.clear, CrichereColor.errorStrong, CrichereColor.outline)
            case .disabled: (CrichereColor.disabledContainer, Color(hex: 0x6B756D), nil)
            }
        }()
        Button(action: action) {
            HStack(spacing: 6) {
                CrIcon(icon, size: 18, tint: content)
                Text(text).crichere(.sans(style == .disabled ? 14.5 : 13.5, .semiBold), content).lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: height, maxHeight: height)
        }
        .buttonStyle(PillButtonStyle(fill: container, pressedFill: style == .primary ? CrichereColor.greenPressed : container.opacity(0.85),
                                     content: content, border: border, radius: height / 2))
        .disabled(!enabled || style == .disabled)
    }
}

// MARK: - Awards

/// Section title: Archivo 700 14.5, 14 pt above, 9 pt below.
private struct SectionHeader: View {
    let text: String

    var body: some View {
        Text(text).crichere(.archivo(14.5, .bold), CrichereColor.onBackground)
            .accessibilityAddTraits(.isHeader)
            .padding(.top, 14)
            .padding(.bottom, 9)
    }
}

private struct AwardsSection: View {
    let awards: [LeagueAwardDto]

    var body: some View {
        if !awards.isEmpty {
            let sorted = awards.sorted { $0.displayOrder < $1.displayOrder }
            SectionHeader(text: "Awards")
            VStack(spacing: 8) {
                ForEach(Array(stride(from: 0, to: sorted.count, by: 3)), id: \.self) { start in
                    // Equal-height tiles that grow with their content -- a long amount or name wraps.
                    HStack(alignment: .top, spacing: 8) {
                        ForEach(start..<min(start + 3, sorted.count), id: \.self) { index in
                            AwardCard(award: sorted[index], index: index).frame(maxHeight: .infinity)
                        }
                        ForEach(0..<(3 - min(3, sorted.count - start)), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity) }
                    }
                    .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}

private struct AwardCard: View {
    let award: LeagueAwardDto
    let index: Int

    var body: some View {
        let (icon, tint): (String, Color) = index == 0 ? ("ic_trophy", Color(hex: 0xB8860B))
            : index == 1 ? ("ic_military_tech", CrichereColor.inkSubtle) : ("ic_star_filled", CrichereColor.primary)
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            CrIcon(icon, size: 21, tint: tint)
            Spacer().frame(height: 8)
            Text(award.name).crichere(.sans(13, .semiBold, lineHeight: 14.3), CrichereColor.onBackground).lineLimit(2)
            if let cash = award.cashAmount {
                Text("Cash \(rupees(cash.doubleValue))").crichere(.mono(11.5, .medium, lineHeight: 14), CrichereColor.onSurfaceVariant).padding(.top, 4)
            }
            if award.hasTrophy {
                Text("Trophy").crichere(.sans(11.5, .medium), CrichereColor.onSurfaceVariant).padding(.top, 4)
            }
            Spacer(minLength: 0)
        }
        .padding(11)
        .frame(maxWidth: .infinity, minHeight: 108, alignment: .topLeading)
        .background(CrichereColor.surface, in: shape)
        .overlay(shape.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }
}

// MARK: - Rosters

private struct OrganizerRowActions {
    /// Completed league (U4 E13): only View payment screenshot stays.
    let readOnly: Bool
    let isRemoving: Bool
    let isResponding: Bool
    let onViewScreenshot: (String) -> Void
    let onRemove: () -> Void
    let onApproveLeave: () -> Void
    let onDismissLeave: () -> Void
}

private struct RowMenuSpec {
    let userId: String
    let name: String
    let role: String
    let blocked: Bool
}

private struct RosterCard<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        VStack(spacing: 0, content: content)
            .background(CrichereColor.surface, in: shape)
            .clipShape(shape)
            .overlay(shape.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }
}

private struct RowDivider: View {
    var body: some View { Rectangle().fill(CrichereColor.surfaceVariant).frame(height: 1) }
}

private struct RosterRow: View {
    let avatarText: String
    let avatarUrl: String?
    /// Design D10: a deleted user's row gets the neutral avatar and a muted name.
    let deleted: Bool
    /// Board R8a: blocked or removed -- the neutral avatar, the name left as it is.
    let neutral: Bool
    /// A franchise row: the square shield, not the round person.
    let neutralLogo: Bool
    let title: String
    let subtitle: String?
    let leaveRequested: Bool
    let screenshotUrl: String?
    let organizer: OrganizerRowActions?
    let menu: RowMenuSpec?
    let actions: LeagueDetailActions

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            avatar
            VStack(alignment: .leading, spacing: 0) {
                Text(title).crichere(.sans(13.5, .semiBold, lineHeight: 14.85), deleted ? DeletedAvatar.name : CrichereColor.onBackground).lineLimit(1)
                if let subtitle {
                    Text(subtitle).crichere(.sans(11.5, lineHeight: 13.8), CrichereColor.onSurfaceVariant).padding(.top, 3)
                }
                rowActions
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let menu {
                ModerationRowMenu(name: menu.name, blocked: menu.blocked,
                                  onReport: { actions.onReportPerson(menu.userId, menu.name, menu.role) },
                                  onBlock: { actions.onBlock(menu.userId, menu.name) },
                                  onUnblock: { actions.onUnblock(menu.userId, menu.name) })
                    .padding(.vertical, -6)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
    }

    @ViewBuilder private var avatar: some View {
        if neutral && neutralLogo {
            NeutralLogo(size: 32, iconSize: 18, corner: 8)
        } else if deleted || neutral {
            DeletedUserAvatar(size: 32, iconSize: 18)
        } else {
            ZStack {
                Color(hex: 0xE3E8DD)
                Text(avatarText).crichere(.archivo(11.5, .bold), CrichereColor.primary)
                RemoteImage(url: avatarUrl)
            }
            .frame(width: 32, height: 32)
            .clipShape(Circle())
        }
    }

    @ViewBuilder private var rowActions: some View {
        if let organizer {
            if organizer.readOnly {
                if let screenshotUrl { ScreenshotLink(url: screenshotUrl, onView: organizer.onViewScreenshot).padding(.top, 8) }
            } else {
                HStack(spacing: 6) {
                    if leaveRequested {
                        Text("Requested to leave").crichere(.sans(12, .semiBold, lineHeight: 14.4), CrichereColor.error)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        SmallRosterPill(text: "Approve leave", filled: true, enabled: !organizer.isResponding, action: organizer.onApproveLeave)
                        SmallRosterPill(text: "Dismiss", filled: false, enabled: !organizer.isResponding, action: organizer.onDismissLeave)
                    } else {
                        Group {
                            if let screenshotUrl { ScreenshotLink(url: screenshotUrl, onView: organizer.onViewScreenshot) }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        SmallRosterPill(text: organizer.isRemoving ? "Removing…" : "Remove", filled: false, danger: true,
                                        enabled: !organizer.isRemoving, action: organizer.onRemove)
                    }
                }
                .padding(.top, 8)
            }
        } else if leaveRequested {
            Text("Requested to leave").crichere(.sans(12, .semiBold), CrichereColor.error).padding(.top, 8)
        }
    }
}

private struct ScreenshotLink: View {
    let url: String
    let onView: (String) -> Void

    var body: some View {
        Button { onView(url) } label: {
            HStack(spacing: 4) {
                CrIcon("ic_photo_library", size: 16, tint: CrichereColor.primary)
                Text("View payment screenshot").crichere(.sans(12, .semiBold), CrichereColor.primary)
            }
        }
        .buttonStyle(.plain)
    }
}

/// 30 pt roster pill: filled green, outlined, or danger (red text, pink border).
private struct SmallRosterPill: View {
    let text: String
    let filled: Bool
    var danger: Bool = false
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        let content: Color = filled ? CrichereColor.onPrimary : (danger ? CrichereColor.errorStrong : CrichereColor.primary)
        Button(action: action) {
            Text(text).crichere(.sans(12, .semiBold), content).padding(.horizontal, 12).frame(height: 30)
        }
        .buttonStyle(PillButtonStyle(fill: filled ? CrichereColor.primary : .clear, pressedFill: filled ? CrichereColor.greenPressed : content.opacity(0.08),
                                     content: content, border: filled ? nil : (danger ? Color(hex: 0xE4B4AC) : CrichereColor.outline), radius: 15))
        .disabled(!enabled)
    }
}

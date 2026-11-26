import SwiftUI
import Shared

/// Mirrors `AuctionViewModel`'s shared `StateFlow<AuctionState>` -- same pattern
/// `AuctionSettingsViewModelWrapper` documents.
@MainActor
final class AuctionViewModelWrapper: ObservableObject {
    @Published var state: AuctionState

    private let viewModel: AuctionViewModel

    init(leagueId: String) {
        let viewModel = KoinHelper().auctionViewModel(leagueId: leagueId)
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func retry() { viewModel.retry() }
    func onBidAmountChanged(_ value: String) { viewModel.onBidAmountChanged(value: value) }
    func onSuggestionPicked(_ amount: Double) { viewModel.onSuggestionPicked(amount: amount) }
    func placeBid() { viewModel.placeBid() }
    func onStepBid() { viewModel.onStepBid() }
    func start() { viewModel.start() }
    func nextPlayer() { viewModel.nextPlayer() }
    func sold() { viewModel.sold() }
    func unsold() { viewModel.unsold() }
    func undo() { viewModel.undo() }
    /// U5 L19: End Auction asks first; the alert's destructive button confirms.
    func requestEnd() { viewModel.requestEnd() }
    func dismissEnd() { viewModel.dismissEnd() }
    func confirmEnd() { viewModel.confirmEnd() }
    func clearEndFailure() { viewModel.clearEndFailure() }
    func toggleExceedPurse(_ allow: Bool) { viewModel.toggleExceedPurse(allow: allow) }
}


// Board L1-L10 (dark auction theme).
private let dockColor = Color(hex: 0x101B14)
private let textSoft = Color(hex: 0xD5DDD6)
private let textDim = Color(hex: 0xB7C2B9)
private let coral = Color(hex: 0xFF8B70)
private let hairline = Color.white.opacity(0.08)
private let ghostButton = Color.white.opacity(0.08)
private let photoPlaceholder = Color(hex: 0x25362B)
private let liveFeed = Color(hex: 0x7BC47F)
private let gold = CrichereColor.auctionGold
private let muted = CrichereColor.auctionMuted
private let surface = CrichereColor.auctionSurface
private let auctionBg = CrichereColor.auctionBg
private let deletedDark = Color.white.opacity(0.08)
private let deletedNameDark = Color(hex: 0x8FA094)

/// The live auction (androidApp `ui/AuctionLiveScreen.kt`, board L1-L10): status chip and progress, the
/// player on the block (photo, role, current bid, minimum next bid), recent bids, then a docked panel --
/// the bid form for a franchise owner, organizer controls, or both (dual roles are allowed). Results
/// replace the block once the auction ends. Kept live by the ViewModel's stream; no manual refresh.
struct AuctionLiveView: View {
    let leagueId: String
    var onBack: () -> Void = {}

    @StateObject private var wrapper: AuctionViewModelWrapper
    @StateObject private var block = BlockViewModelWrapper()

    init(leagueId: String, onBack: @escaping () -> Void = {}) {
        self.leagueId = leagueId
        self.onBack = onBack
        _wrapper = StateObject(wrappedValue: AuctionViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        AuctionLiveContent(state: wrapper.state, blockedIds: block.blockedIds, actions: AuctionLiveActions(
            onBack: onBack, onRetry: wrapper.retry, onBidAmountChanged: wrapper.onBidAmountChanged, onSuggestionPicked: wrapper.onSuggestionPicked, onPlaceBid: wrapper.placeBid,
            onStepBid: wrapper.onStepBid, onStart: wrapper.start, onNextPlayer: wrapper.nextPlayer, onSold: wrapper.sold,
            onUnsold: wrapper.unsold, onUndo: wrapper.undo, onRequestEnd: wrapper.requestEnd, onToggleExceedPurse: wrapper.toggleExceedPurse,
            onClearEndFailure: wrapper.clearEndFailure))
            .crichereScreen(background: auctionBg)
            .lightStatusBarIcons(true)
            .onAppear { wrapper.retry() }
            .crichereOverlay(isPresented: Binding(get: { wrapper.state.isEndConfirmOpen }, set: { if !$0 { wrapper.dismissEnd() } })) {
                EndAuctionDialog(state: wrapper.state, onDismiss: wrapper.dismissEnd, onConfirm: wrapper.confirmEnd)
            }
    }
}

/// Everything the live auction screen does.
struct AuctionLiveActions {
    var onBack: () -> Void = {}
    var onRetry: () -> Void = {}
    var onBidAmountChanged: (String) -> Void = { _ in }
    var onSuggestionPicked: (Double) -> Void = { _ in }
    var onPlaceBid: () -> Void = {}
    var onStepBid: () -> Void = {}
    var onStart: () -> Void = {}
    var onNextPlayer: () -> Void = {}
    var onSold: () -> Void = {}
    var onUnsold: () -> Void = {}
    var onUndo: () -> Void = {}
    var onRequestEnd: () -> Void = {}
    var onToggleExceedPurse: (Bool) -> Void = { _ in }
    var onClearEndFailure: () -> Void = {}
}

/// The screen, stateless for the DEBUG parity gallery.
struct AuctionLiveContent: View {
    let state: AuctionState
    var blockedIds: Set<String> = []
    var actions = AuctionLiveActions()

    var body: some View {
        VStack(spacing: 0) {
            AuctionTopBar(onBack: actions.onBack)
            if let auction = state.auction {
                LoadedAuction(state: state, auction: auction, blockedIds: blockedIds, actions: actions)
            } else if state.loadFailed {
                AuctionLoadFailed(onRetry: actions.onRetry)
            } else {
                AuctionLoading()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(auctionBg.ignoresSafeArea())
    }
}

private struct LoadedAuction: View {
    let state: AuctionState
    let auction: AuctionStateDto
    let blockedIds: Set<String>
    let actions: AuctionLiveActions

    private var phase: ConnectionPhase { state.connectionPhase }
    private var offline: Bool { phase == .reconnecting || phase == .lost }
    private var franchises: [LeagueFranchiseDto] { state.league?.franchises ?? [] }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 0) {
                        StatusChip(status: auction.auctionStatus, pulsing: !offline && !state.isDeadEnd)
                            .opacity(phase == .lost ? 0.5 : 1)
                        ConnectionPill(phase: phase, onRetry: actions.onRetry)
                    }
                    if auction.auctionStatus == .inProgress && auction.playersTotal > 0 {
                        Spacer().frame(height: 6)
                        ProgressLine(auction: auction, lost: phase == .lost, lastEventAt: state.lastEventAtMillis?.int64Value)
                    }
                    // U4 A4: content stays visible at 50% while the feed is down, back to 100% over 200 ms.
                    VStack(alignment: .leading, spacing: 0) {
                        statusContent
                        // Board V2: quiet, at the end of the auction content.
                        Spacer().frame(height: 16)
                        Text(auctionMoneyNote).crichere(.sans(12, lineHeight: 18), muted).multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                    }
                    .opacity(offline ? 0.5 : 1)
                    .animation(.easeOut(duration: 0.2), value: offline)
                }
                .padding(.horizontal, 16)
                .padding(.top, 2)
                .padding(.bottom, 16)
            }
            .scrollDismissesKeyboard(.interactively)
            // U5 L21: inverse bar 12 pt above the dock. Retry reopens the dialog rather than resending.
            if let failure = state.endFailure {
                CrichereSnackbar(message: failure == .network ? "Couldn't end the auction. Check your connection and try again."
                                    : "Couldn't end the auction right now. Try again in a moment.",
                                 actionLabel: "Retry", onAction: actions.onRequestEnd, inverse: true)
                    .padding(.horizontal, 13)
                    .padding(.bottom, 12)
            }
            AuctionDock(state: state, auction: auction, franchises: franchises, blockedIds: blockedIds, offline: offline, actions: actions)
        }
    }

    @ViewBuilder private var statusContent: some View {
        switch auction.auctionStatus {
        case .notStarted:
            Spacer().frame(height: 12)
            NoticeCardDark(title: state.isOrganizer ? "Ready when you are" : "The auction hasn't started",
                           message: state.isOrganizer ? "Current bid: none yet. Start the auction, then bring up the first player."
                               : "It starts when the organizer opens it. This screen updates on its own.")
        case .inProgress:
            Spacer().frame(height: 12)
            if auction.currentPlayerId != nil {
                BlockCard(auction: auction, minimumNextBid: state.minimumNextBid?.doubleValue, stepLabels: state.stepLabels, franchises: franchises,
                          players: state.league?.players ?? [], blockedIds: blockedIds)
                Spacer().frame(height: 15)
                RecentBids(bids: auction.recentBids, franchises: franchises, blockedIds: blockedIds)
            } else if state.isDeadEnd {
                DeadEndCard(auction: auction, basePrice: state.league?.auctionBasePrice?.doubleValue, isOrganizer: state.isOrganizer)
            } else {
                EmptyBlockCard(auction: auction, isOrganizer: state.isOrganizer)
            }
        case .completed:
            Spacer().frame(height: 12)
            Text("This auction has ended.").crichere(.sans(13.5, .medium, lineHeight: 17.55), .white)
            Spacer().frame(height: 16)
            AuctionResults(results: state.results?.franchises ?? [], franchises: franchises, purse: state.league?.auctionPurse?.doubleValue,
                           blockedIds: blockedIds)
        }
    }
}

/// "Player 4 of 40" / "3 of 40 done"; while the feed is lost, "· updated 34s ago" ticking each second (U4 L16).
private struct ProgressLine: View {
    let auction: AuctionStateDto
    let lost: Bool
    let lastEventAt: Int64?

    var body: some View {
        let progress = auction.currentPlayerId != nil ? "Player \(auction.playersSold + 1) of \(auction.playersTotal)"
            : "\(auction.playersSold) of \(auction.playersTotal) done"
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let seconds = lastEventAt.map { max(0, (Int64(context.date.timeIntervalSince1970 * 1000) - $0) / 1000) } ?? 0
            Text(lost ? "\(progress) · updated \(seconds)s ago" : progress).crichere(.sans(11.5, .medium, lineHeight: 11.5), muted)
                .padding(.leading, 2)
        }
    }
}

/// U5 L19a/b and L20: the dialog shell in the auction's dark tokens -- scrim .6, surface with a hairline,
/// coral confirm. While ending: spinner + "Ending…", Cancel at 38%, and the scrim does nothing.
struct EndAuctionDialog: View {
    let state: AuctionState
    let onDismiss: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        let ending = state.isEnding
        let body = state.endAuctionBody
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea().onTapGesture { if !ending { onDismiss() } }
            VStack(alignment: .leading, spacing: 0) {
                Text("End the auction?").crichere(.sans(19, .semiBold, lineHeight: 22.8).tight, .white)
                Spacer().frame(height: 12)
                (Text(body.prefix) + Text(body.emphasis).foregroundColor(.white).font(CrichereTextStyle.sans(13.5, .semiBold).font) + Text(body.suffix))
                    .crichere(.sans(13.5, lineHeight: 19.6).tight, textDim)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer().frame(height: 18)
                HStack(spacing: 22) {
                    Spacer(minLength: 0)
                    Button(action: onDismiss) { Text("Cancel").crichere(.sans(14, .semiBold, lineHeight: 20).tight, textSoft) }
                        .buttonStyle(.plain)
                        .opacity(ending ? 0.38 : 1)
                        .allowsHitTesting(!ending)  // .disabled would dim a .plain button a second time
                    Button(action: onConfirm) {
                        HStack(spacing: 8) {
                            if ending { CrichereSpinner(color: coral, track: coral.opacity(0.25), lineWidth: 2, size: 16) }
                            Text(ending ? "Ending…" : "End auction").crichere(.sans(14, .semiBold, lineHeight: 20).tight, coral)
                        }
                    }
                    .buttonStyle(.plain)
                    .allowsHitTesting(!ending)
                }
            }
            .padding(25)
            .background(surface, in: shape)
            .overlay(shape.strokeBorder(hairline, lineWidth: 1))
            .padding(.horizontal, 22)
        }
    }
}

private struct AuctionTopBar: View {
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Button(action: onBack) { CrIcon("ic_arrow_back", size: 24, tint: .white).frame(width: 48, height: 48) }
                .buttonStyle(PressHighlight(shape: Circle(), color: .white.opacity(0.12)))
                .accessibilityLabel("Back")
            Text("Live Auction").crichere(.archivo(16, .bold, lineHeight: 16), .white)
            Spacer(minLength: 0)
        }
        .padding(.leading, 5)
        .padding(.top, 3)
        .frame(height: 51, alignment: .bottom)
    }
}

/// "● Live" (pulsing), "Not started", "Ended". The dot holds still while the feed is down or nothing can sell.
private struct StatusChip: View {
    let status: AuctionStatus
    let pulsing: Bool

    var body: some View {
        let live = status == .inProgress
        HStack(spacing: 7) {
            if live {
                ZStack {
                    if pulsing {
                        TimelineView(.animation) { context in
                            let pulse = CGFloat(context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.4) / 1.4)
                            Circle().fill(gold.opacity(0.35 * (1 - pulse))).frame(width: 8 + 10 * pulse, height: 8 + 10 * pulse)
                        }
                    }
                    Circle().fill(gold).frame(width: 8, height: 8)
                }
                .frame(width: 8, height: 8)
            }
            Text(status == .inProgress ? "Live" : status == .notStarted ? "Not started" : "Ended")
                .crichere(.sans(12, .semiBold, lineHeight: 12), live ? gold : textDim)
        }
        .padding(.leading, live ? 9 : 10)
        .padding(.trailing, live ? 11 : 10)
        .frame(height: 26)
        .background(live ? gold.opacity(0.14) : Color.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
    }
}

/// U4 A4: the connection state as a pill beside the status chip. The whole "Connection lost" pill is the Retry target.
private struct ConnectionPill: View {
    let phase: ConnectionPhase
    let onRetry: () -> Void

    var body: some View {
        ZStack {
            if phase != .online {
                pill.transition(.opacity).id(phase)
            }
        }
        .animation(.easeOut(duration: 0.15), value: phase)
    }

    @ViewBuilder private var pill: some View {
        let shape = RoundedRectangle(cornerRadius: 13, style: .continuous)
        let content = HStack(spacing: 6) {
            switch phase {
            case .lost:
                CrIcon("ic_cloud_off", size: 15, tint: coral)
                Text("Connection lost").crichere(.sans(12, .semiBold, lineHeight: 12).tight, coral)
                Text("Retry").crichere(.sans(11.5, .bold, lineHeight: 11.5).tight, auctionBg)
                    .padding(.horizontal, 9).frame(height: 22).background(coral, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
            case .backOnline:
                CrIcon("ic_check", size: 15, tint: liveFeed)
                Text("Back online").crichere(.sans(12, .semiBold, lineHeight: 12).tight, liveFeed)
            default:
                CrichereSpinner(color: textDim, lineWidth: 2, size: 12)
                Text("Reconnecting…").crichere(.sans(12, .semiBold, lineHeight: 12).tight, textDim).padding(.leading, 1)
            }
        }
        .padding(.leading, phase == .backOnline ? 8 : 9)
        .padding(.trailing, phase == .lost ? 4 : 11)
        .frame(height: 26)
        .background(phase == .lost ? coral.opacity(0.14) : phase == .backOnline ? liveFeed.opacity(0.14) : Color.white.opacity(0.07), in: shape)
        .padding(.leading, 8)
        if phase == .lost {
            Button(action: onRetry) { content }.buttonStyle(.plain).accessibilityLabel("Connection lost. Retry")
        } else {
            content.accessibilityElement(children: .ignore).accessibilityLabel(phase == .backOnline ? "Back online" : "Reconnecting")
        }
    }
}

/// L1/L2/L4: photo, name, role, current bid with the leading franchise, minimum next bid (U4 A5 geometry).
private struct BlockCard: View {
    let auction: AuctionStateDto
    let minimumNextBid: Double?
    var stepLabels: [StepLabel] = []
    let franchises: [LeagueFranchiseDto]
    let players: [LeaguePlayerDto]
    let blockedIds: Set<String>

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            header
            Spacer().frame(height: 14)
            hairline.frame(height: 1)
            Spacer().frame(height: 14)
            Text("Current bid").crichere(.sans(12, .medium, lineHeight: 16).tight, muted)
            Spacer().frame(height: 4)
            bidRow.frame(height: 40, alignment: .bottom)
            if let minimumNextBid {
                Spacer().frame(height: 4)
                (Text(auction.currentBidAmount == nil ? "Opening bid at least " : "Next bid at least ")
                    + Text(rupees(minimumNextBid)).font(CrichereTextStyle.mono(12, .medium).font).foregroundColor(textDim))
                    .crichere(.sans(12, .medium, lineHeight: 16).tight, muted)
            }
            if !stepLabels.isEmpty {
                // Design L1 (docs/PHASE22.md): the league's bid steps, the current bid's range lit.
                Spacer().frame(height: 10)
                FlowRow(spacing: 6) {
                    ForEach(Array(stepLabels.enumerated()), id: \.offset) { _, label in
                        Text(label.text).crichere(.mono(10.5, .semiBold, lineHeight: 14), label.isCurrent ? auctionBg : muted)
                            .padding(.horizontal, 7).padding(.vertical, 4)
                            .background(RoundedRectangle(cornerRadius: 7).fill(label.isCurrent ? gold : Color.clear))
                            .overlay(RoundedRectangle(cornerRadius: 7).strokeBorder(label.isCurrent ? gold : hairline))
                    }
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Bid steps")
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(surface, in: shape)
        .overlay(shape.strokeBorder(hairline, lineWidth: 1))
    }

    private var header: some View {
        let name = auction.currentPlayerName ?? "Unnamed player"
        let userId = players.first { $0.id == auction.currentPlayerId }?.userId
        let hidden = auction.currentPlayerPhotoRemoved || (userId.map { blockedIds.contains($0) } ?? false)
        return HStack(spacing: 14) {
            if hidden {
                DeletedUserAvatar(size: 88, iconSize: 48, background: deletedDark, tint: deletedNameDark, cornerRadius: 20)
            } else {
                PlayerPhoto(url: auction.currentPlayerPhotoUrl, name: name, size: 88, radius: 20, initialsSize: 30)
            }
            VStack(alignment: .leading, spacing: 6) {
                Text("ON THE BLOCK").crichere(.mono(10, .semiBold, lineHeight: 12, tracking: 0.8).tight, gold)
                Text(name).crichere(.archivo(22, .extraBold, lineHeight: 26).tight, .white).lineLimit(2)
                if let role = auction.currentPlayerRole {
                    Text(role.label).crichere(.sans(11.5, .semiBold, lineHeight: 11.5).tight, textSoft).lineLimit(1)
                        .padding(.horizontal, 9)
                        .frame(height: 22)
                        .overlay(RoundedRectangle(cornerRadius: 11, style: .continuous).strokeBorder(Color.white.opacity(0.16), lineWidth: 1))
                }
            }
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder private var bidRow: some View {
        if let amount = auction.currentBidAmount?.doubleValue {
            // The amount never truncates; the leader's name gives way instead.
            HStack(alignment: .bottom, spacing: 10) {
                Text(rupees(amount)).crichere(.mono(36, .bold, lineHeight: 40, tracking: -0.72).tight, .white).lineLimit(1).fixedSize()
                if let leaderId = auction.currentLeadingFranchiseId {
                    HStack(spacing: 7) {
                        Spacer(minLength: 0)
                        FranchiseTile(id: leaderId, name: auction.currentLeadingFranchiseName ?? "", franchises: franchises, blockedIds: blockedIds,
                                      size: 20, radius: 6, fontSize: 7)
                        Text(auction.currentLeadingFranchiseName ?? "").crichere(.sans(13, .semiBold, lineHeight: 16).tight, textSoft).lineLimit(1)
                    }
                    .padding(.bottom, 6)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            Text("No bids yet").crichere(.sans(20, .semiBold, lineHeight: 28).tight, textSoft).padding(.bottom, 4)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// "Last: Rohan Patil sold to Kolhapur Kings for ₹15,000." / "Last: Rohan Patil went unsold."
private func lastResultLine(_ auction: AuctionStateDto) -> String? {
    guard let last = auction.lastResult else { return nil }
    let name = last.playerName ?? "The last player"
    guard last.sold else { return "Last: \(name) went unsold." }
    return "Last: \(name) sold to \(last.franchiseName ?? "a franchise")" + (last.amount.map { " for \(rupees($0.doubleValue))." } ?? ".")
}

/// L8: nobody up -- what just happened, and (organizer) what to do next.
private struct EmptyBlockCard: View {
    let auction: AuctionStateDto
    let isOrganizer: Bool

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        let next = isOrganizer ? "Bring up the next player." : "Waiting for the organizer to bring up the next player."
        VStack(alignment: .leading, spacing: 0) {
            Text("ON THE BLOCK").crichere(.mono(10, .semiBold, lineHeight: 10, tracking: 0.8), muted)
            Spacer().frame(height: 8)
            Text("No player up yet").crichere(.archivo(18, .bold, lineHeight: 21.6), .white)
            Spacer().frame(height: 7)
            Text([lastResultLine(auction), next].compactMap { $0 }.joined(separator: " ")).crichere(.sans(13, lineHeight: 18.85), muted)
        }
        .padding(17)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(surface, in: shape)
        .overlay(shape.strokeBorder(Color.white.opacity(0.18), style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
    }
}

/// U4 A2: live, nobody up, and no franchise can open a bid. The organizer gets the reason with a breakdown.
private struct DeadEndCard: View {
    let auction: AuctionStateDto
    let basePrice: Double?
    let isOrganizer: Bool

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            CrIcon(isOrganizer ? "ic_block" : "ic_hourglass_empty", size: 22, tint: isOrganizer ? coral : muted)
                .frame(width: 40, height: 40)
                .background(isOrganizer ? coral.opacity(0.14) : Color.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            Spacer().frame(height: 10)
            Text(isOrganizer ? "No franchise can bid on the remaining players" : "Bidding has closed")
                .crichere(.archivo(18, .bold, lineHeight: 21.6).tight, .white)
            Spacer().frame(height: 10)
            explanation.crichere(.sans(13, lineHeight: 18.85).tight, textDim).fixedSize(horizontal: false, vertical: true)
            if isOrganizer {
                Spacer().frame(height: 10)
                hairline.frame(height: 1)
                Spacer().frame(height: 12)
                BreakdownRow(label: "Squads full", value: "\(auction.squadsFull) of \(auction.franchisesTotal)")
                Spacer().frame(height: 6)
                BreakdownRow(label: "Purse below base price", value: "\(auction.purseBelowBase) of \(auction.franchisesTotal)")
                Spacer().frame(height: 6)
                BreakdownRow(label: "Left in pool", value: "\(auction.playersPending) \(auction.playersPending == 1 ? "player" : "players")")
            }
            if let line = lastResultLine(auction) {
                Spacer().frame(height: 10)
                Text(line).crichere(.sans(12, lineHeight: 16.8).tight, muted)
            }
        }
        .padding(.horizontal, 17)
        .padding(.vertical, 19)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(surface, in: shape)
        .overlay(shape.strokeBorder(isOrganizer ? coral.opacity(0.35) : hairline, lineWidth: 1))
    }

    private var explanation: Text {
        guard isOrganizer else { return Text("No franchise can buy the remaining players. Waiting for the organizer to end the auction.") }
        let base = basePrice.map { rupees($0) } ?? "base"
        var text: Text
        switch AuctionPresentationKt.deadEndKind(squadsFull: auction.squadsFull, purseBelowBase: auction.purseBelowBase) {
        case .allFull: text = Text("Every squad is full, so nothing else can sell. End the auction to publish the results.")
        case .allPurse: text = Text("Every purse is below the \(base) base price, so nothing else can sell. End the auction, or turn on ")
        case .mixed: text = Text("Squads are full or purses are below the \(base) base price, so nothing else can sell. End the auction, or turn on ")
        }
        if auction.purseBelowBase > 0 {
            text = text + Text("Allow exceeding purse").foregroundColor(.white).font(CrichereTextStyle.sans(13, .semiBold).font)
                + Text(" to let franchises keep buying.")
        }
        return text
    }
}

private struct BreakdownRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label).crichere(.sans(12.5, .medium, lineHeight: 12.5).tight, muted).frame(maxWidth: .infinity, alignment: .leading)
            Text(value).crichere(.mono(12.5, .medium, lineHeight: 12.5).tight, textSoft)
        }
    }
}

/// L3 / L9: the not-started card.
private struct NoticeCardDark: View {
    let title: String
    let message: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            CrIcon("ic_schedule", size: 28, tint: muted).padding(.top, 3)
            Spacer().frame(height: 11)
            Text(title).crichere(.archivo(18, .bold, lineHeight: 21.6), .white)
            Spacer().frame(height: 8)
            Text(message).crichere(.sans(13, lineHeight: 18.85), muted)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

// MARK: - Bids and results

private struct RecentBids: View {
    let bids: [AuctionBidTickerDto]
    let franchises: [LeagueFranchiseDto]
    let blockedIds: Set<String>

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Recent bids").crichere(.archivo(13, .bold, lineHeight: 13), .white)
            Spacer().frame(height: 12)
            if bids.isEmpty {
                Text("No bids yet.").crichere(.sans(13, lineHeight: 18.2), muted)
            } else {
                // "12s ago" ticks along without waiting for the next bid.
                TimelineView(.periodic(from: .now, by: 5)) { context in
                    VStack(spacing: 2) {
                        ForEach(Array(bids.prefix(5).enumerated()), id: \.offset) { index, bid in
                            row(bid, top: index == 0, now: context.date)
                        }
                    }
                }
            }
        }
    }

    private func row(_ bid: AuctionBidTickerDto, top: Bool, now: Date) -> some View {
        HStack(spacing: 10) {
            FranchiseTile(id: bid.franchiseId, name: bid.franchiseName ?? "", franchises: franchises, blockedIds: blockedIds, size: 24, radius: 7, fontSize: 9)
            Text(bid.franchiseName ?? "Franchise").crichere(.sans(13, .semiBold, lineHeight: 13), top ? .white : textDim).lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(rupees(bid.amount)).crichere(.mono(13, .bold, lineHeight: 13), top ? gold : textSoft)
            Text(timeAgo(bid.placedAt, now: now)).crichere(.sans(11.5, .medium, lineHeight: 11.5), muted).frame(width: 46, alignment: .trailing)
        }
        .padding(.horizontal, 10)
        .frame(height: 36)
        .background(top ? gold.opacity(0.08) : .clear, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

/// "12s ago", "3m ago", "1h ago" from an ISO instant; blank if it can't be parsed.
private func timeAgo(_ iso: String, now: Date) -> String {
    let plain = ISO8601DateFormatter()
    let fractional = ISO8601DateFormatter()
    fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    guard let placed = plain.date(from: iso) ?? fractional.date(from: iso) else { return "" }
    let seconds = max(0, Int(now.timeIntervalSince(placed)))
    if seconds < 60 { return "\(seconds)s ago" }
    if seconds < 3600 { return "\(seconds / 60)m ago" }
    return "\(seconds / 3600)h ago"
}

/// L5: one expandable card per franchise -- spend, then the players it won with prices.
private struct AuctionResults: View {
    let results: [FranchiseAuctionResultDto]
    let franchises: [LeagueFranchiseDto]
    let purse: Double?
    let blockedIds: Set<String>

    @State private var expandedId: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Results").crichere(.archivo(14, .bold, lineHeight: 14), .white)
            Spacer().frame(height: 16)
            if results.isEmpty {
                Text("No franchises took part.").crichere(.sans(13, lineHeight: 18.2), muted)
            } else {
                VStack(spacing: 16) {
                    ForEach(results, id: \.franchiseId) { result in
                        let expanded = (expandedId ?? results.first?.franchiseId) == result.franchiseId
                        ResultCard(result: result, franchises: franchises, purse: purse, blockedIds: blockedIds, expanded: expanded) {
                            expandedId = expanded ? "" : result.franchiseId
                        }
                    }
                }
            }
        }
    }
}

/// L5 / U4 L11: spend per franchise; over its purse, the amount over gets its own coral line and a Purse / Spent footer.
private struct ResultCard: View {
    let result: FranchiseAuctionResultDto
    let franchises: [LeagueFranchiseDto]
    let purse: Double?
    let blockedIds: Set<String>
    let expanded: Bool
    let onToggle: () -> Void

    @State private var showAll = false

    private var over: Double? { AuctionPresentationKt.overPurseAmount(purseRemaining: result.purseRemaining)?.doubleValue }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        let border = over != nil ? coral.opacity(0.45) : expanded ? gold.opacity(0.25) : Color.white.opacity(0.06)
        VStack(spacing: 0) {
            Button(action: onToggle) { header.contentShape(Rectangle()) }.buttonStyle(.plain)
            if expanded {
                Color.white.opacity(0.06).frame(height: 1)
                players
            }
        }
        .background(surface)
        .clipShape(shape)
        .overlay(shape.strokeBorder(border, lineWidth: 1))
    }

    private var header: some View {
        HStack(spacing: 10) {
            FranchiseTile(id: result.franchiseId, name: result.franchiseName, franchises: franchises, blockedIds: blockedIds, size: 32, radius: 9, fontSize: 12)
            VStack(alignment: .leading, spacing: 5) {
                Text(result.franchiseName).crichere(.sans(14, .semiBold, lineHeight: 14).tight, .white).lineLimit(1)
                summary.crichere(.sans(11.5, .medium, lineHeight: 11.5).tight, muted)
                if let over {
                    HStack(spacing: 4) {
                        CrIcon("ic_error", size: 14, tint: coral)
                        (Text(rupees(over)).font(CrichereTextStyle.mono(11.5, .semiBold).font) + Text(" over purse"))
                            .crichere(.sans(11.5, .semiBold, lineHeight: 11.5).tight, coral)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            CrIcon(expanded ? "ic_expand_less" : "ic_expand_more", size: 22, tint: muted)
                .accessibilityLabel(expanded ? "Collapse" : "Expand")
        }
        .padding(13)
    }

    private var summary: Text {
        let mono = CrichereTextStyle.mono(11.5, .medium).font
        let count = result.playersWon.count
        var text = Text("\(count) \(count == 1 ? "player" : "players") · ") + Text(rupees(result.purseSpent)).font(mono) + Text(" spent")
        if let left = result.purseRemaining?.doubleValue, over == nil {
            text = text + Text(" · ") + Text(rupees(left)).font(mono) + Text(" left")
        }
        if result.belowSquadMin { text = text + Text(" · below min squad").foregroundColor(coral) }
        return text
    }

    private var players: some View {
        VStack(alignment: .leading, spacing: 0) {
            if result.playersWon.isEmpty {
                Text("No players won.").crichere(.sans(13, lineHeight: 36), muted)
            }
            ForEach(Array((showAll ? result.playersWon : Array(result.playersWon.prefix(4))).enumerated()), id: \.offset) { _, player in
                let name = player.playerName ?? "Unnamed player"
                HStack(spacing: 10) {
                    // Design D10b: a deleted user gets the neutral tile and a muted name.
                    if player.deleted || player.photoRemoved || blockedIds.contains(player.userId) {
                        DeletedUserAvatar(size: 28, iconSize: 16, background: deletedDark, tint: deletedNameDark, cornerRadius: 8)
                    } else {
                        PlayerPhoto(url: player.photoUrl, name: name, size: 28, radius: 8, initialsSize: 10)
                    }
                    Text(name).crichere(.sans(13, .medium, lineHeight: 13), player.deleted ? deletedNameDark : textSoft).lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(rupees(player.soldPrice)).crichere(.mono(12.5, .semiBold, lineHeight: 12.5), .white)
                }
                .frame(height: 36)
            }
            if !showAll && result.playersWon.count > 4 {
                Button { showAll = true } label: {
                    Text("Show all \(result.playersWon.count)").crichere(.sans(12, .semiBold, lineHeight: 12), gold)
                        .padding(.vertical, 5).frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
            }
            if over != nil, let purse {
                Color.white.opacity(0.06).frame(height: 1)
                HStack {
                    (Text("Purse ") + Text(rupees(purse)).font(CrichereTextStyle.mono(12, .medium).font).foregroundColor(textSoft))
                        .crichere(.sans(12, .medium, lineHeight: 12).tight, muted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    (Text("Spent ") + Text(rupees(result.purseSpent)).font(CrichereTextStyle.mono(12, .medium).font))
                        .crichere(.sans(12, .medium, lineHeight: 12).tight, coral)
                }
                .frame(height: 31)
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 5)
        .padding(.bottom, 8)
    }
}

// MARK: - Dock

/// The docked panel: bid form (franchise owner, player up -- or a dead end, where it shows the owner's own
/// reason), organizer controls, or the viewer note. While the feed is down Place Bid and the organizer
/// buttons are disabled (U4 A4).
private struct AuctionDock: View {
    let state: AuctionState
    let auction: AuctionStateDto
    let franchises: [LeagueFranchiseDto]
    let blockedIds: Set<String>
    let offline: Bool
    let actions: AuctionLiveActions

    var body: some View {
        let live = auction.auctionStatus == .inProgress
        let playerUp = live && auction.currentPlayerId != nil
        let modeIsBid: Bool = { if case .bid = onEnum(of: state.dockMode) { return true } else { return false } }()
        let ownerAtDeadEnd = state.isDeadEnd && state.myFranchiseId != nil && !modeIsBid
        let canBid = (playerUp && state.myFranchiseId != nil) || ownerAtDeadEnd
        let viewerNote = playerUp && !canBid && !state.isOrganizer
        if canBid || state.isOrganizer || viewerNote {
            let shape = UnevenRoundedRectangle(topLeadingRadius: 22, topTrailingRadius: 22, style: .continuous)
            VStack(alignment: .leading, spacing: 0) {
                if viewerNote {
                    HStack(spacing: 8) {
                        CrIcon("ic_visibility", size: 18, tint: muted)
                        Text("Only franchise owners can bid.").crichere(.sans(13, .medium, lineHeight: 18.2), textDim)
                    }
                }
                if canBid { BidForm(state: state, franchises: franchises, blockedIds: blockedIds, offline: offline, actions: actions) }
                if state.isOrganizer {
                    if canBid { Spacer().frame(height: 16) }
                    OrganizerControls(state: state, auction: auction, offline: offline, actions: actions)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(shape.fill(dockColor).ignoresSafeArea(edges: .bottom))
            .overlay(shape.stroke(hairline, lineWidth: 1).ignoresSafeArea(edges: .bottom))
        }
    }
}

/// L1 / L6 / L7 and U4 A3: when the owner leads, their squad is full, or their purse can't cover the next
/// bid, a 54 pt status tile replaces the Amount field (cross-fade 180 ms) and both buttons go inert.
/// Outbid: the field returns with "Outbid · ₹X" inside it for 2 s and one haptic tick.
private struct BidForm: View {
    let state: AuctionState
    let franchises: [LeagueFranchiseDto]
    let blockedIds: Set<String>
    let offline: Bool
    let actions: AuctionLiveActions

    @State private var previous: (leading: Bool, playerId: String?) = (false, nil)
    @State private var outbidAmount: Double?

    private var modeIsBid: Bool { if case .bid = onEnum(of: state.dockMode) { return true } else { return false } }
    private var modeIsLeading: Bool { if case .leading = onEnum(of: state.dockMode) { return true } else { return false } }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let context = state.biddingContext {
                biddingAs(context)
                Spacer().frame(height: 6)
            }
            ZStack {
                if modeIsBid {
                    AmountField(value: state.bidAmountInput, onChange: actions.onBidAmountChanged, error: state.bidError,
                                enabled: !state.isBidding && !offline, outbidAmount: outbidAmount,
                                suggestions: state.bidSuggestions.map(\.doubleValue), onSuggestion: actions.onSuggestionPicked)
                        .transition(.opacity)
                } else {
                    DockStatusTile(mode: state.dockMode).transition(.opacity)
                }
            }
            .animation(.easeOut(duration: 0.18), value: modeIsBid)
            .padding(.top, 6)
            Spacer().frame(height: 12)
            buttons
        }
        .onChange(of: "\(modeIsLeading)-\(modeIsBid)-\(state.auction?.currentPlayerId ?? "")") { _, _ in
            let playerId = state.auction?.currentPlayerId
            if previous.leading && modeIsBid && previous.playerId == playerId, let amount = state.auction?.currentBidAmount?.doubleValue {
                outbidAmount = amount
                UISelectionFeedbackGenerator().selectionChanged()
            }
            previous = (modeIsLeading, playerId)
        }
        .onAppear { previous = (modeIsLeading, state.auction?.currentPlayerId) }
        .task(id: outbidAmount) {
            guard outbidAmount != nil else { return }
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            withAnimation(.easeOut(duration: 0.2)) { outbidAmount = nil }
        }
    }

    private func biddingAs(_ context: BiddingContext) -> some View {
        let over = AuctionPresentationKt.overPurseAmount(purseRemaining: context.purseLeft)?.doubleValue
        var text = Text("Bidding as ") + Text(context.franchiseName).foregroundColor(.white).font(CrichereTextStyle.sans(12.5, .semiBold).font)
        if let over {
            text = text + Text(" · ") + Text(rupees(over)).font(CrichereTextStyle.mono(12.5, .semiBold).font).foregroundColor(coral)
                + Text(" over purse").font(CrichereTextStyle.sans(12.5, .semiBold).font).foregroundColor(coral)
        } else if let left = context.purseLeft?.doubleValue {
            text = text + Text(" · \(rupees(left)) left")
        }
        if let squadMax = context.squadMax { text = text + Text(" · \(context.playersWon)/\(squadMax) players") }
        return HStack(alignment: .top, spacing: 7) {
            FranchiseTile(id: context.franchiseId, name: context.franchiseName, franchises: franchises, blockedIds: blockedIds, size: 18, radius: 5, fontSize: 6)
            text.crichere(.sans(12.5, .medium, lineHeight: 16.25), textDim).lineLimit(over != nil ? 2 : 1)
        }
    }

    private var buttons: some View {
        let inert = !modeIsBid || offline
        let inputEnabled = !state.isBidding && !offline && modeIsBid
        return HStack(spacing: 10) {
            if let increment = state.stepButtonAmount?.doubleValue {
                Button(action: actions.onStepBid) {
                    Text("+\(rupees(increment))").crichere(.mono(14, .bold, lineHeight: 14), inert ? muted.opacity(0.5) : gold)
                        .padding(.horizontal, 16).frame(height: 48)
                        .overlay(Capsule().strokeBorder(inert ? Color.white.opacity(0.12) : gold.opacity(0.45), lineWidth: 1))
                        .contentShape(Capsule())
                }
                .buttonStyle(PressHighlight(shape: Capsule(), color: gold.opacity(0.12)))
                .disabled(!inputEnabled)
                .opacity(state.isBidding ? 0.4 : 1)
            }
            Button(action: actions.onPlaceBid) {
                HStack(spacing: 8) {
                    if state.isBidding { CrichereSpinner(color: auctionBg, track: auctionBg.opacity(0.25), lineWidth: 2, size: 20) }
                    Text(state.isBidding ? "Placing…" : "Place Bid").crichere(.sans(14.5, .bold, lineHeight: 14.5), inert ? muted : auctionBg)
                }
                .frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
                .background(inert ? Color.white.opacity(0.08) : state.isBidding ? gold.opacity(0.35) : gold, in: Capsule())
                .contentShape(Capsule())
            }
            .buttonStyle(PressHighlight(shape: Capsule(), color: .black.opacity(0.12)))
            .disabled(!inputEnabled)
        }
    }
}

/// U4 A3 tile: leading (gold), or a rule that stops this franchise bidding (neutral -- it's not an error).
private struct DockStatusTile: View {
    let mode: DockMode

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        let leading: Bool = { if case .leading = onEnum(of: mode) { return true } else { return false } }()
        HStack(spacing: 10) {
            CrIcon(icon, size: 20, tint: leading ? gold : muted)
            VStack(alignment: .leading, spacing: 2) {
                title.crichere(.sans(14, .semiBold, lineHeight: 18).tight, .white).lineLimit(1)
                subtitle.crichere(.sans(12, lineHeight: 16).tight, muted).lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 15)
        .frame(height: 54)
        .background(leading ? gold.opacity(0.10) : Color.white.opacity(0.05), in: shape)
        .overlay(shape.strokeBorder(leading ? gold.opacity(0.35) : hairline, lineWidth: 1))
    }

    private var icon: String {
        switch onEnum(of: mode) {
        case .leading: return "ic_check_circle_filled"
        case .squadFull: return "ic_groups"
        default: return "ic_account_balance_wallet"
        }
    }

    private var title: Text {
        switch onEnum(of: mode) {
        case let .leading(leading):
            return Text("You're leading at ") + Text(rupees(leading.amount)).font(CrichereTextStyle.mono(14, .semiBold).font).foregroundColor(gold)
        case let .squadFull(full): return Text("Your squad is full (\(full.playersWon)/\(full.squadMax)).")
        default: return Text("Your purse can't cover the next bid.")
        }
    }

    private var subtitle: Text {
        switch onEnum(of: mode) {
        case .leading: return Text("Bidding reopens if someone outbids you.")
        case .squadFull: return Text("You can keep watching. Bidding is off for you.")
        case let .purseShort(short):
            return Text("Next bid at least ") + Text(rupees(short.minimumNextBid)).font(CrichereTextStyle.mono(12, .normal).font).foregroundColor(textDim)
        case .bid: return Text("")
        }
    }
}

/// The ₹ amount field in the dock's dark style (U4 A6): notched label; idle 1 pt white .16, focused 2 pt
/// gold, error 2 pt coral, disabled 1 pt white .08 with the value at 40%. Digits only, at most 9, shown
/// with Indian grouping. [outbidAmount] shows "Outbid · ₹X" inside it.
private struct AmountField: View {
    let value: String
    let onChange: (String) -> Void
    let error: String?
    let enabled: Bool
    let outbidAmount: Double?
    /// Design L2: "₹2,200 isn't a bid step. Try [₹2,000] [₹2,500]" -- each fills the amount in.
    var suggestions: [Double] = []
    var onSuggestion: (Double) -> Void = { _ in }

    @FocusState private var focused: Bool

    var body: some View {
        let thick = enabled && (error != nil || focused)
        let border: Color = !enabled ? Color.white.opacity(0.08) : error != nil ? coral : focused ? gold : Color.white.opacity(0.16)
        let label: Color = !enabled ? muted.opacity(0.6) : error != nil ? coral : focused ? gold : muted
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 4) {
                Text("₹").crichere(.mono(18, .semiBold, lineHeight: 18), enabled ? muted : Color.white.opacity(0.4))
                TextField("", text: Binding(
                    get: { AmountGroupingKt.groupIndianAmount(raw: value).text },
                    set: { text in
                        let digits = text.filter(\.isNumber)
                        if digits.count <= 9 { onChange(digits) }
                    }))
                    .font(CrichereTextStyle.mono(18, .semiBold).font)
                    .foregroundStyle(enabled ? Color.white : Color.white.opacity(0.4))
                    .tint(gold)
                    .keyboardType(.numberPad)
                    .focused($focused)
                    .disabled(!enabled)
                if let outbidAmount {
                    (Text("Outbid · ") + Text(rupees(outbidAmount)).font(CrichereTextStyle.mono(11.5, .semiBold).font))
                        .crichere(.sans(11.5, .semiBold, lineHeight: 11.5).tight, coral)
                        .padding(.leading, 6)
                        .transition(.opacity)
                }
                if error != nil { CrIcon("ic_error", size: 20, tint: coral) }
            }
            .padding(.horizontal, 16)
            .frame(height: 54)
            .overlay(shape.strokeBorder(border, lineWidth: thick ? 2 : 1))
            .overlay(alignment: .topLeading) {
                Text("Amount").crichere(.sans(11, .medium, lineHeight: 11), label)
                    .padding(.horizontal, 4)
                    .background(dockColor)
                    .offset(x: thick ? 12 : 11, y: -6)
            }
            if let error {
                Spacer().frame(height: 6)
                if suggestions.isEmpty {
                    Text(error).crichere(.sans(12, .medium, lineHeight: 16).tight, coral).padding(.leading, 4)
                } else {
                    FlowRow(spacing: 8) {
                        Text(error + " Try").crichere(.sans(12.5, .medium, lineHeight: 16), coral).frame(minHeight: 40)
                        ForEach(suggestions, id: \.self) { amount in
                            Button { onSuggestion(amount) } label: {
                                Text(rupees(amount)).crichere(.mono(13.5, .bold, lineHeight: 14), gold)
                                    .padding(.horizontal, 14).frame(height: 40)
                                    .background(Capsule().fill(gold.opacity(0.1)))
                                    .overlay(Capsule().strokeBorder(gold, lineWidth: 1.5))
                                    .contentShape(Capsule())
                            }
                            .buttonStyle(PressHighlight(shape: Capsule(), color: gold.opacity(0.15)))
                        }
                    }
                    .padding(.leading, 4)
                }
            }
        }
    }
}

/// L2 (player up), L8 (between players), L3 (not started), L5 (ended).
private struct OrganizerControls: View {
    let state: AuctionState
    let auction: AuctionStateDto
    let offline: Bool
    let actions: AuctionLiveActions

    private var acting: Bool { state.isActing || offline }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Organizer controls").crichere(.archivo(13, .bold, lineHeight: 13), .white)
            Spacer().frame(height: 12)
            controls
            if let error = state.actionError {
                Spacer().frame(height: 10)
                Text(error).crichere(.sans(12, .medium, lineHeight: 16.2), coral)
            }
        }
    }

    @ViewBuilder private var controls: some View {
        switch auction.auctionStatus {
        case .notStarted:
            DockButton(label: "Start Auction", icon: "ic_play_arrow", enabled: !acting, primary: true, action: actions.onStart)
        case .completed:
            DockButton(label: "Undo", icon: "ic_undo", enabled: !acting, action: actions.onUndo)
        case .inProgress:
            if auction.currentPlayerId != nil {
                SoldButton(auction: auction, enabled: !acting && auction.currentLeadingFranchiseId != nil, action: actions.onSold)
                Spacer().frame(height: 12)
                HStack(spacing: 8) {
                    DockButton(label: "Unsold", icon: "ic_block", enabled: !acting, action: actions.onUnsold)
                    // Owner decision 2026-10-02: shown but disabled while a player is up.
                    DockButton(label: "Next Player", icon: "ic_skip_next", enabled: false, action: actions.onNextPlayer)
                    DockButton(label: "Undo", icon: "ic_undo", enabled: !acting, action: actions.onUndo)
                }
                Spacer().frame(height: 14)
                ExceedPurseRow(allow: auction.allowExceedPurse, enabled: !acting, onToggle: actions.onToggleExceedPurse)
                Spacer().frame(height: 12)
                EndAuctionOutlined(enabled: !acting && !state.isEnding, action: actions.onRequestEnd)
            } else if state.isDeadEnd {
                deadEndControls
            } else {
                DockButton(label: "Next Player", icon: "ic_skip_next", enabled: !acting, primary: true, action: actions.onNextPlayer)
                Spacer().frame(height: 12)
                DockButton(label: "Undo", icon: "ic_undo", enabled: !acting, action: actions.onUndo)
                Spacer().frame(height: 14)
                ExceedPurseRow(allow: auction.allowExceedPurse, enabled: !acting, onToggle: actions.onToggleExceedPurse)
                Spacer().frame(height: 12)
                EndAuctionOutlined(enabled: !acting && !state.isEnding, action: actions.onRequestEnd)
            }
        }
    }

    /// U4 A2: nothing can sell. End Auction becomes the filled main button; the switch is highlighted
    /// only when purses (not squads) are the block.
    @ViewBuilder private var deadEndControls: some View {
        HStack(spacing: 8) {
            DockButton(label: "Next Player", icon: "ic_skip_next", enabled: !acting, action: actions.onNextPlayer)
            DockButton(label: "Undo", icon: "ic_undo", enabled: !acting, action: actions.onUndo)
        }
        Spacer().frame(height: 12)
        if auction.purseBelowBase > 0 {
            let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Allow exceeding purse").crichere(.sans(13, .medium, lineHeight: 16.9).tight, .white)
                    Text(auction.purseBelowBase == 1 ? "1 franchise is stopped by its purse" : "\(auction.purseBelowBase) franchises are stopped by their purse")
                        .crichere(.sans(11.5, lineHeight: 14.95).tight, textDim)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ExceedPurseSwitch(allow: auction.allowExceedPurse, enabled: !acting, onToggle: actions.onToggleExceedPurse)
            }
            .padding(.horizontal, 13)
            .padding(.vertical, 11)
            .background(gold.opacity(0.08), in: shape)
            .overlay(shape.strokeBorder(gold.opacity(0.35), lineWidth: 1))
        } else {
            ExceedPurseRow(allow: auction.allowExceedPurse, enabled: !acting, onToggle: actions.onToggleExceedPurse)
        }
        Spacer().frame(height: 12)
        Button(action: actions.onRequestEnd) {
            HStack(spacing: 6) {
                CrIcon("ic_stop_circle", size: 18, tint: auctionBg)
                Text("End Auction").crichere(.sans(13, .semiBold, lineHeight: 13), auctionBg)
            }
            .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
            .background(coral, in: Capsule())
        }
        .buttonStyle(PressHighlight(shape: Capsule(), color: .black.opacity(0.12)))
        .disabled(acting || state.isEnding)
        .opacity(acting ? 0.4 : 1)
    }
}

private struct ExceedPurseRow: View {
    let allow: Bool
    let enabled: Bool
    let onToggle: (Bool) -> Void

    var body: some View {
        HStack {
            Text("Allow exceeding purse").crichere(.sans(13, .medium, lineHeight: 16.9), textSoft).padding(.leading, 2)
                .frame(maxWidth: .infinity, alignment: .leading)
            ExceedPurseSwitch(allow: allow, enabled: enabled, onToggle: onToggle)
        }
    }
}

/// Material3 Switch in the auction colours: 52 x 32 track; gold with a dark 24 pt thumb when on, white .14
/// with a muted 16 pt thumb when off. Drawn in a 24 pt row as on Android.
private struct ExceedPurseSwitch: View {
    let allow: Bool
    let enabled: Bool
    let onToggle: (Bool) -> Void

    var body: some View {
        Button { onToggle(!allow) } label: {
            ZStack(alignment: allow ? .trailing : .leading) {
                Capsule().fill(allow ? gold : Color.white.opacity(0.14)).frame(width: 52, height: 32)
                Circle().fill(allow ? auctionBg : muted).frame(width: allow ? 24 : 16, height: allow ? 24 : 16)
                    .padding(.horizontal, allow ? 4 : 8)
            }
            .frame(height: 24)
            .animation(.easeOut(duration: 0.15), value: allow)
        }
        .buttonStyle(.plain)
        .allowsHitTesting(enabled)  // .disabled would dim a .plain button on top of the 38%
        .opacity(enabled ? 1 : 0.38)
        .accessibilityLabel("Allow exceeding purse")
        .accessibilityValue(allow ? "On" : "Off")
    }
}

private struct EndAuctionOutlined: View {
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                CrIcon("ic_stop_circle", size: 18, tint: coral)
                Text("End Auction").crichere(.sans(13, .semiBold, lineHeight: 13), coral)
            }
            .frame(maxWidth: .infinity, minHeight: 40, maxHeight: 40)
            .overlay(Capsule().strokeBorder(coral.opacity(0.4), lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(PressHighlight(shape: Capsule(), color: coral.opacity(0.1)))
        .disabled(!enabled)
    }
}

/// "Sold to Kolhapur Kings · ₹15,000" -- just "Sold", dimmed, until someone bids.
private struct SoldButton: View {
    let auction: AuctionStateDto
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        let leader = auction.currentLeadingFranchiseName
        var label = Text(leader.map { "Sold to \($0)" } ?? "Sold")
        if leader != nil, let amount = auction.currentBidAmount?.doubleValue {
            label = label + Text(" · ") + Text(rupees(amount)).font(CrichereTextStyle.mono(14, .bold).font)
        }
        return Button(action: action) {
            HStack(spacing: 7) {
                CrIcon("ic_gavel", size: 18, tint: auctionBg)
                label.crichere(.sans(14, .bold, lineHeight: 14), auctionBg).lineLimit(1)
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
            .background(gold, in: Capsule())
        }
        .buttonStyle(PressHighlight(shape: Capsule(), color: .black.opacity(0.12)))
        .disabled(!enabled)
        .opacity(leader != nil ? 1 : 0.4)
    }
}

private struct DockButton: View {
    let label: String
    let icon: String
    let enabled: Bool
    var primary: Bool = false
    let action: () -> Void

    var body: some View {
        let content: Color = primary ? auctionBg : .white
        Button(action: action) {
            HStack(spacing: 6) {
                CrIcon(icon, size: 18, tint: content)
                Text(label).crichere(.sans(13, .semiBold, lineHeight: 13), content).lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
            .background(primary ? gold : ghostButton, in: Capsule())
        }
        .buttonStyle(PressHighlight(shape: Capsule(), color: .white.opacity(0.08)))
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
    }
}

/// A franchise's logo, or its initials on its tile colour.
private struct FranchiseTile: View {
    let id: String
    let name: String
    let franchises: [LeagueFranchiseDto]
    let blockedIds: Set<String>
    let size: CGFloat
    let radius: CGFloat
    let fontSize: CGFloat

    var body: some View {
        let franchise = franchises.first { $0.id == id }
        if let franchise, franchise.logoRemoved || blockedIds.contains(franchise.ownerUserId) {
            DeletedUserAvatar(size: size, iconSize: size * 0.55, background: deletedDark, tint: deletedNameDark, cornerRadius: radius)
        } else {
            ZStack {
                tileColor(id)
                Text(shortCode(name.isEmpty ? "?" : name)).crichere(.archivo(fontSize, .extraBold, lineHeight: fontSize), .white)
                if let logo = franchise?.logoUrl { RemoteImage(url: logo) }
            }
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        }
    }
}

/// Profile photo, or gold initials when there's none / it fails (board L4).
private struct PlayerPhoto: View {
    let url: String?
    let name: String
    let size: CGFloat
    let radius: CGFloat
    let initialsSize: CGFloat

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        Group {
            if let url, let parsed = URL(string: url) {
                AsyncImage(url: parsed) { phase in
                    switch phase {
                    case let .success(image): image.resizable().scaledToFill()
                    case .failure: initialsBox
                    default: photoPlaceholder
                    }
                }
            } else {
                initialsBox
            }
        }
        .frame(width: size, height: size)
        .clipShape(shape)
        .accessibilityLabel(name)
    }

    private var initialsBox: some View {
        Text(initials(name)).crichere(.archivo(initialsSize, .extraBold, lineHeight: initialsSize, tracking: -0.3), gold)
            .frame(width: size, height: size)
            .background(gold.opacity(0.16))
    }
}

private struct AuctionLoading: View {
    var body: some View {
        VStack(spacing: 7) {
            CrichereSpinner(color: gold, track: Color.white.opacity(0.12), lineWidth: 3, size: 48)
            Text("Loading auction…").crichere(.sans(13, .medium, lineHeight: 13), muted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct AuctionLoadFailed: View {
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            CrIcon("ic_cloud_off", size: 40, tint: muted)
            Spacer().frame(height: 10)
            Text("Couldn't load the auction").crichere(.archivo(17, .bold), .white)
            Spacer().frame(height: 10)
            Text("Check your connection and try again.").crichere(.sans(13, lineHeight: 18.85), muted)
            Spacer().frame(height: 14)
            Button(action: onRetry) {
                Text("Retry").crichere(.sans(13.5, .semiBold, lineHeight: 13.5), auctionBg).padding(.horizontal, 20).frame(height: 40)
                    .background(gold, in: Capsule())
            }
            .buttonStyle(PressHighlight(shape: Capsule(), color: .black.opacity(0.12)))
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

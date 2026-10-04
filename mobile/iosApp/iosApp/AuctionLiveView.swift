import SwiftUI
import Shared

/// Mirrors `AuctionViewModel`'s shared `StateFlow<AuctionState>` -- same pattern
/// `AuctionSettingsViewModelWrapper` documents. **Authored but unverified** (no Xcode here, see
/// docs/PHASE15.md 8.4): real-time SSE consumption on iOS has never run.
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

/// iOS equivalent of `androidApp/.../ui/AuctionLiveScreen.kt`, restyled to design update #5 L22–L26: the
/// one custom surface on iOS (dark broadcast look), with system alert, Toggle and ProgressView inside it.
/// Organizer controls and the caller's own bidding dock render independently (dual roles are allowed,
/// docs/PHASE3.md). Owners get no dock between players (U5 L23 overruled, docs/DESIGN-REVIEW.md).
struct AuctionLiveView: View {
    let leagueId: String

    @StateObject private var wrapper: AuctionViewModelWrapper
    /// "Outbid · ₹X" for 2 s after this franchise loses the lead on the same player (U4 A3).
    @State private var outbidAmount: Double?
    @State private var previousMode: (leading: Bool, playerId: String?) = (false, nil)
    @State private var lastResultKey: String?
    @FocusState private var amountFocused: Bool

    init(leagueId: String) {
        self.leagueId = leagueId
        _wrapper = StateObject(wrappedValue: AuctionViewModelWrapper(leagueId: leagueId))
    }

    private var state: AuctionState { wrapper.state }
    private var offline: Bool { state.connectionPhase == .reconnecting || state.connectionPhase == .lost }

    var body: some View {
        ZStack {
            AuctionPalette.background.ignoresSafeArea()
            if state.auction == nil && state.loadFailed {
                VStack(spacing: 10) {
                    Text("Couldn't load the auction").font(BrandFont.archivo(20, .bold)).foregroundColor(.white)
                    Text("Check your connection and try again.").font(.system(size: 15)).foregroundColor(AuctionPalette.dim)
                    Button("Retry") { wrapper.retry() }.buttonStyle(AuctionButtonStyle(kind: .secondary))
                        .frame(width: 160)
                }
            } else if state.auction == nil {
                ProgressView("Loading auction…").tint(AuctionPalette.gold).foregroundColor(AuctionPalette.dim)
            } else if let auction = state.auction {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        statusHeader(auction)
                        VStack(spacing: 14) {
                            blockCard(auction)
                            if !auction.recentBids.isEmpty && auction.currentPlayerId != nil {
                                recentBids(auction)
                            }
                            if let results = state.results, auction.auctionStatus == .completed {
                                resultsCards(results)
                            }
                            if let error = state.actionError {
                                Text(error).font(.system(size: 15)).foregroundColor(AuctionPalette.coral)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                        .opacity(offline ? 0.5 : 1)
                        .animation(.easeOut(duration: 0.2), value: offline)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 16)
                    }
                }
                .scrollDismissesKeyboard(.interactively)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    VStack(spacing: 12) {
                        endFailureBanner
                        dock(auction)
                    }
                }
            }
        }
        .navigationTitle("Live Auction")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(AuctionPalette.background, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .tint(AuctionPalette.gold)
        // Dark controls (Toggle track, ProgressView, keyboard) on this screen only -- not the whole window.
        .environment(\.colorScheme, .dark)
        .onAppear { wrapper.retry() }
        .onChange(of: dockKey) { _, _ in trackOutbid() }
        .onChange(of: soldKey) { _, _ in trackWin() }
        .task(id: outbidAmount) {
            guard outbidAmount != nil else { return }
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            withAnimation(.easeOut(duration: 0.2)) { outbidAmount = nil }
        }
        // U5 L19c: system alert. Alerts close on tap, so "Ending…" shows on the dock button (L20 on iOS);
        // the alert is not re-presented while the request runs.
        .alert(
            "End the auction?",
            isPresented: Binding(
                get: { state.isEndConfirmOpen && !state.isEnding },
                set: { presented in if !presented { wrapper.dismissEnd() } }
            )
        ) {
            Button("Cancel", role: .cancel) { wrapper.dismissEnd() }
            Button("End Auction", role: .destructive) { wrapper.confirmEnd() }
        } message: {
            Text(state.endAuctionBody.text)
        }
        .onChange(of: state.endFailure) { _, failure in
            if let failure { UIAccessibility.post(notification: .announcement, argument: endFailureMessage(failure)) }
        }
    }

    // MARK: - status chip + connection pill (L26)

    private func statusHeader(_ auction: AuctionStateDto) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                statusChip(auction.auctionStatus)
                    .opacity(state.connectionPhase == .lost ? 0.5 : 1)
                connectionPill
            }
            if auction.auctionStatus == .inProgress && auction.playersTotal > 0 {
                let progress = auction.currentPlayerId != nil
                    ? "Player \(auction.playersSold + 1) of \(auction.playersTotal)"
                    : "\(auction.playersTotal - auction.playersPending) of \(auction.playersTotal) done"
                Group {
                    if state.connectionPhase == .lost {
                        TimelineView(.periodic(from: .now, by: 1)) { context in
                            Text("\(progress) · updated \(secondsSince(state.lastEventAtMillis, now: context.date))s ago")
                        }
                    } else {
                        Text(progress)
                    }
                }
                .font(.system(size: 13))
                .foregroundColor(AuctionPalette.muted)
                .padding(.leading, 2)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    @ViewBuilder
    private func statusChip(_ status: AuctionStatus) -> some View {
        switch status {
        case .inProgress:
            HStack(spacing: 7) {
                Circle().fill(AuctionPalette.gold).frame(width: 8, height: 8)
                    .background(Circle().fill(AuctionPalette.gold.opacity(0.25)).frame(width: 14, height: 14))
                Text("Live")
            }
            .chip(background: AuctionPalette.gold.opacity(0.14), foreground: AuctionPalette.gold)
        case .notStarted:
            Text("Not started").chip(background: Color.white.opacity(0.07), foreground: AuctionPalette.dim)
        default:
            Text("Ended").chip(background: Color.white.opacity(0.07), foreground: AuctionPalette.dim)
        }
    }

    @ViewBuilder
    private var connectionPill: some View {
        switch state.connectionPhase {
        case .reconnecting:
            HStack(spacing: 7) {
                ProgressView().controlSize(.mini).tint(AuctionPalette.dim)
                Text("Reconnecting…")
            }
            .chip(background: Color.white.opacity(0.07), foreground: AuctionPalette.dim)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Reconnecting")
        case .lost:
            // U4 L16 / U5 A6: the whole pill is the Retry target.
            Button { wrapper.retry() } label: {
                HStack(spacing: 6) {
                    Image(systemName: "icloud.slash").font(.system(size: 13, weight: .semibold))
                    Text("Connection lost")
                    Text("Retry")
                        .font(.system(size: 12, weight: .bold))
                        .padding(.horizontal, 9)
                        .frame(height: 22)
                        .background(Capsule().fill(AuctionPalette.coral))
                        .foregroundColor(AuctionPalette.background)
                }
                .padding(.leading, 9)
                .padding(.trailing, 4)
                .frame(height: 26)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(AuctionPalette.coral)
                .background(Capsule().fill(AuctionPalette.coral.opacity(0.14)))
                .contentShape(Rectangle().inset(by: -9))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Connection lost. Retry")
        case .backOnline:
            HStack(spacing: 6) {
                Image(systemName: "checkmark").font(.system(size: 13, weight: .semibold))
                Text("Back online")
            }
            .chip(background: AuctionPalette.liveGreen.opacity(0.14), foreground: AuctionPalette.liveGreen, leading: 8)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Back online")
        default:
            EmptyView()
        }
    }

    // MARK: - block card (L22) / between players / dead end (L24)

    @ViewBuilder
    private func blockCard(_ auction: AuctionStateDto) -> some View {
        switch auction.auctionStatus {
        case .notStarted:
            noticeCard(
                icon: "clock", tint: AuctionPalette.muted,
                title: state.isOrganizer ? "Ready when you are" : "The auction hasn't started",
                body: Text(state.isOrganizer ? "Start the auction, then bring up the first player."
                           : "It starts when the organizer opens it. This screen updates on its own.")
            )
        case .inProgress:
            if auction.currentPlayerId != nil {
                onTheBlock(auction)
            } else if state.isDeadEnd {
                deadEndCard(auction)
            } else {
                noticeCard(
                    icon: "hourglass", tint: AuctionPalette.muted,
                    title: state.isOrganizer ? "No player up yet" : "Next player coming up",
                    body: lastText(auction, fallback: state.isOrganizer ? "Bring up the next player." : "Waiting for the organizer to bring up the next player.")
                )
            }
        default:
            noticeCard(icon: "flag.checkered", tint: AuctionPalette.muted, title: "Auction complete", body: Text("Squads are final."))
        }
    }

    /// L22: the 225 pt card -- photo slot 88, ON THE BLOCK, Archivo name 22/26, role chip, then the bid.
    private func onTheBlock(_ auction: AuctionStateDto) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .center, spacing: 14) {
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color(hex: 0x25362B))
                    .frame(width: 88, height: 88)
                    .overlay(Image(systemName: "person.fill").font(.system(size: 34)).foregroundColor(AuctionPalette.muted.opacity(0.6)))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 6) {
                    Text("ON THE BLOCK")
                        .font(BrandFont.mono(10, .semibold))
                        .kerning(0.8)
                        .foregroundColor(AuctionPalette.gold)
                    Text(auction.currentPlayerName ?? "Unnamed player")
                        .font(BrandFont.archivo(22, .heavy))
                        .foregroundColor(.white)
                        .lineLimit(2)
                    if let role = auction.currentPlayerRole {
                        Text(roleLabel(role))
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(AuctionPalette.soft)
                            .padding(.horizontal, 9)
                            .frame(height: 22)
                            .overlay(Capsule().strokeBorder(Color.white.opacity(0.16), lineWidth: 1))
                    }
                }
                Spacer(minLength: 0)
            }
            Rectangle().fill(AuctionPalette.hairline).frame(height: 1)
            VStack(alignment: .leading, spacing: 4) {
                Text("Current bid").font(.system(size: 12)).foregroundColor(AuctionPalette.muted)
                HStack(alignment: .center, spacing: 10) {
                    if let amount = auction.currentBidAmount?.doubleValue {
                        Text(rupees(amount))
                            .font(BrandFont.mono(36, .bold))
                            .kerning(-0.72)
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    } else {
                        Text("No bids yet").font(BrandFont.archivo(20, .bold)).foregroundColor(AuctionPalette.muted)
                    }
                    Spacer(minLength: 0)
                    if let leader = auction.currentLeadingFranchiseName {
                        HStack(spacing: 7) {
                            FranchiseBadge(name: leader, size: 20, radius: 6, fontSize: 7)
                            Text(leader).font(.system(size: 13, weight: .semibold)).foregroundColor(AuctionPalette.soft).lineLimit(1)
                        }
                    }
                }
                .frame(height: 40)
                if let minimum = state.minimumNextBid?.doubleValue {
                    (Text(auction.currentBidAmount == nil ? "Opening bid at least " : "Next bid at least ")
                        + Text(rupees(minimum)).font(BrandFont.mono(12)).foregroundColor(AuctionPalette.dim))
                        .font(.system(size: 12))
                        .foregroundColor(AuctionPalette.muted)
                }
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 20).fill(AuctionPalette.surface))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(AuctionPalette.hairline, lineWidth: 1))
    }

    /// L24 (organizer) and the neutral "Bidding has closed" for everyone else.
    @ViewBuilder
    private func deadEndCard(_ auction: AuctionStateDto) -> some View {
        if state.isOrganizer {
            VStack(alignment: .leading, spacing: 10) {
                iconTile("nosign", tint: AuctionPalette.coral, background: AuctionPalette.coral.opacity(0.14))
                Text("No franchise can bid on the remaining players")
                    .font(BrandFont.archivo(20, .bold))
                    .foregroundColor(.white)
                    .fixedSize(horizontal: false, vertical: true)
                deadEndBody(auction)
                    .font(.system(size: 15))
                    .foregroundColor(AuctionPalette.dim)
                    .fixedSize(horizontal: false, vertical: true)
                VStack(spacing: 8) {
                    breakdownRow("Squads full", "\(auction.squadsFull) of \(auction.franchisesTotal)")
                    breakdownRow("Purse below base price", "\(auction.purseBelowBase) of \(auction.franchisesTotal)")
                    breakdownRow("Left in pool", "\(auction.playersPending) \(auction.playersPending == 1 ? "player" : "players")")
                }
                .padding(.top, 10)
                .overlay(alignment: .top) { Rectangle().fill(AuctionPalette.hairline).frame(height: 1) }
            }
            .padding(.vertical, 18)
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 20).fill(AuctionPalette.surface))
            .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(AuctionPalette.coral.opacity(0.35), lineWidth: 1))
        } else {
            noticeCard(
                icon: "hourglass", tint: AuctionPalette.muted, title: "Bidding has closed",
                body: Text("No franchise can buy the remaining players. Waiting for the organizer to end the auction.")
            )
        }
    }

    private func deadEndBody(_ auction: AuctionStateDto) -> Text {
        let base = (state.league?.auctionBasePrice?.doubleValue).map(rupees) ?? "base"
        let allow = Text("Allow exceeding purse").fontWeight(.semibold).foregroundColor(.white)
        if auction.purseBelowBase == 0 {
            return Text("Every squad is full, so nothing else can sell. End the auction to publish the results.")
        } else if auction.squadsFull == 0 {
            return Text("Every purse is below the \(base) base price, so nothing else can sell. End the auction, or turn on ") + allow + Text(" to let franchises keep buying.")
        }
        return Text("Squads are full or purses are below the \(base) base price, so nothing else can sell. End the auction, or turn on ") + allow + Text(".")
    }

    private func breakdownRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.system(size: 15)).foregroundColor(AuctionPalette.muted)
            Spacer()
            Text(value).font(BrandFont.mono(14, .medium)).foregroundColor(AuctionPalette.soft)
        }
    }

    /// L23's card shape: dashed hairline, 40 pt icon tile, Archivo 700 20/25 headline, SF 15/20 body.
    private func noticeCard(icon: String, tint: Color, title: String, body: Text) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            iconTile(icon, tint: tint, background: Color.white.opacity(0.07))
            Text(title).font(BrandFont.archivo(20, .bold)).foregroundColor(.white)
            body.font(.system(size: 15)).foregroundColor(AuctionPalette.dim).fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 18)
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 20).fill(AuctionPalette.surface))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(Color.white.opacity(0.14), style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
    }

    private func iconTile(_ name: String, tint: Color, background: Color) -> some View {
        Image(systemName: name)
            .font(.system(size: 20))
            .foregroundColor(tint)
            .frame(width: 40, height: 40)
            .background(RoundedRectangle(cornerRadius: 12).fill(background))
            .accessibilityHidden(true)
    }

    private func recentBids(_ auction: AuctionStateDto) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Recent bids").font(BrandFont.archivo(15, .bold)).foregroundColor(.white)
            ForEach(auction.recentBids.prefix(5), id: \.placedAt) { bid in
                HStack {
                    Text(bid.franchiseName ?? "Franchise").font(.system(size: 15)).foregroundColor(AuctionPalette.soft).lineLimit(1)
                    Spacer()
                    Text(rupees(bid.amount)).font(BrandFont.mono(14, .medium)).foregroundColor(.white)
                }
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 20).fill(AuctionPalette.surface))
    }

    // MARK: - results (U4 A1)

    private func resultsCards(_ results: AuctionResultsDto) -> some View {
        VStack(spacing: 10) {
            ForEach(results.franchises, id: \.franchiseId) { franchise in
                let remaining = franchise.purseRemaining?.doubleValue
                let over = remaining.map { $0 < 0 } ?? false
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 10) {
                        FranchiseBadge(name: franchise.franchiseName, size: 28, radius: 8, fontSize: 10)
                        Text(franchise.franchiseName).font(.system(size: 15, weight: .semibold)).foregroundColor(.white)
                    }
                    let players = "\(franchise.playersWon.count) \(franchise.playersWon.count == 1 ? "player" : "players") · \(rupees(franchise.purseSpent)) spent"
                    Text(players + (!over ? (remaining.map { " · \(rupees($0)) left" } ?? "") : ""))
                        .font(.system(size: 13)).foregroundColor(AuctionPalette.muted)
                    if let remaining, over {
                        Label("\(rupees(-remaining)) over purse", systemImage: "exclamationmark.circle")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(AuctionPalette.coral)
                    }
                    if franchise.belowSquadMin {
                        Text("Below minimum squad size").font(.system(size: 13, weight: .semibold)).foregroundColor(AuctionPalette.coral)
                    }
                    ForEach(franchise.playersWon, id: \.playerId) { player in
                        HStack {
                            Text(player.playerName ?? "Unnamed player").font(.system(size: 15)).foregroundColor(AuctionPalette.soft)
                            Spacer()
                            Text(rupees(player.soldPrice)).font(BrandFont.mono(14)).foregroundColor(AuctionPalette.dim)
                        }
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 20).fill(AuctionPalette.surface))
                .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(over ? AuctionPalette.coral.opacity(0.35) : AuctionPalette.hairline, lineWidth: 1))
            }
        }
    }

    // MARK: - dock

    @ViewBuilder
    private func dock(_ auction: AuctionStateDto) -> some View {
        let bidder = showsBidder(auction)
        if bidder || state.isOrganizer {
            VStack(alignment: .leading, spacing: 12) {
                if bidder { bidderDock(auction) }
                if state.isOrganizer { organizerDock(auction) }
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)
            .padding(.bottom, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            // Radius 22 on top only: the bottom corners run on past the home indicator, out of sight.
            .background(
                RoundedRectangle(cornerRadius: 22)
                    .fill(AuctionPalette.dock)
                    .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(AuctionPalette.hairline, lineWidth: 1))
                    .padding(.bottom, -60)
                    .ignoresSafeArea(edges: .bottom)
            )
        }
    }

    private func showsBidder(_ auction: AuctionStateDto) -> Bool {
        let playerUp = auction.auctionStatus == .inProgress && auction.currentPlayerId != nil
        let ownerAtDeadEnd = state.isDeadEnd && state.myFranchiseId != nil && !(state.dockMode is DockModeBid)
        return (playerUp && state.myFranchiseId != nil) || ownerAtDeadEnd
    }

    /// L25: context line, then the Amount field + step + Place Bid, or a status tile in their place.
    @ViewBuilder
    private func bidderDock(_ auction: AuctionStateDto) -> some View {
        let mode = state.dockMode
        if let context = state.biddingContext {
            biddingLine(context)
        }
        if let leading = mode as? DockModeLeading {
            statusTile(icon: "checkmark.circle.fill", tint: AuctionPalette.gold, highlighted: true,
                       title: Text("You're leading at ") + Text(rupees(leading.amount)).font(BrandFont.mono(15, .semibold)).foregroundColor(AuctionPalette.gold),
                       sub: "Bidding reopens if someone outbids you.")
        } else if let full = mode as? DockModeSquadFull {
            statusTile(icon: "person.3", tint: AuctionPalette.muted, highlighted: false,
                       title: Text("Your squad is full (\(full.playersWon)/\(full.squadMax))."),
                       sub: "You can keep watching. Bidding is off for you.")
        } else if let short = mode as? DockModePurseShort {
            statusTile(icon: "wallet.pass", tint: AuctionPalette.muted, highlighted: false,
                       title: Text("Your purse can't cover the next bid."),
                       sub: "Next bid at least \(rupees(short.minimumNextBid))")
        } else {
            amountField
            if let bidError = state.bidError {
                Text(bidError).font(.system(size: 13)).foregroundColor(AuctionPalette.coral)
            }
        }
        let inert = !(mode is DockModeBid) || offline || state.isBidding
        HStack(spacing: 10) {
            if let increment = state.bidIncrement?.doubleValue {
                Button("+\(rupees(increment))") { wrapper.onStepBid() }
                    .buttonStyle(AuctionButtonStyle(kind: .step))
                    .fixedSize(horizontal: true, vertical: false)
                    .disabled(inert)
            }
            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                wrapper.placeBid()
            } label: {
                Text(state.isBidding ? "Placing…" : "Place Bid")
            }
            .buttonStyle(AuctionButtonStyle(kind: .gold))
            .disabled(inert)
        }
    }

    @ViewBuilder
    private func biddingLine(_ context: BiddingContext) -> some View {
        let left = context.purseLeft?.doubleValue
        let squad = context.squadMax.map { " · \(context.playersWon)/\($0)" } ?? ""
        HStack(alignment: .top, spacing: 8) {
            FranchiseBadge(name: context.franchiseName, size: 20, radius: 6, fontSize: 7)
            Group {
                if let left, left < 0 {
                    // U4 A1: never a negative number -- the amount over goes in coral.
                    Text("Bidding as ") + Text(context.franchiseName).fontWeight(.semibold).foregroundColor(.white) + Text(" · ")
                        + Text("\(rupees(-left)) over purse").fontWeight(.semibold).foregroundColor(AuctionPalette.coral) + Text(squad)
                } else {
                    Text("Bidding as ") + Text(context.franchiseName).fontWeight(.semibold).foregroundColor(.white)
                        + (left.map { Text(" · ") + Text(rupees($0)).font(BrandFont.mono(14)) + Text(" left") } ?? Text(""))
                        + Text(squad)
                }
            }
            .font(.system(size: 15))
            .foregroundColor(AuctionPalette.dim)
            .lineLimit(2)
        }
    }

    /// U4 L18 geometry: 54 tall, radius 12, floating "Amount" label; gold 2 pt border when focused.
    private var amountField: some View {
        HStack(spacing: 4) {
            Text("₹").font(BrandFont.mono(18, .semibold)).foregroundColor(AuctionPalette.muted)
            TextField("", text: amountBinding, prompt: Text("Amount").foregroundColor(AuctionPalette.muted))
                .keyboardType(.numberPad)
                .focused($amountFocused)
                .font(BrandFont.mono(18, .semibold))
                .foregroundColor(.white)
                .disabled(state.isBidding || offline)
            if let outbid = outbidAmount {
                Text("Outbid · \(rupees(outbid))")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(AuctionPalette.coral)
                    .transition(.opacity)
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 54)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(amountFocused ? AuctionPalette.gold : Color.white.opacity(0.16), lineWidth: amountFocused ? 2 : 1)
        )
        .overlay(alignment: .topLeading) {
            if amountFocused || !state.bidAmountInput.isEmpty {
                Text("Amount")
                    .font(.system(size: 12))
                    .foregroundColor(amountFocused ? AuctionPalette.gold : AuctionPalette.muted)
                    .padding(.horizontal, 4)
                    .background(AuctionPalette.dock)
                    .offset(x: 12, y: -8)
            }
        }
    }

    private func statusTile(icon: String, tint: Color, highlighted: Bool, title: Text, sub: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).font(.system(size: 20)).foregroundColor(tint)
            VStack(alignment: .leading, spacing: 2) {
                title.font(.system(size: 15, weight: .semibold)).foregroundColor(.white).lineLimit(1)
                Text(sub).font(.system(size: 13)).foregroundColor(AuctionPalette.muted).lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 14)
        .frame(minHeight: 54)
        .background(RoundedRectangle(cornerRadius: 12).fill(highlighted ? AuctionPalette.gold.opacity(0.1) : Color.white.opacity(0.04)))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(highlighted ? AuctionPalette.gold.opacity(0.35) : AuctionPalette.hairline, lineWidth: 1))
        .transition(.opacity)
    }

    /// L22 / L24 organizer controls: main 50 pt buttons, system Toggle tinted gold, 44 pt secondary row.
    @ViewBuilder
    private func organizerDock(_ auction: AuctionStateDto) -> some View {
        let acting = state.isActing || offline || state.isEnding
        Text("Organizer controls").font(BrandFont.archivo(15, .bold)).foregroundColor(.white)
        switch auction.auctionStatus {
        case .notStarted:
            Button("Start Auction") { wrapper.start() }
                .buttonStyle(AuctionButtonStyle(kind: .gold))
                .disabled(acting)
        case .inProgress:
            if auction.currentPlayerId != nil {
                HStack(spacing: 10) {
                    Button { wrapper.sold() } label: { Label("Sold", systemImage: "hammer.fill") }
                        .buttonStyle(AuctionButtonStyle(kind: .gold))
                        .disabled(acting || auction.currentLeadingFranchiseId == nil)
                    Button("Unsold") { wrapper.unsold() }
                        .buttonStyle(AuctionButtonStyle(kind: .quiet))
                        .disabled(acting)
                }
                exceedToggle(auction, acting: acting)
                HStack(spacing: 10) {
                    Button { wrapper.undo() } label: { Label("Undo", systemImage: "arrow.uturn.backward") }
                        .buttonStyle(AuctionButtonStyle(kind: .secondary))
                        .disabled(acting)
                    endButton(main: false, acting: acting)
                }
            } else if state.isDeadEnd {
                exceedToggle(auction, acting: acting)
                endButton(main: true, acting: acting)
                HStack(spacing: 10) {
                    Button("Next Player") { wrapper.nextPlayer() }
                        .buttonStyle(AuctionButtonStyle(kind: .secondary))
                        .disabled(acting)
                    Button("Undo") { wrapper.undo() }
                        .buttonStyle(AuctionButtonStyle(kind: .secondary))
                        .disabled(acting)
                }
            } else {
                Button { wrapper.nextPlayer() } label: { Label("Next Player", systemImage: "forward.end.fill") }
                    .buttonStyle(AuctionButtonStyle(kind: .gold))
                    .disabled(acting)
                exceedToggle(auction, acting: acting)
                HStack(spacing: 10) {
                    Button { wrapper.undo() } label: { Label("Undo", systemImage: "arrow.uturn.backward") }
                        .buttonStyle(AuctionButtonStyle(kind: .secondary))
                        .disabled(acting)
                    endButton(main: false, acting: acting)
                }
            }
        case .completed:
            // See AuctionLiveScreen.kt's matching comment: the sale/unsold call that completed the
            // auction is still undoable server-side.
            Button { wrapper.undo() } label: { Label("Undo", systemImage: "arrow.uturn.backward") }
                .buttonStyle(AuctionButtonStyle(kind: .secondary))
                .disabled(acting)
        default:
            EmptyView()
        }
    }

    /// U5 L20 on iOS: "Ending…" with a 16 pt ProgressView on the button itself while the request runs.
    private func endButton(main: Bool, acting: Bool) -> some View {
        Button { wrapper.requestEnd() } label: {
            if state.isEnding {
                HStack(spacing: 8) {
                    ProgressView().tint(main ? AuctionPalette.background : AuctionPalette.coral).frame(width: 16, height: 16)
                    Text("Ending…")
                }
            } else {
                Label("End Auction", systemImage: "stop.circle")
            }
        }
        .buttonStyle(AuctionButtonStyle(kind: main ? .coral : .endOutline))
        .disabled(acting)
    }

    /// U4 A2: highlighted only when purses (not squads) are what stops bidding.
    private func exceedToggle(_ auction: AuctionStateDto, acting: Bool) -> some View {
        let highlighted = state.isDeadEnd && auction.purseBelowBase > 0
        return HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Allow exceeding purse").font(.system(size: 17)).foregroundColor(highlighted ? .white : AuctionPalette.soft)
                if highlighted {
                    Text(auction.purseBelowBase == 1 ? "1 franchise is stopped by its purse" : "\(auction.purseBelowBase) franchises are stopped by their purse")
                        .font(.system(size: 13)).foregroundColor(AuctionPalette.dim)
                }
            }
            Spacer(minLength: 0)
            Toggle("", isOn: Binding(get: { auction.allowExceedPurse }, set: { wrapper.toggleExceedPurse($0) }))
                .labelsHidden()
                .tint(AuctionPalette.gold)
                .disabled(acting)
        }
        .frame(minHeight: 44)
        .padding(.vertical, highlighted ? 10 : 0)
        .padding(.horizontal, highlighted ? 12 : 0)
        .background(RoundedRectangle(cornerRadius: 12).fill(highlighted ? AuctionPalette.gold.opacity(0.08) : .clear))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(highlighted ? AuctionPalette.gold.opacity(0.35) : .clear, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }

    // MARK: - End Auction failure banner (U5 L21 on iOS)

    @ViewBuilder
    private var endFailureBanner: some View {
        if let failure = state.endFailure {
            HStack(spacing: 12) {
                Text(endFailureMessage(failure))
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(Brand.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button("Retry") { wrapper.requestEnd() }
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Brand.primary)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .frame(minHeight: 48)
            .background(Capsule().fill(Brand.background))
            .shadow(color: .black.opacity(0.4), radius: 9, y: 6)
            .padding(.horizontal, 12)
            .gesture(DragGesture(minimumDistance: 20).onEnded { _ in wrapper.clearEndFailure() })
            .transition(.opacity)
        }
    }

    private func endFailureMessage(_ failure: EndAuctionFailure) -> String {
        switch failure {
        case .network: return "Couldn't end the auction. Check your connection and try again."
        default: return "Couldn't end the auction right now. Try again in a moment."
        }
    }

    // MARK: - helpers

    /// A6: the field shows Indian grouping; the ViewModel keeps plain digits (at most 9).
    private var amountBinding: Binding<String> {
        Binding(
            get: { AmountGroupingKt.groupIndianAmount(raw: state.bidAmountInput).text },
            set: { typed in
                let digits = typed.filter(\.isNumber)
                if digits.count <= 9 { wrapper.onBidAmountChanged(digits) }
            }
        )
    }

    /// Changes whenever the bid-area mode or the player changes -- drives the outbid flash.
    private var dockKey: String {
        "\(state.dockMode is DockModeLeading)-\(state.auction?.currentPlayerId ?? "")-\(state.auction?.currentBidAmount?.doubleValue ?? 0)"
    }

    /// Changes when a new lot result arrives -- drives the winner's success haptic.
    private var soldKey: String {
        guard let last = state.auction?.lastResult else { return "" }
        return "\(last.playerName ?? "")-\(last.sold)-\(last.franchiseName ?? "")-\(last.amount?.doubleValue ?? 0)"
    }

    private func trackOutbid() {
        let leadingNow = state.dockMode is DockModeLeading
        let player = state.auction?.currentPlayerId
        if previousMode.leading && !leadingNow && state.dockMode is DockModeBid && previousMode.playerId == player,
           let bid = state.auction?.currentBidAmount?.doubleValue {
            outbidAmount = bid
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
        previousMode = (leadingNow, player)
    }

    /// U5 C haptics: .success on Sold for the winning franchise only, none for spectators.
    private func trackWin() {
        defer { lastResultKey = soldKey }
        guard lastResultKey != nil, let last = state.auction?.lastResult, last.sold,
              let mine = state.biddingContext?.franchiseName, last.franchiseName == mine else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    private func lastText(_ auction: AuctionStateDto, fallback: String) -> Text {
        guard let last = auction.lastResult else { return Text(fallback) }
        let name = last.playerName ?? "The last player"
        if last.sold {
            let base = Text("Last: \(name) sold to \(last.franchiseName ?? "a franchise")")
            if let amount = last.amount?.doubleValue {
                return base + Text(" for ") + Text(rupees(amount)).font(BrandFont.mono(15)).foregroundColor(.white) + Text(".")
            }
            return base + Text(".")
        }
        return Text("Last: \(name) went unsold.")
    }

    private func roleLabel(_ role: PlayingRole) -> String {
        switch role {
        case .batsman: return "Batsman"
        case .bowler: return "Bowler"
        case .allRounder: return "All-rounder"
        case .wicketkeeper: return "Wicketkeeper"
        default: return ""
        }
    }

    private func secondsSince(_ millis: KotlinLong?, now: Date) -> Int {
        guard let millis else { return 0 }
        return max(0, Int(now.timeIntervalSince1970 - Double(millis.int64Value) / 1000))
    }
}

/// Initials badge in a stable colour per franchise name (same idea as Android's `FranchiseBadge`).
struct FranchiseBadge: View {
    let name: String
    let size: CGFloat
    let radius: CGFloat
    let fontSize: CGFloat

    private static let palette: [UInt32] = [0xB5462B, 0x6B4FA8, 0x2F6FA8, 0x2E7D4F, 0xA8742F, 0x8A3D6B]

    var body: some View {
        let initials = name.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
        let index = abs(name.unicodeScalars.reduce(0) { $0 &+ Int($1.value) }) % Self.palette.count
        Text(initials.isEmpty ? "?" : initials)
            .font(BrandFont.archivo(fontSize, .heavy))
            .foregroundColor(.white)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: radius).fill(Color(hex: Self.palette[index])))
            .accessibilityHidden(true)
    }
}

/// U5 C button sizes on the auction: main 50 pt (radius 25, SF 17/22 600), secondary 44 pt (radius 22,
/// SF 15/20 600). Disabled buttons drop to 38% like Android.
struct AuctionButtonStyle: ButtonStyle {
    enum Kind { case gold, coral, quiet, secondary, endOutline, step }
    let kind: Kind

    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        let main = kind == .gold || kind == .coral || kind == .quiet || kind == .step
        let height: CGFloat = main ? 50 : 44
        return configuration.label
            .font(kind == .step ? BrandFont.mono(15, .bold) : .system(size: main ? 17 : 15, weight: .semibold))
            .foregroundColor(foreground)
            .padding(.horizontal, kind == .step ? 18 : 12)
            .frame(maxWidth: kind == .step ? nil : .infinity)
            .frame(height: height)
            .background(Capsule().fill(background))
            .overlay(Capsule().strokeBorder(border, lineWidth: 1))
            .opacity(isEnabled ? (configuration.isPressed ? 0.8 : 1) : 0.38)
            .contentShape(Capsule())
    }

    private var foreground: Color {
        switch kind {
        case .gold, .coral: return AuctionPalette.background
        case .quiet, .secondary: return .white
        case .endOutline: return AuctionPalette.coral
        case .step: return AuctionPalette.gold
        }
    }

    private var background: Color {
        switch kind {
        case .gold: return AuctionPalette.gold
        case .coral: return AuctionPalette.coral
        case .quiet, .secondary: return Color.white.opacity(0.08)
        case .endOutline, .step: return .clear
        }
    }

    private var border: Color {
        switch kind {
        case .endOutline: return AuctionPalette.coral.opacity(0.4)
        case .step: return AuctionPalette.gold.opacity(0.45)
        default: return .clear
        }
    }
}

private extension View {
    /// L26 chip: 26 pt capsule, SF 13/18 600, padding 9 / 11.
    func chip(background: Color, foreground: Color, leading: CGFloat = 9) -> some View {
        font(.system(size: 13, weight: .semibold))
            .foregroundColor(foreground)
            .padding(.leading, leading)
            .padding(.trailing, 11)
            .frame(height: 26)
            .background(Capsule().fill(background))
            .transition(.opacity)
    }
}

import SwiftUI
import Shared

/// Mirrors `AuctionViewModel`'s shared `StateFlow<AuctionState>` -- same pattern
/// `AuctionSettingsViewModelWrapper` documents. **Authored but unverified, not wired into any
/// navigation** -- see docs/PHASE2.md Section 5 / docs/PHASE5.md. Real-time SSE consumption on
/// iOS is untested in this environment (no device/simulator here); this view is authored for
/// compile-time parity only, matching this project's standing iOS posture.
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
    func end() { viewModel.end() }
    func toggleExceedPurse(_ allow: Bool) { viewModel.toggleExceedPurse(allow: allow) }
}

/// iOS equivalent of `androidApp/.../ui/AuctionLiveScreen.kt`: organizer controls and a bidding
/// form for the caller's own franchise render independently (dual roles are allowed, see
/// docs/PHASE3.md), driven entirely by the ViewModel's own SSE subscription -- no manual refresh.
///
/// Design update #4 (docs/PHASE15.md section 7) ported in this file's plain `Form` style: the
/// connection pill (A4), the nobody-can-bid cards (A2), the bid area's status in place of the
/// amount (A3, with the outbid flash), over-purse results (A1) and the amount format (A6:
/// Indian grouping, digits only, at most 9, number pad).
struct AuctionLiveView: View {
    let leagueId: String

    @StateObject private var wrapper: AuctionViewModelWrapper
    /// "Outbid · ₹X" for 2 s after this franchise loses the lead on the same player (A3).
    @State private var outbidAmount: Double?
    @State private var previousMode: (leading: Bool, playerId: String?) = (false, nil)

    init(leagueId: String) {
        self.leagueId = leagueId
        _wrapper = StateObject(wrappedValue: AuctionViewModelWrapper(leagueId: leagueId))
    }

    private var state: AuctionState { wrapper.state }
    private var offline: Bool { state.connectionPhase == .reconnecting || state.connectionPhase == .lost }

    var body: some View {
        Group {
            if state.auction == nil && state.loadFailed {
                VStack(spacing: 10) {
                    Text("Couldn't load the auction").font(.headline)
                    Text("Check your connection and try again.").font(.footnote).foregroundColor(.secondary)
                    Button("Retry") { wrapper.retry() }
                }
            } else if state.auction == nil {
                ProgressView("Loading auction…")
            } else if let auction = state.auction {
                Form {
                    statusSection(auction)
                    Group {
                        blockSection(auction)
                        if let results = state.results, auction.auctionStatus == .completed {
                            resultsSection(results)
                        }
                    }
                    .opacity(offline ? 0.5 : 1)
                    .animation(.easeOut(duration: 0.2), value: offline)
                    bidSection(auction)
                    organizerSection(auction)
                    if let error = state.actionError {
                        Text(error).foregroundColor(.red)
                    }
                }
            }
        }
        .navigationTitle("Live Auction")
        .onAppear { wrapper.retry() }
        .onChange(of: dockKey) { _, _ in trackOutbid() }
        .task(id: outbidAmount) {
            guard outbidAmount != nil else { return }
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            withAnimation(.easeOut(duration: 0.2)) { outbidAmount = nil }
        }
    }

    // MARK: - status + connection (A4)

    @ViewBuilder
    private func statusSection(_ auction: AuctionStateDto) -> some View {
        Section {
            HStack(spacing: 8) {
                Text(statusLabel(auction.auctionStatus))
                    .font(.system(size: 12, weight: .semibold))
                    .opacity(state.connectionPhase == .lost ? 0.5 : 1)
                connectionPill
            }
            if auction.auctionStatus == .inProgress && auction.playersTotal > 0 {
                let progress = auction.currentPlayerId != nil
                    ? "Player \(auction.playersSold + 1) of \(auction.playersTotal)"
                    : "\(auction.playersSold) of \(auction.playersTotal) done"
                if state.connectionPhase == .lost {
                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        Text("\(progress) · updated \(secondsSince(state.lastEventAtMillis, now: context.date))s ago")
                            .font(.caption).foregroundColor(.secondary)
                    }
                } else {
                    Text(progress).font(.caption).foregroundColor(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private var connectionPill: some View {
        switch state.connectionPhase {
        case .reconnecting:
            HStack(spacing: 7) {
                ProgressView().controlSize(.mini)
                Text("Reconnecting…")
            }
            .pill(background: Color.secondary.opacity(0.15), foreground: .secondary)
            .accessibilityLabel("Reconnecting")
        case .lost:
            Button { wrapper.retry() } label: {
                HStack(spacing: 6) {
                    Image(systemName: "icloud.slash")
                    Text("Connection lost")
                    Text("Retry")
                        .font(.system(size: 11.5, weight: .bold))
                        .padding(.horizontal, 9)
                        .frame(height: 22)
                        .background(Capsule().fill(coral))
                        .foregroundColor(.black)
                }
            }
            .buttonStyle(.borderless)
            .pill(background: coral.opacity(0.14), foreground: coral)
            .accessibilityLabel("Connection lost. Retry")
        case .backOnline:
            HStack(spacing: 6) {
                Image(systemName: "checkmark")
                Text("Back online")
            }
            .pill(background: liveGreen.opacity(0.14), foreground: liveGreen)
            .accessibilityLabel("Back online")
        default:
            EmptyView()
        }
    }

    // MARK: - block / between players / dead end (A2, A5)

    @ViewBuilder
    private func blockSection(_ auction: AuctionStateDto) -> some View {
        switch auction.auctionStatus {
        case .notStarted:
            Section {
                Text(state.isOrganizer ? "Ready when you are" : "The auction hasn't started").font(.headline)
                Text(state.isOrganizer ? "Current bid: none yet. Start the auction, then bring up the first player."
                     : "It starts when the organizer opens it. This screen updates on its own.")
                    .font(.footnote).foregroundColor(.secondary)
            }
        case .inProgress:
            if auction.currentPlayerId != nil {
                Section("On the block") {
                    Text(auction.currentPlayerName ?? "Unnamed player").font(.title3.bold()).lineLimit(2)
                    if let amount = auction.currentBidAmount?.doubleValue {
                        HStack(alignment: .lastTextBaseline) {
                            Text(rupees(amount)).font(.system(size: 30, weight: .bold, design: .monospaced))
                            Spacer()
                            Text(auction.currentLeadingFranchiseName ?? "").lineLimit(1)
                        }
                    } else {
                        Text("No bids yet").font(.title3).foregroundColor(.secondary)
                    }
                    if let minimum = state.minimumNextBid?.doubleValue {
                        Text("\(auction.currentBidAmount == nil ? "Opening bid at least" : "Next bid at least") \(rupees(minimum))")
                            .font(.caption).foregroundColor(.secondary)
                    }
                }
                if !auction.recentBids.isEmpty {
                    Section("Recent bids") {
                        ForEach(auction.recentBids.prefix(5), id: \.placedAt) { bid in
                            HStack {
                                Text(bid.franchiseName ?? "Franchise")
                                Spacer()
                                Text(rupees(bid.amount)).font(.system(.body, design: .monospaced))
                            }
                        }
                    }
                }
            } else if state.isDeadEnd {
                deadEndSection(auction)
            } else {
                Section("On the block") {
                    Text("No player up yet").font(.headline)
                    Text([lastLine(auction), state.isOrganizer ? "Bring up the next player." : "Waiting for the organizer to bring up the next player."]
                        .compactMap { $0 }.joined(separator: " "))
                        .font(.footnote).foregroundColor(.secondary)
                }
            }
        default:
            Section { Text("This auction has ended.") }
        }
    }

    @ViewBuilder
    private func deadEndSection(_ auction: AuctionStateDto) -> some View {
        Section {
            if state.isOrganizer {
                Label("No franchise can bid on the remaining players", systemImage: "nosign")
                    .font(.headline).foregroundColor(coral)
                Text(deadEndBody(auction)).font(.footnote)
                breakdownRow("Squads full", "\(auction.squadsFull) of \(auction.franchisesTotal)")
                breakdownRow("Purse below base price", "\(auction.purseBelowBase) of \(auction.franchisesTotal)")
                breakdownRow("Left in pool", "\(auction.playersPending) \(auction.playersPending == 1 ? "player" : "players")")
            } else {
                Label("Bidding has closed", systemImage: "hourglass")
                    .font(.headline)
                Text("No franchise can buy the remaining players. Waiting for the organizer to end the auction.").font(.footnote)
            }
            if let last = lastLine(auction) {
                Text(last).font(.caption).foregroundColor(.secondary)
            }
        }
    }

    private func deadEndBody(_ auction: AuctionStateDto) -> String {
        let base = (state.league?.auctionBasePrice?.doubleValue).map(rupees) ?? "base"
        if auction.purseBelowBase == 0 {
            return "Every squad is full, so nothing else can sell. End the auction to publish the results."
        } else if auction.squadsFull == 0 {
            return "Every purse is below the \(base) base price, so nothing else can sell. End the auction, or turn on Allow exceeding purse to let franchises keep buying."
        }
        return "Squads are full or purses are below the \(base) base price, so nothing else can sell. End the auction, or turn on Allow exceeding purse to let franchises keep buying."
    }

    private func breakdownRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).foregroundColor(.secondary)
            Spacer()
            Text(value).font(.system(.body, design: .monospaced))
        }
        .font(.system(size: 12.5, weight: .medium))
    }

    // MARK: - bid area (A1 dock line, A3, A6)

    @ViewBuilder
    private func bidSection(_ auction: AuctionStateDto) -> some View {
        let playerUp = auction.auctionStatus == .inProgress && auction.currentPlayerId != nil
        let mode = state.dockMode
        let ownerAtDeadEnd = state.isDeadEnd && state.myFranchiseId != nil && !(mode is DockModeBid)
        if (playerUp && state.myFranchiseId != nil) || ownerAtDeadEnd {
            Section("Your bid") {
                if let context = state.biddingContext {
                    biddingLine(context)
                }
                if let leading = mode as? DockModeLeading {
                    statusTile(icon: "checkmark.circle.fill", tint: gold,
                               title: Text("You're leading at ") + Text(rupees(leading.amount)).foregroundColor(gold),
                               sub: Text("Bidding reopens if someone outbids you."))
                } else if let full = mode as? DockModeSquadFull {
                    statusTile(icon: "person.3", tint: .secondary,
                               title: Text("Your squad is full (\(full.playersWon)/\(full.squadMax))."),
                               sub: Text("You can keep watching. Bidding is off for you."))
                } else if let short = mode as? DockModePurseShort {
                    statusTile(icon: "wallet.pass", tint: .secondary,
                               title: Text("Your purse can't cover the next bid."),
                               sub: Text("Next bid at least \(rupees(short.minimumNextBid))"))
                } else {
                    HStack {
                        Text("₹").foregroundColor(.secondary)
                        TextField("Amount", text: amountBinding)
                            .keyboardType(.numberPad)
                            .font(.system(size: 18, weight: .semibold, design: .monospaced))
                            .disabled(state.isBidding || offline)
                        if let outbid = outbidAmount {
                            Text("Outbid · \(rupees(outbid))")
                                .font(.system(size: 11.5, weight: .semibold))
                                .foregroundColor(coral)
                                .transition(.opacity)
                        }
                    }
                    if let bidError = state.bidError {
                        Text(bidError).font(.caption).foregroundColor(coral)
                    }
                }
                let inert = !(mode is DockModeBid) || offline
                HStack {
                    if let increment = state.bidIncrement?.doubleValue {
                        Button("+\(rupees(increment))") { wrapper.onStepBid() }
                            .disabled(inert || state.isBidding)
                    }
                    Spacer()
                    Button(state.isBidding ? "Placing…" : "Place Bid") { wrapper.placeBid() }
                        .buttonStyle(.borderedProminent)
                        .disabled(inert || state.isBidding)
                }
            }
        } else if playerUp && !state.isOrganizer {
            Section { Label("Only franchise owners can bid.", systemImage: "eye").font(.footnote).foregroundColor(.secondary) }
        }
    }

    @ViewBuilder
    private func biddingLine(_ context: BiddingContext) -> some View {
        let left = context.purseLeft?.doubleValue
        let squad = context.squadMax.map { " · \(context.playersWon)/\($0) players" } ?? ""
        if let left, left < 0 {
            // A1: never a negative number -- the amount over goes in coral.
            (Text("Bidding as ") + Text(context.franchiseName).bold() + Text(" · ")
                + Text("\(rupees(-left)) over purse").foregroundColor(coral).bold() + Text(squad))
                .font(.footnote).lineLimit(2)
        } else {
            (Text("Bidding as ") + Text(context.franchiseName).bold() + Text(left.map { " · \(rupees($0)) left" } ?? "") + Text(squad))
                .font(.footnote).lineLimit(1)
        }
    }

    private func statusTile(icon: String, tint: Color, title: Text, sub: Text) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).foregroundColor(tint).font(.system(size: 20))
            VStack(alignment: .leading, spacing: 2) {
                title.font(.system(size: 14, weight: .semibold)).lineLimit(1)
                sub.font(.system(size: 12)).foregroundColor(.secondary).lineLimit(1)
            }
        }
        .frame(minHeight: 54)
        .transition(.opacity)
    }

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

    // MARK: - organizer controls (A2 dead-end variant)

    @ViewBuilder
    private func organizerSection(_ auction: AuctionStateDto) -> some View {
        if state.isOrganizer {
            let acting = state.isActing || offline
            Section("Organizer controls") {
                switch auction.auctionStatus {
                case .notStarted:
                    Button("Start Auction") { wrapper.start() }.disabled(acting)
                case .inProgress:
                    if auction.currentPlayerId != nil {
                        Button("Sold") { wrapper.sold() }.disabled(acting || auction.currentLeadingFranchiseId == nil)
                        Button("Unsold") { wrapper.unsold() }.disabled(acting)
                        Button("Undo") { wrapper.undo() }.disabled(acting)
                    } else {
                        Button("Next Player") { wrapper.nextPlayer() }.disabled(acting)
                        Button("Undo") { wrapper.undo() }.disabled(acting)
                    }
                    VStack(alignment: .leading, spacing: 3) {
                        Toggle(
                            "Allow exceeding purse",
                            isOn: Binding(get: { auction.allowExceedPurse }, set: { wrapper.toggleExceedPurse($0) })
                        )
                        .disabled(acting)
                        // A2: highlighted only when purses (not squads) are what stops bidding.
                        if state.isDeadEnd && auction.purseBelowBase > 0 {
                            Text(auction.purseBelowBase == 1 ? "1 franchise is stopped by its purse" : "\(auction.purseBelowBase) franchises are stopped by their purse")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                    .listRowBackground(state.isDeadEnd && auction.purseBelowBase > 0 ? gold.opacity(0.08) : nil)
                    if state.isDeadEnd {
                        // A2: End Auction becomes the main action.
                        Button { wrapper.end() } label: {
                            Label("End Auction", systemImage: "stop.circle").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(coral)
                        .disabled(acting)
                    } else {
                        Button("End Auction", role: .destructive) { wrapper.end() }.disabled(acting)
                    }
                case .completed:
                    // See AuctionLiveScreen.kt's matching comment: the sale/unsold call that completed
                    // the auction is still undoable server-side.
                    Button("Undo") { wrapper.undo() }.disabled(acting)
                default:
                    EmptyView()
                }
            }
        }
    }

    // MARK: - results (A1)

    private func resultsSection(_ results: AuctionResultsDto) -> some View {
        Section("Results") {
            ForEach(results.franchises, id: \.franchiseId) { franchise in
                let remaining = franchise.purseRemaining?.doubleValue
                VStack(alignment: .leading, spacing: 5) {
                    Text(franchise.franchiseName).font(.system(size: 14, weight: .semibold))
                    let players = "\(franchise.playersWon.count) \(franchise.playersWon.count == 1 ? "player" : "players") · \(rupees(franchise.purseSpent)) spent"
                    if let remaining, remaining < 0 {
                        Text(players).font(.caption).foregroundColor(.secondary)
                        Label("\(rupees(-remaining)) over purse", systemImage: "exclamationmark.circle")
                            .font(.system(size: 11.5, weight: .semibold))
                            .foregroundColor(coral)
                        if let purse = state.league?.auctionPurse?.doubleValue {
                            HStack {
                                Text("Purse \(rupees(purse))").foregroundColor(.secondary)
                                Spacer()
                                Text("Spent \(rupees(franchise.purseSpent))").foregroundColor(coral)
                            }
                            .font(.system(size: 12, weight: .medium))
                        }
                    } else {
                        Text(players + (remaining.map { " · \(rupees($0)) left" } ?? "")).font(.caption).foregroundColor(.secondary)
                    }
                    if franchise.belowSquadMin {
                        Text("Below minimum squad size").font(.caption).foregroundColor(coral)
                    }
                    ForEach(franchise.playersWon, id: \.playerId) { player in
                        HStack {
                            Text(player.playerName ?? "Unnamed player").font(.footnote)
                            Spacer()
                            Text(rupees(player.soldPrice)).font(.system(.footnote, design: .monospaced))
                        }
                    }
                }
            }
        }
    }

    // MARK: - helpers

    /// Changes whenever the bid-area mode or the player changes -- drives the outbid flash.
    private var dockKey: String {
        "\(state.dockMode is DockModeLeading)-\(state.auction?.currentPlayerId ?? "")-\(state.auction?.currentBidAmount?.doubleValue ?? 0)"
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

    private func lastLine(_ auction: AuctionStateDto) -> String? {
        guard let last = auction.lastResult else { return nil }
        let name = last.playerName ?? "The last player"
        if last.sold {
            return "Last: \(name) sold to \(last.franchiseName ?? "a franchise")" + (last.amount.map { " for \(rupees($0.doubleValue))." } ?? ".")
        }
        return "Last: \(name) went unsold."
    }

    private func statusLabel(_ status: AuctionStatus) -> String {
        switch status {
        case .inProgress: return "● Live"
        case .notStarted: return "Not started"
        default: return "Ended"
        }
    }

    private func secondsSince(_ millis: KotlinLong?, now: Date) -> Int {
        guard let millis else { return 0 }
        return max(0, Int(now.timeIntervalSince1970 - Double(millis.int64Value) / 1000))
    }
}

private let coral = Color(red: 0xFF / 255, green: 0x8B / 255, blue: 0x70 / 255)
private let gold = Color(red: 0xF2 / 255, green: 0xB5 / 255, blue: 0x44 / 255)
private let liveGreen = Color(red: 0x7B / 255, green: 0xC4 / 255, blue: 0x7F / 255)

private extension View {
    /// A4's 26 pt pill: radius 13, 600 12 pt.
    func pill(background: Color, foreground: Color) -> some View {
        font(.system(size: 12, weight: .semibold))
            .foregroundColor(foreground)
            .padding(.leading, 9)
            .padding(.trailing, 11)
            .frame(height: 26)
            .background(Capsule().fill(background))
            .transition(.opacity)
    }
}

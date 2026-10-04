import SwiftUI
import Shared

/// Mirrors `AuctionSettingsViewModel`'s shared `StateFlow<AuctionSettingsState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents. **Authored but unverified, not wired into any
/// navigation** -- see docs/PHASE2.md Section 5 / docs/PHASE4.md.
@MainActor
final class AuctionSettingsViewModelWrapper: ObservableObject {
    @Published var state: AuctionSettingsState

    private let viewModel: AuctionSettingsViewModel

    init(leagueId: String) {
        let viewModel = KoinHelper().auctionSettingsViewModel(leagueId: leagueId)
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func retry() { viewModel.retry() }
    func onBasePriceChanged(_ value: String) { viewModel.onBasePriceChanged(value: value) }
    func onPurseChanged(_ value: String) { viewModel.onPurseChanged(value: value) }
    func onSquadMinChanged(_ value: String) { viewModel.onSquadMinChanged(value: value) }
    func onSquadMaxChanged(_ value: String) { viewModel.onSquadMaxChanged(value: value) }
    func onBidIncrementChanged(_ value: String) { viewModel.onBidIncrementChanged(value: value) }
    /// `nil` clears it; otherwise stored as an ISO-8601 instant (docs/PHASE11.md D3).
    func onScheduledAtChanged(_ value: Date?) {
        viewModel.onScheduledAtChanged(value: value.map { ISO8601DateFormatter().string(from: $0) })
    }
    /// Puts back an exact earlier value (Undo after a clear), so the saved time is recognised as unchanged.
    func restoreScheduledAt(_ iso: String) { viewModel.onScheduledAtChanged(value: iso) }
    func submit() { viewModel.submit() }
}

/// iOS equivalent of `androidApp/.../ui/AuctionSettingsScreen.kt`: five editable fields plus a
/// read-only auction pool/purse view rendered from the same loaded league -- no second fetch.
struct AuctionSettingsView: View {
    let leagueId: String

    @StateObject private var wrapper: AuctionSettingsViewModelWrapper
    @State private var pickingTime = false
    @State private var draftTime = Date()
    @State private var clearedSchedule: String?
    @State private var banner: Notice?

    init(leagueId: String) {
        self.leagueId = leagueId
        _wrapper = StateObject(wrappedValue: AuctionSettingsViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        Group {
            if wrapper.state.isLoading {
                ProgressView()
            } else if let league = wrapper.state.league {
                Form {
                    Section("Settings") {
                        field("Base price", wrapper.state.basePrice, .basePrice) { wrapper.onBasePriceChanged($0) }
                        field("Purse per franchise", wrapper.state.purse, .purse) { wrapper.onPurseChanged($0) }
                        field("Squad size (min)", wrapper.state.squadMin, .squadMin) { wrapper.onSquadMinChanged($0) }
                        field("Squad size (max)", wrapper.state.squadMax, .squadMax) { wrapper.onSquadMaxChanged($0) }
                        field("Bid increment", wrapper.state.bidIncrement, .bidIncrement) { wrapper.onBidIncrementChanged($0) }
                    }

                    // U4 J9-J12 (iOS: one inline date-and-time picker in a sheet, earliest = now).
                    Section {
                        HStack {
                            Button {
                                // U5 J13 on iOS: the inline picker always has a value, so a new time opens on
                                // now + 1 h rounded to :00.
                                draftTime = scheduledDate.map { max($0, Date()) } ?? nextWholeHour()
                                pickingTime = true
                            } label: {
                                Text(scheduledDate.map(scheduleText) ?? "Auction date & time (optional)")
                                    .foregroundColor(scheduledDate == nil ? .secondary : .primary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .disabled(wrapper.state.isSaving)
                            if let iso = wrapper.state.scheduledAt {
                                Button {
                                    wrapper.onScheduledAtChanged(nil)
                                    clearedSchedule = iso
                                    banner = Notice(message: "Auction time cleared", actionLabel: "Undo")
                                } label: {
                                    Image(systemName: "xmark").foregroundColor(.secondary).frame(width: 40, height: 40)
                                }
                                .buttonStyle(.borderless)
                                .accessibilityLabel("Clear auction time")
                                .disabled(wrapper.state.isSaving)
                            } else {
                                Image(systemName: "calendar").foregroundColor(.secondary).frame(width: 40, height: 40)
                            }
                        }
                    } header: {
                        Text("Auction date & time (optional)")
                    } footer: {
                        if let error = wrapper.state.scheduledAtError {
                            Text(error).foregroundColor(Color(red: 0xB3 / 255, green: 0x26 / 255, blue: 0x1E / 255))
                        } else if wrapper.state.scheduledAtPassed {
                            Label("This time has passed. Pick a new one or clear it.", systemImage: "clock")
                                .foregroundColor(Color(red: 0x7A / 255, green: 0x5B / 255, blue: 0x12 / 255))
                        } else {
                            // U5 J14: no zone name on either platform.
                            Text("Uses your phone's time zone.")
                        }
                    }

                    if let warning = wrapper.state.squadWarning {
                        Text("Squad max × franchises (\(warning.squadMax) × \(warning.franchises) = \(warning.total)) is more than players required (\(warning.playersRequired)). Some squads may not fill.")
                            .foregroundColor(.orange)
                    }

                    if let error = wrapper.state.saveError {
                        Text(error.message).foregroundColor(.red)
                    } else if wrapper.state.showSavedNotice {
                        Text("Auction settings saved")
                    }

                    Button(wrapper.state.isSaving ? "Saving…" : "Save") { wrapper.submit() }
                        .disabled(!wrapper.state.canSave)

                    Section("Auction pool") {
                        Text("\(league.players.count) player(s) joined")
                        if league.franchises.isEmpty {
                            Text("No franchises yet. They appear here once owners claim them.")
                        }
                        ForEach(league.franchises, id: \.id) { franchise in
                            Text(franchise.name)
                        }
                    }
                }
            } else {
                Text("Couldn't load auction settings. Check your connection and try again.").foregroundColor(.red)
            }
        }
        .navigationTitle("Auction Settings")
        .onAppear { wrapper.retry() }
        .sheet(isPresented: $pickingTime) {
            NavigationStack {
                DatePicker("Auction date & time", selection: $draftTime, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                    .datePickerStyle(.graphical)
                    .padding()
                    .navigationTitle("Auction date & time")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) { Button("Cancel") { pickingTime = false } }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Done") {
                                wrapper.onScheduledAtChanged(draftTime)
                                pickingTime = false
                            }
                        }
                    }
            }
            .presentationDetents([.medium, .large])
        }
        .noticeBanner($banner, onAction: {
            if let iso = clearedSchedule { wrapper.restoreScheduledAt(iso) }
            clearedSchedule = nil
            banner = nil
        })
    }

    /// "12 Oct 2026 · 6:30 PM" in the device locale (U4 J9).
    private func scheduleText(_ date: Date) -> String {
        let day = DateFormatter()
        day.setLocalizedDateFormatFromTemplate("d MMM yyyy")
        let time = DateFormatter()
        time.timeStyle = .short
        return "\(day.string(from: date)) · \(time.string(from: date))"
    }

    private var scheduledDate: Date? {
        wrapper.state.scheduledAt.flatMap { ISO8601DateFormatter().date(from: $0) }
    }

    private func field(_ label: String, _ value: String, _ key: AuctionField, onChange: @escaping (String) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            TextField(label, text: Binding(get: { value }, set: onChange))
                .keyboardType(.decimalPad)
            if let error = wrapper.state.errorFor(field: key) {
                Text(error).font(.caption).foregroundColor(.red)
            }
        }
    }
}

/// Now + 1 h, rounded down to the hour (U5 J13): 14:20 opens on 15:00.
private func nextWholeHour(from now: Date = Date()) -> Date {
    let calendar = Calendar.current
    let inAnHour = now.addingTimeInterval(3600)
    return calendar.date(from: calendar.dateComponents([.year, .month, .day, .hour], from: inAnHour)) ?? inAnHour
}

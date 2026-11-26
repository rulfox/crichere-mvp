import SwiftUI
import Shared

/// Mirrors `AuctionSettingsViewModel`'s shared `StateFlow<AuctionSettingsState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents.
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
    func onAddRange() { viewModel.onAddRange() }
    func onRemoveRange(_ index: Int32) { viewModel.onRemoveRange(index: index) }
    func onRangeUpToChanged(_ index: Int32, _ value: String) { viewModel.onRangeUpToChanged(index: index, value: value) }
    func onRangeStepChanged(_ index: Int32, _ value: String) { viewModel.onRangeStepChanged(index: index, value: value) }
    /// `nil` clears it; otherwise stored as an ISO-8601 instant (docs/PHASE11.md D3).
    func onScheduledAtChanged(_ value: Date?) {
        viewModel.onScheduledAtChanged(value: value.map { ISO8601DateFormatter().string(from: $0) })
    }
    /// Puts back an exact earlier value (Undo after a clear), so the saved time is recognised as unchanged.
    func restoreScheduledAt(_ iso: String) { viewModel.onScheduledAtChanged(value: iso) }
    func submit() { viewModel.submit() }
    func onSavedNoticeShown() { viewModel.onSavedNoticeShown() }
}


/// Auction settings (design J1-J5, androidApp `ui/AuctionSettingsScreen.kt`): five fields (base price,
/// purse, squad min/max, bid increment), the optional auction time (J9-J14), the live squad warning,
/// and the read-only auction pool -- rendered from the league already loaded, no second call. Save
/// sits in a bottom bar; saved / failed / "time cleared" show as the dark bar above it.
struct AuctionSettingsView: View {
    let leagueId: String
    var onBack: () -> Void = {}

    @StateObject private var wrapper: AuctionSettingsViewModelWrapper
    // U4 J9: clearing the auction time is instant; "Auction time cleared" offers Undo for 4 s.
    @State private var clearedSchedule: String?
    @State private var pickingDate = false
    @State private var pickedDate: Date?

    init(leagueId: String, onBack: @escaping () -> Void = {}) {
        self.leagueId = leagueId
        self.onBack = onBack
        _wrapper = StateObject(wrappedValue: AuctionSettingsViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        let state = wrapper.state
        AuctionSettingsContent(
            state: state,
            clearedSchedule: clearedSchedule,
            actions: AuctionSettingsActions(
                onBack: onBack,
                onRetry: wrapper.retry,
                onBasePrice: wrapper.onBasePriceChanged,
                onPurse: wrapper.onPurseChanged,
                onSquadMin: wrapper.onSquadMinChanged,
                onSquadMax: wrapper.onSquadMaxChanged,
                onBidIncrement: wrapper.onBidIncrementChanged,
                onAddRange: wrapper.onAddRange,
                onRemoveRange: wrapper.onRemoveRange,
                onRangeUpTo: wrapper.onRangeUpToChanged,
                onRangeStep: wrapper.onRangeStepChanged,
                onPickSchedule: { pickingDate = true },
                onClearSchedule: {
                    guard let current = state.scheduledAt else { return }
                    wrapper.onScheduledAtChanged(nil)
                    clearedSchedule = current
                },
                onUndoClear: {
                    if let clearedSchedule { wrapper.restoreScheduledAt(clearedSchedule) }
                    clearedSchedule = nil
                },
                onSave: wrapper.submit
            )
        )
        .crichereScreen()
        .onAppear { wrapper.retry() }
        .task(id: state.showSavedNotice) {
            guard state.showSavedNotice else { return }
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            wrapper.onSavedNoticeShown()
        }
        .task(id: clearedSchedule) {
            guard clearedSchedule != nil else { return }
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            clearedSchedule = nil
        }
        .crichereDialog(isPresented: $pickingDate) {
            CrichereDatePickerDialog(title: "Auction date", initial: pickedDate ?? scheduleDay(state.scheduledAt), minimum: localTodayAsUtc(),
                                     style: .auction, onDismiss: { pickingDate = false; pickedDate = nil }) { day in
                pickingDate = false
                // Let the date dialog fade out before the time dialog fades in.
                Task { try? await Task.sleep(nanoseconds: 350_000_000); pickedDate = day }
            }
        }
        .crichereDialog(isPresented: Binding(get: { pickedDate != nil && !pickingDate }, set: { if !$0 { pickedDate = nil } })) {
            if let day = pickedDate {
                CrichereTimePickerDialog(title: "Auction time · \(utcDateFormatter("d MMM yyyy").string(from: day))",
                                         initial: parseInstant(state.scheduledAt) ?? sevenPm(),
                                         onBack: { pickingDate = true }) { time in
                    pickedDate = nil
                    wrapper.onScheduledAtChanged(combine(day: day, time: time))
                }
            }
        }
    }
}

/// `2026-10-12T13:30:00Z` (with or without fractional seconds) → `Date`.
private func parseInstant(_ iso: String?) -> Date? {
    guard let iso, !iso.isEmpty else { return nil }
    let plain = ISO8601DateFormatter()
    if let date = plain.date(from: iso) { return date }
    let fractional = ISO8601DateFormatter()
    fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return fractional.date(from: iso)
}

/// The scheduled instant's local calendar day, as the UTC midnight the date picker works in.
private func scheduleDay(_ iso: String?) -> Date? {
    guard let date = parseInstant(iso) else { return nil }
    let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
    return utcCalendar.date(from: parts)
}

/// Today in the phone's zone, as the UTC midnight the date picker works in (earlier days are disabled).
private func localTodayAsUtc() -> Date {
    utcCalendar.date(from: Calendar.current.dateComponents([.year, .month, .day], from: Date())) ?? Date()
}

private func sevenPm() -> Date {
    Calendar.current.date(bySettingHour: 19, minute: 0, second: 0, of: Date()) ?? Date()
}

/// [day] (UTC midnight of the picked calendar day) at [time]'s wall-clock hour and minute, in the phone's zone.
private func combine(day: Date, time: Date) -> Date {
    var parts = utcCalendar.dateComponents([.year, .month, .day], from: day)
    let clock = Calendar.current.dateComponents([.hour, .minute], from: time)
    parts.hour = clock.hour
    parts.minute = clock.minute
    parts.second = 0
    return Calendar.current.date(from: parts) ?? day
}

/// Everything the Auction settings screen does.
struct AuctionSettingsActions {
    var onBack: () -> Void = {}
    var onRetry: () -> Void = {}
    var onBasePrice: (String) -> Void = { _ in }
    var onPurse: (String) -> Void = { _ in }
    var onSquadMin: (String) -> Void = { _ in }
    var onSquadMax: (String) -> Void = { _ in }
    var onBidIncrement: (String) -> Void = { _ in }
    var onAddRange: () -> Void = {}
    var onRemoveRange: (Int32) -> Void = { _ in }
    var onRangeUpTo: (Int32, String) -> Void = { _, _ in }
    var onRangeStep: (Int32, String) -> Void = { _, _ in }
    var onPickSchedule: () -> Void = {}
    var onClearSchedule: () -> Void = {}
    var onUndoClear: () -> Void = {}
    var onSave: () -> Void = {}
}

/// The screen, stateless for the DEBUG parity gallery.
struct AuctionSettingsContent: View {
    let state: AuctionSettingsState
    var clearedSchedule: String? = nil
    var actions = AuctionSettingsActions()

    var body: some View {
        VStack(spacing: 0) {
            BackTitleBar(title: "Auction Settings", onBack: actions.onBack)
            if state.isLoading && state.league == nil {
                CrichereSpinner(color: CrichereColor.primary, lineWidth: 4, size: 40).frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let league = state.league {
                ZStack(alignment: .bottom) {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            if state.isLocked { LockedAuctionNote() }
                            SettingsFields(state: state, actions: actions)
                            if let warning = state.squadWarning {
                                SquadWarningNote(text: "Squad max × franchises (\(warning.squadMax) × \(warning.franchises) = \(warning.total)) is more than "
                                                 + "players required (\(warning.playersRequired)). Some squads may not fill.")
                            }
                            PoolCard(league: league)
                        }
                        .padding(.horizontal, 19)
                        .padding(.top, 5)
                        .padding(.bottom, 96)
                    }
                    .scrollDismissesKeyboard(.interactively)
                    snackbar
                        .padding(.horizontal, 13)
                        .padding(.bottom, 19)
                }
                if !state.isLocked { SaveBar(state: state, onSave: actions.onSave) }
            } else {
                LoadErrorView(title: "Couldn't load auction settings", onRetry: actions.onRetry)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(CrichereColor.background.ignoresSafeArea())
    }

    @ViewBuilder private var snackbar: some View {
        if let error = state.saveError {
            CrichereSnackbar(message: error.message, actionLabel: error.canRetry ? "Retry" : nil, onAction: error.canRetry ? actions.onSave : nil)
        } else if state.showSavedNotice {
            CrichereSnackbar(message: "Auction settings saved")
        } else if clearedSchedule != nil {
            CrichereSnackbar(message: "Auction time cleared", actionLabel: "Undo", onAction: actions.onUndoClear)
        }
    }
}

/// J3: the fields fade to 55% and stop taking input while saving. Each field reserves 7 pt above its
/// box for the notched label, so the board's 12 pt gaps are 5 pt here.
private struct SettingsFields: View {
    let state: AuctionSettingsState
    let actions: AuctionSettingsActions

    var body: some View {
        let errors = state.fieldErrors
        VStack(alignment: .leading, spacing: 5) {
            field(state.basePrice, actions.onBasePrice, "Base price", errors[.basePrice], .decimalPad)
            field(state.purse, actions.onPurse, "Purse per franchise", errors[.purse], .decimalPad)
            // Board V1.
            Text(auctionMoneyNote).crichere(.sans(12, lineHeight: 18), CrichereColor.onSurfaceVariant).padding(.horizontal, 2)
            HStack(alignment: .top, spacing: 10) {
                field(state.squadMin, actions.onSquadMin, "Squad size (min)", errors[.squadMin], .numberPad)
                field(state.squadMax, actions.onSquadMax, "Squad size (max)", errors[.squadMax], .numberPad)
            }
            // Design S1-S4 (docs/PHASE22.md): the bid steps replace the single bid increment field.
            if state.isLocked {
                LockedBidSteps(rows: state.lockedSteps).padding(.vertical, 8)
            } else {
                BidStepsSection(state: state, actions: actions).padding(.bottom, 8)
            }
            ScheduledAtField(state: state, actions: actions).disabled(state.isLocked)
        }
        .opacity(state.isSaving ? 0.55 : 1)
    }

    private func field(_ value: String, _ onChange: @escaping (String) -> Void, _ label: String, _ error: String?,
                       _ keyboard: UIKeyboardType) -> some View {
        CrichereTextField(text: Binding(get: { value }, set: onChange), label: label, look: .form, error: error, mono: true,
                          readOnly: state.isSaving, enabled: !state.isLocked, keyboard: keyboard)
    }
}

private let passedWarning = Color(hex: 0x7A5B12)

/// Optional "bidding opens at" time (J9-J12): a read-only tap field that opens the date dialog, then
/// the time dialog. The row under it always holds the time-zone helper, the passed-time warning (J9)
/// or the error (J12). Clearing is immediate, with Undo.
private struct ScheduledAtField: View {
    let state: AuctionSettingsState
    let actions: AuctionSettingsActions

    var body: some View {
        let readOnly = state.isSaving
        let current = parseInstant(state.scheduledAt)
        CrichereTapField(
            label: "Auction date & time (optional)",
            value: current.map { valueFormatter.string(from: $0) },
            look: .form,
            error: state.scheduledAtError,
            trailingIcon: current != nil ? "ic_close" : "ic_event",
            valueSize: 14.5,
            onTrailingClick: {
                guard !readOnly else { return }
                if current != nil { actions.onClearSchedule() } else { actions.onPickSchedule() }
            },
            trailingLabel: current != nil ? "Clear auction time" : "Pick auction time",
            trailingSize: current != nil ? 20 : 22,
            supporting: supporting,
            onClick: { if !readOnly { actions.onPickSchedule() } }
        )
    }

    private var valueFormatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateFormat = "d MMM yyyy · h:mm a"
        return formatter
    }

    @ViewBuilder private var supporting: some View {
        if let error = state.scheduledAtError {
            Text(error).crichere(.sans(12, .medium, lineHeight: 16), CrichereColor.error).padding(.leading, 14)
        } else if state.scheduledAtPassed {
            HStack(spacing: 4) {
                CrIcon("ic_schedule", size: 15, tint: passedWarning)
                Text("This time has passed. Pick a new one or clear it.").crichere(.sans(12, .medium, lineHeight: 16), passedWarning)
            }
            .padding(.leading, 14)
        } else {
            // U5 J14: no zone name -- "GMT+05:30" means nothing to most organizers.
            Text("Uses your phone's time zone.").crichere(.sans(12, lineHeight: 16), CrichereColor.onSurfaceVariant).padding(.leading, 14)
        }
    }
}

/// J2's amber note.
private struct SquadWarningNote: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            CrIcon("ic_warning", size: 18, tint: CrichereColor.onWarning).padding(.top, 3.6)
            Text(text).crichere(.sans(12.5, .medium, lineHeight: 17.5), CrichereColor.onWarning)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(CrichereColor.warningContainer, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct PoolCard: View {
    let league: LeagueDto

    var body: some View {
        let card = RoundedRectangle(cornerRadius: 14, style: .continuous)
        VStack(alignment: .leading, spacing: 6) {
            Text("Auction pool").crichere(.archivo(14, .bold, lineHeight: 14), CrichereColor.onBackground)
            Text("\(league.players.count) player(s) joined").crichere(.sans(13, lineHeight: 16.9), CrichereColor.onBackground)
            if league.franchises.isEmpty {
                Text("No franchises yet. They appear here once owners claim them.").crichere(.sans(13, lineHeight: 18.2), CrichereColor.onSurfaceVariant)
            } else {
                let purse = league.auctionPurse.map { AuctionSettingsViewModelKt.amountText(value: $0) } ?? "not set"
                Text("Franchises (purse: \(purse) each):").crichere(.sans(13, lineHeight: 16.9), CrichereColor.onBackground)
                Text(league.franchises.map { "- \($0.name)" }.joined(separator: "\n")).crichere(.sans(12.5, lineHeight: 18.75), Color(hex: 0x3E4A41))
            }
        }
        .padding(.horizontal, 15)
        .padding(.vertical, 13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(CrichereColor.surface, in: card)
        .overlay(card.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }
}

/// J1-J5's bottom bar: 44 pt Save pill -- grey while errors show (J2), spinner while saving (J3).
private struct SaveBar: View {
    let state: AuctionSettingsState
    let onSave: () -> Void

    var body: some View {
        let enabled = state.canSave
        let active = enabled || state.isSaving
        VStack(spacing: 0) {
            Rectangle().fill(CrichereColor.outlineVariant).frame(height: 1)
            Button(action: onSave) {
                HStack(spacing: 8) {
                    if state.isSaving { CrichereSpinner(color: .white, lineWidth: 2, size: 20) }
                    Text(state.isSaving ? "Saving…" : "Save").crichere(.sans(13.5, .semiBold, lineHeight: 13.5))
                }
                .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
            }
            .buttonStyle(PillButtonStyle(fill: active ? CrichereColor.primary : CrichereColor.disabledContainer, pressedFill: CrichereColor.greenPressed,
                                         content: active ? .white : Color(hex: 0x6B756D), radius: 22))
            .disabled(!enabled)
            .padding(.horizontal, 19)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
    }
}

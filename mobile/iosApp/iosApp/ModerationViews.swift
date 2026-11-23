import SafariServices
import SwiftUI
import Shared

/// iOS twin of `androidApp/.../ui/ModerationUi.kt` (docs/PHASE18.md, boards P11 and P12).

let termsUrl = URL(string: "https://crichere.com/terms")!
let privacyUrl = URL(string: "https://crichere.com/privacy")!
let auctionMoneyNote = "Bids are recorded here only. No money moves through Crichere."

/// In-app browser for the legal pages, the iOS side of Android's Custom Tabs.
struct SafariView: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController { SFSafariViewController(url: url) }
    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

extension URL: @retroactive Identifiable {
    public var id: String { absoluteString }
}

// MARK: - wrappers

@MainActor
final class ReportViewModelWrapper: ObservableObject {
    @Published var state: ReportState
    private let viewModel: ReportViewModel

    init() {
        let viewModel = KoinHelper().reportViewModel
        self.viewModel = viewModel
        self.state = viewModel.state.value
        Task { [weak self] in
            for await newState in viewModel.state { self?.state = newState }
        }
    }

    func open(_ subject: ReportSubject) { viewModel.open(subject: subject) }
    func close() { viewModel.close() }
    func selectReason(_ reason: ReportReason) { viewModel.selectReason(reason: reason) }
    func next() { viewModel.next() }
    func back() { viewModel.back() }
    func onDetailsChanged(_ value: String) { viewModel.onDetailsChanged(value: value) }
    func setAlsoBlock(_ value: Bool) { viewModel.setAlsoBlock(value: value) }
    func send() { viewModel.send() }
}

@MainActor
final class BlockViewModelWrapper: ObservableObject {
    @Published var state: BlockState
    @Published var blockedIds: Set<String> = []
    private let viewModel: BlockViewModel

    init() {
        let viewModel = KoinHelper().blockViewModel
        self.viewModel = viewModel
        self.state = viewModel.state.value
        Task { [weak self] in
            for await newState in viewModel.state { self?.state = newState }
        }
        Task { [weak self] in
            for await ids in viewModel.blockedIds { self?.blockedIds = ids }
        }
    }

    func askToBlock(userId: String, name: String) { viewModel.askToBlock(target: BlockTarget(userId: userId, name: name)) }
    func cancel() { viewModel.cancel() }
    func confirm() { viewModel.confirm() }
    func unblock(userId: String, name: String) { viewModel.unblock(target: BlockTarget(userId: userId, name: name)) }
    func undoBlock() { viewModel.undoBlock() }
    func undoUnblock() { viewModel.undoUnblock() }
    func dismissSnackbar() { viewModel.dismissSnackbar() }
}

@MainActor
final class BlockedPeopleViewModelWrapper: ObservableObject {
    @Published var state: BlockedPeopleState
    private let viewModel: BlockedPeopleViewModel

    init() {
        let viewModel = KoinHelper().blockedPeopleViewModel
        self.viewModel = viewModel
        self.state = viewModel.state.value
        Task { [weak self] in
            for await newState in viewModel.state { self?.state = newState }
        }
    }

    func load() { viewModel.load() }
    func unblock(_ userId: String) { viewModel.unblock(userId: userId) }
    func undo() { viewModel.undo() }
    func dismissSnackbar() { viewModel.dismissSnackbar() }
}

@MainActor
final class ModerationNoticesWrapper: ObservableObject {
    @Published var notices: [ModerationNoticeDto] = []
    private let viewModel: ModerationNoticesViewModel

    init() {
        let viewModel = KoinHelper().moderationNoticesViewModel
        self.viewModel = viewModel
        Task { [weak self] in
            for await value in viewModel.notices { self?.notices = value }
        }
    }

    func dismissFirst() { viewModel.dismissFirst() }
}

// MARK: - report (boards R4 to R7)

private func reasonLabel(_ reason: ReportReason, league: Bool) -> String {
    switch reason {
    case .inappropriateImage: return league ? "Inappropriate image or text" : "Inappropriate photo or logo"
    case .hateOrHarassment: return "Hate or harassment"
    case .impersonation: return "Pretending to be someone else"
    case .under18: return "Under 18"
    case .spamOrMisleading: return "Spam or misleading"
    case .bettingOrGambling: return "Betting or gambling"
    case .fakeOrScamLeague: return "Fake or scam league"
    case .somethingElse: return "Something else"
    }
}

private func firstName(_ name: String) -> String { name.split(separator: " ").first.map(String.init) ?? name }

/// What the report sheet does (ReportViewModel).
struct ReportActions {
    var onClose: () -> Void = {}
    var onSelectReason: (ReportReason) -> Void = { _ in }
    var onNext: () -> Void = {}
    var onBack: () -> Void = {}
    var onDetailsChanged: (String) -> Void = { _ in }
    var onAlsoBlock: (Bool) -> Void = { _ in }
    var onSend: () -> Void = {}
}

extension ReportActions {
    @MainActor init(_ wrapper: ReportViewModelWrapper) {
        self.init(onClose: wrapper.close, onSelectReason: wrapper.selectReason, onNext: wrapper.next, onBack: wrapper.back,
                  onDetailsChanged: wrapper.onDetailsChanged, onAlsoBlock: wrapper.setAlsoBlock, onSend: wrapper.send)
    }
}

extension View {
    /// Boards R4 to R6 as Android's ModalBottomSheet (white, can't be dismissed while sending -- R5e).
    func reportSheet(_ wrapper: ReportViewModelWrapper) -> some View {
        crichereSheet(isPresented: Binding(get: { wrapper.state.subject != nil }, set: { if !$0 { wrapper.close() } }),
                      dismissable: !wrapper.state.isSubmitting, style: CrichereSheetStyle(background: CrichereColor.surface, scrim: CrichereSheetStyle.material.scrim)) {
            ReportSheetContent(state: wrapper.state, actions: ReportActions(wrapper))
        }
    }
}

/// The report sheet's body (androidApp `ModerationUi.kt` ReportSheet): reason, details, sent / already reported.
struct ReportSheetContent: View {
    let state: ReportState
    var actions = ReportActions()

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let subject = state.subject {
                switch state.step {
                case .reason: ReasonStep(state: state, subject: subject, actions: actions)
                case .details: DetailsStep(state: state, subject: subject, actions: actions)
                case .sent:
                    ReportOutcome(icon: "ic_check", title: "Thanks. Our team will review this.",
                                  message: subject.type == .league
                                    ? "The organizer won't be told who reported this league. If something breaks our rules, we'll remove it."
                                    : "\(firstName(subject.name)) won't be told who reported them. If something breaks our rules, we'll remove it.",
                                  onDone: actions.onClose)
                case .alreadyReported:
                    ReportOutcome(icon: "ic_hourglass_top", title: "You've already reported this. Our team will review it.",
                                  message: "Your earlier report is still open, so this one wasn't sent again.", onDone: actions.onClose,
                                  written: state.details.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : state.details)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
        .padding(.top, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ReasonStep: View {
    let state: ReportState
    let subject: ReportSubject
    let actions: ReportActions

    var body: some View {
        let person = subject.type == .user
        Text(person ? "REPORT A PERSON" : "REPORT A LEAGUE").crichere(.sans(11, .medium), CrichereColor.onSurfaceVariant)
        Spacer().frame(height: 10)
        Text(subject.name).crichere(.sans(17, .semiBold), CrichereColor.onBackground)
        Spacer().frame(height: 4)
        Text(subject.context).crichere(.sans(13), CrichereColor.onSurfaceVariant)
        Spacer().frame(height: 16)
        Text("What's the problem?").crichere(.sans(16, .semiBold), CrichereColor.onBackground)
        Spacer().frame(height: 4)
        ForEach(state.reasons, id: \.self) { reason in
            Button { actions.onSelectReason(reason) } label: {
                HStack(spacing: 14) {
                    RadioDot(selected: state.reason == reason)
                    Text(reasonLabel(reason, league: !person)).crichere(.sans(15), CrichereColor.onBackground)
                    Spacer(minLength: 0)
                }
                .frame(height: 52)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(state.reason == reason ? .isSelected : [])
        }
        Spacer().frame(height: 8)
        Text(person ? "\(firstName(subject.name)) won't be told who reported them. The organizer doesn't see reports. The Crichere team reviews each one."
                    : "The organizer won't be told who reported this league, and doesn't see reports. The Crichere team reviews each one.")
            .crichere(.sans(12, lineHeight: 18), CrichereColor.onSurfaceVariant)
        Spacer().frame(height: 14)
        CricherePrimaryButton("Next", enabled: state.reason != nil, action: actions.onNext)
    }
}

/// Material RadioButton: 20 pt ring, 2 pt stroke, 10 pt dot when selected (in a 24 pt box, no ripple target).
private struct RadioDot: View {
    let selected: Bool

    var body: some View {
        ZStack {
            Circle().strokeBorder(selected ? CrichereColor.primary : CrichereColor.onSurfaceVariant, lineWidth: 2).frame(width: 20, height: 20)
            if selected { Circle().fill(CrichereColor.primary).frame(width: 10, height: 10) }
        }
        .frame(width: 20, height: 20)
    }
}

private struct DetailsStep: View {
    let state: ReportState
    let subject: ReportSubject
    let actions: ReportActions

    // The field is one line tall until it grows; tapping anywhere in the 132 pt box focuses it, like
    // Android's full-height text area.
    @FocusState private var detailsFocused: Bool

    var body: some View {
        let atLimit = state.details.count >= 500
        HStack(spacing: 0) {
            Button(action: actions.onBack) { CrIcon("ic_arrow_back", size: 22, tint: CrichereColor.onBackground).frame(width: 44, height: 44) }
                .buttonStyle(.pressCircle)
                .disabled(state.isSubmitting)
                .accessibilityLabel("Back")
            Text("Report \(subject.name)").crichere(.sans(17, .semiBold), CrichereColor.onBackground)
            Spacer(minLength: 0)
        }
        Spacer().frame(height: 10)
        if let reason = state.reason {
            Text(reasonLabel(reason, league: subject.type == .league)).crichere(.sans(12, .medium), CrichereColor.onBackground)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(CrichereColor.surfaceVariant, in: Capsule())
        }
        Spacer().frame(height: 16)
        HStack {
            Text(state.detailsRequired ? "Tell us what's wrong" : "Add details").crichere(.sans(14, .medium), CrichereColor.onBackground)
            Spacer()
            Text(state.detailsRequired ? "Required" : "Optional").crichere(.sans(13), CrichereColor.onSurfaceVariant)
        }
        Spacer().frame(height: 8)
        ZStack(alignment: .topLeading) {
            if state.details.isEmpty {
                Text(state.detailsRequired ? "Describe the problem in a sentence or two." : "What should our team look at?")
                    .crichere(.sans(15, lineHeight: 22.5), CrichereColor.onSurfaceVariant)
            }
            TextField("", text: Binding(get: { state.details }, set: actions.onDetailsChanged), axis: .vertical)
                .crichere(.sans(15, lineHeight: 22.5), CrichereColor.onBackground)
                .tint(CrichereColor.primary)
                .lineLimit(1...5)
                .focused($detailsFocused)
                .disabled(state.isSubmitting)
                .accessibilityLabel("Report details")
        }
        .padding(12)
        .frame(maxWidth: .infinity, minHeight: 132, maxHeight: 132, alignment: .topLeading)
        .contentShape(Rectangle())
        .onTapGesture { if !state.isSubmitting { detailsFocused = true } }
        .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).strokeBorder(CrichereColor.outline, lineWidth: 1))
        Spacer().frame(height: 8)
        HStack {
            Text(atLimit ? "That's the limit. Extra text isn't added." : state.detailsRequired ? "Needed so the team knows what to check." : "Helps the team find it faster.")
                .crichere(.sans(12), CrichereColor.onSurfaceVariant)
            Spacer()
            Text("\(state.details.count) / 500").crichere(.sans(12), CrichereColor.onSurfaceVariant)
        }
        if subject.type == .user {
            Spacer().frame(height: 14)
            Button { actions.onAlsoBlock(!state.alsoBlock) } label: {
                HStack(spacing: 14) {
                    CrichereCheckbox(checked: state.alsoBlock, size: 24) { _ in }.allowsHitTesting(false)
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Also block this person").crichere(.sans(15), CrichereColor.onBackground)
                        Text("Hides their photo and uploads from you. They aren't told.").crichere(.sans(12), CrichereColor.onSurfaceVariant)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.vertical, 6)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .allowsHitTesting(!state.isSubmitting)  // Compose's clickable(enabled = false) doesn't dim the row
        }
        if state.failed {
            Spacer().frame(height: 10)
            Text("Your report didn't send. Your text is still here.").crichere(.sans(13), CrichereColor.error)
        }
        Spacer().frame(height: 14)
        CricherePrimaryButton(state.failed ? "Retry" : "Send report", enabled: state.canSend, loading: state.isSubmitting, loadingText: "Sending…",
                              action: actions.onSend)
    }
}

private struct ReportOutcome: View {
    let icon: String
    let title: String
    let message: String
    let onDone: () -> Void
    var written: String? = nil

    var body: some View {
        VStack(spacing: 0) {
            CrIcon(icon, size: 28, tint: CrichereColor.primary)
                .frame(width: 56, height: 56)
                .background(CrichereColor.primaryContainer, in: Circle())
            Spacer().frame(height: 14)
            Text(title).crichere(.sans(17, .semiBold, lineHeight: 22), CrichereColor.onBackground).composeWrap()
            Spacer().frame(height: 8)
            Text(message).crichere(.sans(14, lineHeight: 21), CrichereColor.onSurfaceVariant).composeWrap()
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 8)
        if let written {
            Spacer().frame(height: 14)
            Text("WHAT YOU WROTE").crichere(.sans(11, .medium), CrichereColor.onSurfaceVariant)
            Spacer().frame(height: 6)
            Text(written).crichere(.sans(14, lineHeight: 21), CrichereColor.onBackground)
        }
        Spacer().frame(height: 18)
        CricherePrimaryButton("Done", action: onDone)
    }
}

// MARK: - block (boards R7, R9)

/// Board R7a's confirm button.
private let blockRed = Color(hex: 0xD64545)

extension View {
    /// Boards R7a to R7e. [coOrganizerIds] adds the R7e note for someone who co-organizes this league.
    func blockConfirm(_ wrapper: BlockViewModelWrapper, coOrganizerIds: Set<String> = []) -> some View {
        crichereDialog(isPresented: Binding(get: { wrapper.state.confirming != nil }, set: { if !$0 { wrapper.cancel() } }),
                       dismissable: !wrapper.state.isSubmitting) {
            if let target = wrapper.state.confirming {
                BlockDialog(state: wrapper.state, target: target, coOrganizer: coOrganizerIds.contains(target.userId),
                            onCancel: wrapper.cancel, onConfirm: wrapper.confirm)
            }
        }
    }

    /// Boards R7d and R11g: "Name blocked" / "Name unblocked" with Undo, for 6 seconds.
    func blockToast(_ wrapper: BlockViewModelWrapper) -> some View {
        overlay(alignment: .bottom) {
            if let target = wrapper.state.justBlocked {
                UndoBar(text: "\(target.name) blocked", onUndo: wrapper.undoBlock, onExpire: wrapper.dismissSnackbar)
            } else if let target = wrapper.state.justUnblocked {
                UndoBar(text: "\(target.name) unblocked", onUndo: wrapper.undoUnblock, onExpire: wrapper.dismissSnackbar)
            }
        }
    }
}

/// The dark snackbar with Undo; it goes after 6 seconds.
struct UndoBar: View {
    let text: String
    let onUndo: () -> Void
    let onExpire: () -> Void

    var body: some View {
        CrichereSnackbar(message: text, actionLabel: "Undo", onAction: onUndo)
            .padding(16)
            .transition(.opacity)
            .task(id: text) {
                try? await Task.sleep(nanoseconds: 6_000_000_000)
                onExpire()
            }
    }
}

/// Material AlertDialog on white: title 21/26 Bold, the four lines, the R7e note, the reassurance, Cancel and a
/// filled red Block (40 pt, 8 pt corners).
struct BlockDialog: View {
    let state: BlockState
    let target: BlockTarget
    var coOrganizer: Bool = false
    let onCancel: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Block \(target.name)?").crichere(.sans(21, .bold, lineHeight: 26), CrichereColor.onBackground)
            Spacer().frame(height: 16)
            VStack(alignment: .leading, spacing: 14) {
                    ForEach(["Their photo and uploads are hidden from you.",
                             "Leagues they organize leave your dashboard, unless you've joined them.",
                             "Their name still appears in leagues you share, so results stay correct.",
                             "They aren't told."], id: \.self) { line in
                        Text(line).crichere(.sans(14, lineHeight: 21), CrichereColor.onBackground)
                    }
                    if coOrganizer {
                        Text("\(firstName(target.name)) co-organizes this league. It stays on your dashboard because only leagues they run as main organizer are removed.")
                            .crichere(.sans(13, lineHeight: 19), CrichereColor.onBackground)
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(CrichereColor.surfaceVariant, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    Text("You can both still join the same leagues. Unblock any time from My Profile.").crichere(.sans(12, lineHeight: 18), CrichereColor.onSurfaceVariant)
                    if state.failed {
                        Text("Couldn't block \(firstName(target.name)). Check your connection and try again. Nothing has changed yet.")
                            .crichere(.sans(13, lineHeight: 19), CrichereColor.error)
                    }
            }
            .fixedSize(horizontal: false, vertical: true)
            Spacer().frame(height: 24)
            HStack(spacing: 8) {
                Spacer(minLength: 0)
                DialogTextButton(label: "Cancel", enabled: !state.isSubmitting, action: onCancel)
                Button(action: onConfirm) {
                    Text(state.isSubmitting ? "Blocking…" : state.failed ? "Try again" : "Block").crichere(.sans(14, .medium))
                        .padding(.horizontal, 20).frame(height: 40)
                }
                .buttonStyle(PillButtonStyle(fill: blockRed, pressedFill: blockRed.opacity(0.85), content: .white, radius: 8))
                .disabled(state.isSubmitting)
                .opacity(state.isSubmitting ? 0.6 : 1)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(CrichereColor.surface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .frame(width: platformDialogWidth)
    }
}

// MARK: - blocked people (boards R11)

/// Boards R11a to R11g. Avatars are always neutral: this screen must not show the photo the user asked to stop seeing.
struct BlockedPeopleView: View {
    var onBack: () -> Void = {}
    @StateObject private var wrapper = BlockedPeopleViewModelWrapper()

    var body: some View {
        BlockedPeopleContent(state: wrapper.state, onBack: onBack, onRetry: wrapper.load, onUnblock: wrapper.unblock)
            .crichereScreen()
            .overlay(alignment: .bottom) {
                if let person = wrapper.state.justUnblocked {
                    UndoBar(text: "\(person.name ?? "Person") unblocked", onUndo: wrapper.undo, onExpire: wrapper.dismissSnackbar)
                }
            }
            .onAppear { wrapper.load() }
    }
}

/// The screen, stateless for the DEBUG parity gallery.
struct BlockedPeopleContent: View {
    let state: BlockedPeopleState
    var onBack: () -> Void = {}
    var onRetry: () -> Void = {}
    var onUnblock: (String) -> Void = { _ in }

    var body: some View {
        VStack(spacing: 0) {
            BackTitleBar(title: "Blocked people", onBack: onBack)
            if state.isLoading {
                Spacer()
            } else if state.loadFailed {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Couldn't load your list").crichere(.sans(18, .semiBold), CrichereColor.onBackground)
                    Spacer().frame(height: 8)
                    Text("Check your connection and try again. Everyone you blocked is still blocked.").crichere(.sans(14, lineHeight: 21), CrichereColor.onSurfaceVariant)
                    Spacer().frame(height: 16)
                    CricherePrimaryButton("Retry", action: onRetry)
                }
                .padding(24)
                .frame(maxHeight: .infinity)
            } else if state.rows.isEmpty {
                VStack(spacing: 0) {
                    CrIcon("ic_block", size: 32, tint: CrichereColor.onSurfaceVariant)
                    Spacer().frame(height: 12)
                    Text("No one is blocked").crichere(.sans(18, .semiBold), CrichereColor.onBackground)
                    Spacer().frame(height: 8)
                    Text("To block someone, open ⋮ on their row in any league roster.").crichere(.sans(14, lineHeight: 21), CrichereColor.onSurfaceVariant)
                        .composeWrap()
                }
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Their photo and uploads are hidden from you. They aren't told, and their names still appear in leagues you share.")
                            .crichere(.sans(13, lineHeight: 19), CrichereColor.onSurfaceVariant)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                        ForEach(state.rows, id: \.person.userId) { row in
                            BlockedPersonRow(row: row) { onUnblock(row.person.userId) }
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(CrichereColor.background.ignoresSafeArea())
    }
}

private struct BlockedPersonRow: View {
    let row: BlockedRow
    let onUnblock: () -> Void

    var body: some View {
        let name = row.person.name ?? "Person"
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                DeletedUserAvatar(size: 40, iconSize: 22).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 0) {
                    Text(name).crichere(.sans(15, .medium), CrichereColor.onBackground)
                    Text("Blocked \(blockedOn(row.person.blockedAt))").crichere(.sans(12), CrichereColor.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                // Material OutlinedButton: 40 pt, 24 pt side padding.
                Button(action: onUnblock) {
                    Text(row.isUnblocking ? "Unblocking…" : row.failed ? "Retry" : "Unblock").crichere(.sans(13, .medium))
                        .padding(.horizontal, 24).frame(height: 40)
                }
                .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.ink.opacity(0.06), content: CrichereColor.onBackground,
                                             border: CrichereColor.outline, radius: 20))
                .disabled(row.isUnblocking)
                .opacity(row.isUnblocking ? 0.38 : 1)
            }
            if row.failed {
                Text("Couldn't unblock. \(firstName(name)) is still blocked.").crichere(.sans(12), CrichereColor.error)
                    .padding(.leading, 52).padding(.top, 4)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }
}

/// "12 Oct" in the phone's zone.
private func blockedOn(_ iso: String) -> String {
    let plain = ISO8601DateFormatter()
    let fractional = ISO8601DateFormatter()
    fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    guard let date = plain.date(from: iso) ?? fractional.date(from: iso) else { return "" }
    let out = DateFormatter()
    out.locale = Locale(identifier: "en_US_POSIX")
    out.dateFormat = "d MMM"
    return out.string(from: date)
}

// MARK: - suspended and notices (boards R12, R13)

/// Boards R13a to R13c: one way out. Neutral, because this is a state, not something the user got wrong.
struct SuspendedView: View {
    let maskedPhone: String?
    let onBackToSignIn: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 14) {
                CrIcon("ic_lock", size: 28, tint: CrichereColor.onSurfaceVariant)
                    .frame(width: 56, height: 56)
                    .background(CrichereColor.surfaceVariant, in: Circle())
                Text("Your account is suspended").crichere(.sans(26, .bold, lineHeight: 31), CrichereColor.onBackground)
                Text(verbatim: "Contact hello@crichere.com.").crichere(.sans(16, lineHeight: 25), CrichereColor.onBackground)
                if let maskedPhone { Text(maskedPhone).crichere(.sans(13), CrichereColor.onSurfaceVariant) }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            Button(action: onBackToSignIn) {
                Text("Back to sign in").crichere(.sans(15, .medium)).frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
            }
            .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.ink.opacity(0.06), content: CrichereColor.onBackground,
                                         border: CrichereColor.outline, radius: 24))
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(CrichereColor.background.ignoresSafeArea())
    }
}

/// Boards R12b to R12e, one dialog at a time: Material AlertDialog on white, OK only, no scrim dismiss.
struct ModerationNoticeDialog: View {
    let notice: ModerationNoticeDto
    let onDismiss: () -> Void

    var body: some View {
        let subject = notice.subjectName ?? ""
        let (title, body, next): (String, String, String?) = {
            switch notice.kind {
            case .photo: return ("Your photo was removed", "Your photo", "You can upload a new photo from Edit profile.")
            case .leagueLogo: return ("Your league logo was removed", "Your logo for \(subject)", "You can upload a new logo from Edit league.")
            case .leagueBanner: return ("Your league banner was removed", "Your banner for \(subject)", "You can upload a new banner from Edit league.")
            case .franchiseLogo: return ("Your franchise logo was removed", "Your logo for \(subject)", nil)
            }
        }()
        CrichereDialog(title: title, titleStyle: .sans(19, .semiBold, lineHeight: 24), titleColor: CrichereColor.onBackground,
                       surface: CrichereColor.surface, buttons: [DialogButton(label: "OK", action: onDismiss)]) {
            VStack(alignment: .leading, spacing: 10) {
                // verbatim: a string literal would be Markdown and turn the address into a link.
                Text(verbatim: "\(body) was removed because it broke our rules. Contact hello@crichere.com if you think this is a mistake.")
                    .crichere(.sans(14, lineHeight: 21), CrichereColor.onBackground)
                if let next { Text(next).crichere(.sans(13, lineHeight: 19), CrichereColor.onSurfaceVariant) }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
        .frame(width: platformDialogWidth)
    }
}

/// Queues the removed-image notices on the signed-in shell, one at a time.
struct ModerationNoticeHost: ViewModifier {
    @StateObject private var wrapper = ModerationNoticesWrapper()

    func body(content: Content) -> some View {
        content.crichereDialog(isPresented: Binding(get: { !wrapper.notices.isEmpty }, set: { _ in }), dismissable: false) {
            if let notice = wrapper.notices.first {
                ModerationNoticeDialog(notice: notice, onDismiss: wrapper.dismissFirst)
            }
        }
    }
}

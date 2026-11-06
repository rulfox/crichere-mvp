import SafariServices
import SwiftUI
import Shared

/// iOS twin of `androidApp/.../ui/ModerationUi.kt` (docs/PHASE18.md, boards P11 and P12).
/// **Authored but unverified** (no Mac in this setup): SKIE enum case names and flow types below
/// follow the patterns of the other wrappers, and have never been compiled.

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

struct ReportSheetView: View {
    @ObservedObject var wrapper: ReportViewModelWrapper

    private var isLeague: Bool { wrapper.state.subject?.type == .league }
    private var firstName: String {
        (wrapper.state.subject?.name ?? "").split(separator: " ").first.map(String.init) ?? ""
    }

    var body: some View {
        NavigationStack {
            Group {
                switch wrapper.state.step {
                case .reason: reasonStep
                case .details: detailsStep
                case .sent:
                    outcome(
                        "Thanks. Our team will review this.",
                        isLeague
                            ? "The organizer won't be told who reported this league. If something breaks our rules, we'll remove it."
                            : "\(firstName) won't be told who reported them. If something breaks our rules, we'll remove it."
                    )
                case .alreadyReported:
                    outcome(
                        "You've already reported this. Our team will review it.",
                        "Your earlier report is still open, so this one wasn't sent again."
                    )
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { wrapper.close() } }
            }
        }
    }

    private var reasonStep: some View {
        Form {
            Section {
                Text(wrapper.state.subject?.name ?? "").font(.title3.bold())
                Text(wrapper.state.subject?.context ?? "").foregroundColor(.secondary)
            } header: {
                Text(isLeague ? "Report a league" : "Report a person")
            }
            Section("What's the problem?") {
                ForEach(wrapper.state.reasons, id: \.self) { reason in
                    Button {
                        wrapper.selectReason(reason)
                    } label: {
                        HStack {
                            Text(reasonLabel(reason, league: isLeague)).foregroundColor(Brand.ink)
                            Spacer()
                            if wrapper.state.reason == reason { Image(systemName: "checkmark").foregroundColor(Brand.primary) }
                        }
                    }
                }
            }
            Section {
                Button("Next") { wrapper.next() }.disabled(wrapper.state.reason == nil)
            } footer: {
                Text(isLeague
                    ? "The organizer won't be told who reported this league, and doesn't see reports. The Crichere team reviews each one."
                    : "\(firstName) won't be told who reported them. The organizer doesn't see reports. The Crichere team reviews each one.")
            }
        }
    }

    private var detailsStep: some View {
        Form {
            Section(wrapper.state.detailsRequired ? "Tell us what's wrong (required)" : "Add details (optional)") {
                TextEditor(text: Binding(
                    get: { wrapper.state.details },
                    set: { wrapper.onDetailsChanged($0) }
                ))
                .frame(minHeight: 120)
                .accessibilityLabel("Report details")
                Text("\(wrapper.state.details.count) / 500").font(.caption).foregroundColor(.secondary)
            }
            if !isLeague {
                Section {
                    Toggle(isOn: Binding(get: { wrapper.state.alsoBlock }, set: { wrapper.setAlsoBlock($0) })) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Also block this person")
                            Text("Hides their photo and uploads from you. They aren't told.").font(.caption).foregroundColor(.secondary)
                        }
                    }
                }
            }
            if wrapper.state.failed {
                Section { Text("Your report didn't send. Your text is still here.").foregroundColor(Brand.error) }
            }
            Section {
                Button(wrapper.state.isSubmitting ? "Sending…" : wrapper.state.failed ? "Retry" : "Send report") { wrapper.send() }
                    .disabled(!wrapper.state.canSend)
            }
        }
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button { wrapper.back() } label: { Image(systemName: "chevron.left") }.accessibilityLabel("Back")
            }
        }
    }

    private func outcome(_ title: String, _ line: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill").font(.system(size: 40)).foregroundColor(Brand.primary)
            Text(title).font(.headline).multilineTextAlignment(.center)
            Text(line).font(.subheadline).foregroundColor(.secondary).multilineTextAlignment(.center)
            Button("Done") { wrapper.close() }.buttonStyle(.borderedProminent).padding(.top, 8)
        }
        .padding(24)
    }
}

// MARK: - block (boards R2, R3, R9)

/// The block confirm, with the same lines as Android's `BlockDialog`.
extension View {
    func blockConfirm(_ wrapper: BlockViewModelWrapper) -> some View {
        alert(
            "Block \(wrapper.state.confirming?.name ?? "this person")?",
            isPresented: Binding(get: { wrapper.state.confirming != nil }, set: { if !$0 { wrapper.cancel() } })
        ) {
            Button("Cancel", role: .cancel) { wrapper.cancel() }
            Button(wrapper.state.failed ? "Try again" : "Block", role: .destructive) { wrapper.confirm() }
        } message: {
            Text("Their photo and uploads are hidden from you. Leagues they organize leave your dashboard, unless you've joined them. Their name still appears in leagues you share, so results stay correct. They aren't told.\n\nYou can both still join the same leagues. Unblock any time from My Profile.")
        }
    }

    /// Undo for a block or unblock, the toast from board R3.
    func blockToast(_ wrapper: BlockViewModelWrapper) -> some View {
        overlay(alignment: .bottom) {
            if let target = wrapper.state.justBlocked {
                undoToast("\(target.name) blocked", onUndo: wrapper.undoBlock, onExpire: wrapper.dismissSnackbar)
            } else if let target = wrapper.state.justUnblocked {
                undoToast("\(target.name) unblocked", onUndo: wrapper.undoUnblock, onExpire: wrapper.dismissSnackbar)
            }
        }
    }
}

func undoToast(_ text: String, onUndo: @escaping () -> Void, onExpire: @escaping () -> Void) -> some View {
    HStack {
        Text(text).font(.system(size: 14, weight: .medium)).foregroundColor(.white).lineLimit(1)
        Spacer()
        Button("Undo", action: onUndo).font(.system(size: 14, weight: .semibold)).foregroundColor(.white)
    }
    .padding(.horizontal, 16).padding(.vertical, 14)
    .background(RoundedRectangle(cornerRadius: 12).fill(Brand.ink))
    .padding(.horizontal, 16).padding(.bottom, 16)
    .task {
        try? await Task.sleep(nanoseconds: 6_000_000_000)
        onExpire()
    }
}

/// Board R9a: someone who isn't in the league opens one run by a person they blocked.
struct BlockedOrganizerNoticeView: View {
    let organizerName: String
    let onUnblock: () -> Void
    let onHome: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text("You blocked this league's organizer").font(.title3.bold()).multilineTextAlignment(.center)
            Text("\(organizerName == "this organizer" ? "This person" : organizerName) organizes this league. While they're blocked, its details stay hidden from you. They haven't been told.")
                .foregroundColor(.secondary).multilineTextAlignment(.center)
            Button("Unblock this organizer", action: onUnblock).buttonStyle(.bordered).padding(.top, 8)
            Button("Go to Home", action: onHome)
        }
        .padding(24)
    }
}

// MARK: - blocked people (boards R11)

struct BlockedPeopleView: View {
    @StateObject private var wrapper = BlockedPeopleViewModelWrapper()

    var body: some View {
        Group {
            if wrapper.state.isLoading {
                ProgressView()
            } else if wrapper.state.loadFailed {
                VStack(spacing: 10) {
                    Text("Couldn't load blocked people").font(.headline)
                    Button("Retry") { wrapper.load() }
                }
            } else if wrapper.state.rows.isEmpty {
                Text("You haven't blocked anyone.").foregroundColor(.secondary)
            } else {
                List {
                    Section {
                        ForEach(wrapper.state.rows, id: \.person.userId) { row in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(row.person.name ?? "Player")
                                    Text("Blocked \(eraseDateLabel(row.person.blockedAt))").font(.caption).foregroundColor(.secondary)
                                    if row.failed { Text("Couldn't unblock. Try again.").font(.caption).foregroundColor(Brand.error) }
                                }
                                Spacer()
                                Button(row.isUnblocking ? "Unblocking…" : "Unblock") { wrapper.unblock(row.person.userId) }
                                    .disabled(row.isUnblocking)
                            }
                        }
                    } footer: {
                        Text("Their photo and uploads are hidden from you. They aren't told, and their names still appear in leagues you share.")
                    }
                }
            }
        }
        .navigationTitle("Blocked people")
        .overlay(alignment: .bottom) {
            if let person = wrapper.state.justUnblocked {
                undoToast("\(person.name ?? "Player") unblocked", onUndo: wrapper.undo, onExpire: wrapper.dismissSnackbar)
            }
        }
    }
}

// MARK: - suspended and notices (boards R12, R13)

struct SuspendedView: View {
    let maskedPhone: String?
    let onBackToSignIn: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Spacer()
            Image(systemName: "lock").font(.system(size: 28)).foregroundColor(.secondary)
            Text("Your account is suspended").font(BrandFont.archivo(26, .bold))
            Text("Contact hello@crichere.com.")
            if let maskedPhone { Text(maskedPhone).font(.footnote).foregroundColor(.secondary) }
            Spacer()
            Button("Back to sign in", action: onBackToSignIn).buttonStyle(.bordered).frame(maxWidth: .infinity)
        }
        .padding(24)
    }
}

/// Queues the removed-image notices on the signed-in shell, one at a time.
struct ModerationNoticeHost: ViewModifier {
    @StateObject private var wrapper = ModerationNoticesWrapper()

    func body(content: Content) -> some View {
        let notice = wrapper.notices.first
        content.alert(
            notice.map(noticeTitle) ?? "",
            isPresented: Binding(get: { notice != nil }, set: { _ in })
        ) {
            Button("OK") { wrapper.dismissFirst() }
        } message: {
            Text(notice.map(noticeBody) ?? "")
        }
    }

    private func noticeTitle(_ notice: ModerationNoticeDto) -> String {
        switch notice.kind {
        case .photo: return "Your photo was removed"
        case .leagueLogo: return "Your league logo was removed"
        case .leagueBanner: return "Your league banner was removed"
        case .franchiseLogo: return "Your franchise logo was removed"
        }
    }

    private func noticeBody(_ notice: ModerationNoticeDto) -> String {
        let subject = notice.subjectName ?? ""
        let rule = "was removed because it broke our rules. Contact hello@crichere.com if you think this is a mistake."
        switch notice.kind {
        case .photo: return "Your photo \(rule)\n\nYou can upload a new photo from Edit profile."
        case .leagueLogo: return "Your logo for \(subject) \(rule)\n\nYou can upload a new logo from Edit league."
        case .leagueBanner: return "Your banner for \(subject) \(rule)\n\nYou can upload a new banner from Edit league."
        case .franchiseLogo: return "Your logo for \(subject) \(rule)"
        }
    }
}

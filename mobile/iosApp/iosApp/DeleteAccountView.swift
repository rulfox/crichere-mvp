import SwiftUI
import Shared

/// Mirrors `DeleteAccountViewModel` (docs/PHASE16.md) -- same wrapper pattern as
/// `OwnProfileViewModelWrapper`. **Authored but unverified** (no Mac in this setup).
@MainActor
final class DeleteAccountViewModelWrapper: ObservableObject {
    @Published var state: DeleteAccountState
    @Published var signedOut = false

    private let viewModel: DeleteAccountViewModel

    init() {
        let viewModel = KoinHelper().deleteAccountViewModel
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
        Task { [weak self] in
            for await _ in viewModel.events {
                self?.signedOut = true
            }
        }
    }

    func check() { viewModel.check() }
    func openConfirm() { viewModel.openConfirm() }
    func dismissConfirm() { viewModel.dismissConfirm() }
    func onInputChange(_ value: String) { viewModel.onInputChange(value: value) }
    func confirm() { viewModel.confirm() }
}

/// The erase date as the design's "25 Oct 2026", in the phone's time zone.
func eraseDateLabel(_ iso: String) -> String {
    let parser = ISO8601DateFormatter()
    parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let date = parser.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
    guard let date else { return iso }
    let out = DateFormatter()
    out.locale = Locale(identifier: "en_GB")
    out.dateFormat = "d MMM yyyy"
    return out.string(from: date)
}

private let errorStrong = Color(hex: 0x8C1D12)
private let onWarningInk = Color(hex: 0x6E4700)
private let greenContainerFill = Color(hex: 0xD7EBD2)
private let onGreenContainerInk = Color(hex: 0x0B2E10)

/// iOS twin of `DeleteAccountScreen.kt` (design D2–D6). Pushed from My Profile; the back chevron
/// comes from the NavigationStack.
struct DeleteAccountView: View {
    let onOpenLeague: (String) -> Void
    let onSignedOut: () -> Void

    @StateObject private var wrapper = DeleteAccountViewModelWrapper()

    var body: some View {
        VStack(spacing: 0) {
            switch onEnum(of: wrapper.state.screen) {
            case .error:
                errorState
            case .loading:
                scroll { loading }
                bottomBar { deleteButton(enabled: false) }
            case .canDelete(let screen):
                scroll { canDelete(screen) }
                bottomBar { deleteButton(enabled: true) }
            case .blocked(let screen):
                scroll { blocked(screen.blockers) }
                bottomBar {
                    Button { wrapper.check() } label: {
                        Label("Check again", systemImage: "arrow.clockwise").frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.bordered)
                    .clipShape(Capsule())
                }
            }
        }
        .background(Brand.background)
        .navigationTitle("Delete account")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { wrapper.check() }
        .onChange(of: wrapper.signedOut) { _, out in if out { onSignedOut() } }
        .sheet(isPresented: Binding(
            get: { wrapper.state.dialog != nil },
            set: { if !$0 { wrapper.dismissConfirm() } }
        )) {
            if let dialog = wrapper.state.dialog, case .canDelete(let screen) = onEnum(of: wrapper.state.screen) {
                DeleteAccountConfirmSheet(
                    state: dialog,
                    erasesOn: eraseDateLabel(screen.erasesOn),
                    onInputChange: wrapper.onInputChange,
                    onCancel: wrapper.dismissConfirm,
                    onConfirm: wrapper.confirm
                )
                .presentationDetents([.medium])
                .interactiveDismissDisabled(dialog.isSubmitting)
            }
        }
    }

    private func scroll<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) { content() }
                .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 24)
        }
    }

    private func bottomBar<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(spacing: 0) {
            Divider()
            content().padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 12)
        }
        .background(Brand.background)
    }

    private func deleteButton(enabled: Bool) -> some View {
        Button { wrapper.openConfirm() } label: {
            Text("Delete account").font(.system(size: 15, weight: .semibold)).frame(maxWidth: .infinity, minHeight: 52)
        }
        .foregroundColor(enabled ? .white : Color(hex: 0x8A948C))
        .background(Capsule().fill(enabled ? errorStrong : Color(hex: 0xDCE0D7)))
        .disabled(!enabled)
    }

    private func heading(_ text: String) -> some View {
        Text(text).font(BrandFont.archivo(24, .heavy)).foregroundColor(Brand.ink)
    }

    // D2
    @ViewBuilder private var loading: some View {
        heading("Delete your account")
        HStack(spacing: 12) {
            ProgressView()
            Text("Checking your leagues…").font(.system(size: 14, weight: .medium)).foregroundColor(Color(hex: 0x3E4A41))
        }
    }

    // D3a / D3b
    @ViewBuilder private func canDelete(_ screen: DeleteAccountScreenStateCanDelete) -> some View {
        heading("Delete your account")
        Text("You'll be signed out on all your devices straight away.").font(.system(size: 14)).foregroundColor(Color(hex: 0x3E4A41))
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "clock.arrow.circlepath").foregroundColor(Brand.primary)
            VStack(alignment: .leading, spacing: 4) {
                Text("14 days to change your mind").font(.system(size: 14.5, weight: .bold)).foregroundColor(onGreenContainerInk)
                Text("Nothing is erased until \(eraseDateLabel(screen.erasesOn)). Sign in again before then and the deletion is cancelled.")
                    .font(.system(size: 13)).foregroundColor(Color(hex: 0x2E4A31))
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(greenContainerFill))

        section("Erased after 14 days") {
            card {
                ForEach([("phone", "Your phone number"), ("person.crop.circle", "Your profile and photo"),
                         ("doc.text", "Payment screenshots you uploaded"), ("bookmark", "Leagues you follow")], id: \.1) { icon, label in
                    HStack(spacing: 12) {
                        Image(systemName: icon).foregroundColor(Brand.inkMuted).frame(width: 20)
                        Text(label).font(.system(size: 14)).foregroundColor(Brand.ink)
                        Spacer()
                    }
                    .padding(.horizontal, 14).padding(.vertical, 12)
                }
            }
        }
        section("Kept") {
            card {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Auction results and rosters of leagues that already ran. Your name shows as:")
                        .font(.system(size: 14)).foregroundColor(Brand.ink)
                    HStack(spacing: 10) {
                        DeletedUserAvatar(size: 32)
                        Text("Deleted user").font(.system(size: 13.5, weight: .semibold)).foregroundColor(Brand.inkMuted)
                    }
                }
                .padding(.horizontal, 14).padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        if !screen.handovers.isEmpty {
            section("Leagues you organize") {
                Text("When your account is erased, the earliest co-organizer becomes the organizer.")
                    .font(.system(size: 13)).foregroundColor(Brand.inkMuted)
                card {
                    ForEach(screen.handovers, id: \.leagueId) { handover in
                        HStack(spacing: 12) {
                            FranchiseBadge(name: handover.leagueName, size: 36, radius: 10, fontSize: 11)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(handover.leagueName).font(.system(size: 14, weight: .semibold)).foregroundColor(Brand.ink).lineLimit(1)
                                Label("Passes to \(handover.newOrganizerName ?? "a co-organizer")", systemImage: "arrow.right")
                                    .font(.system(size: 12.5)).foregroundColor(Brand.inkMuted)
                            }
                            Spacer()
                        }
                        .padding(.horizontal, 14).padding(.vertical, 12)
                    }
                }
            }
        }
    }

    // D4
    @ViewBuilder private func blocked(_ blockers: [DeletionBlockerDto]) -> some View {
        heading("You can't delete your account yet")
        Text("These leagues need you first. Sort them out, then come back and try again.")
            .font(.system(size: 14)).foregroundColor(Color(hex: 0x3E4A41))
        card {
            ForEach(blockers, id: \.leagueId) { blocker in
                Button { onOpenLeague(blocker.leagueId) } label: {
                    HStack(spacing: 12) {
                        FranchiseBadge(name: blocker.leagueName, size: 40, radius: 10, fontSize: 12)
                        VStack(alignment: .leading, spacing: 5) {
                            Text(blocker.leagueName).font(.system(size: 14.5, weight: .semibold)).foregroundColor(Brand.ink).lineLimit(1)
                            if blocker.reason == .auctionLive {
                                Label("Auction is live. Wait for it to end.", systemImage: "hammer").foregroundColor(onWarningInk)
                            } else {
                                Label("Add a co-organizer or mark the league completed.", systemImage: "person.badge.plus").foregroundColor(Brand.primary)
                            }
                        }
                        .font(.system(size: 13))
                        Spacer()
                        Image(systemName: "chevron.right").foregroundColor(Color(hex: 0x9AA39C))
                    }
                    .padding(14)
                }
            }
        }
        Label("Nothing has changed on your account.", systemImage: "info.circle")
            .font(.system(size: 12.5)).foregroundColor(Brand.inkMuted)
    }

    // D5
    private var errorState: some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "icloud.slash").font(.system(size: 40)).foregroundColor(Brand.inkMuted)
            Text("Couldn't check your account").font(BrandFont.archivo(18, .bold)).foregroundColor(Brand.ink)
            Text("Check your connection and try again. Nothing has been deleted.")
                .font(.system(size: 14)).foregroundColor(Color(hex: 0x3E4A41)).multilineTextAlignment(.center)
            Button { wrapper.check() } label: { Label("Retry", systemImage: "arrow.clockwise").frame(width: 160, height: 48) }
                .buttonStyle(.bordered).clipShape(Capsule())
            Spacer()
        }
        .padding(.horizontal, 36).padding(.bottom, 80)
        .frame(maxWidth: .infinity)
    }

    private func section<Content: View>(_ title: String, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(BrandFont.archivo(14.5, .bold)).foregroundColor(Brand.ink)
            content()
        }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(spacing: 0) { content() }
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Brand.outlineVariant, lineWidth: 1))
    }
}

/// D6a–e as a sheet: the TextField takes capitals with no autocorrect, Confirm is enabled only for
/// exactly `DELETE` (the shared `canConfirm`), and the sheet can't be swiped away while submitting.
struct DeleteAccountConfirmSheet: View {
    let state: DeleteAccountDialogState
    let erasesOn: String
    let onInputChange: (String) -> Void
    let onCancel: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: "trash.slash").font(.system(size: 24)).foregroundColor(errorStrong)
            Text("Delete your account?").font(.system(size: 19, weight: .semibold)).foregroundColor(Brand.ink)
            Text("You'll be signed out on all your devices. Your account is erased on \(erasesOn) unless you sign in before then.")
                .font(.system(size: 13.5)).foregroundColor(Color(hex: 0x3E4A41))
            VStack(alignment: .leading, spacing: 6) {
                Text("Type DELETE to confirm").font(.system(size: 12.5, weight: .semibold)).foregroundColor(Color(hex: 0x3E4A41))
                TextField("DELETE", text: Binding(get: { state.input }, set: onInputChange))
                    .font(BrandFont.mono(16, .medium))
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .disabled(state.isSubmitting)
                    .padding(.horizontal, 14).frame(height: 56)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(state.showMismatch ? Brand.error : Brand.primary, lineWidth: 1.6))
                if state.showMismatch && !state.isSubmitting {
                    Text("Type it in capital letters: DELETE").font(.system(size: 12.5)).foregroundColor(Brand.error)
                }
            }
            if state.failed {
                Label("Couldn't delete your account. Check your connection and try again.", systemImage: "exclamationmark.circle.fill")
                    .font(.system(size: 13)).foregroundColor(Brand.error)
            }
            HStack(spacing: 22) {
                Spacer()
                Button("Cancel", action: onCancel).foregroundColor(Brand.primary).disabled(state.isSubmitting).opacity(state.isSubmitting ? 0.38 : 1)
                Button(action: onConfirm) {
                    HStack(spacing: 8) {
                        if state.isSubmitting { ProgressView().tint(errorStrong) }
                        Text(state.isSubmitting ? "Deleting…" : "Delete account")
                    }
                }
                .foregroundColor(state.canConfirm || state.isSubmitting ? errorStrong : Brand.ink.opacity(0.38))
                .disabled(!state.canConfirm)
            }
            .font(.system(size: 14, weight: .semibold))
        }
        .padding(24)
    }
}

/// Design D10: a deleted user never shows a photo or initials.
struct DeletedUserAvatar: View {
    let size: CGFloat
    var body: some View {
        Image(systemName: "person.fill")
            .font(.system(size: size * 0.5))
            .foregroundColor(Color(hex: 0x8A948C))
            .frame(width: size, height: size)
            .background(Circle().fill(Color(hex: 0xE3E8DD)))
            .accessibilityLabel("Deleted user")
    }
}

/// D7 / D9: a tinted notice with a close button.
struct DismissibleNotice: View {
    let systemImage: String
    let text: String
    let container: Color
    let ink: Color
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: systemImage).foregroundColor(ink)
            Text(text).font(.system(size: 13.5, weight: .medium)).foregroundColor(ink).frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onDismiss) { Image(systemName: "xmark").foregroundColor(ink) }.accessibilityLabel("Dismiss")
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 16).fill(container))
    }
}

/// D7 amber and D9 green, shared with the other views.
enum NoticeColors {
    static let warningContainer = Color(hex: 0xFFF3D6)
    static let onWarning = onWarningInk
    static let greenContainer = greenContainerFill
    static let onGreenContainer = onGreenContainerInk
}

/// Observes the shared one-time account notices (D7/D8).
@MainActor
final class AccountNoticesWrapper: ObservableObject {
    @Published var deletionScheduledOn: String?
    @Published var deletionCancelled = false
    private let notices: AccountNotices

    init() {
        let notices = KoinHelper().accountNotices
        self.notices = notices
        Task { [weak self] in
            for await value in notices.deletionScheduledOn { self?.deletionScheduledOn = value }
        }
        Task { [weak self] in
            for await value in notices.deletionCancelled { self?.deletionCancelled = value.boolValue }
        }
    }

    func dismissScheduled() { notices.dismissDeletionScheduled() }
    func dismissCancelled() { notices.dismissDeletionCancelled() }
}

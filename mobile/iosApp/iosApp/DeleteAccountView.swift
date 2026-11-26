import SwiftUI
import Shared

/// Mirrors `DeleteAccountViewModel` (docs/PHASE16.md) -- same wrapper pattern as
/// `OwnProfileViewModelWrapper`.
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

private let bodyInk = Color(hex: 0x3E4A41)
private let graceBody = Color(hex: 0x2E4A31)
private let skeleton = Color(hex: 0xE6E9E1)

/// Delete account (design D2-D6, androidApp `ui/DeleteAccountScreen.kt`). The server check runs on every
/// entry; the screen then shows exactly one of can delete (D3), blocked (D4) or error (D5). [onSignedOut]
/// fires after a successful request, once the local session is gone (D7). [onOpenLeague] opens a blocker.
struct DeleteAccountView: View {
    var onBack: () -> Void = {}
    let onOpenLeague: (String) -> Void
    let onSignedOut: () -> Void

    @StateObject private var wrapper = DeleteAccountViewModelWrapper()

    var body: some View {
        let state = wrapper.state
        DeleteAccountContent(screen: state.screen, onBack: onBack, onOpenLeague: onOpenLeague, onRetry: wrapper.check, onDelete: wrapper.openConfirm)
            .crichereScreen()
            .onAppear { wrapper.check() }
            .onChange(of: wrapper.signedOut) { _, out in if out { onSignedOut() } }
            .crichereDialog(isPresented: Binding(get: { state.dialog != nil }, set: { if !$0 { wrapper.dismissConfirm() } }),
                            dismissable: !(state.dialog?.isSubmitting ?? false)) {
                if let dialog = wrapper.state.dialog, case let .canDelete(screen) = onEnum(of: wrapper.state.screen) {
                    DeleteAccountConfirmDialog(state: dialog, erasesOn: eraseDateLabel(screen.erasesOn), onInputChange: wrapper.onInputChange,
                                               onDismiss: wrapper.dismissConfirm, onConfirm: wrapper.confirm)
                }
            }
    }
}

/// The screen, stateless for the DEBUG parity gallery.
struct DeleteAccountContent: View {
    let screen: DeleteAccountScreenState
    var onBack: () -> Void = {}
    var onOpenLeague: (String) -> Void = { _ in }
    var onRetry: () -> Void = {}
    var onDelete: () -> Void = {}

    var body: some View {
        VStack(spacing: 0) {
            DeleteTopBar(onBack: onBack)
            switch onEnum(of: screen) {
            case .error:
                ErrorStateView(onRetry: onRetry)
            default:
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) { content }
                        .padding(.horizontal, 20)
                        .padding(.top, 8)
                        .padding(.bottom, 24)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                DeleteBottomBar {
                    if case .blocked = onEnum(of: screen) {
                        CheckAgainButton(label: "Check again", action: onRetry).frame(maxWidth: .infinity)
                    } else {
                        DeleteButton(enabled: { if case .canDelete = onEnum(of: screen) { return true } else { return false } }(), action: onDelete)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(CrichereColor.background.ignoresSafeArea())
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: screen) {
        case .loading: LoadingContent()
        case let .canDelete(state): CanDeleteContent(screen: state)
        case let .blocked(state): BlockedContent(blockers: state.blockers, onOpenLeague: onOpenLeague)
        case .error: EmptyView()
        }
    }
}

/// Back arrow + "Delete account", 56 pt.
private struct DeleteTopBar: View {
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Button(action: onBack) { CrIcon("ic_arrow_back", size: 24, tint: CrichereColor.onBackground).frame(width: 48, height: 48) }
                .buttonStyle(.pressCircle)
                .accessibilityLabel("Back")
            Text("Delete account").crichere(.sans(18, .semiBold, lineHeight: 18), CrichereColor.onBackground)
            Spacer(minLength: 0)
        }
        .padding(.leading, 4)
        .padding(.trailing, 8)
        .frame(height: 56)
    }
}

private func heading(_ text: String) -> some View {
    Text(text).crichere(.archivo(24, .extraBold, lineHeight: 27.6, tracking: -0.48), CrichereColor.onBackground)
}

private func lead(_ text: String) -> some View {
    Text(text).crichere(.sans(14, lineHeight: 21), bodyInk)
}

/// D2.
private struct LoadingContent: View {
    var body: some View {
        heading("Delete your account")
        HStack(spacing: 12) {
            CrichereSpinner(color: CrichereColor.primary, lineWidth: 2, size: 20)
            Text("Checking your leagues…").crichere(.sans(14, .medium, lineHeight: 18.2), bodyInk)
        }
        DeletionCard {
            ForEach(Array([239, 188, 212, 164].enumerated()), id: \.offset) { index, width in
                if index > 0 { RowDivider() }
                HStack(spacing: 12) {
                    skeleton.frame(width: 20, height: 20).clipShape(RoundedRectangle(cornerRadius: 6))
                    skeleton.frame(width: CGFloat(width), height: 12).clipShape(RoundedRectangle(cornerRadius: 6))
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 14)
                .frame(height: 44)
            }
        }
        skeleton.frame(height: 96).clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// D3a / D3b.
private struct CanDeleteContent: View {
    let screen: DeleteAccountScreenStateCanDelete

    var body: some View {
        heading("Delete your account")
        lead("You'll be signed out on all your devices straight away.")
        HStack(alignment: .top, spacing: 12) {
            CrIcon("ic_history", size: 22, tint: CrichereColor.primary)
            VStack(alignment: .leading, spacing: 4) {
                Text("14 days to change your mind").crichere(.sans(14.5, .bold, lineHeight: 17.4), CrichereColor.onPrimaryContainer)
                Text("Nothing is erased until \(eraseDateLabel(screen.erasesOn)). Sign in again before then and the deletion is cancelled.")
                    .crichere(.sans(13, lineHeight: 18.2), graceBody)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(CrichereColor.primaryContainer, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        DeletionSection(title: "Erased after 14 days") {
            DeletionCard {
                ForEach(Array([("ic_call", "Your phone number"), ("ic_account_circle", "Your profile and photo"),
                               ("ic_receipt_long", "Payment screenshots you uploaded"), ("ic_bookmark", "Leagues you follow")].enumerated()),
                        id: \.offset) { index, item in
                    if index > 0 { RowDivider() }
                    HStack(spacing: 12) {
                        CrIcon(item.0, size: 20, tint: CrichereColor.onSurfaceVariant)
                        Text(item.1).crichere(.sans(14, lineHeight: 18.9), CrichereColor.onBackground)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                }
            }
        }
        DeletionSection(title: "Kept") {
            DeletionCard {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Auction results and rosters of leagues that already ran. Your name shows as:")
                        .crichere(.sans(14, lineHeight: 19.6), CrichereColor.onBackground)
                    HStack(spacing: 10) {
                        DeletedUserAvatar(size: 32, iconSize: 18)
                        Text("Deleted user").crichere(.sans(13.5, .semiBold), DeletedAvatar.name)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        if !screen.handovers.isEmpty {
            DeletionSection(title: "Leagues you organize") {
                Text("When your account is erased, the earliest co-organizer becomes the organizer.")
                    .crichere(.sans(13, lineHeight: 18.2), CrichereColor.onSurfaceVariant)
                DeletionCard {
                    ForEach(Array(screen.handovers.enumerated()), id: \.offset) { index, handover in
                        if index > 0 { RowDivider() }
                        HStack(spacing: 12) {
                            LeagueTile(id: handover.leagueId, name: handover.leagueName, logoUrl: handover.leagueLogoUrl, size: 36, fontSize: 11)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(handover.leagueName).crichere(.sans(14, .semiBold, lineHeight: 16.8), CrichereColor.onBackground).lineLimit(1)
                                HStack(spacing: 4) {
                                    CrIcon("ic_arrow_forward", size: 14, tint: CrichereColor.onSurfaceVariant)
                                    Text("Passes to \(handover.newOrganizerName ?? "a co-organizer")")
                                        .crichere(.sans(12.5, lineHeight: 15), CrichereColor.onSurfaceVariant).lineLimit(1)
                                }
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                    }
                }
            }
        }
    }
}

/// D4. Each row opens that league; the reason line is the fix.
private struct BlockedContent: View {
    let blockers: [DeletionBlockerDto]
    let onOpenLeague: (String) -> Void

    var body: some View {
        heading("You can't delete your account yet")
        lead("These leagues need you first. Sort them out, then come back and try again.")
        DeletionCard {
            ForEach(Array(blockers.enumerated()), id: \.offset) { index, blocker in
                if index > 0 { RowDivider() }
                Button { onOpenLeague(blocker.leagueId) } label: { row(blocker) }
                    .buttonStyle(.pressHighlight)
            }
        }
        HStack(spacing: 6) {
            CrIcon("ic_info", size: 16, tint: CrichereColor.onSurfaceVariant)
            Text("Nothing has changed on your account.").crichere(.sans(12.5, lineHeight: 16.875), CrichereColor.onSurfaceVariant)
        }
    }

    private func row(_ blocker: DeletionBlockerDto) -> some View {
        let live = blocker.reason == .auctionLive
        return HStack(spacing: 12) {
            LeagueTile(id: blocker.leagueId, name: blocker.leagueName, logoUrl: blocker.leagueLogoUrl, size: 40, fontSize: 12)
            VStack(alignment: .leading, spacing: 5) {
                Text(blocker.leagueName).crichere(.sans(14.5, .semiBold, lineHeight: 17.4), CrichereColor.onBackground).lineLimit(1)
                HStack(alignment: .top, spacing: 6) {
                    CrIcon(live ? "ic_gavel" : "ic_person_add", size: 16, tint: live ? CrichereColor.onWarning : CrichereColor.primary).padding(.top, 1)
                    Text(live ? "Auction is live. Wait for it to end." : "Add a co-organizer or mark the league completed.")
                        .crichere(.sans(13, lineHeight: 17.55), bodyInk)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            CrIcon("ic_chevron_right", size: 20, tint: CrichereColor.inkDisabled)
        }
        .padding(14)
        .contentShape(Rectangle())
    }
}

/// D5.
private struct ErrorStateView: View {
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            CrIcon("ic_cloud_off", size: 40, tint: CrichereColor.onSurfaceVariant)
            Text("Couldn't check your account").crichere(.archivo(18, .bold, lineHeight: 21.6), CrichereColor.onBackground)
            Text("Check your connection and try again. Nothing has been deleted.").crichere(.sans(14, lineHeight: 21), bodyInk)
            Spacer().frame(height: 8)
            CheckAgainButton(label: "Retry", action: onRetry).frame(width: 160)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 36)
        .padding(.bottom, 80)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct DeletionSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).crichere(.archivo(14.5, .bold, lineHeight: 14.5), CrichereColor.onBackground).padding(.top, 4)
            content()
        }
    }
}

private struct DeletionCard<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        VStack(alignment: .leading, spacing: 0, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(CrichereColor.surface)
            .clipShape(shape)
            .overlay(shape.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }
}

private struct RowDivider: View {
    var body: some View { CrichereColor.surfaceVariant.frame(height: 1) }
}

/// The bottom action bar: hairline on top, 12/20/28 padding.
private struct DeleteBottomBar<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: 0) {
            CrichereColor.outlineVariant.frame(height: 1)
            content()
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 28)
        }
        .background(CrichereColor.background)
    }
}

/// D3's destructive 52 pt pill; greyed out while the check runs (D2).
private struct DeleteButton: View {
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("Delete account").crichere(.sans(15, .semiBold)).padding(.horizontal, 16).frame(maxWidth: .infinity, minHeight: 52, maxHeight: 52)
        }
        .buttonStyle(PillButtonStyle(fill: enabled ? CrichereColor.errorStrong : CrichereColor.disabledContainer,
                                     pressedFill: CrichereColor.errorStrong.opacity(0.85),
                                     content: enabled ? .white : CrichereColor.inkSubtle, radius: 26))
        .disabled(!enabled)
    }
}

/// D4 "Check again" and D5 "Retry": 48 pt outlined pill with a refresh icon.
private struct CheckAgainButton: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                CrIcon("ic_refresh", size: 18, tint: CrichereColor.primary)
                Text(label).crichere(.sans(14.5, .semiBold))
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
        }
        .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.primary.opacity(0.08), content: CrichereColor.primary,
                                     border: CrichereColor.outline, radius: 24))
    }
}

/// D6a-e (androidApp `DeleteAccountConfirmDialog.kt`): the typed-DELETE confirmation. Confirm is enabled
/// only for exactly `DELETE`; any other input shows the D6b helper. While submitting (D6d) nothing in it
/// responds and the scrim can't close it. A failure (D6e) keeps it open.
struct DeleteAccountConfirmDialog: View {
    let state: DeleteAccountDialogState
    let erasesOn: String
    let onInputChange: (String) -> Void
    let onDismiss: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        let submitting = state.isSubmitting
        VStack(alignment: .leading, spacing: 14) {
            CrIcon("ic_delete_forever", size: 26, tint: CrichereColor.errorStrong)
            Text("Delete your account?").crichere(.sans(19, .semiBold, lineHeight: 22.8), CrichereColor.ink)
            Text("You'll be signed out on all your devices. Your account is erased on \(erasesOn) unless you sign in before then.")
                .crichere(.sans(13.5, lineHeight: 19.575), bodyInk)
                .fixedSize(horizontal: false, vertical: true)
            VStack(alignment: .leading, spacing: 6) {
                Text("Type DELETE to confirm").crichere(.sans(12.5, .semiBold), bodyInk)
                ConfirmField(state: state, onInputChange: onInputChange)
                if state.showMismatch && !submitting {
                    Text("Type it in capital letters: DELETE").crichere(.sans(12.5, lineHeight: 16.875), CrichereColor.error)
                }
            }
            if state.failed {
                HStack(alignment: .top, spacing: 8) {
                    CrIcon("ic_error_filled", size: 18, tint: CrichereColor.error)
                    Text("Couldn't delete your account. Check your connection and try again.").crichere(.sans(13, lineHeight: 18.2), CrichereColor.error)
                }
            }
            HStack(spacing: 14) {
                Spacer(minLength: 0)
                Button(action: onDismiss) { Text("Cancel").crichere(.sans(14, .semiBold), CrichereColor.primary).padding(4) }
                    .buttonStyle(.plain)
                    .opacity(submitting ? 0.38 : 1)
                    .disabled(submitting)
                Button(action: onConfirm) {
                    HStack(spacing: 8) {
                        if submitting {
                            CrichereSpinner(color: CrichereColor.errorStrong, track: CrichereColor.errorStrong.opacity(0.25), lineWidth: 1.6, size: 16)
                        }
                        Text(submitting ? "Deleting…" : "Delete account")
                            .crichere(.sans(14, .semiBold), state.canConfirm || submitting ? CrichereColor.errorStrong : CrichereColor.ink.opacity(0.38))
                    }
                    .padding(4)
                }
                .buttonStyle(.plain)
                .allowsHitTesting(state.canConfirm)  // the label already greys itself; .disabled would dim it again
            }
            .padding(.top, 4)
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(CrichereColor.dialogSurface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .padding(.horizontal, 1)
    }
}

/// The 56 pt field: mono capitals, no autocorrect. Green border while focused, red with an error icon on a
/// mismatch (D6b), plain once it loses focus (D6e), greyed while submitting (D6d).
private struct ConfirmField: View {
    let state: DeleteAccountDialogState
    let onInputChange: (String) -> Void

    @FocusState private var focused: Bool

    var body: some View {
        let submitting = state.isSubmitting
        let (width, color): (CGFloat, Color) = submitting ? (1, CrichereColor.outlineVariant)
            : state.showMismatch ? (1.6, CrichereColor.error) : focused ? (1.6, CrichereColor.primary) : (1, CrichereColor.outline)
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        HStack(spacing: 0) {
            ZStack(alignment: .leading) {
                if state.input.isEmpty { Text("DELETE").crichere(.sans(15), CrichereColor.inkSubtle) }
                TextField("", text: Binding(get: { state.input }, set: onInputChange))
                    .font(CrichereTextStyle.mono(16, .medium).font)
                    .tracking(1.28)
                    .foregroundStyle(submitting ? CrichereColor.inkSubtle : CrichereColor.ink)
                    .tint(CrichereColor.primary)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .keyboardType(.asciiCapable)
                    .submitLabel(.done)
                    .focused($focused)
                    .disabled(submitting)
            }
            if !submitting {
                if state.showMismatch {
                    CrIcon("ic_error_filled", size: 20, tint: CrichereColor.error)
                } else if state.canConfirm && focused {
                    // D6e: once focus is gone the field is plain -- no check.
                    CrIcon("ic_check_circle_filled", size: 20, tint: CrichereColor.primary)
                }
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 56)
        .background(submitting ? CrichereColor.background : CrichereColor.surface, in: shape)
        .overlay(shape.strokeBorder(color, lineWidth: width))
        .onAppear { focused = true }
    }
}

/// Observes the shared one-time account notices (D7/D8).
@MainActor
final class AccountNoticesWrapper: ObservableObject {
    @Published var deletionScheduledOn: String?
    @Published var deletionCancelled = false
    @Published var isSuspended = false
    @Published var suspendedPhone: String?
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
        Task { [weak self] in
            for await value in notices.suspended {
                self?.suspendedPhone = value?.maskedPhone
                self?.isSuspended = value != nil
            }
        }
    }

    func dismissScheduled() { notices.dismissDeletionScheduled() }
    func dismissCancelled() { notices.dismissDeletionCancelled() }
    func dismissSuspended() { notices.dismissSuspended() }
}

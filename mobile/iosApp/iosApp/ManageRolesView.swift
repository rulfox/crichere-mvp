import SwiftUI
import Shared

/// Mirrors `ManageRolesViewModel`'s shared `StateFlow<ManageRolesState>` -- same pattern
/// `AuctionSettingsViewModelWrapper` documents.
@MainActor
final class ManageRolesViewModelWrapper: ObservableObject {
    @Published var state: ManageRolesState

    private let viewModel: ManageRolesViewModel

    init(leagueId: String) {
        let viewModel = KoinHelper().manageRolesViewModel(leagueId: leagueId)
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func retry() { viewModel.retry() }
    func onPhoneNumberChanged(_ value: String) { viewModel.onPhoneNumberChanged(value: value) }
    func lookup() { viewModel.lookup() }
    func grant() { viewModel.grant() }
    func revoke(_ roleId: String) { viewModel.revoke(roleId: roleId) }
}

private let rowAvatar = Color(hex: 0xE3E8DD)
private let revokeBorder = Color(hex: 0xD9B8B2)
private let revokeTrack = Color(hex: 0xEBD6D2)
private let youTagBg = Color(hex: 0xD7EBD2)
private let youTagText = Color(hex: 0x0B2E10)

/// Manage co-organizers (design K1-K10, androidApp `ui/ManageRolesScreen.kt`): look up another
/// registered user by phone number, confirm, then grant them full organizer authority over this
/// league; below that, the current co-organizers, each revocable after its own confirmation.
struct ManageRolesView: View {
    let leagueId: String
    var onBack: () -> Void = {}
    /// The signed-in co-organizer removed themselves (U4 K10): leave, telling the league page which league.
    let onAccessRevoked: (String) -> Void

    @StateObject private var wrapper: ManageRolesViewModelWrapper
    @State private var showGrantConfirm = false
    @State private var pendingRevoke: LeagueRoleDto?

    init(leagueId: String, onBack: @escaping () -> Void = {}, onAccessRevoked: @escaping (String) -> Void = { _ in }) {
        self.leagueId = leagueId
        self.onBack = onBack
        self.onAccessRevoked = onAccessRevoked
        _wrapper = StateObject(wrappedValue: ManageRolesViewModelWrapper(leagueId: leagueId))
    }

    var body: some View {
        let state = wrapper.state
        let selfRevoke = pendingRevoke.map { $0.userId == state.currentUserId } ?? false
        ManageRolesContent(state: state, actions: ManageRolesActions(
            onBack: onBack,
            onRetry: wrapper.retry,
            onPhoneChanged: wrapper.onPhoneNumberChanged,
            onLookup: {
                UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                wrapper.lookup()
            },
            onGrant: { showGrantConfirm = true },
            onRevoke: { pendingRevoke = $0 }
        ))
        .crichereScreen()
        .onAppear { wrapper.retry() }
        // A co-organizer who revokes their own access has nothing left to manage here.
        .onChange(of: state.accessRevoked) { _, revoked in
            if revoked { onAccessRevoked(state.league?.name.trimmingCharacters(in: .whitespaces) ?? "") }
        }
        // A failed self-revoke closes the dialog; the banner above the list says why.
        .onChange(of: state.revokeError) { _, error in if error != nil && selfRevoke { pendingRevoke = nil } }
        .crichereDialog(isPresented: $showGrantConfirm) {
            let name = state.lookupResult?.name ?? "This user"
            RoleConfirmDialog(title: "Grant co-organizer access?",
                              message: "\(name) will be able to do everything you can do for this league, including editing it and running the auction.",
                              confirmLabel: "Grant", confirmColor: CrichereColor.primary,
                              onDismiss: { showGrantConfirm = false },
                              onConfirm: { showGrantConfirm = false; wrapper.grant() })
        }
        .crichereDialog(isPresented: Binding(get: { pendingRevoke != nil }, set: { if !$0 { pendingRevoke = nil } }),
                        dismissable: !(pendingRevoke.map { state.revokingRoleIds.contains($0.id) } ?? false)) {
            if let role = pendingRevoke {
                if role.userId == state.currentUserId {
                    // U4 K9: removing yourself gets its own wording and stays open, locked, while it goes through.
                    DestructiveConfirmDialog(
                        title: "Remove your own access?",
                        message: "You'll stop being a co-organizer of \(state.league?.name.trimmingCharacters(in: .whitespaces) ?? "this league") right away. You won't be able to edit the league or run the auction. Only an organizer can add you back.",
                        confirmLabel: "Remove me", submittingLabel: "Removing…",
                        submitting: state.revokingRoleIds.contains(role.id),
                        onDismiss: { pendingRevoke = nil },
                        onConfirm: { wrapper.revoke(role.id) })
                } else {
                    RoleConfirmDialog(title: "Revoke \(role.name ?? "this co-organizer")?",
                                      message: "They'll lose co-organizer access to this league right away.",
                                      confirmLabel: "Revoke", confirmColor: CrichereColor.errorStrong,
                                      onDismiss: { pendingRevoke = nil },
                                      onConfirm: { pendingRevoke = nil; wrapper.revoke(role.id) })
                }
            }
        }
    }
}

/// Everything the co-organizers screen does.
struct ManageRolesActions {
    var onBack: () -> Void = {}
    var onRetry: () -> Void = {}
    var onPhoneChanged: (String) -> Void = { _ in }
    var onLookup: () -> Void = {}
    var onGrant: () -> Void = {}
    var onRevoke: (LeagueRoleDto) -> Void = { _ in }
}

/// The screen, stateless for the DEBUG parity gallery.
struct ManageRolesContent: View {
    let state: ManageRolesState
    var actions = ManageRolesActions()

    var body: some View {
        VStack(spacing: 0) {
            BackTitleBar(title: "Manage Co-Organizers", onBack: actions.onBack)
            if state.isLoading && state.league == nil {
                CrichereSpinner(color: CrichereColor.primary, lineWidth: 4, size: 40).frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let league = state.league {
                ScrollView { content(league) }
                    .scrollDismissesKeyboard(.interactively)
            } else {
                LoadErrorView(title: "Couldn't load co-organizers", onRetry: actions.onRetry)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(CrichereColor.background.ignoresSafeArea())
    }

    private func content(_ league: LeagueDto) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("A co-organizer can do everything you can do for this league.").crichere(.sans(12.5, lineHeight: 17.5), CrichereColor.onSurfaceVariant)
            Spacer().frame(height: 5) // + the field's 7 pt notch reserve = the board's 12 pt
            LookupRow(state: state, actions: actions)
            if let found = state.lookupResult {
                Spacer().frame(height: 12)
                FoundCard(name: found.name ?? "Unnamed user", isGranting: state.isGranting, onGrant: actions.onGrant)
            }
            if let error = state.grantError {
                Spacer().frame(height: 12)
                RoleErrorBanner(notice: error)
            }
            Spacer().frame(height: 16)
            Text("Current co-organizers").crichere(.archivo(14.5, .bold, lineHeight: 14.5), CrichereColor.onBackground)
            if let error = state.revokeError {
                Spacer().frame(height: 12)
                RoleErrorBanner(notice: error)
            }
            Spacer().frame(height: 12)
            if league.coOrganizers.isEmpty {
                Text("No co-organizers yet.").crichere(.sans(13, lineHeight: 18.2), CrichereColor.onSurfaceVariant)
            } else {
                CoOrganizerList(roles: league.coOrganizers, currentUserId: state.currentUserId, revoking: state.revokingRoleIds,
                                onRevoke: actions.onRevoke)
            }
        }
        .padding(.horizontal, 19)
        .padding(.top, 4)
        .padding(.bottom, 24)
    }
}

/// Phone field + Look up pill (K1/K2/K4/K6); the pill widens to "Looking up…" and the field gives way.
private struct LookupRow: View {
    let state: ManageRolesState
    let actions: ManageRolesActions

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            CrichereTextField(text: Binding(get: { state.phoneNumberInput }, set: actions.onPhoneChanged), label: "Phone number",
                              look: .form, error: state.lookupError, placeholder: "10-digit mobile number", keyboard: .phonePad,
                              contentType: .telephoneNumber, submitLabel: .search, onSubmit: actions.onLookup)
            RolePill(text: state.isLookingUp ? "Looking up…" : "Look up", loading: state.isLookingUp, height: 52, fontSize: 14,
                     horizontalPadding: 18, action: actions.onLookup)
                .padding(.top, 7)
        }
    }
}

private struct FoundCard: View {
    let name: String
    let isGranting: Bool
    let onGrant: () -> Void

    var body: some View {
        let card = RoundedRectangle(cornerRadius: 14, style: .continuous)
        HStack(spacing: 12) {
            RoleAvatar(name: name, size: 38, fontSize: 13, background: CrichereColor.greenContainer)
            Text("Found: \(name)").crichere(.sans(14, .semiBold, lineHeight: 16.8), CrichereColor.onBackground)
                .frame(maxWidth: .infinity, alignment: .leading)
            RolePill(text: isGranting ? "Granting…" : "Grant", loading: isGranting, height: 36, fontSize: 13,
                     horizontalPadding: isGranting ? 12 : 15, action: onGrant)
        }
        .padding(.horizontal, 15)
        .padding(.vertical, 13)
        .background(CrichereColor.surface, in: card)
        .overlay(card.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }
}

private struct CoOrganizerList: View {
    let roles: [LeagueRoleDto]
    let currentUserId: String?
    let revoking: Set<String>
    let onRevoke: (LeagueRoleDto) -> Void

    var body: some View {
        let card = RoundedRectangle(cornerRadius: 14, style: .continuous)
        VStack(spacing: 0) {
            ForEach(Array(roles.enumerated()), id: \.element.id) { index, role in
                if index > 0 { Rectangle().fill(CrichereColor.surfaceVariant).frame(height: 1) }
                row(role)
            }
        }
        .padding(1)
        .background(CrichereColor.surface, in: card)
        .overlay(card.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }

    private func row(_ role: LeagueRoleDto) -> some View {
        let name = role.name ?? "Unnamed user"
        let isMe = role.userId == currentUserId
        return HStack(spacing: 12) {
            RoleAvatar(name: name, size: 34, fontSize: 12, background: rowAvatar, photoUrl: role.photoUrl)
            HStack(spacing: 6) {
                Text(name).crichere(.sans(13.5, .semiBold, lineHeight: 13.5), CrichereColor.onBackground).lineLimit(1)
                if isMe {
                    // U4 K9: the signed-in user's own row.
                    Text("You").crichere(.sans(10.5, .semiBold, lineHeight: 10.5), youTagText)
                        .padding(.horizontal, 6)
                        .frame(height: 18)
                        .background(youTagBg, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            // The self-revoke dialog shows its own progress, so the row keeps its button.
            if revoking.contains(role.id) && !isMe {
                // K5: spinner + muted label in place of the pill.
                HStack(spacing: 8) {
                    CrichereSpinner(color: CrichereColor.errorStrong, track: revokeTrack, lineWidth: 2, size: 20)
                    Text("Revoking…").crichere(.sans(12.5, .semiBold, lineHeight: 12.5), CrichereColor.onSurfaceVariant)
                }
                .frame(height: 36)
            } else {
                Button { onRevoke(role) } label: {
                    Text("Revoke").crichere(.sans(12.5, .semiBold, lineHeight: 12.5)).padding(.horizontal, 13).frame(height: 36)
                }
                .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.errorStrong.opacity(0.08), content: CrichereColor.errorStrong,
                                             border: revokeBorder, radius: 18))
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 61)
    }
}

/// Initials underneath: they show while the photo loads and if it fails.
private struct RoleAvatar: View {
    let name: String
    let size: CGFloat
    let fontSize: CGFloat
    let background: Color
    var photoUrl: String? = nil

    var body: some View {
        ZStack {
            background
            Text(initials(name)).crichere(.archivo(fontSize, .bold, lineHeight: fontSize), CrichereColor.primary)
            if photoUrl != nil { RemoteImage(url: photoUrl) }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }
}

/// Green pill with an optional white spinner (Look up / Grant and their in-flight states).
private struct RolePill: View {
    let text: String
    let loading: Bool
    let height: CGFloat
    let fontSize: CGFloat
    let horizontalPadding: CGFloat
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if loading { CrichereSpinner(color: .white, track: .white.opacity(0.35), lineWidth: 2, size: 20) }
                Text(text).crichere(.sans(fontSize, .semiBold, lineHeight: fontSize)).lineLimit(1)
            }
            .padding(.horizontal, horizontalPadding)
            .frame(height: height)
        }
        .buttonStyle(PillButtonStyle(fill: CrichereColor.primary, pressedFill: CrichereColor.greenPressed, content: .white, radius: height / 2))
        .allowsHitTesting(!loading)
        .fixedSize()
    }
}

/// K8's red banner (also used for a failed revoke).
private struct RoleErrorBanner: View {
    let notice: RoleNotice

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            CrIcon("ic_error", size: 20, tint: CrichereColor.errorStrong).padding(.top, 2)
            VStack(alignment: .leading, spacing: 3) {
                Text(notice.title).crichere(.sans(13, .semiBold, lineHeight: 17.55), CrichereColor.errorStrong)
                Text(notice.message).crichere(.sans(12, lineHeight: 16.8), CrichereColor.errorStrong)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(CrichereColor.errorContainer, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

/// K3 / K5: title 19/23.75, body, Cancel + the coloured confirm.
struct RoleConfirmDialog: View {
    let title: String
    let message: String
    let confirmLabel: String
    let confirmColor: Color
    let onDismiss: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        CrichereDialog(title: title, titleStyle: .sans(19, .semiBold, lineHeight: 23.75), titleColor: CrichereColor.onBackground, buttons: [
            DialogButton(label: "Cancel", action: onDismiss),
            DialogButton(label: confirmLabel, color: confirmColor, action: onConfirm),
        ]) {
            Text(message).crichere(.sans(13.5, lineHeight: 19.575), CrichereColor.dialogBody).fixedSize(horizontal: false, vertical: true)
        }
    }
}

import SwiftUI

// The moderation pieces screens embed (androidApp `ui/ModerationUi.kt`, `AccountDeletionUi.kt`): the
// R2 row menu, R3 end links, R9 blocked-organizer notice / banner, and the D10 / R8a neutral avatar.

extension DeletedAvatar {
    /// Design D10: a deleted user's muted name.
    static let name = Color(hex: 0x5A665D)
}

/// Design D10 / board R8a: the neutral person avatar (a deleted, blocked or removed user).
struct DeletedUserAvatar: View {
    let size: CGFloat
    let iconSize: CGFloat
    var background: Color = DeletedAvatar.background
    var tint: Color = DeletedAvatar.icon
    var cornerRadius: CGFloat? = nil

    var body: some View {
        CrIcon("ic_person_filled", size: iconSize, tint: tint)
            .frame(width: size, height: size)
            .background(background, in: RoundedRectangle(cornerRadius: cornerRadius ?? size / 2, style: .continuous))
            .accessibilityLabel("Deleted user")
    }
}

extension DeletedUserAvatar {
    /// The round avatar with Android's icon ratio (18 pt in 32 pt).
    init(size: CGFloat) { self.init(size: size, iconSize: (size * 18 / 32).rounded()) }
}

/// Board R2: the quiet ⋮ on someone else's roster row, and its menu (name header, Report, Block in red
/// or Unblock) -- a Material DropdownMenu, 208 pt wide, end-aligned with the ⋮.
struct ModerationRowMenu: View {
    let name: String
    let blocked: Bool
    let onReport: () -> Void
    let onBlock: () -> Void
    let onUnblock: () -> Void

    @State private var open = false
    @State private var anchor: CGRect = .zero

    var body: some View {
        Button { open = true } label: {
            CrIcon("ic_more_vert", size: 20, tint: CrichereColor.onSurfaceVariant).frame(width: 44, height: 44)
        }
        .buttonStyle(.pressCircle)
        .accessibilityLabel("More options for \(name)")
        .background(GeometryReader { proxy in
            Color.clear
                .onAppear { anchor = proxy.frame(in: .global) }
                .onChange(of: proxy.frame(in: .global)) { _, frame in anchor = frame }
        })
        .crichereOverlay(isPresented: $open) {
            AnchoredMenu(anchor: anchor, width: 208, onDismiss: { open = false }) {
                Text(name)
                    .crichere(.sans(12), CrichereColor.onSurfaceVariant)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 6)
                    .frame(maxWidth: .infinity, alignment: .leading)
                MenuItem(icon: "ic_flag", label: "Report") { open = false; onReport() }
                if blocked {
                    MenuItem(icon: "ic_block", label: "Unblock") { open = false; onUnblock() }
                } else {
                    MenuItem(icon: "ic_block", label: "Block", tint: CrichereColor.errorStrong, labelColor: CrichereColor.errorStrong) {
                        open = false
                        onBlock()
                    }
                }
            }
        }
    }
}

/// Material3 `DropdownMenuItem`: 48 pt, 12 pt side padding, leading icon, 15 pt label.
struct MenuItem: View {
    let icon: String
    let label: String
    var tint: Color = CrichereColor.onSurfaceVariant
    var labelColor: Color = CrichereColor.onBackground
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                CrIcon(icon, size: 20, tint: tint).frame(width: 24, height: 24)
                Text(label).crichere(.sans(15), labelColor)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .frame(height: 48)
        }
        .buttonStyle(.pressHighlight)
    }
}

/// A Material menu surface under [anchor] (end-aligned with it, flipped above when there's no room):
/// 12 pt corners, white, elevation 8, 8 pt vertical padding. Tap outside to dismiss.
struct AnchoredMenu<Content: View>: View {
    let anchor: CGRect
    let width: CGFloat
    let onDismiss: () -> Void
    @ViewBuilder let content: () -> Content

    @State private var height: CGFloat = 0

    var body: some View {
        ZStack {
            Color.clear.contentShape(Rectangle()).ignoresSafeArea().onTapGesture(perform: onDismiss)
                .accessibilityAddTraits(.isButton).accessibilityLabel("Dismiss menu")
            GeometryReader { proxy in
                let screen = proxy.frame(in: .global)
                let below = screen.maxY - anchor.maxY - 8
                let showBelow = height <= below || below >= anchor.minY - screen.minY
                let x = min(max(anchor.maxX - width, screen.minX + 8), screen.maxX - width - 8)
                VStack(spacing: 0, content: content)
                    .padding(.vertical, 8)
                    .frame(width: width)
                    .background(CrichereColor.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .shadow(color: .black.opacity(0.18), radius: 8, y: 3)
                    .background(GeometryReader { menu in Color.clear.onAppear { height = menu.size.height } })
                    .offset(x: x - screen.minX, y: (showBelow ? anchor.maxY : anchor.minY - height) - screen.minY)
            }
        }
    }
}

/// Boards R3 and R3e-R3g: quiet links at the end of the page, never near Share and Follow. nil hides one.
struct LeagueEndLinks: View {
    let onReportLeague: (() -> Void)?
    let onBlockOrganizer: (() -> Void)?

    var body: some View {
        if onReportLeague != nil || onBlockOrganizer != nil {
            HStack(spacing: 4) {
                if let onReportLeague { QuietLink(icon: "ic_flag", label: "Report league", action: onReportLeague) }
                if let onBlockOrganizer { QuietLink(icon: "ic_block", label: "Block organizer", action: onBlockOrganizer) }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 28)
        }
    }
}

private struct QuietLink: View {
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                CrIcon(icon, size: 18, tint: CrichereColor.onSurfaceVariant)
                Text(label).crichere(.sans(13, .medium), CrichereColor.onSurfaceVariant)
            }
            .padding(.horizontal, 14)
            .frame(minHeight: 44)
        }
        .buttonStyle(PressHighlight(shape: RoundedRectangle(cornerRadius: 8, style: .continuous)))
    }
}

/// Board R9a: the league's details stay hidden until the organizer is unblocked.
struct BlockedOrganizerNotice: View {
    let organizerName: String
    let onUnblock: () -> Void
    let onHome: () -> Void

    private var who: String {
        let capitalized = organizerName.prefix(1).uppercased() + organizerName.dropFirst()
        return capitalized.replacingOccurrences(of: "This organizer", with: "This person")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("You blocked this league's organizer").crichere(.sans(22, .bold, lineHeight: 28), CrichereColor.onBackground)
            Spacer().frame(height: 12)
            Text("\(who) organizes this league. While they're blocked, its details stay hidden from you. They haven't been told.")
                .crichere(.sans(15, lineHeight: 23), CrichereColor.onSurfaceVariant)
            Spacer().frame(height: 24)
            Button(action: onUnblock) {
                Text("Unblock \(organizerName)").crichere(.sans(15, .medium)).frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
            }
            .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.ink.opacity(0.06), content: CrichereColor.onBackground,
                                         border: CrichereColor.outline, radius: 24))
            Spacer().frame(height: 8)
            Button(action: onHome) {
                Text("Go to Home").crichere(.sans(15, .medium)).frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
            }
            .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.primary.opacity(0.1), content: CrichereColor.primary, radius: 24))
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Board R9b, for a member: the league stays, without the blocked organizer's uploads.
struct BlockedOrganizerBanner: View {
    let organizerName: String
    let onUnblock: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Text("You blocked \(organizerName), who organizes this league. Their logo, banner and description are hidden.")
                .crichere(.sans(13, lineHeight: 19), CrichereColor.onBackground)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onUnblock) {
                Text("Unblock").crichere(.sans(13, .semiBold), CrichereColor.primary).padding(.horizontal, 12).frame(minHeight: 40)
            }
            .buttonStyle(PressHighlight(shape: Capsule()))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(CrichereColor.surfaceVariant, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

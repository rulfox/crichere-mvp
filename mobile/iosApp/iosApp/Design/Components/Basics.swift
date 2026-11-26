import SwiftUI

// SwiftUI twins of androidApp `ui/CrichereComponents.kt` (wordmark, pills, notice card, snackbar,
// back bar) plus the small pieces every screen needs (icon, spinner, press feedback).

/// An Android Material Symbols drawable from `Design/Icons.xcassets` (scripts/convert_android_icons.py),
/// tinted like Compose's `Icon(tint = …)`.
struct CrIcon: View {
    let name: String
    var size: CGFloat = 24
    var tint: Color = CrichereColor.ink

    init(_ name: String, size: CGFloat = 24, tint: Color = CrichereColor.ink) {
        self.name = name
        self.size = size
        self.tint = tint
    }

    var body: some View {
        Image(name)
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .foregroundStyle(tint)
            .accessibilityHidden(true)
    }
}

/// Material's indeterminate `CircularProgressIndicator`: a rotating arc on an optional track.
struct CrichereSpinner: View {
    var color: Color = CrichereColor.primary
    var track: Color = .clear
    var lineWidth: CGFloat = 2
    var size: CGFloat = 22

    @State private var rotating = false

    var body: some View {
        ZStack {
            Circle().stroke(track, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: 0.72)
                .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .square))
                .rotationEffect(.degrees(rotating ? 360 : 0))
                .animation(.linear(duration: 1.1).repeatForever(autoreverses: false), value: rotating)
        }
        .frame(width: size - lineWidth, height: size - lineWidth)
        .frame(width: size, height: size)
        .onAppear { rotating = true }
        .accessibilityLabel("Loading")
    }
}

/// Stands in for Compose's ripple: a light ink wash over the shape while pressed.
struct PressHighlight<S: Shape>: ButtonStyle {
    var shape: S
    var color: Color = CrichereColor.ink.opacity(0.1)

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(shape)
            .overlay(shape.fill(configuration.isPressed ? color : .clear).allowsHitTesting(false))
    }
}

extension ButtonStyle where Self == PressHighlight<Rectangle> {
    static var pressHighlight: PressHighlight<Rectangle> { PressHighlight(shape: Rectangle()) }
}

extension ButtonStyle where Self == PressHighlight<Circle> {
    static var pressCircle: PressHighlight<Circle> { PressHighlight(shape: Circle()) }
}

/// Compact green wordmark used as the header on auth screens once the hero is gone.
struct CrichereWordmark: View {
    var body: some View {
        Text("Crichere")
            .crichere(.archivo(28, .extraBold, lineHeight: 28, tracking: -0.84), CrichereColor.primary)
            .accessibilityAddTraits(.isHeader)
    }
}

private let pillLabel = CrichereTextStyle.sans(15, .semiBold)

/// Full-width 52 pt green pill. While [loading] it stays green (at 85% opacity, the design's
/// in-flight state) instead of greying out; the view models already ignore repeat taps.
struct CricherePrimaryButton: View {
    let text: String
    var enabled: Bool = true
    var loading: Bool = false
    var loadingText: String? = nil
    var height: CGFloat = 52
    let action: () -> Void

    init(_ text: String, enabled: Bool = true, loading: Bool = false, loadingText: String? = nil, height: CGFloat = 52,
         action: @escaping () -> Void) {
        self.text = text
        self.enabled = enabled
        self.loading = loading
        self.loadingText = loadingText
        self.height = height
        self.action = action
    }

    var body: some View {
        let active = enabled || loading
        Button(action: action) {
            HStack(spacing: 10) {
                if loading {
                    CrichereSpinner(color: CrichereColor.onPrimary, size: 22)
                    Text(loadingText ?? text).crichere(pillLabel)
                } else {
                    Text(text).crichere(pillLabel)
                }
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, minHeight: height, maxHeight: height)
        }
        .buttonStyle(PillButtonStyle(
            fill: active ? CrichereColor.primary : CrichereColor.disabledContainer,
            pressedFill: CrichereColor.greenPressed,
            content: active ? CrichereColor.onPrimary : CrichereColor.inkSubtle
        ))
        .disabled(!active)
        .opacity(loading ? 0.85 : 1)
    }
}

/// Full-width 52 pt outlined pill (secondary action).
struct CrichereOutlinedButton: View {
    let text: String
    let action: () -> Void

    init(_ text: String, action: @escaping () -> Void) {
        self.text = text
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(text)
                .crichere(pillLabel)
                .padding(.horizontal, 16)
                .frame(maxWidth: .infinity, minHeight: 52, maxHeight: 52)
        }
        .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.primary.opacity(0.1), content: CrichereColor.primary, border: CrichereColor.outline))
    }
}

/// The 26 pt-radius pill shape both buttons share, with the pressed fill.
struct PillButtonStyle: ButtonStyle {
    var fill: Color
    var pressedFill: Color
    var content: Color
    var border: Color? = nil
    var radius: CGFloat = 26

    func makeBody(configuration: Configuration) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        configuration.label
            .foregroundStyle(content)
            .background(shape.fill(configuration.isPressed ? pressedFill : fill))
            .overlay { if let border { shape.strokeBorder(border, lineWidth: 1) } }
            .contentShape(shape)
    }
}

/// Icon + title + body callout (error / warning banners).
struct NoticeCard: View {
    let icon: String
    let title: String
    let message: String
    let containerColor: Color
    let titleColor: Color
    let bodyColor: Color

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            CrIcon(icon, size: 20, tint: titleColor).padding(.top, 2)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).crichere(.sans(14, .semiBold, lineHeight: 16.8), titleColor)
                Text(message).crichere(.sans(12.5, lineHeight: 17.5), bodyColor)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(containerColor, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

/// The board's dark bar (I12, J4, J5): message plus an optional green action. [inverse] is U5 L21's
/// light bar for the dark auction screen.
struct CrichereSnackbar: View {
    let message: String
    var actionLabel: String? = nil
    var onAction: (() -> Void)? = nil
    var inverse: Bool = false

    var body: some View {
        HStack(spacing: 12) {
            Text(message)
                .crichere(.sans(13.5, .medium, lineHeight: 18.225), inverse ? CrichereColor.ink : .white)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let actionLabel, let onAction {
                Button(action: onAction) {
                    Text(actionLabel).crichere(.sans(13.5, .semiBold, lineHeight: 13.5), inverse ? CrichereColor.primary : CrichereColor.snackbarAction)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(minHeight: 48)
        .background(inverse ? CrichereColor.inverseSnackbar : CrichereColor.ink, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .shadow(color: .black.opacity(inverse ? 0.4 : 0.25), radius: 9, y: 6)
    }
}

/// A message for [snackHost]. A nil [durationSeconds] stays until its action or a swipe (U4 E12).
struct Snack: Equatable {
    let id = UUID()
    let message: String
    var actionLabel: String? = nil
    var onAction: (() -> Void)? = nil
    var durationSeconds: Double? = 4

    static func == (lhs: Snack, rhs: Snack) -> Bool { lhs.id == rhs.id }
}

/// Where the snackbar sits: 24 pt above the bottom inset, or 12 pt above a docked panel (U5 L21).
enum SnackPlacement { case aboveNavBar, aboveDock }

private struct SnackHostModifier: ViewModifier {
    @Binding var snack: Snack?
    var inverse: Bool
    var placement: SnackPlacement
    @State private var dragX: CGFloat = 0

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let current = snack {
                CrichereSnackbar(message: current.message, actionLabel: current.actionLabel, onAction: current.onAction, inverse: inverse)
                    .offset(x: dragX)
                    .gesture(
                        DragGesture(minimumDistance: 10)
                            .onChanged { dragX = $0.translation.width }
                            .onEnded { value in
                                if abs(value.translation.width) > 80 { snack = nil }
                                withAnimation(.easeOut(duration: 0.2)) { dragX = 0 }
                            }
                    )
                    .padding(.horizontal, 12)
                    .padding(.bottom, placement == .aboveNavBar ? 24 : 12)
                    .transition(.opacity)
                    .task(id: current.id) {
                        guard let seconds = current.durationSeconds else { return }
                        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                        if snack?.id == current.id { snack = nil }
                    }
                    .onAppear { UIAccessibility.post(notification: .announcement, argument: current.message) }
            }
        }
        .animation(.easeOut(duration: 0.2), value: snack)
    }
}

extension View {
    /// The I12 snackbar over the bottom of this view: 12 pt from the sides, 24 pt above the bottom
    /// inset, swipe sideways to dismiss. The caller owns [snack].
    func snackHost(_ snack: Binding<Snack?>, inverse: Bool = false, placement: SnackPlacement = .aboveNavBar) -> some View {
        modifier(SnackHostModifier(snack: snack, inverse: inverse, placement: placement))
    }
}

/// Back arrow + 16 pt title, 52 pt under the status bar (boards J, K; C1-edit uses 17 pt with a 4 pt gap).
struct BackTitleBar: View {
    let title: String
    var titleSize: CGFloat = 16
    var titleLineHeight: CGFloat = 19.2
    var gap: CGFloat = 6
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: gap) {
            BackArrowButton(action: onBack)
            Text(title)
                .crichere(.sans(titleSize, .semiBold, lineHeight: titleLineHeight), CrichereColor.onBackground)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
        }
        .padding(.leading, 5)
        .frame(height: 52)
    }
}

/// The 48 pt round back target with the 24 pt arrow every top bar uses.
struct BackArrowButton: View {
    var tint: Color = CrichereColor.onBackground
    var icon: String = "ic_arrow_back"
    var label: String = "Back"
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            CrIcon(icon, size: 24, tint: tint).frame(width: 48, height: 48)
        }
        .buttonStyle(.pressCircle)
        .accessibilityLabel(label)
    }
}

/// A notice the user can close (design D7, androidApp `AccountDeletionUi.kt`): 22 pt icon, Medium
/// 13.5/18.9 text, 20 pt close, 14 pt padding, 16 pt corners.
struct DismissibleNotice: View {
    let icon: String
    let text: String
    let container: Color
    let ink: Color
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            CrIcon(icon, size: 22, tint: ink)
            Text(text).crichere(.sans(13.5, .medium, lineHeight: 18.9), ink).frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onDismiss) { CrIcon("ic_close", size: 20, tint: ink) }
                .buttonStyle(.pressCircle)
                .accessibilityLabel("Dismiss")
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(container, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// Full-screen load failure (androidApp `LeagueDetailScreen.kt` `LoadError`): cloud-off icon, Archivo
extension View {
    /// A Compose `Text` with no `textAlign` in a centred column: centred while it fits on one line; once it
    /// wraps it takes the full width and its lines start at the left.
    func composeWrap() -> some View {
        ViewThatFits(in: .horizontal) {
            fixedSize()
            multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// title, hint, and a green Retry pill.
struct LoadErrorView: View {
    var title: String = "Couldn't load this league"
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            CrIcon("ic_cloud_off", size: 40, tint: CrichereColor.outline)
            Spacer().frame(height: 10)
            Text(title).crichere(.archivo(17, .bold), CrichereColor.onBackground).composeWrap()
            Spacer().frame(height: 10)
            Text("Check your connection and try again.").crichere(.sans(13, lineHeight: 18.85), CrichereColor.onSurfaceVariant)
                .composeWrap()
            Spacer().frame(height: 14)
            Button(action: onRetry) {
                Text("Retry").crichere(.sans(13.5, .semiBold)).padding(.horizontal, 20).frame(height: 40)
            }
            .buttonStyle(PillButtonStyle(fill: CrichereColor.primary, pressedFill: CrichereColor.greenPressed, content: CrichereColor.onPrimary, radius: 20))
        }
        .padding(.horizontal, 40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// The system share sheet (native on both platforms -- Android uses its chooser) with plain text.
@MainActor
func presentShareSheet(text: String) {
    let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
    guard var top = windows.first(where: \.isKeyWindow)?.rootViewController else { return }
    while let presented = top.presentedViewController { top = presented }
    let sheet = UIActivityViewController(activityItems: [text], applicationActivities: nil)
    sheet.popoverPresentationController?.sourceView = top.view
    top.present(sheet, animated: true)
}

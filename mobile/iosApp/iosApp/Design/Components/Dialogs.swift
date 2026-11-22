import SwiftUI
import UIKit

// Material-style dialogs (androidApp `ui/CrichereComponents.kt`: DiscardChangesDialog,
// DestructiveConfirmDialog, and the dialog shell the screens build on). Not `.alert`: Android's
// dialogs are drawn, so the iOS ones are too.

// MARK: - Overlay presentation

/// Presents [overlay] full screen over everything (tab bar, navigation) on a clear background with a
/// cross-fade -- the layer dialogs, dropdown menus and other popups draw on.
private struct OverlayPresenter<Overlay: View>: UIViewControllerRepresentable {
    @Binding var isPresented: Bool
    let overlay: () -> Overlay

    final class Coordinator {
        var host: UIHostingController<AnyView>?
    }

    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeUIViewController(context: Context) -> UIViewController { UIViewController() }

    func updateUIViewController(_ controller: UIViewController, context: Context) {
        let coordinator = context.coordinator
        if isPresented {
            if let host = coordinator.host {
                host.rootView = AnyView(overlay())
            } else {
                let host = UIHostingController(rootView: AnyView(overlay()))
                host.view.backgroundColor = .clear
                host.modalPresentationStyle = .overFullScreen
                host.modalTransitionStyle = .crossDissolve
                coordinator.host = host
                DispatchQueue.main.async {
                    guard var top = controller.view.window?.rootViewController else { return }
                    while let presented = top.presentedViewController, presented !== host { top = presented }
                    if host.presentingViewController == nil { top.present(host, animated: true) }
                }
            }
        } else if let host = coordinator.host {
            coordinator.host = nil
            host.dismiss(animated: true)
        }
    }

    static func dismantleUIViewController(_ controller: UIViewController, coordinator: Coordinator) {
        coordinator.host?.dismiss(animated: false)
        coordinator.host = nil
    }
}

extension View {
    /// Shows [content] over the whole app while [isPresented] is true (fade in/out, clear backdrop).
    func crichereOverlay<Content: View>(isPresented: Binding<Bool>, @ViewBuilder content: @escaping () -> Content) -> some View {
        background(OverlayPresenter(isPresented: isPresented, overlay: content).frame(width: 0, height: 0))
    }

    /// A Material dialog: scrim, then [content] (usually a [CrichereDialog]) centred. Tapping the scrim
    /// dismisses unless [dismissable] is false.
    func crichereDialog<Content: View>(isPresented: Binding<Bool>, dismissable: Bool = true, @ViewBuilder content: @escaping () -> Content) -> some View {
        crichereOverlay(isPresented: isPresented) {
            DialogScrim(dismissable: dismissable, onDismiss: { isPresented.wrappedValue = false }, content: content)
        }
    }
}

private struct DialogScrim<Content: View>: View {
    let dismissable: Bool
    let onDismiss: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            Color.black.opacity(0.32)
                .ignoresSafeArea()
                .onTapGesture { if dismissable { onDismiss() } }
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Dismiss")
            content()
                .padding(.horizontal, 23)
        }
    }
}

// MARK: - Dialog shell

/// A dialog button: Material `TextButton` (40 pt high, 12 pt side padding), SemiBold 14 in [color].
struct DialogButton: Identifiable {
    let id = UUID()
    let label: String
    var color: Color = CrichereColor.primary
    var enabled: Bool = true
    var busy: Bool = false
    /// Faded (38%) while another action runs -- Cancel during a destructive submit.
    var faded: Bool = false
    let action: () -> Void
}

/// Material3 `AlertDialog` geometry: 24 pt padding, title, 16 pt, text, 24 pt, buttons at the end
/// 8 pt apart, 28 pt corners, on the board's #F1F4EE surface.
struct CrichereDialog<Body: View>: View {
    let title: String
    var titleStyle: CrichereTextStyle = .sans(19, .semiBold, lineHeight: 22.8)
    var titleColor: Color = CrichereColor.ink
    var surface: Color = CrichereColor.dialogSurface
    var radius: CGFloat = 28
    /// Material's optional top icon (24 pt slot, 16 pt above the title); with one, the title centres.
    var icon: String? = nil
    var iconTint: Color = CrichereColor.primary
    var iconSize: CGFloat = 24
    var buttons: [DialogButton]
    @ViewBuilder var content: () -> Body

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let icon {
                CrIcon(icon, size: iconSize, tint: iconTint)
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, 16)
            }
            Text(title)
                .crichere(titleStyle, titleColor)
                .multilineTextAlignment(icon != nil ? .center : .leading)
                .frame(maxWidth: .infinity, alignment: icon != nil ? .center : .leading)
                .accessibilityAddTraits(.isHeader)
            content()
                .padding(.top, 16)
            HStack(spacing: 8) {
                Spacer(minLength: 0)
                ForEach(buttons) { button in
                    Button(action: button.action) {
                        HStack(spacing: 8) {
                            if button.busy {
                                CrichereSpinner(color: button.color, track: button.color.opacity(0.25), size: 16)
                            }
                            Text(button.label).crichere(.sans(14, .semiBold, lineHeight: 14), button.color)
                        }
                        .padding(.horizontal, 12)
                        .frame(minHeight: 40)
                    }
                    .buttonStyle(PressHighlight(shape: Capsule(), color: button.color.opacity(0.1)))
                    .disabled(!button.enabled)
                    .opacity(button.faded ? 0.38 : 1)
                }
            }
            .padding(.top, 24)
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(surface, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
    }
}

extension CrichereDialog where Body == AnyView {
    /// Title + body text, the common case.
    init(title: String, message: String, icon: String? = nil, iconTint: Color = CrichereColor.primary, iconSize: CGFloat = 24,
         buttons: [DialogButton]) {
        self.init(title: title, icon: icon, iconTint: iconTint, iconSize: iconSize, buttons: buttons) {
            AnyView(Text(message).crichere(.sans(13.5, lineHeight: 19.575), CrichereColor.dialogBody).fixedSize(horizontal: false, vertical: true))
        }
    }
}

/// "Discard changes?" (boards I10, C1-discard): leaving a form with unsaved edits. [message] says what
/// stays as it was.
struct DiscardChangesDialog: View {
    let message: String
    let onKeepEditing: () -> Void
    let onDiscard: () -> Void

    var body: some View {
        CrichereDialog(title: "Discard changes?", message: message, buttons: [
            DialogButton(label: "Keep editing", action: onKeepEditing),
            DialogButton(label: "Discard", color: CrichereColor.errorStrong, action: onDiscard),
        ])
    }
}

/// The N4 / K3 confirmation shell for an irreversible action (U4 E10/E11, K9): confirm in the
/// destructive red. While [submitting]: spinner + [submittingLabel], Cancel at 38% and inert. Present
/// with `crichereDialog(isPresented:dismissable: !submitting)` so the scrim can't dismiss it mid-request.
struct DestructiveConfirmDialog: View {
    let title: String
    let message: String
    let confirmLabel: String
    let submittingLabel: String
    let submitting: Bool
    let onDismiss: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        CrichereDialog(title: title, message: message, buttons: [
            DialogButton(label: "Cancel", enabled: !submitting, faded: submitting, action: onDismiss),
            DialogButton(label: submitting ? submittingLabel : confirmLabel, color: CrichereColor.errorStrong, enabled: !submitting, busy: submitting, action: onConfirm),
        ])
    }
}

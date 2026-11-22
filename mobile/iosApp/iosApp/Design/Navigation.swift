import SwiftUI
import UIKit

// Navigation chrome for the Android-matched screens: every screen draws its own top bar (BackTitleBar,
// FlowTopBar, …) like the Android app, so the system navigation bar is hidden -- while iOS's
// edge-swipe back keeps working (docs/IOS-REDESIGN-PLAN.md: "keep native").

/// UIKit turns the interactive pop gesture off when the navigation bar is hidden; re-enable it for
/// every pushed screen (not the root, where it would freeze the stack).
extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        viewControllers.count > 1
    }

    public func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        false
    }
}

extension View {
    /// A full Android-style screen: no system navigation bar, the board's background behind everything.
    func crichereScreen(background: Color = CrichereColor.background) -> some View {
        self
            .toolbar(.hidden, for: .navigationBar)
            .background(background.ignoresSafeArea())
    }

    /// White status-bar icons while [enabled] (over the green hero or the dark auction), dark otherwise
    /// -- Android's `LightStatusBarIcons`. The app is light-only (Theme.kt), so the window's interface
    /// style only ever flips for the status bar; every Crichere colour is fixed.
    func lightStatusBarIcons(_ enabled: Bool) -> some View {
        modifier(StatusBarStyleModifier(light: enabled))
    }
}

private struct StatusBarStyleModifier: ViewModifier {
    let light: Bool

    func body(content: Content) -> some View {
        content
            .onAppear { apply(light) }
            .onChange(of: light) { _, value in apply(value) }
            .onDisappear { apply(false) }
    }

    private func apply(_ light: Bool) {
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
        for window in windows { window.overrideUserInterfaceStyle = light ? .dark : .light }
    }
}

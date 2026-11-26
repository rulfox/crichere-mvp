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
        viewControllers.count > 1 && SwipeBackGuard.blockers == 0
    }

    public func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        false
    }
}

/// Screens that must intercept Back (an edited form asks "Discard changes?" first, like Android's
/// BackHandler) turn the edge swipe off while they're dirty; their own close button asks instead.
@MainActor
enum SwipeBackGuard {
    static var blockers = 0
}

private struct SwipeBackBlocker: ViewModifier {
    let blocked: Bool
    @State private var holding = false

    func body(content: Content) -> some View {
        content
            .onAppear { update(blocked) }
            .onChange(of: blocked) { _, value in update(value) }
            .onDisappear { update(false) }
    }

    private func update(_ value: Bool) {
        guard value != holding else { return }
        holding = value
        SwipeBackGuard.blockers += value ? 1 : -1
    }
}

extension View {
    /// Turns iOS's edge-swipe back off while [blocked] (see [SwipeBackGuard]).
    func swipeBackBlocked(_ blocked: Bool) -> some View {
        modifier(SwipeBackBlocker(blocked: blocked))
    }

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

/// Counts the screens currently asking for white icons; the window is dark-styled while any do. A
/// counter, not last-writer-wins, so a parent's "light" can't undo a child's request on appear order.
@MainActor
private enum StatusBarCoordinator {
    static var lightRequests = 0

    static func update(_ delta: Int) {
        lightRequests = max(0, lightRequests + delta)
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
        for window in windows { window.overrideUserInterfaceStyle = lightRequests > 0 ? .dark : .light }
    }
}

private struct StatusBarStyleModifier: ViewModifier {
    let light: Bool
    @State private var holding = false

    func body(content: Content) -> some View {
        content
            .onAppear { set(light) }
            .onChange(of: light) { _, value in set(value) }
            .onDisappear { set(false) }
    }

    private func set(_ wanted: Bool) {
        if wanted != holding {
            holding = wanted
            StatusBarCoordinator.update(wanted ? 1 : -1)
        } else {
            StatusBarCoordinator.update(0)
        }
    }
}

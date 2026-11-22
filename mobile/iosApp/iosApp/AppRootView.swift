import SwiftUI
import Shared

/// Mirrors `AppStartViewModel`'s shared `StateFlow<AppStartDestination?>` -- `nil` while the
/// silent app-start session check is in flight. Same pattern `OwnProfileViewModelWrapper`
/// documents. **Authored but unverified** -- see docs/PHASE9.md.
@MainActor
final class AppStartViewModelWrapper: ObservableObject {
    @Published var destination: AppStartDestination?
    @Published var isOffline = false
    private let viewModel: AppStartViewModel

    init() {
        let viewModel = KoinHelper().appStartViewModel
        self.viewModel = viewModel
        self.destination = viewModel.destination.value

        Task { [weak self] in
            for await newDestination in viewModel.destination {
                self?.destination = newDestination
            }
        }
        Task { [weak self] in
            for await offline in viewModel.isOffline {
                self?.isOffline = offline.boolValue
            }
        }
    }

    func retry() { viewModel.retry() }
}

/// The 3 destinations reachable once signed in and past Profile Setup -- `MainTabView`'s tabs.
enum MainTab: Hashable {
    case dashboard, myLeagues, myProfile
}

/// The full navigation contract, a direct SwiftUI port of `AuthNavHost.kt`'s state machine (same
/// destinations, same transitions, same "plain state switcher, not a nav-stack" shape at this
/// level -- there is no "back" concept between these five, same as Android):
///  - `.starting` -> `.phoneEntry` / `.profileSetup` / `.main`: the app-start routing check.
///  - `.phoneEntry` -> `.otpVerify`: real.
///  - `.otpVerify` -> `.profileSetup` / `.main`: real, driven by `AuthNavigationEvent`.
///  - `.otpVerify` -> `.phoneEntry`: real (the 5th-wrong-attempt forced bounce-back).
///  - `.profileSetup` -> `.main`: real, on a save that completes the profile.
///  - `.main` (My Profile tab) -> `.profileSetup(isEditMode: true)` / `.phoneEntry` (logout): real.
private enum AuthDestination {
    case starting
    /// [lockedOut]: bounced back after 5 wrong OTP attempts (design B6).
    case phoneEntry(lockedOut: Bool)
    case otpVerify(phoneNumber: String, verificationId: String, resendToken: Any?)
    case profileSetup(isEditMode: Bool)
    case main(initialTab: MainTab)
    case suspended
}

/// Holds the Sign in wrapper so it can be kept (back) or replaced (fresh entry).
@MainActor
final class PhoneEntryHolder: ObservableObject {
    @Published private(set) var wrapper = PhoneEntryViewModelWrapper()
    func reset() { wrapper = PhoneEntryViewModelWrapper() }
}

/// Root of the app (see `iosAppApp.swift`). `pendingDeepLinkLeagueId` (a `crichere://leagues/{id}`
/// URL, see that file's `.onOpenURL`) is a **binding**, not a plain seeded `@State` -- a `View`'s
/// `@State` only takes its `init` argument the *first* time the view is created and ignores it on
/// every later re-render, so a URL arriving while the app is already running (updating the
/// parent's own `@State`) would silently never reach here with a plain value parameter. Consumed
/// once `.main` is actually reached -- mirrors `AuthNavHost.kt`'s own `pendingDeepLinkLeagueId` doc
/// exactly: every branch above eventually funnels into `.main`, so "not logged in -> normal flow
/// first, then resume into the league" falls out for free.
struct AppRootView: View {
    @Binding var pendingDeepLinkLeagueId: String?

    @State private var destination: AuthDestination = .starting
    /// Sign in's state outlives the screen, like an Android back-stack entry: a voluntary back from OTP
    /// Verify finds the typed number; lockout / logout / suspension start a fresh one (`replaceAll`).
    @StateObject private var phoneEntry = PhoneEntryHolder()
    @StateObject private var accountNotices = AccountNoticesWrapper()

    var body: some View {
        Group {
            switch destination {
            case .suspended:
                SuspendedView(maskedPhone: accountNotices.suspendedPhone) {
                    accountNotices.dismissSuspended()
                    phoneEntry.reset()
                    destination = .phoneEntry(lockedOut: false)
                }

            case .starting:
                AppStartRootView { resolved in
                    // A suspended refresh also resolves as "no session": the suspended screen wins (board R13).
                    guard !accountNotices.isSuspended else { return }
                    switch onEnum(of: resolved) {
                    case .phoneEntry: destination = .phoneEntry(lockedOut: false)
                    case .profileSetup: destination = .profileSetup(isEditMode: false)
                    case .main: destination = .main(initialTab: .dashboard)
                    }
                }

            case let .phoneEntry(lockedOut):
                PhoneEntryView(wrapper: phoneEntry.wrapper, showLockoutNotice: lockedOut) { phoneNumber, verificationId, resendToken in
                    destination = .otpVerify(phoneNumber: phoneNumber, verificationId: verificationId, resendToken: resendToken)
                }

            case let .otpVerify(phoneNumber, verificationId, resendToken):
                OtpVerifyView(phoneNumber: phoneNumber, verificationId: verificationId, resendToken: resendToken) { event, lockedOut in
                    switch onEnum(of: event) {
                    case .navigateToProfileSetup: destination = .profileSetup(isEditMode: false)
                    case .navigateToOwnProfile: destination = .main(initialTab: .dashboard)
                    case .navigateToPhoneEntry:
                        // The on-screen "Edit" is a back (number kept); the lockout gets a fresh Sign in with its notice.
                        if lockedOut { phoneEntry.reset() }
                        destination = .phoneEntry(lockedOut: lockedOut)
                    }
                }

            case let .profileSetup(isEditMode):
                NavigationStack {
                    ProfileSetupView(
                        isEditMode: isEditMode,
                        // Design update #6: only Edit profile (C1-edit) has a way back; first-time setup (C1) has none.
                        onBack: isEditMode ? { destination = .main(initialTab: .myProfile) } : nil
                    ) {
                        destination = .main(initialTab: isEditMode ? .myProfile : .dashboard)
                    }
                }

            case let .main(initialTab):
                MainTabView(
                    initialTab: initialTab,
                    pendingLeagueId: $pendingDeepLinkLeagueId,
                    onNavigateToEditProfile: { destination = .profileSetup(isEditMode: true) },
                    onNavigateToPhoneEntry: {
                        phoneEntry.reset()
                        destination = .phoneEntry(lockedOut: false)
                    }
                )
            }
        }
        // Any request can be the one that finds out (docs/PHASE18.md): nothing stale stays on screen.
        .onChange(of: accountNotices.isSuspended) { _, suspended in
            if suspended {
                KoinHelper().moderationRepository.clear()
                destination = .suspended
            }
        }
    }
}

/// The minimal loading/splash state while the silent app-start check is in flight.
private struct AppStartRootView: View {
    let onResolved: (AppStartDestination) -> Void

    @StateObject private var wrapper = AppStartViewModelWrapper()

    var body: some View {
        AppStartContent(isOffline: wrapper.isOffline, onRetry: wrapper.retry)
            .onReceive(wrapper.$destination) { resolved in
                // Existential `AppStartDestination` isn't `Equatable`, so observe the `@Published`
                // publisher rather than `onChange(of:)`.
                if let resolved { onResolved(resolved) }
            }
    }
}

/// The app-start check (androidApp `AuthNavHost.kt` `AppStartRoute`): Material's 40 pt spinner, or
/// "Couldn't connect" with Try again when the session is stored but the backend can't be reached.
struct AppStartContent: View {
    let isOffline: Bool
    let onRetry: () -> Void

    var body: some View {
        ZStack {
            if isOffline {
                VStack(spacing: 0) {
                    CrIcon("ic_cloud_off", size: 40, tint: CrichereColor.onSurfaceVariant)
                    Spacer().frame(height: 14)
                    Text("Couldn't connect").crichere(.sans(17, .semiBold), CrichereColor.onBackground)
                    Spacer().frame(height: 6)
                    Text("Check your internet connection and try again.")
                        .crichere(.sans(13, lineHeight: 18), CrichereColor.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                    Spacer().frame(height: 20)
                    CrichereOutlinedButton("Try again", action: onRetry)
                }
            } else {
                CrichereSpinner(color: CrichereColor.primary, lineWidth: 4, size: 40)
            }
        }
        .padding(.horizontal, 40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(CrichereColor.background.ignoresSafeArea())
    }
}

/// D8 (androidApp `AuthNavHost.kt` `DeletionCancelledSnack`): dark bar with a green check, 16 pt in from
/// the sides and 15 pt above the tab bar; tap to dismiss.
struct DeletionCancelledSnack: View {
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            CrIcon("ic_check_circle_filled", size: 20, tint: Color(hex: 0x9CD49A))
            Text("Welcome back. Your account deletion was cancelled.")
                .crichere(.sans(14, .medium, lineHeight: 18.9), .white)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(CrichereColor.onBackground, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .shadow(color: .black.opacity(0.4), radius: 8, y: 6)
        .onTapGesture(perform: onDismiss)
    }
}

/// Pushed destinations reachable from either the Dashboard or My Leagues tab -- one shared
/// `NavigationPath` for both (see `MainTabView`'s own doc for why this is a single stack, not one
/// per tab).
private enum MainDestination: Hashable {
    case leagueDetail(String)
    case leagueCreation(String?)
    case joinLeague(String)
    case claimFranchise(String)
    case screenshotViewer(String, title: String = "Payment screenshot")
    case auctionSettings(String)
    case auctionLive(String)
    case manageRoles(String)
    case deleteAccount
    case blockedPeople
}

/// The 3-tab bottom-nav shell (Dashboard, My Leagues, My Profile) hosting everything reachable
/// after a complete profile -- direct port of `AuthNavHost.kt`'s `MainRoute`.
///
/// **One `NavigationPath` shared by every tab**, not one per tab: Android's `MainRoute` pushes
/// `MainDestination`s as siblings *above* the entire tab `Scaffold` (replacing the tab bar
/// entirely while, say, League Detail is open), not nested inside whichever tab was active. A
/// single `NavigationStack` wrapping the whole `TabView` reproduces that exactly -- pushing
/// anything hides the tab bar, from any tab, the same way Android's plain state switcher does.
///
/// My Leagues (Phase 3) is a persistent 3rd tab, not a pushed destination, same reasoning
/// `AuthNavHost.kt`'s own doc gives. My Profile's Edit/Logout go all the way up to `AppRootView`
/// (not handled here), matching Android's `onNavigateToEditProfile`/`onNavigateToPhoneEntry`
/// bubbling past `MainRoute` to `AuthNavHost` itself.
struct MainTabView: View {
    let initialTab: MainTab
    @Binding var pendingLeagueId: String?
    let onNavigateToEditProfile: () -> Void
    let onNavigateToPhoneEntry: () -> Void

    @State private var selectedTab: MainTab
    @State private var path = NavigationPath()
    /// One-off messages for a league page to show when it is next on screen (design update #4 K10: a
    /// co-organizer removing themselves pops back to the league). Mirrors `AuthNavHost.kt`'s `leagueNotices`.
    @State private var leagueNotices: [String: String] = [:]
    @StateObject private var notices = AccountNoticesWrapper()

    init(
        initialTab: MainTab,
        pendingLeagueId: Binding<String?>,
        onNavigateToEditProfile: @escaping () -> Void,
        onNavigateToPhoneEntry: @escaping () -> Void
    ) {
        self.initialTab = initialTab
        self._pendingLeagueId = pendingLeagueId
        self.onNavigateToEditProfile = onNavigateToEditProfile
        self.onNavigateToPhoneEntry = onNavigateToPhoneEntry
        _selectedTab = State(initialValue: initialTab)
    }

    var body: some View {
        NavigationStack(path: $path) {
            // Android's MainRoute: the selected tab's screen above the board's tab bar (MainBottomBar).
            VStack(spacing: 0) {
                ZStack(alignment: .bottom) {
                    tabContent
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    if notices.deletionCancelled {
                        DeletionCancelledSnack(onDismiss: notices.dismissCancelled)
                            .padding(.horizontal, 16)
                            .padding(.bottom, 15)
                            .transition(.opacity)
                            .task {
                                try? await Task.sleep(nanoseconds: 4_000_000_000)
                                notices.dismissCancelled()
                            }
                    }
                }
                .animation(.easeOut(duration: 0.2), value: notices.deletionCancelled)
                CrichereTabBar(tabs: Self.tabs, selected: selectedTab) { selectedTab = $0 }
            }
            .crichereScreen()
            .navigationDestination(for: MainDestination.self) { mainDestinationView($0) }
        }
        // Loads the block list once signed in, and queues removed-image notices (R12b to R12e).
        .modifier(ModerationNoticeHost())
        .onAppear { consumePendingLeagueIdIfAny() }
        // A deep link can also arrive *after* this view already appeared (the app was already in
        // the foreground on the Main destination) -- `.onAppear` alone would miss that, since it
        // only fires once per appearance, not on every later change to the bound value.
        .onChange(of: pendingLeagueId) { _, _ in consumePendingLeagueIdIfAny() }
    }

    static let tabs = [
        CrichereTab(id: MainTab.dashboard, label: "Dashboard", icon: "ic_sports_cricket", selectedIcon: "ic_sports_cricket_filled"),
        CrichereTab(id: MainTab.myLeagues, label: "My Leagues", icon: "ic_groups", selectedIcon: "ic_groups_filled"),
        CrichereTab(id: MainTab.myProfile, label: "My Profile", icon: "ic_account_circle", selectedIcon: "ic_account_circle_filled"),
    ]

    /// Like Android's `when (tab)`: only the selected tab's screen exists.
    @ViewBuilder private var tabContent: some View {
        switch selectedTab {
        case .dashboard:
            LeagueDashboardView(
                onOpenLeague: { path.append(MainDestination.leagueDetail($0)) },
                onCreateLeague: { path.append(MainDestination.leagueCreation(nil)) },
                onOpenProfile: { selectedTab = .myProfile }
            )
        case .myLeagues:
            MyLeaguesView(
                onOpenLeague: { path.append(MainDestination.leagueDetail($0)) },
                onBrowseLeagues: { selectedTab = .dashboard },
                onCreateLeague: { path.append(MainDestination.leagueCreation(nil)) }
            )
        case .myProfile:
            OwnProfileView(
                onNavigateToEditProfile: onNavigateToEditProfile,
                onNavigateToPhoneEntry: onNavigateToPhoneEntry,
                onViewPhoto: { path.append(MainDestination.screenshotViewer($0, title: "Profile photo")) },
                onDeleteAccount: { path.append(MainDestination.deleteAccount) },
                onBlockedPeople: { path.append(MainDestination.blockedPeople) }
            )
        }
    }

    private func consumePendingLeagueIdIfAny() {
        guard let leagueId = pendingLeagueId else { return }
        path.append(MainDestination.leagueDetail(leagueId))
        pendingLeagueId = nil
    }

    @ViewBuilder
    private func mainDestinationView(_ destination: MainDestination) -> some View {
        switch destination {
        case .blockedPeople:
            BlockedPeopleView()

        case .deleteAccount:
            DeleteAccountView(
                onBack: { if !path.isEmpty { path.removeLast() } },
                onOpenLeague: { path.append(MainDestination.leagueDetail($0)) },
                // D7: the session is gone; back to Sign in, which shows the one-time notice.
                onSignedOut: onNavigateToPhoneEntry
            )

        case .leagueDetail(let leagueId):
            LeagueDetailView(
                leagueId: leagueId,
                onEditLeague: { path.append(MainDestination.leagueCreation($0)) },
                onJoinLeague: { path.append(MainDestination.joinLeague($0)) },
                onClaimFranchise: { path.append(MainDestination.claimFranchise($0)) },
                onViewScreenshot: { path.append(MainDestination.screenshotViewer($0)) },
                onAuctionSettings: { path.append(MainDestination.auctionSettings($0)) },
                onAuctionLive: { path.append(MainDestination.auctionLive($0)) },
                onManageRoles: { path.append(MainDestination.manageRoles($0)) },
                notice: leagueNotices[leagueId],
                onNoticeShown: { leagueNotices[leagueId] = nil },
                onHome: { if !path.isEmpty { path.removeLast() } }
            )

        case .leagueCreation(let editingLeagueId):
            LeagueCreationView(
                editingLeagueId: editingLeagueId,
                onDone: { savedLeagueId in
                    if !path.isEmpty { path.removeLast() }
                    path.append(MainDestination.leagueDetail(savedLeagueId))
                },
                onCancel: { if !path.isEmpty { path.removeLast() } }
            )

        case .joinLeague(let leagueId):
            JoinLeagueView(leagueId: leagueId, onDone: { if !path.isEmpty { path.removeLast() } })

        case .claimFranchise(let leagueId):
            ClaimFranchiseView(leagueId: leagueId, onDone: { if !path.isEmpty { path.removeLast() } })

        case let .screenshotViewer(imageUrl, title):
            ScreenshotViewerView(imageUrl: imageUrl, title: title, onBack: { if !path.isEmpty { path.removeLast() } })

        case .auctionSettings(let leagueId):
            AuctionSettingsView(leagueId: leagueId, onBack: { if !path.isEmpty { path.removeLast() } })

        case .auctionLive(let leagueId):
            AuctionLiveView(leagueId: leagueId, onBack: { if !path.isEmpty { path.removeLast() } })

        case .manageRoles(let leagueId):
            ManageRolesView(
                leagueId: leagueId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onAccessRevoked: { leagueName in
                    leagueNotices[leagueId] = "You're no longer a co-organizer of \(leagueName)."
                    if !path.isEmpty { path.removeLast() }
                }
            )
        }
    }
}

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
    case phoneEntry
    case otpVerify(phoneNumber: String, verificationId: String, resendToken: Any?)
    case profileSetup(isEditMode: Bool)
    case main(initialTab: MainTab)
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

    var body: some View {
        Group {
            switch destination {
            case .starting:
                AppStartRootView { resolved in
                    switch resolved {
                    case .phoneEntry: destination = .phoneEntry
                    case .profileSetup: destination = .profileSetup(isEditMode: false)
                    case .main: destination = .main(initialTab: .dashboard)
                    }
                }

            case .phoneEntry:
                PhoneEntryView { phoneNumber, verificationId, resendToken in
                    destination = .otpVerify(phoneNumber: phoneNumber, verificationId: verificationId, resendToken: resendToken)
                }

            case let .otpVerify(phoneNumber, verificationId, resendToken):
                OtpVerifyView(phoneNumber: phoneNumber, verificationId: verificationId, resendToken: resendToken) { event in
                    switch event {
                    case .navigateToProfileSetup: destination = .profileSetup(isEditMode: false)
                    case .navigateToOwnProfile: destination = .main(initialTab: .dashboard)
                    case .navigateToPhoneEntry: destination = .phoneEntry
                    }
                }

            case let .profileSetup(isEditMode):
                NavigationStack {
                    ProfileSetupView(isEditMode: isEditMode) {
                        destination = .main(initialTab: isEditMode ? .myProfile : .dashboard)
                    }
                }

            case let .main(initialTab):
                MainTabView(
                    initialTab: initialTab,
                    pendingLeagueId: $pendingDeepLinkLeagueId,
                    onNavigateToEditProfile: { destination = .profileSetup(isEditMode: true) },
                    onNavigateToPhoneEntry: { destination = .phoneEntry }
                )
            }
        }
    }
}

/// The minimal loading/splash state while the silent app-start check is in flight.
private struct AppStartRootView: View {
    let onResolved: (AppStartDestination) -> Void

    @StateObject private var wrapper = AppStartViewModelWrapper()

    var body: some View {
        Group {
            if wrapper.isOffline {
                // Session still stored; the backend just couldn't be reached (see AppStartViewModel).
                VStack(spacing: 12) {
                    Image(systemName: "icloud.slash").font(.system(size: 36)).foregroundStyle(.secondary)
                    Text("Couldn't connect").font(.headline)
                    Text("Check your internet connection and try again.")
                        .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    Button("Try again") { wrapper.retry() }.buttonStyle(.bordered)
                }
                .padding(40)
            } else {
                ProgressView()
            }
        }
        .onChange(of: wrapper.destination) { _, resolved in
            if let resolved { onResolved(resolved) }
        }
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
    case screenshotViewer(String)
    case auctionSettings(String)
    case auctionLive(String)
    case manageRoles(String)
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
            TabView(selection: $selectedTab) {
                LeagueDashboardView(
                    onOpenLeague: { path.append(MainDestination.leagueDetail($0)) },
                    onCreateLeague: { path.append(MainDestination.leagueCreation(nil)) }
                )
                .tabItem { Label("Dashboard", systemImage: "list.bullet") }
                .tag(MainTab.dashboard)

                MyLeaguesView(onOpenLeague: { path.append(MainDestination.leagueDetail($0)) })
                    .tabItem { Label("My Leagues", systemImage: "person.2") }
                    .tag(MainTab.myLeagues)

                OwnProfileView(onNavigateToEditProfile: onNavigateToEditProfile, onNavigateToPhoneEntry: onNavigateToPhoneEntry)
                    .tabItem { Label("My Profile", systemImage: "person.crop.circle") }
                    .tag(MainTab.myProfile)
            }
            .navigationDestination(for: MainDestination.self) { mainDestinationView($0) }
        }
        .onAppear { consumePendingLeagueIdIfAny() }
        // A deep link can also arrive *after* this view already appeared (the app was already in
        // the foreground on the Main destination) -- `.onAppear` alone would miss that, since it
        // only fires once per appearance, not on every later change to the bound value.
        .onChange(of: pendingLeagueId) { _, _ in consumePendingLeagueIdIfAny() }
    }

    private func consumePendingLeagueIdIfAny() {
        guard let leagueId = pendingLeagueId else { return }
        path.append(MainDestination.leagueDetail(leagueId))
        pendingLeagueId = nil
    }

    @ViewBuilder
    private func mainDestinationView(_ destination: MainDestination) -> some View {
        switch destination {
        case .leagueDetail(let leagueId):
            LeagueDetailView(
                leagueId: leagueId,
                onEditLeague: { path.append(MainDestination.leagueCreation($0)) },
                onJoinLeague: { path.append(MainDestination.joinLeague($0)) },
                onClaimFranchise: { path.append(MainDestination.claimFranchise($0)) },
                onViewScreenshot: { path.append(MainDestination.screenshotViewer($0)) },
                onAuctionSettings: { path.append(MainDestination.auctionSettings($0)) },
                onAuctionLive: { path.append(MainDestination.auctionLive($0)) },
                onManageRoles: { path.append(MainDestination.manageRoles($0)) }
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

        case .screenshotViewer(let imageUrl):
            ScreenshotViewerView(imageUrl: imageUrl)

        case .auctionSettings(let leagueId):
            AuctionSettingsView(leagueId: leagueId)

        case .auctionLive(let leagueId):
            AuctionLiveView(leagueId: leagueId)

        case .manageRoles(let leagueId):
            ManageRolesView(leagueId: leagueId)
        }
    }
}

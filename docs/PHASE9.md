# Phase 9 — iOS Wiring

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions, and
[PHASE2.md](PHASE2.md) Section 5 for the standing iOS posture this phase closes the biggest gap
in ("keep authoring iOS in lockstep with Android, verification stays deferred -- no Mac/Xcode in
this environment").

**Last updated:** 2026-09-28
**Status:** implemented (iOS only -- authored, not yet compiled/run; needs a Mac to verify). The
Kotlin/`commonMain`/`iosMain` side this phase touches (`KoinHelper` additions) is
compile-verified for real (`./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64
:shared:compileKotlinIosX64` all green). No Android files changed this phase.

---

## 1. Overview

Every phase doc since Phase 2 has carried the same line: the iOS SwiftUI screen for that phase is
"authored but unwired." By the start of this phase, 10 real screens existed -- each one a genuine,
working `View` that calls into the shared KMP module -- but nothing constructed or presented any
of them. The app always showed the Phase 1 toolchain-proof screen. There was no login flow (no
`PhoneEntryView`/`OtpVerifyView` existed at all), no main post-login hub
(`LeagueDashboardView`/`LeagueCreationView` didn't exist either), and no navigation host of any
kind. This phase builds what was missing and wires everything together into a real, navigable
app -- still unverified (no Mac in this environment), but for the first time a complete, coherent
app rather than a pile of disconnected screens.

---

## 2. Features

- A real login flow: phone number entry, OTP verification (60s cooldown, resend limits, wrong-
  attempt bounce-back) -- iOS had never had either screen before this phase.
- A real post-login landing hub: League Dashboard (filter by State/District/City or "nearest to
  me"), League Creation (create and edit, including a real interactive MapKit ground-picker).
- Every previously-standalone screen is now actually reachable: Profile Setup, Own Profile, My
  Leagues, League Detail, Join League, Claim Franchise, Auction Settings, Live Auction, Manage
  Co-Organizers, Screenshot Viewer.
- League Detail gained three organizer actions it never had a way to reach on iOS: Auction
  Settings, Live Auction, Manage Co-Organizers -- plus per-row leave-request approve/dismiss
  buttons the underlying `ViewModel` already supported but no Swift UI ever called.
- The `crichere://leagues/{id}` deep link is a real resume target, not a parse-and-print no-op --
  works whether the app is cold-launched from the link or already running.
- Location permission is actually requested (League Dashboard's "near me", Profile Setup's "use
  my location") -- previously nothing ever called `CLLocationManager.requestWhenInUseAuthorization()`.
- A real, automatable path to an actual `.xcodeproj`: `xcodegen generate` from a checked-in
  `project.yml`, instead of manual "open Xcode, add each file to the target" steps.

---

## 3. Screens

New this phase:
- **Phone Entry** -- phone number field, "Send code".
- **OTP Verify** -- 6-digit code, cooldown countdown, resend, forced bounce-back after 5 wrong
  attempts or 3 exhausted resends.
- **League Dashboard** -- filterable league list, "near me" toggle, "Create a league".
- **League Creation** -- Basics/Location/Ground/Schedule/Format/Capacity/Fees/Awards, reused for
  create and edit.
- **Ground Map Picker** -- a component, not a standalone screen: MapKit draggable-pin picker used
  by League Creation's Ground section.

Wired this phase (already existed, now reachable): Profile Setup, Own Profile, My Leagues, League
Detail (plus its three new organizer actions and leave-request approve/dismiss), Join League,
Claim Franchise, Auction Settings, Live Auction, Manage Co-Organizers, Screenshot Viewer.

---

## 4. Decisions Made

| Decision | Rationale |
|---|---|
| **XcodeGen (`project.yml`), not a hand-authored `.xcodeproj`** | Confirmed explicitly (2026-09-28). A `.xcodeproj` is a fragile, mostly-binary project file unsafe to hand-author without Xcode to validate it -- this environment has neither. A text spec a Mac generates the real project from stays in sync automatically as Swift files are added, and is fully authorable/reviewable from here. |
| **A real MapKit ground-picker, not a search-only fallback** | Confirmed explicitly (2026-09-28), over shipping League Creation without the interactive pin-drop Android already has. Real feature parity, not a reduced-scope iOS version. |
| **`MKMapView` via `UIViewRepresentable`, not SwiftUI's native `Map`** | Programmatic draggable-annotation handling (`MKMapViewDelegate.mapView(_:annotationView:didChange:fromOldState:)`) is the more reliably documented, cross-iOS-version approach for exactly this interaction; SwiftUI's native `Map` gained comparable annotation-drag support only recently. |
| **One shared `NavigationStack` for the whole `TabView`, not one per tab** | Mirrors Android's `MainRoute` exactly: pushed destinations (League Detail, Auction Live, etc.) replace the *entire* tab scaffold, not just the active tab's content -- a single stack wrapping the whole `TabView` reproduces that; per-tab stacks would leave the tab bar visible during a push, which is standard iOS behavior generally but not what this app's Android side actually does. |
| **`ContentView.swift`/`ToolchainProofViewModelWrapper.swift` deleted, not kept as a hidden debug screen** | Their purpose (prove the shared framework links and a `ViewModel` is reachable from Swift) is superseded the moment the whole app depends on that working -- keeping a dead, unreferenced screen around had no upside. |
| **Deep link threaded as a `@Binding`, not a plain `@State`-seeded value** | A real bug caught while authoring this phase: SwiftUI `@State` only takes its `init` argument the first time a view is created, so a URL arriving after `AppRootView` already exists (app already running) would never have reached it through a plain value parameter. |

---

## 5. Open Questions and Gaps

- **Nothing in this phase has ever been compiled, linked, or run.** No Mac/Xcode exists in this
  environment. Every claim above is "authored to match the documented Android behavior and the
  real Kotlin API surface," not "verified working." First real build on a Mac will likely surface
  interop mismatches -- exactly the kind already found and fixed while authoring this phase (see
  Decisions Made's deep-link `@Binding` bug, and `OwnProfileView.swift`'s pre-existing missing
  `retry()` call, fixed as part of this phase's wiring pass).
- **Firebase iOS SDK is declared but not resolved.** `project.yml`'s pinned version is a
  placeholder -- needs checking against live Firebase iOS SDK releases before first real use.
- **No real `GoogleService-Info.plist` exists** -- same category as Android's still-missing
  `google-services.json`. `FirebaseApp.configure()` will crash at launch until a human adds one.
- **iOS push notifications remain explicitly out of scope** (Phase 8's own decision -- needs an
  APNs key from the Apple Developer Portal). Not touched this phase.
- **`Package.swift`/CocoaPods were not considered** -- SPM via XcodeGen's `packages:` block was
  the only dependency-management approach evaluated, since it needs no extra tooling beyond
  XcodeGen itself.

---

## 6. Pure Technical Things (as built)

### Kotlin

- `KoinHelper` (`shared/src/commonMain/kotlin/com/crichere/app/di/KoinInit.kt`) gained
  `appStartViewModel`, `phoneEntryViewModel`, `otpVerifyViewModel(phoneNumber:verificationId:resendToken:)`,
  `leagueDashboardViewModel`, `leagueCreationViewModel(editingLeagueId:)` -- the only Kotlin change
  this phase, mirroring `AuthNavHost.kt`'s own `koinViewModel(...)` call sites exactly.

### Swift (`mobile/iosApp/iosApp/`)

- `AppRootView.swift` -- the nav host: `AppStartViewModelWrapper`, the `AuthDestination` state
  switcher, `MainTabView` (the 3-tab shell + shared `NavigationStack` + `MainDestination` push
  enum).
- `PhoneEntryView.swift`, `OtpVerifyView.swift`, `LeagueDashboardView.swift`,
  `LeagueCreationView.swift`, `GroundMapPickerView.swift` -- new screens/component, each following
  the established `View` + `@MainActor final class XViewModelWrapper: ObservableObject` pattern
  (`OwnProfileViewModelWrapper` is the reference every wrapper in this repo follows).
- `LocationPermission.swift` (new) -- `LocationPermissionRequester`, an async wrapper around
  `CLLocationManager.requestWhenInUseAuthorization()`, shared by League Dashboard's "near me" and
  Profile Setup's "use my location" (`DeviceLocationProvider.ios.kt` deliberately only reads
  *existing* authorization and never prompts -- see that file's own doc comment -- so the UI layer
  has to trigger the system dialog itself).
- `LeagueDetailView.swift` -- gained `onAuctionSettings`/`onAuctionLive`/`onManageRoles` callback
  params and buttons, plus per-row leave-request approve/dismiss buttons calling
  `LeagueDetailViewModelWrapper` methods that already existed but nothing in the view ever called.
- `OwnProfileView.swift` -- added the missing `wrapper.retry()` call in `.onAppear` (every other
  wrapper in this repo already did this; this one silently never loaded).
- `iosAppApp.swift` -- root swapped from `ContentView()` to `AppRootView`; `.onOpenURL` now feeds a
  real `@State` the new nav host binds to, instead of printing and discarding the parsed league id.
- `Info.plist` -- added `NSLocationWhenInUseUsageDescription`.
- `ContentView.swift`, `ToolchainProofViewModelWrapper.swift` -- deleted (see Decisions Made).

### Tooling

- `mobile/iosApp/project.yml` (new) -- XcodeGen spec: target `iosApp`, iOS 16.0 deployment target,
  bundle id `com.crichere.app`, Firebase iOS SDK via SPM (`FirebaseAuth`/`FirebaseCore`), a
  pre-build Run Script phase invoking `../gradlew :shared:embedAndSignAppleFrameworkForXcode`.
- `mobile/iosApp/README.md` -- rewritten setup instructions around `xcodegen generate`.

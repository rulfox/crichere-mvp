# iosApp

Swift sources plus an XcodeGen spec (`project.yml`). No `.xcodeproj` is checked in (it's
gitignored); `xcodegen generate` produces it, and `project.yml` also generates `Info.plist`
(`info.properties`: fonts, URL scheme, portrait lock, location string), so regenerating is safe.

The app builds on a Mac with Xcode and runs on the simulator. Every screen is drawn to match the
Android app exactly (docs/IOS-REDESIGN-PLAN.md, docs/PHASE21.md): the design system is in
`iosApp/Design/`, and Android's composables are the source of truth for sizes, colours and copy.

## Everyday commands

From this directory:

- `scripts/sim.sh build`: regenerate the project and build for the iPhone 17 Pro simulator
  (`DEVICE="iPhone 16e" scripts/sim.sh …` for another one).
- `scripts/sim.sh run [launch args]`: install and launch.
- `scripts/sim.sh parity <board>…`: launch the DEBUG parity gallery on each design board
  (`-parity G3`, `-parity L16-lost`, …) and save a half-resolution capture to
  `docs/ios-parity/ios/<board>.png`. Boards are defined in `iosApp/Design/Debug/ParityGallery.swift`
  from fixtures, so no sign-in is needed. `-gallery 1|2|3` shows the component gallery.

## To stand this up on a Mac

1. Install XcodeGen if you don't have it: `brew install xcodegen`.
2. Open `project.yml` and set the placeholder Firebase iOS SDK version to whatever's actually
   current (check https://github.com/firebase/firebase-ios-sdk/releases -- don't just trust the
   placeholder committed here, it was picked without access to live sources).
3. From this directory: `xcodegen generate`. This produces `iosApp.xcodeproj`, wired to:
   - The Firebase iOS SDK via Swift Package Manager (`FirebaseAuth`, `FirebaseCore`).
   - A pre-build Run Script phase invoking `../gradlew :shared:embedAndSignAppleFrameworkForXcode`
     (the standard KMP Gradle task), linking `shared/build.gradle.kts`'s exported `Shared.framework`
     -- SKIE's Swift-friendly API surface for it comes along automatically, no extra config needed.
4. Open `iosApp.xcodeproj`, set your own Apple Developer Team in Signing & Capabilities
   (`project.yml`'s `DEVELOPMENT_TEAM` is deliberately left blank -- not something to fabricate
   here).
5. Add a real `GoogleService-Info.plist` to the project (see "Firebase Phone Auth" below -- still
   missing, still external, still not something this environment can produce).
6. Build and run on an iOS Simulator. The app talks to the production backend
   (`https://api.crichere.com`, `ApiConfig.kt`). Plain-http local backends are blocked by App
   Transport Security; there is no exception in `Info.plist` (docs/SECURITY-AUDIT.md, mobile).

Re-run `xcodegen generate` (step 3) any time `project.yml` or the set of Swift files under
`iosApp/iosApp/` changes.

## Firebase Phone Auth

`FirebasePhoneAuthBridge.swift` calls the real Firebase iOS Auth SDK, but this needs two things
neither this environment nor this repo has:

1. **The Firebase iOS SDK itself** -- `project.yml` already declares this via Swift Package
   Manager (`FirebaseAuth` product), resolved automatically the first time `xcodegen generate`'s
   output is opened in Xcode.
2. **A real `GoogleService-Info.plist`**, downloaded from the real Firebase project's console and
   added to the Xcode project's target -- the iOS equivalent of Android's `google-services.json`
   (also not present in this environment; see `androidApp/build.gradle.kts`).

Until that file exists, sign-in can't send a code. Never commit a stub `GoogleService-Info.plist`
(it's gitignored).

## Navigation (Phase 9, see docs/PHASE9.md)

`AppRootView.swift` is the real navigation host -- a direct SwiftUI port of
`androidApp/.../ui/AuthNavHost.kt`'s state machine (Starting/PhoneEntry/OtpVerify/ProfileSetup/Main,
same transitions), replacing `iosAppApp.swift`'s previous hardcoded single screen. `MainTabView`
(same file) is the 3-tab bottom-nav shell (Dashboard/My Leagues/My Profile) plus the pushed
destinations (League Detail/Creation/Join/Claim/Screenshot Viewer/Auction Settings/Live/Manage
Co-Organizers) via one shared `NavigationStack` wrapping the whole tab shell (a custom bottom bar matching Android's `MainBottomBar`) -- see that file's own
doc comment for why a single shared stack, not one per tab, is the faithful port of Android's
"pushed destinations replace the entire tab scaffold" behavior.

Every screen this repo has ever authored (`ProfileSetupView`, `OwnProfileView`, `MyLeaguesView`,
`LeagueDetailView`, `JoinLeagueView`, `ClaimFranchiseView`, `AuctionSettingsView`, `AuctionLiveView`,
`ManageRolesView`, `ScreenshotViewerView`) is now actually reachable, plus the two screens that
never existed before Phase 9 (`PhoneEntryView`, `OtpVerifyView`) and the main post-login hub
(`LeagueDashboardView`, `LeagueCreationView` with a real MapKit ground-picker,
`GroundMapPickerView.swift`).

Every pushed screen hides the system navigation bar and draws its own top bar, as on Android;
edge-swipe back still works (`Design/Navigation.swift`), and is turned off only while a form has
unsaved edits (its close button asks "Discard changes?" instead).

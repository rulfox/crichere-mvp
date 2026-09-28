# iosApp

Swift sources plus an XcodeGen spec (`project.yml`) -- no `.xcodeproj` is checked in (it's a
fragile, mostly-binary-shaped project file this environment, with no Mac/Xcode, cannot safely
hand-author or validate). Per Phase 9 (docs/PHASE9.md), project-file generation is automatable
text instead of a manual "open Xcode, add each file to the target" chore: `xcodegen generate`
produces a real `.xcodeproj` from `project.yml`, and re-running it after adding/removing Swift
files keeps the project in sync with no Xcode-side project surgery. The Kotlin/`commonMain`/
`iosMain` side is **compile-verified for real** (`./gradlew :shared:compileKotlinIosArm64
:shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosX64` all succeed, using the
Kotlin/Native cross-compiler's Windows-hosted klib toolchain). What's still unverified is every
Swift file and the final framework link/run -- both need Xcode's Apple SDKs.

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
6. Build and run on an iOS Simulator. The backend must be reachable at `http://localhost:8080`
   from the simulator (see `ApiConfig.ios.kt` -- the simulator shares the host Mac's network
   namespace directly, unlike Android's `10.0.2.2` alias).

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

Until that file exists, `FirebaseApp.configure()` in `iosAppApp.swift` will crash at launch (as it
should -- there is no real Firebase project connected client-side in this environment, same as
the Android side). `FirebasePhoneAuthBridgeImpl` has never been compiled here.

## Navigation (Phase 9, see docs/PHASE9.md)

`AppRootView.swift` is the real navigation host -- a direct SwiftUI port of
`androidApp/.../ui/AuthNavHost.kt`'s state machine (Starting/PhoneEntry/OtpVerify/ProfileSetup/Main,
same transitions), replacing `iosAppApp.swift`'s previous hardcoded single screen. `MainTabView`
(same file) is the 3-tab bottom-nav shell (Dashboard/My Leagues/My Profile) plus the pushed
destinations (League Detail/Creation/Join/Claim/Screenshot Viewer/Auction Settings/Live/Manage
Co-Organizers) via one shared `NavigationStack` wrapping the whole `TabView` -- see that file's own
doc comment for why a single shared stack, not one per tab, is the faithful port of Android's
"pushed destinations replace the entire tab scaffold" behavior.

Every screen this repo has ever authored (`ProfileSetupView`, `OwnProfileView`, `MyLeaguesView`,
`LeagueDetailView`, `JoinLeagueView`, `ClaimFranchiseView`, `AuctionSettingsView`, `AuctionLiveView`,
`ManageRolesView`, `ScreenshotViewerView`) is now actually reachable, plus the two screens that
never existed before Phase 9 (`PhoneEntryView`, `OtpVerifyView`) and the main post-login hub
(`LeagueDashboardView`, `LeagueCreationView` with a real MapKit ground-picker,
`GroundMapPickerView.swift`).

Like every other Swift/`iosMain`-`actual` file in this repo, **none of this has been compiled** --
no Mac/Xcode available here. What **is** compile-verified for real: every shared `ViewModel` this
navigation host and these screens depend on builds successfully for all three iOS klib targets
(`:shared:compileKotlinIosArm64`/`compileKotlinIosSimulatorArm64`/`compileKotlinIosX64`).

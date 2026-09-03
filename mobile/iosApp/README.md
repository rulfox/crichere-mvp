# iosApp

Swift sources only -- no `.xcodeproj` is checked in. This environment has no Mac/Xcode, and a
`.xcodeproj` is a fragile, mostly-binary-shaped project file that isn't safely hand-authored
without a way to open/validate it in Xcode. Per Task 5's brief, the Kotlin/`commonMain`/`iosMain`
side was built and **compile-verified for real** (`./gradlew :shared:compileKotlinIosArm64
:shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosX64` all succeed, using the
Kotlin/Native cross-compiler's Windows-hosted klib toolchain) -- see `task-5-report.md` for
details. What's still unverified is the Swift side and the final framework link/run, both of
which need Xcode's Apple SDKs.

## To stand this up on a Mac

1. Open Xcode, create a new iOS App project named `iosApp` inside this `iosApp/` directory
   (SwiftUI lifecycle, bundle identifier `com.crichere.app` -- matches `shared/build.gradle.kts`'s
   Android namespace and `androidApp`'s `applicationId`, per this task's "namespace everywhere"
   requirement).
2. Replace the generated `ContentView.swift`/`<AppName>App.swift`/`Info.plist` with the ones
   already in this directory (`ContentView.swift`, `iosAppApp.swift`,
   `ToolchainProofViewModelWrapper.swift`, `Info.plist`).
3. Link the `shared` module's exported framework. Two supported approaches:
   - **Direct framework embed**: add a Run Script build phase that invokes
     `../gradlew :shared:embedAndSignAppleFrameworkForXcode` (the standard KMP Gradle task for
     this), matching the `binaries.framework { baseName = "Shared" }` export configured in
     `shared/build.gradle.kts`.
   - **CocoaPods**, if preferred later -- not set up here; the direct-embed script phase above is
     simpler for a single-module setup like this one.
4. SKIE (applied to `shared` in `shared/build.gradle.kts`) generates the Swift-friendly API
   surface for the `Shared` framework automatically as part of that build -- no extra Xcode-side
   configuration needed beyond the Run Script phase in step 3.
5. Build and run on an iOS Simulator. The backend must be reachable at `http://localhost:8080`
   from the simulator (see `ApiConfig.ios.kt` -- the simulator shares the host Mac's network
   namespace, so this is direct, unlike Android's `10.0.2.2` alias).

## Firebase Phone Auth (Task 6)

`FirebasePhoneAuthBridge.swift` calls the real Firebase iOS Auth SDK, but this needs two things
neither this environment nor this repo has:

1. **The Firebase iOS SDK itself**, added via Swift Package Manager (`https://github.com/firebase/firebase-ios-sdk`,
   `FirebaseAuth` product) or CocoaPods (`pod 'FirebaseAuth'`) once the `.xcodeproj` from step 1
   above exists.
2. **A real `GoogleService-Info.plist`**, downloaded from the real Firebase project's console and
   added to the Xcode project's target -- the iOS equivalent of Android's `google-services.json`
   (also not present in this environment; see `androidApp/build.gradle.kts`).

Until both exist, `FirebaseApp.configure()` in `iosAppApp.swift` will crash at launch (as it
should -- there is no real Firebase project connected client-side in this environment, same as
the Android side). `FirebasePhoneAuthBridgeImpl` has never been compiled here.

import SwiftUI
import FirebaseCore
import Shared

/// App entry point. `initKoinIos()` (`shared/src/commonMain/kotlin/com/crichere/app/di/KoinInit.kt`)
/// is the Swift-callable wrapper around Koin's `startKoin { }` DSL -- Swift can't invoke that
/// Kotlin trailing-lambda builder directly, and iOS has no `Context` to register the way
/// Android's `CricherApplication.onCreate()` does, so this call needs nothing further.
///
/// `FirebaseApp.configure()` and installing `IosPhoneAuthBridgeHolder.bridge` are Task 6's
/// additions -- real Firebase iOS Auth calls need a real `GoogleService-Info.plist` (this
/// environment has none, same category as Android's missing `google-services.json`) and a real
/// Xcode/CocoaPods/SPM setup this environment cannot run, so none of this has ever been compiled;
/// see `FirebasePhoneAuthBridge.swift` and task-6-report.md.
@main
struct IosAppApp: App {
    init() {
        FirebaseApp.configure()
        IosPhoneAuthBridgeHolder.shared.bridge = FirebasePhoneAuthBridgeImpl()
        initKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    // Phase 3 deep link (see docs/PHASE3.md): crichere://leagues/{id}. This is a
                    // parse-and-stash no-op stub, not a functioning resume flow -- there is no iOS
                    // nav shell to resume into yet (see docs/PHASE2.md Section 5's deferred iOS
                    // pass; Android's equivalent wiring is AuthNavHost.kt's pendingDeepLinkLeagueId).
                    guard url.scheme == "crichere", url.host == "leagues" else { return }
                    let leagueId = url.pathComponents.dropFirst().first
                    print("Deep link received for league id: \(leagueId ?? "unknown") -- no-op until the iOS nav shell exists")
                }
        }
    }
}

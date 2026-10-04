import SwiftUI
import FirebaseCore
import Shared

/// App entry point. `initKoinIos()` (`shared/src/commonMain/kotlin/com/crichere/app/di/KoinInit.kt`)
/// is the Swift-callable wrapper around Koin's `startKoin { }` DSL -- Swift can't invoke that
/// Kotlin trailing-lambda builder directly, and iOS has no `Context` to register the way
/// Android's `CricherApplication.onCreate()` does, so this call needs nothing further.
///
/// `FirebaseApp.configure()` and installing `IosPhoneAuthBridgeHolder.bridge` need a real
/// `GoogleService-Info.plist` (this environment has none, same category as Android's missing
/// `google-services.json`) and a real Xcode/SPM setup this environment cannot run, so none of
/// this has ever been compiled; see `FirebasePhoneAuthBridge.swift` and `iosApp/README.md`.
///
/// Phase 9 (iOS wiring, see docs/PHASE9.md) replaced the Phase-1 toolchain-proof `ContentView()`
/// root with the real `AppRootView` nav host, and made the deep link a real resume target instead
/// of a parse-and-print no-op -- mirrors `MainActivity`'s intent-read -> `AuthNavHost(pendingDeepLinkLeagueId:)`
/// handoff on Android.
@main
struct IosAppApp: App {
    @State private var pendingDeepLinkLeagueId: String?

    init() {
        FirebaseApp.configure()
        IosPhoneAuthBridgeHolder.shared.bridge = FirebasePhoneAuthBridgeImpl()
        initKoinIos()
        applyCrichereAppearance()
    }

    var body: some Scene {
        WindowGroup {
            AppRootView(pendingDeepLinkLeagueId: $pendingDeepLinkLeagueId)
                .tint(Brand.primary)
                .onOpenURL { url in
                    // crichere://leagues/{id} -- see docs/PHASE3.md. A URL arriving while the app
                    // is already running updates this same @State, which AppRootView re-reads via
                    // its own `pendingDeepLinkLeagueId` init param on the next relevant recomposition
                    // -- same category of "resume into the league once Main is reached" behavior
                    // AuthNavHost.kt's own doc describes, not a full nav-stack re-entry.
                    guard url.scheme == "crichere", url.host == "leagues" else { return }
                    pendingDeepLinkLeagueId = url.pathComponents.dropFirst().first
                }
        }
    }
}

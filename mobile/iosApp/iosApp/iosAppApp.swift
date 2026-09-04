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
        }
    }
}

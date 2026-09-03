import SwiftUI
import Shared

/// App entry point. `initKoinIos()` (`shared/src/commonMain/kotlin/com/crichere/app/di/KoinInit.kt`)
/// is the Swift-callable wrapper around Koin's `startKoin { }` DSL -- Swift can't invoke that
/// Kotlin trailing-lambda builder directly, and iOS has no `Context` to register the way
/// Android's `CricherApplication.onCreate()` does, so this call needs nothing further.
@main
struct IosAppApp: App {
    init() {
        initKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

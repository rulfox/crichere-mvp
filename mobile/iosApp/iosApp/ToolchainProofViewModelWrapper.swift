import Foundation
import Shared

/// Mirrors `ReferenceViewModel`'s shared `StateFlow<ToolchainProofState>` into a `@Published var`
/// SwiftUI can bind to -- SwiftUI's binding model doesn't understand `StateFlow` directly, per
/// ARCHITECTURE.md's documented pattern. `for await` over `viewModel.state` relies on SKIE's
/// `Flow` -> `AsyncSequence` bridging (configured in `shared/build.gradle.kts`); without SKIE,
/// this loop wouldn't compile against the raw Kotlin/Native-exported `Flow` type.
///
/// Pulls its `ReferenceViewModel` instance from Koin via `KoinHelper` (`KoinInit.kt`) rather than
/// Koin's Kotlin DSL directly, since `KoinComponent`/reified `get<T>()` don't bridge to Swift.
@MainActor
final class ToolchainProofViewModelWrapper: ObservableObject {
    @Published var state: ToolchainProofState

    private let viewModel: ReferenceViewModel

    init() {
        let viewModel = KoinHelper().referenceViewModel
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func retry() {
        viewModel.loadStates()
    }
}

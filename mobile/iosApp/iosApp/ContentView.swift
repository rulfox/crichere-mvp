import SwiftUI
import Shared

/// iOS equivalent of `androidApp/.../ui/ToolchainProofScreen.kt` -- on load, fires a real GET
/// against the locally running backend's `/api/v1/reference/states` through the same shared
/// Ktor client (Darwin engine here, OkHttp on Android) and Koin DI wiring Android uses, proving
/// the toolchain end to end on this platform too. Per this task's ruling,
/// `/api/v1/reference/states` stands in for a health-check endpoint (none exists on the backend).
struct ContentView: View {
    @StateObject private var viewModelWrapper = ToolchainProofViewModelWrapper()

    var body: some View {
        VStack(spacing: 12) {
            Text("Crichere mobile toolchain proof")
                .font(.headline)
            Text("GET /api/v1/reference/states")
                .font(.subheadline)
                .foregroundColor(.secondary)

            if viewModelWrapper.state.isLoading {
                ProgressView()
                    .padding(24)
            } else if let error = viewModelWrapper.state.error {
                Text("Request failed: \(error)")
                    .multilineTextAlignment(.center)
                Button("Retry") {
                    viewModelWrapper.retry()
                }
            } else {
                Text("Loaded \(viewModelWrapper.state.states.count) states from the backend:")
                List(viewModelWrapper.state.states, id: \.code) { stateDto in
                    Text("\(stateDto.code) — \(stateDto.name)")
                }
                .listStyle(.plain)
            }
        }
        .padding()
    }
}

#Preview {
    ContentView()
}

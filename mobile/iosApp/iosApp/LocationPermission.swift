import CoreLocation

/// Thin async wrapper around `CLLocationManager`'s authorization request. `DeviceLocationProvider.ios.kt`
/// deliberately only reads *existing* authorization and never prompts (see that file's own doc
/// comment) -- so the UI layer is what has to trigger the system permission dialog before calling
/// into the shared `LocationProvider`-backed ViewModel methods. Used by both League Dashboard's
/// "near me" toggle and Profile Setup's "use my location" button.
@MainActor
final class LocationPermissionRequester: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<Void, Never>?

    /// Resumes once the user has answered the system prompt, or immediately if authorization is
    /// already determined (granted, denied, or restricted) -- a second tap never re-prompts, same
    /// as the OS itself guarantees.
    func requestWhenInUseAuthorization() async {
        guard manager.authorizationStatus == .notDetermined else { return }
        await withCheckedContinuation { continuation in
            self.continuation = continuation
            manager.delegate = self
            manager.requestWhenInUseAuthorization()
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            self.continuation?.resume()
            self.continuation = nil
        }
    }
}

import SwiftUI
import MapKit

/// New-ground registration's map-pin picker -- MapKit equivalent of
/// `androidApp/.../ui/GroundMapPicker.kt`. A fixed pin sits at the map's centre and the user moves
/// the map under it (owner decision 2026-10-02, replacing the draggable pin); the location is the
/// map's centre each time it comes to rest after a user gesture. Uses `MKMapView` via
/// `UIViewRepresentable` for `regionDidChangeAnimated`.
///
/// [initialLatitude]/[initialLongitude] seed the camera; once seeded or moved by the user, every
/// stop calls [onPositionChanged] so `LeagueCreationViewModel.onNewGroundPositionChanged` always
/// reflects exactly where the pin points. The starting India-centroid camera is never reported --
/// it isn't something the user chose. Place search is Android-only for now (docs/PHASE2.md).
///
/// **Authored but unverified** -- see docs/PHASE9.md.
struct GroundMapPickerView: View {
    let initialLatitude: Double?
    let initialLongitude: Double?
    let onPositionChanged: (_ latitude: Double, _ longitude: Double) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Move the map to put the pin on the ground")
                .font(.caption)
            ZStack {
                MapKitPinPicker(
                    initialLatitude: initialLatitude,
                    initialLongitude: initialLongitude,
                    onPositionChanged: onPositionChanged
                )
                // Tip of the pin on the map's centre.
                Image(systemName: "mappin")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.green)
                    .offset(y: -18)
                    .allowsHitTesting(false)
            }
            .frame(height: 240)
        }
    }
}

private let indiaCentroid = CLLocationCoordinate2D(latitude: 20.5937, longitude: 78.9629)
/// Whole-country zoom -- India's real extent is roughly 8-37 deg latitude, 68-97 deg longitude.
private let countryLevelSpan = MKCoordinateSpan(latitudeDelta: 30, longitudeDelta: 30)
/// Street-level zoom, once a real position is seeded.
private let streetLevelSpan = MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01)

private struct MapKitPinPicker: UIViewRepresentable {
    let initialLatitude: Double?
    let initialLongitude: Double?
    let onPositionChanged: (Double, Double) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onPositionChanged: onPositionChanged)
    }

    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        let seeded = seededCoordinate
        context.coordinator.placed = seeded != nil
        mapView.setRegion(
            MKCoordinateRegion(center: seeded ?? indiaCentroid, span: seeded != nil ? streetLevelSpan : countryLevelSpan),
            animated: false
        )
        return mapView
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        // A seed that arrives after the view is up moves the camera once; never again, so it
        // can't keep overwriting where the user has since moved the map.
        guard let seeded = seededCoordinate, !context.coordinator.placed else { return }
        context.coordinator.placed = true
        mapView.setRegion(MKCoordinateRegion(center: seeded, span: streetLevelSpan), animated: true)
    }

    private var seededCoordinate: CLLocationCoordinate2D? {
        guard let lat = initialLatitude, let lon = initialLongitude else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
        let onPositionChanged: (Double, Double) -> Void
        /// True once the centre is a real choice -- seeded, or moved by the user's own gesture.
        var placed = false

        init(onPositionChanged: @escaping (Double, Double) -> Void) {
            self.onPositionChanged = onPositionChanged
        }

        func mapView(_ mapView: MKMapView, regionWillChangeAnimated animated: Bool) {
            // A pan/pinch has a gesture recognizer in a began/changed state on the map's subview.
            let gestures = mapView.subviews.first?.gestureRecognizers ?? []
            if gestures.contains(where: { $0.state == .began || $0.state == .changed }) {
                placed = true
            }
        }

        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            guard placed else { return }
            onPositionChanged(mapView.centerCoordinate.latitude, mapView.centerCoordinate.longitude)
        }
    }
}

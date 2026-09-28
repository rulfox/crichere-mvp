import SwiftUI
import MapKit

/// New-ground registration's map-pin picker -- MapKit equivalent of
/// `androidApp/.../ui/GroundMapPicker.kt` (`GoogleMap`/draggable `Marker`). Uses `MKMapView` via
/// `UIViewRepresentable` rather than SwiftUI's native `Map`, since programmatic draggable-pin
/// handling (`MKMapViewDelegate.mapView(_:annotationView:didChange:fromOldState:)`) is the more
/// reliably documented cross-iOS-version approach; confirm against Apple's current MapKit docs at
/// build time, same discipline this repo applies to every other dependency choice.
///
/// [initialLatitude]/[initialLongitude] seed the pin (e.g. from a "use my location" fix resolved
/// by the caller before this view is shown); every drag calls [onPositionChanged] with the pin's
/// new coordinates so `LeagueCreationViewModel.onNewGroundPositionChanged` always reflects exactly
/// where it sits. Matches the Kotlin file's own documented rule: the very first position is only
/// reported if it was actually seeded -- otherwise that first callback would just be the
/// placeholder India-centroid default, not something the user chose.
///
/// **Authored but unverified** -- see docs/PHASE9.md.
struct GroundMapPickerView: View {
    let initialLatitude: Double?
    let initialLongitude: Double?
    let onPositionChanged: (_ latitude: Double, _ longitude: Double) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Drag the pin to the ground's exact location")
                .font(.caption)
            MapKitPinPicker(
                initialLatitude: initialLatitude,
                initialLongitude: initialLongitude,
                onPositionChanged: onPositionChanged
            )
            .frame(height: 240)
        }
    }
}

private let indiaCentroid = CLLocationCoordinate2D(latitude: 20.5937, longitude: 78.9629)
/// Whole-country zoom -- India's real extent is roughly 8-37 deg latitude, 68-97 deg longitude.
private let countryLevelSpan = MKCoordinateSpan(latitudeDelta: 30, longitudeDelta: 30)
/// Street-level zoom, once a real position is seeded or the pin is dropped.
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
        let annotation = MKPointAnnotation()
        annotation.coordinate = seeded ?? indiaCentroid
        mapView.addAnnotation(annotation)
        context.coordinator.annotation = annotation
        context.coordinator.hasSeed = seeded != nil

        mapView.setRegion(
            MKCoordinateRegion(center: seeded ?? indiaCentroid, span: seeded != nil ? streetLevelSpan : countryLevelSpan),
            animated: false
        )
        return mapView
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        // A GPS fix that arrives after this view is already up (e.g. "use my location" resolves
        // mid-registration) should move the existing pin/camera -- but only once, the first time a
        // real seed shows up; a user's own subsequent drag must not keep getting overwritten by a
        // stale `initialLatitude`/`initialLongitude` on every SwiftUI re-render.
        guard let seeded = seededCoordinate, !context.coordinator.hasSeed else { return }
        context.coordinator.hasSeed = true
        context.coordinator.annotation?.coordinate = seeded
        mapView.setRegion(MKCoordinateRegion(center: seeded, span: streetLevelSpan), animated: true)
    }

    private var seededCoordinate: CLLocationCoordinate2D? {
        guard let lat = initialLatitude, let lon = initialLongitude else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
        let onPositionChanged: (Double, Double) -> Void
        weak var annotation: MKPointAnnotation?
        /// True once a real (non-placeholder) position exists -- either seeded externally or
        /// reported at least once via a drag. Mirrors `GroundMapPicker.kt`'s `hasEmittedInitialPosition`.
        var hasSeed = false

        init(onPositionChanged: @escaping (Double, Double) -> Void) {
            self.onPositionChanged = onPositionChanged
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            guard annotation is MKPointAnnotation else { return nil }
            let identifier = "ground-pin"
            let view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) as? MKMarkerAnnotationView
                ?? MKMarkerAnnotationView(annotation: annotation, reuseIdentifier: identifier)
            view.annotation = annotation
            view.isDraggable = true
            view.canShowCallout = false
            return view
        }

        func mapView(_ mapView: MKMapView, annotationView view: MKAnnotationView, didChange newState: MKAnnotationView.DragState, fromOldState oldState: MKAnnotationView.DragState) {
            guard newState == .ending, let coordinate = view.annotation?.coordinate else { return }
            view.dragState = .none
            hasSeed = true
            onPositionChanged(coordinate.latitude, coordinate.longitude)
        }
    }
}

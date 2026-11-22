import SwiftUI
import MapKit
import Shared

/// What the ground-registration screen does (the LeagueCreationViewModel calls).
struct GroundRegisterActions {
    var onNameChanged: (String) -> Void = { _ in }
    var onPositionChanged: (Double, Double) -> Void = { _, _ in }
    var onRegister: () -> Void = {}
    var onCancel: () -> Void = {}
}

/// Design I5/I11 (androidApp `ui/GroundMapPicker.kt` GroundRegisterOverlay): registering a new ground,
/// full screen. The map fills the screen under a fixed centre pin -- the user moves the map, not the
/// pin (owner decision 2026-10-02). A search box on top jumps the map to a typed place (MapKit's
/// completer, so it searches as you type); a sheet at the bottom takes the ground's name, shows the
/// pin's coordinates and any error, with Cancel / Register ground. While the name is being typed the
/// sheet shrinks to a bar (field + Register) and the search box steps aside (U4 C1).
///
/// The location is reported each time the map comes to rest, but only once the user has placed it (a
/// gesture, a search pick, or an existing seed) -- the starting camera is not a location anyone chose.
/// Without the league's State and District (I16) the sheet says so, the name field is disabled and
/// Register ground can't be tapped.
struct GroundRegisterOverlay: View {
    let state: LeagueCreationState
    var actions = GroundRegisterActions()

    @StateObject private var search = PlaceSearch()
    @StateObject private var camera = MapCameraControl()
    @FocusState private var nameFocused: Bool
    @State private var query = ""
    @State private var mapTop: CGFloat = 0
    @State private var sheetHeight: CGFloat = 0

    private var compact: Bool { nameFocused }
    private var locationMissing: Bool { !state.canRegisterGround }
    private var areaName: String? {
        let parts = [state.district, state.state].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: ", ")
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let top = compact ? insets.top : mapTop
            let pinY = top + (proxy.size.height + insets.top - top - sheetHeight) / 2
            ZStack(alignment: .top) {
                CentredMap(control: camera, seed: seed, pinY: pinY, onRest: actions.onPositionChanged)
                    .ignoresSafeArea()
                    .onTapGesture { nameFocused = false }
                CentrePin(lifted: camera.moving)
                    .position(x: proxy.size.width / 2, y: pinY - insets.top)
                    .allowsHitTesting(false)
                if !compact {
                    searchArea
                        .padding(.horizontal, 13)
                        .padding(.top, 1)
                        .transition(.opacity)
                }
                sheet
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }
            .animation(.easeOut(duration: 0.15), value: compact)
        }
        .background(CrichereColor.background.ignoresSafeArea())
        .task {
            search.region = nil
            guard seed == nil, let areaName else { return }
            if let point = await geocode(areaName) {
                search.areaCentre = point
                camera.flyToDistrict(point)
            }
        }
    }

    private var seed: CLLocationCoordinate2D? {
        guard let lat = state.newGroundLatitude?.doubleValue, let lng = state.newGroundLongitude?.doubleValue else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    // MARK: Search

    private var searchArea: some View {
        VStack(alignment: .leading, spacing: 8) {
            MapSearchBar(query: $query, isSearching: search.isSearching,
                         onQueryChange: { search.update($0, area: areaName) },
                         onSearch: { search.update(query, area: areaName, now: true) },
                         onClear: { query = ""; search.clear() })
                .background(GeometryReader { bar in
                    Color.clear
                        .onAppear { mapTop = bar.frame(in: .global).maxY }
                        .onChange(of: bar.frame(in: .global).maxY) { _, y in mapTop = y }
                })
            if !search.results.isEmpty {
                SearchResults(results: search.results, onPick: pick)
            } else if search.noMatch {
                SearchMessage(message: "No match. Try a nearby landmark, or move the map by hand.")
            } else {
                MoveMapHint()
            }
        }
    }

    private func pick(_ result: MKLocalSearchCompletion) {
        nameFocused = false
        query = result.title
        Task {
            guard let point = await search.locate(result) else { return }
            camera.flyToGround(point)
        }
    }

    // MARK: Sheet

    private var sheet: some View {
        let radius: CGFloat = compact ? 20 : 24
        return VStack(alignment: .leading, spacing: 0) {
            // One field in both layouts, so focus and the keyboard survive the switch.
            HStack(alignment: .bottom, spacing: 8) {
                CrichereTextField(
                    text: Binding(get: { state.newGroundName }, set: actions.onNameChanged),
                    label: "New ground name",
                    look: compact ? FieldVariant(height: 48, idleFill: .white) : .form,
                    error: state.newGroundNameError ? "Enter the ground name" : nil,
                    enabled: !locationMissing,
                    capitalization: .words,
                    focus: $nameFocused
                )
                if compact { compactRegister.transition(.opacity) }
            }
            if !compact {
                fullControls.transition(.opacity)
            }
        }
        .padding(.horizontal, compact ? 12 : 19)
        .padding(.top, compact ? 3 : 11)
        .padding(.bottom, compact ? 10 : 26)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: radius, topTrailingRadius: radius, style: .continuous)
                .fill(CrichereColor.background)
                .shadow(color: .black.opacity(compact ? 0.08 : 0), radius: 14)
                .ignoresSafeArea(.container, edges: .bottom)
        )
        .background(GeometryReader { sheet in
            Color.clear
                .onAppear { sheetHeight = sheet.size.height }
                .onChange(of: sheet.size.height) { _, height in sheetHeight = height }
        })
    }

    private var compactRegister: some View {
        let canRegister = !state.newGroundName.trimmingCharacters(in: .whitespaces).isEmpty && !state.isRegisteringGround && !locationMissing
        return Button(action: actions.onRegister) {
            Text("Register").crichere(.sans(13.5, .semiBold)).padding(.horizontal, 18).frame(height: 48)
        }
        .buttonStyle(PillButtonStyle(fill: canRegister ? CrichereColor.primary : compactDisabled, pressedFill: CrichereColor.greenPressed,
                                     content: canRegister ? .white : compactDisabledText, radius: 24))
        .disabled(!canRegister)
    }

    @ViewBuilder private var fullControls: some View {
        VStack(alignment: .leading, spacing: 0) {
            if !locationMissing {
                Spacer().frame(height: 12)
                Text(coordinates).crichere(.mono(12, .medium, lineHeight: 12), CrichereColor.onSurfaceVariant)
            }
            let errorTitle = locationMissing ? LeagueCreationViewModel.Companion.shared.GROUND_LOCATION_MISSING_MESSAGE : state.groundErrorTitle
            if let errorTitle {
                Spacer().frame(height: 12)
                GroundErrorBanner(title: errorTitle, message: locationMissing ? nil : state.groundErrorMessage)
            }
            Spacer().frame(height: 12)
            HStack(spacing: 8) {
                Button(action: actions.onCancel) {
                    Text("Cancel").crichere(.sans(13.5, .semiBold)).frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
                }
                .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.primary.opacity(0.08), content: CrichereColor.primary,
                                             border: CrichereColor.outline, radius: 22))
                Button(action: actions.onRegister) {
                    HStack(spacing: 8) {
                        if state.isRegisteringGround { CrichereSpinner(color: .white, lineWidth: 2, size: 16) }
                        Text(state.isRegisteringGround ? "Registering…" : "Register ground").crichere(.sans(13.5, .semiBold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
                }
                .buttonStyle(PillButtonStyle(fill: locationMissing ? compactDisabled : CrichereColor.primary, pressedFill: CrichereColor.greenPressed,
                                             content: locationMissing ? compactDisabledText : .white, radius: 22))
                .disabled(state.isRegisteringGround || locationMissing)
            }
        }
    }

    private var coordinates: String {
        guard let lat = state.newGroundLatitude?.doubleValue, let lng = state.newGroundLongitude?.doubleValue else { return "Pin not placed yet" }
        return String(format: "%.4f, %.4f", lat, lng)
    }

    private func geocode(_ name: String) async -> CLLocationCoordinate2D? {
        let placemarks = try? await CLGeocoder().geocodeAddressString(name)
        return placemarks?.first?.location?.coordinate
    }
}

private let compactDisabled = Color(hex: 0xDCE0D7)
private let compactDisabledText = Color(hex: 0x8A948C)
private let cardShadow = Color.black.opacity(0.12)

private struct GroundErrorBanner: View {
    let title: String
    let message: String?

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            CrIcon("ic_error", size: 20, tint: CrichereColor.errorStrong)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).crichere(.sans(13, .semiBold, lineHeight: 17.55), CrichereColor.errorStrong)
                if let message { Text(message).crichere(.sans(12, lineHeight: 16.8), CrichereColor.errorStrong) }
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(CrichereColor.errorContainer, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

/// The green pin, its tip on the given point; it lifts while the map moves.
private struct CentrePin: View {
    let lifted: Bool

    var body: some View {
        ZStack {
            Ellipse().fill(Color.black.opacity(0.25)).frame(width: 14, height: 5)
            // ic_location_on's tip is at 22/24 of the icon's height: 20 pt below its centre at 48 pt.
            CrIcon("ic_location_on_filled", size: 48, tint: CrichereColor.primary)
                .offset(y: -20 + (lifted ? -10 : 0))
                .animation(.easeOut(duration: 0.2), value: lifted)
        }
    }
}

private struct MapSearchBar: View {
    @Binding var query: String
    let isSearching: Bool
    let onQueryChange: (String) -> Void
    let onSearch: () -> Void
    let onClear: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            CrIcon("ic_search", size: 18, tint: CrichereColor.primary)
            ZStack(alignment: .leading) {
                if query.isEmpty {
                    Text("Search a ground, college or area").crichere(.sans(13, lineHeight: 15.6), CrichereColor.onSurfaceVariant).lineLimit(1)
                }
                TextField("", text: Binding(get: { query }, set: { query = $0; onQueryChange($0) }))
                    .font(CrichereTextStyle.sans(13, .medium).font)
                    .foregroundStyle(CrichereColor.onBackground)
                    .tint(CrichereColor.primary)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .submitLabel(.search)
                    .onSubmit(onSearch)
            }
            ZStack {
                if isSearching {
                    CrichereSpinner(color: CrichereColor.primary, lineWidth: 2, size: 16)
                } else if !query.isEmpty {
                    Button(action: onClear) {
                        CrIcon("ic_close", size: 18, tint: CrichereColor.onSurfaceVariant).frame(width: 32, height: 32)
                    }
                    .buttonStyle(.pressCircle)
                    .accessibilityLabel("Clear search")
                }
            }
            .frame(width: 32, height: 32)
        }
        .padding(.leading, 14)
        .padding(.trailing, 6)
        .frame(height: 44)
        .background(Color.white, in: Capsule())
        .shadow(color: cardShadow, radius: 8, y: 2)
    }
}

private struct SearchResults: View {
    let results: [MKLocalSearchCompletion]
    let onPick: (MKLocalSearchCompletion) -> Void

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(results.prefix(5).enumerated()), id: \.offset) { index, result in
                if index > 0 { Rectangle().fill(CrichereColor.outlineVariant).frame(height: 1) }
                Button { onPick(result) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(result.title).crichere(.sans(13.5, .semiBold, lineHeight: 17), CrichereColor.onBackground).lineLimit(1)
                        if !result.subtitle.isEmpty {
                            Text(result.subtitle).crichere(.sans(12, lineHeight: 15), CrichereColor.onSurfaceVariant).lineLimit(1)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.pressHighlight)
            }
        }
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: cardShadow, radius: 8, y: 2)
    }
}

private struct SearchMessage: View {
    let message: String

    var body: some View {
        Text(message).crichere(.sans(12.5, lineHeight: 16), CrichereColor.onBackground)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(color: cardShadow, radius: 8, y: 2)
    }
}

private struct MoveMapHint: View {
    var body: some View {
        HStack(spacing: 6) {
            CrIcon("ic_pan_tool", size: 16, tint: CrichereColor.primary)
            Text("Move the map to put the pin on the ground").crichere(.sans(12, .medium, lineHeight: 14), CrichereColor.onBackground)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: cardShadow, radius: 6, y: 2)
    }
}

// MARK: - Place search

/// MapKit's completer: searches as you type (from 3 characters, 300 ms debounce), biased to the
/// league's district once it's located.
@MainActor
private final class PlaceSearch: NSObject, ObservableObject, MKLocalSearchCompleterDelegate {
    @Published var results: [MKLocalSearchCompletion] = []
    @Published var isSearching = false
    @Published var noMatch = false
    var areaCentre: CLLocationCoordinate2D? {
        didSet { if let areaCentre { region = MKCoordinateRegion(center: areaCentre, latitudinalMeters: 60_000, longitudinalMeters: 60_000) } }
    }
    var region: MKCoordinateRegion?

    private let completer = MKLocalSearchCompleter()
    private var debounce: Task<Void, Never>?

    override init() {
        super.init()
        completer.delegate = self
        completer.resultTypes = [.pointOfInterest, .address]
    }

    func update(_ text: String, area: String?, now: Bool = false) {
        debounce?.cancel()
        results = []
        noMatch = false
        isSearching = false
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        guard now ? !trimmed.isEmpty : trimmed.count >= 3 else { completer.cancel(); return }
        debounce = Task { [weak self] in
            if !now { try? await Task.sleep(nanoseconds: 300_000_000) }
            guard let self, !Task.isCancelled else { return }
            if let region { completer.region = region }
            isSearching = true
            completer.queryFragment = area.map { "\(trimmed), \($0)" } ?? trimmed
        }
    }

    func clear() {
        debounce?.cancel()
        completer.cancel()
        results = []
        noMatch = false
        isSearching = false
    }

    func locate(_ completion: MKLocalSearchCompletion) async -> CLLocationCoordinate2D? {
        results = []
        let response = try? await MKLocalSearch(request: MKLocalSearch.Request(completion: completion)).start()
        guard let point = response?.mapItems.first?.placemark.coordinate else { noMatch = true; return nil }
        return point
    }

    nonisolated func completerDidUpdateResults(_ completer: MKLocalSearchCompleter) {
        let found = completer.results
        Task { @MainActor in
            self.isSearching = false
            self.results = found
            self.noMatch = found.isEmpty
        }
    }

    nonisolated func completer(_ completer: MKLocalSearchCompleter, didFailWithError error: Error) {
        Task { @MainActor in
            self.isSearching = false
            self.results = []
            self.noMatch = true
        }
    }
}

// MARK: - Map

private let indiaCentroid = CLLocationCoordinate2D(latitude: 20.5937, longitude: 78.9629)
private let countrySpan = MKCoordinateSpan(latitudeDelta: 30, longitudeDelta: 30)
private let districtSpan = MKCoordinateSpan(latitudeDelta: 0.35, longitudeDelta: 0.35)
private let groundSpan = MKCoordinateSpan(latitudeDelta: 0.006, longitudeDelta: 0.006)

/// Moves the camera from SwiftUI, and reports whether the map is moving (the pin lifts).
@MainActor
private final class MapCameraControl: ObservableObject {
    @Published var moving = false
    weak var map: MKMapView?
    var pinY: CGFloat = 0
    /// True once the centre is a real choice -- seeded, searched, or moved by the user's own gesture.
    var placed = false

    func flyToDistrict(_ point: CLLocationCoordinate2D) {
        guard !placed else { return }
        fly(to: point, span: districtSpan)
    }

    func flyToGround(_ point: CLLocationCoordinate2D) {
        placed = true
        fly(to: point, span: groundSpan)
    }

    /// Centres [point] under the pin rather than in the middle of the (full-screen) map view.
    func fly(to point: CLLocationCoordinate2D, span: MKCoordinateSpan, animated: Bool = true) {
        guard let map else { return }
        map.setRegion(MKCoordinateRegion(center: centre(for: point, span: span, in: map), span: span), animated: animated)
    }

    func centre(for point: CLLocationCoordinate2D, span: MKCoordinateSpan, in map: MKMapView) -> CLLocationCoordinate2D {
        let height = map.bounds.height
        guard height > 0, pinY > 0 else { return point }
        let north = MKMapPoint(CLLocationCoordinate2D(latitude: min(point.latitude + span.latitudeDelta / 2, 85), longitude: point.longitude))
        let south = MKMapPoint(CLLocationCoordinate2D(latitude: max(point.latitude - span.latitudeDelta / 2, -85), longitude: point.longitude))
        let perPoint = (south.y - north.y) / height
        let target = MKMapPoint(point)
        return MKMapPoint(x: target.x, y: target.y + (map.bounds.midY - pinY) * perPoint).coordinate
    }
}

private struct CentredMap: UIViewRepresentable {
    let control: MapCameraControl
    let seed: CLLocationCoordinate2D?
    let pinY: CGFloat
    let onRest: (Double, Double) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(control: control, onRest: onRest) }

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        map.showsCompass = false
        map.pointOfInterestFilter = .includingAll
        control.map = map
        control.pinY = pinY
        control.placed = seed != nil
        map.setRegion(MKCoordinateRegion(center: seed ?? indiaCentroid, span: seed != nil ? groundSpan : countrySpan), animated: false)
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.onRest = onRest
        let previous = control.pinY
        control.pinY = pinY
        // First real layout: put a seeded ground under the pin.
        if previous == 0, pinY > 0, let seed, map.bounds.height > 0 {
            control.fly(to: seed, span: groundSpan, animated: false)
        }
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
        let control: MapCameraControl
        var onRest: (Double, Double) -> Void

        init(control: MapCameraControl, onRest: @escaping (Double, Double) -> Void) {
            self.control = control
            self.onRest = onRest
        }

        @MainActor func mapView(_ map: MKMapView, regionWillChangeAnimated animated: Bool) {
            control.moving = true
            // A pan/pinch has a gesture recognizer in a began/changed state on the map's subview.
            let gestures = map.subviews.first?.gestureRecognizers ?? []
            if gestures.contains(where: { $0.state == .began || $0.state == .changed }) { control.placed = true }
        }

        @MainActor func mapView(_ map: MKMapView, regionDidChangeAnimated animated: Bool) {
            control.moving = false
            guard control.placed else { return }
            let point = control.pinY > 0 ? CGPoint(x: map.bounds.midX, y: control.pinY) : CGPoint(x: map.bounds.midX, y: map.bounds.midY)
            let coordinate = map.convert(point, toCoordinateFrom: map)
            onRest(coordinate.latitude, coordinate.longitude)
        }
    }
}

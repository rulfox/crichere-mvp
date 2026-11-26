import SwiftUI
import Shared

/// Mirrors `LeagueDashboardViewModel`'s shared `StateFlow<LeagueDashboardState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents.
@MainActor
final class LeagueDashboardViewModelWrapper: ObservableObject {
    @Published var state: LeagueDashboardState

    private let viewModel: LeagueDashboardViewModel

    init() {
        let viewModel = KoinHelper().leagueDashboardViewModel
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func refresh() { viewModel.refresh() }
    func onStateSelected(_ stateDto: StateDto) { viewModel.onStateSelected(stateDto: stateDto) }
    func onDistrictSelected(_ districtDto: DistrictDto) { viewModel.onDistrictSelected(districtDto: districtDto) }
    func onClearAreaFilters() { viewModel.onClearAreaFilters() }
    func onToggleNearMe() { viewModel.onToggleNearMe() }
}


/// League Dashboard (design board screen D, androidApp `ui/LeagueDashboardScreen.kt`): announced leagues,
/// filtered by State/District or "nearest to me" (mutually exclusive, see `LeagueDashboardViewModel`).
/// Lives inside `MainTabView`, which owns the tab bar.
struct LeagueDashboardView: View {
    let onOpenLeague: (String) -> Void
    let onCreateLeague: () -> Void
    var onOpenProfile: () -> Void = {}

    @StateObject private var wrapper = LeagueDashboardViewModelWrapper()
    @StateObject private var locationPermission = LocationPermissionRequester()
    @State private var locationDenied = false

    var body: some View {
        LeagueDashboardContent(
            state: wrapper.state,
            showLocationOff: locationDenied,
            actions: LeagueDashboardActions(
                onStateSelected: wrapper.onStateSelected,
                onDistrictSelected: wrapper.onDistrictSelected,
                onClearAreaFilters: wrapper.onClearAreaFilters,
                onToggleNearMe: toggleNearMe,
                onOpenLocationSettings: {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    locationDenied = false
                },
                onDismissLocationOff: { locationDenied = false },
                onRefresh: wrapper.refresh,
                onOpenLeague: onOpenLeague,
                onCreateLeague: onCreateLeague,
                onOpenProfile: onOpenProfile
            )
        )
        // A fresh fetch on every visit, not just the ViewModel's first load (Android: LaunchedEffect(Unit)).
        .onAppear { wrapper.refresh() }
    }

    private func toggleNearMe() {
        // Turning near-me off never needs permission.
        if wrapper.state.isNearMode { return wrapper.onToggleNearMe() }
        Task {
            await locationPermission.requestWhenInUseAuthorization()
            locationDenied = locationPermission.isDenied
            if !locationDenied { wrapper.onToggleNearMe() }
        }
    }
}

struct LeagueDashboardActions {
    var onStateSelected: (StateDto) -> Void = { _ in }
    var onDistrictSelected: (DistrictDto) -> Void = { _ in }
    var onClearAreaFilters: () -> Void = {}
    var onToggleNearMe: () -> Void = {}
    var onOpenLocationSettings: () -> Void = {}
    var onDismissLocationOff: () -> Void = {}
    var onRefresh: () -> Void = {}
    var onOpenLeague: (String) -> Void = { _ in }
    var onCreateLeague: () -> Void = {}
    var onOpenProfile: () -> Void = {}
}

private enum AreaLevel: Identifiable {
    case state, district
    var id: Self { self }
    var title: String { self == .state ? "Choose state" : "Choose district" }
    var search: String { self == .state ? "Search states" : "Search districts" }
}

/// The screen, stateless for the DEBUG parity gallery.
struct LeagueDashboardContent: View {
    let state: LeagueDashboardState
    var showLocationOff: Bool = false
    var actions = LeagueDashboardActions()

    @State private var openPicker: AreaLevel?

    private var hasAreaFilter: Bool { !state.isNearMode && (state.selectedState != nil || state.selectedDistrict != nil) }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                header.padding(.horizontal, 19)
                list
            }
            CreateLeagueFab(action: actions.onCreateLeague)
                .padding(.trailing, 15)
                .padding(.bottom, 12)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(CrichereColor.background.ignoresSafeArea())
        .crichereSheet(item: $openPicker) { level in
            switch level {
            case .state:
                AreaPickerSheet(title: level.title, searchHint: level.search, options: state.states, selectedName: state.selectedState,
                                optionLabel: { $0.name }) { picked in
                    openPicker = nil
                    actions.onStateSelected(picked)
                }
            case .district:
                AreaPickerSheet(title: level.title, searchHint: level.search, options: state.districts, selectedName: state.selectedDistrict,
                                optionLabel: { $0.name }) { picked in
                    openPicker = nil
                    actions.onDistrictSelected(picked)
                }
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Leagues")
                    .crichere(.archivo(28, .extraBold, lineHeight: 28, tracking: -0.84), CrichereColor.onBackground)
                    .accessibilityAddTraits(.isHeader)
                    .frame(maxWidth: .infinity, alignment: .leading)
                ViewerAvatar(name: state.viewerName, photoUrl: state.viewerPhotoUrl, onClick: actions.onOpenProfile)
            }
            .padding(.top, 11)
            if showLocationOff {
                Spacer().frame(height: 10)
                LocationOffBanner(onOpenSettings: actions.onOpenLocationSettings, onNotNow: actions.onDismissLocationOff)
                Spacer().frame(height: 14)
            } else {
                Spacer().frame(height: 16)
            }
            NearMePill(isLocating: state.isLocating, isNearMode: state.isNearMode, onClick: actions.onToggleNearMe)
            Spacer().frame(height: 10)
            // Area filters and "near me" are mutually exclusive: dim the chips while near-me owns the list.
            let areaLocked = state.isNearMode || state.isLocating
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    AreaChip(label: "State", value: state.selectedState, enabled: !areaLocked) { openPicker = .state }
                    AreaChip(label: "District", value: state.selectedDistrict, enabled: !areaLocked && state.selectedState != nil) { openPicker = .district }
                }
            }
            .opacity(areaLocked ? 0.5 : 1)
            Spacer().frame(height: 6)
            HStack {
                if hasAreaFilter { TextAction(icon: "ic_filter_alt_off", text: "Clear filters", action: actions.onClearAreaFilters) }
                Spacer()
                TextAction(icon: "ic_refresh", text: "Refresh", action: actions.onRefresh)
            }
            .frame(height: 32)
            if let error = state.errorMessage {
                Text(error).crichere(.sans(12.5, .medium), CrichereColor.error).padding(.top, 4)
            }
        }
    }

    @ViewBuilder private var list: some View {
        if state.isLoading {
            CrichereSpinner(color: CrichereColor.outlineVariant, lineWidth: 3, size: 34)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if state.leagues.isEmpty {
            DashboardEmptyState(showClearFilters: hasAreaFilter, onClearFilters: actions.onClearAreaFilters)
        } else {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(state.leagues, id: \.id) { league in
                        LeagueCard(league: league) { actions.onOpenLeague(league.id) }
                    }
                }
                .padding(.horizontal, 15)
                .padding(.top, 10)
                .padding(.bottom, 88)
            }
        }
    }
}

/// 36 pt avatar: initials (Archivo 700 13, green) on the green container, photo over it.
private struct ViewerAvatar: View {
    let name: String?
    let photoUrl: String?
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            ZStack {
                CrichereColor.primaryContainer
                Text(initials(name)).crichere(.archivo(13, .bold), CrichereColor.primary)
                RemoteImage(url: photoUrl)
            }
            .frame(width: 36, height: 36)
            .clipShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("My profile")
    }
}

/// 42 pt near-me pill: outlined idle, filled green when showing nearest, spinner while locating.
private struct NearMePill: View {
    let isLocating: Bool
    let isNearMode: Bool
    let onClick: () -> Void

    var body: some View {
        let active = isNearMode && !isLocating
        let label = CrichereTextStyle.sans(13.5, .semiBold)
        Button(action: onClick) {
            HStack(spacing: 8) {
                if isLocating {
                    CrichereSpinner(color: CrichereColor.primary, size: 17)
                    Text("Finding your location…").crichere(label, CrichereColor.inkSubtle)
                } else {
                    let tint = active ? CrichereColor.onPrimary : CrichereColor.primary
                    CrIcon(active ? "ic_near_me_filled" : "ic_near_me", size: 18, tint: tint)
                    Text(active ? "Showing nearest leagues -- tap to clear" : "Show nearest to me").crichere(label, tint)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 42, maxHeight: 42)
        }
        .buttonStyle(PillButtonStyle(fill: active ? CrichereColor.primary : CrichereColor.surface,
                                     pressedFill: active ? CrichereColor.greenPressed : CrichereColor.surfaceVariant,
                                     content: CrichereColor.primary,
                                     border: active ? CrichereColor.primary : (isLocating ? CrichereColor.outlineDisabled : CrichereColor.outline),
                                     radius: 21))
        .disabled(isLocating)
    }
}

/// 32 pt filter chip: value on the green container when set, outlined label otherwise, drop arrow.
private struct AreaChip: View {
    let label: String
    let value: String?
    let enabled: Bool
    let onClick: () -> Void

    var body: some View {
        let selected = value != nil
        let shape = RoundedRectangle(cornerRadius: 9, style: .continuous)
        Button(action: onClick) {
            HStack(spacing: 0) {
                Text(value ?? label).crichere(.sans(12.5, selected ? .semiBold : .medium), CrichereColor.onBackground).lineLimit(1)
                CrIcon("ic_arrow_drop_down", size: 17, tint: CrichereColor.onBackground)
            }
            .padding(.leading, 12)
            .padding(.trailing, 6)
            .frame(height: 32)
            .background(selected ? CrichereColor.primaryContainer : CrichereColor.surface, in: shape)
            .overlay { if !selected { shape.strokeBorder(Color(hex: 0xD5DACE), lineWidth: 1) } }
        }
        .buttonStyle(PressHighlight(shape: shape))
        .disabled(!enabled)
    }
}

/// Icon + SemiBold 12.5 green label, 32 pt tall (Clear filters / Refresh).
private struct TextAction: View {
    let icon: String
    let text: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                CrIcon(icon, size: 17, tint: CrichereColor.primary)
                Text(text).crichere(.sans(12.5, .semiBold), CrichereColor.primary)
            }
            .padding(.horizontal, 4)
            .frame(height: 32)
        }
        .buttonStyle(PressHighlight(shape: RoundedRectangle(cornerRadius: 8, style: .continuous)))
    }
}

private struct LocationOffBanner: View {
    let onOpenSettings: () -> Void
    let onNotNow: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            CrIcon("ic_location_off", size: 22, tint: CrichereColor.onWarning)
            VStack(alignment: .leading, spacing: 8) {
                Text("Location is off for Crichere").crichere(.sans(14, .semiBold, lineHeight: 17.5), CrichereColor.onWarning)
                Text("Allow location to see leagues near you, or pick your area below.").crichere(.sans(12.5, lineHeight: 17.5), CrichereColor.onWarning)
                HStack(spacing: 14) {
                    Button(action: onOpenSettings) { Text("Open settings").crichere(.sans(13, .bold), CrichereColor.onWarning) }.buttonStyle(.plain)
                    Button(action: onNotNow) { Text("Not now").crichere(.sans(13, .semiBold), CrichereColor.onWarning) }.buttonStyle(.plain)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(CrichereColor.warningContainer, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private struct DashboardEmptyState: View {
    let showClearFilters: Bool
    let onClearFilters: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text("No leagues found.").crichere(.sans(15, .medium), CrichereColor.dialogBody)
            if showClearFilters {
                Button(action: onClearFilters) {
                    Text("Clear filters").crichere(.sans(13.5, .semiBold)).padding(.horizontal, 18).frame(height: 40)
                }
                .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.primary.opacity(0.1), content: CrichereColor.primary,
                                             border: CrichereColor.outline, radius: 20))
            }
        }
        .padding(.bottom, 88)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// The extended FAB: 52 pt, 16 pt corners, green with a soft green shadow, "+ Create a league".
private struct CreateLeagueFab: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                CrIcon("ic_add", size: 22, tint: .white)
                Text("Create a league").crichere(.sans(14, .semiBold), .white)
            }
            .padding(.leading, 14)
            .padding(.trailing, 18)
            .frame(height: 52)
        }
        .buttonStyle(PillButtonStyle(fill: CrichereColor.primary, pressedFill: CrichereColor.greenPressed, content: .white, radius: 16))
        .shadow(color: CrichereColor.primary.opacity(0.5), radius: 6, y: 4)
    }
}

/// Dashboard league card: logo tile (or short code), name, "<ground> · <district>", start / format /
/// entry chips, and the players-joined bar.
private struct LeagueCard: View {
    let league: LeagueDto
    let onClick: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        Button(action: onClick) {
            HStack(alignment: .top, spacing: 12) {
                LeagueTile(id: league.id, name: league.name, logoUrl: league.logoUrl, size: 48, corner: 12, fontSize: 12, logoRemoved: league.logoRemoved)
                VStack(alignment: .leading, spacing: 0) {
                    Text(league.name).crichere(.archivo(15, .bold, lineHeight: 17.25), CrichereColor.onBackground).lineLimit(1)
                    Spacer().frame(height: 4)
                    // D1 (design update #6): "<ground> · <district>".
                    Text("\(league.groundName) · \(league.district)").crichere(.sans(12, lineHeight: 14.4), CrichereColor.onSurfaceVariant).lineLimit(1)
                    Spacer().frame(height: 6)
                    HStack(spacing: 5) {
                        InfoChip(text: "Starts \(shortDate(league.startsOn))")
                        if let format = league.format, !format.trimmingCharacters(in: .whitespaces).isEmpty { InfoChip(text: format) }
                        InfoChip(text: entryLabel(league.playerFee?.doubleValue), highlighted: true)
                    }
                    Spacer().frame(height: 10)
                    PlayerProgress(joined: league.players.count, required: league.playersRequired.map { Int(truncating: $0) })
                }
            }
            .padding(13)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(CrichereColor.surface, in: shape)
            .overlay(shape.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
        }
        .buttonStyle(PressHighlight(shape: shape))
    }
}

private struct InfoChip: View {
    let text: String
    var highlighted: Bool = false

    var body: some View {
        Text(text)
            .crichere(.sans(11, .semiBold), highlighted ? CrichereColor.onPrimaryContainer : CrichereColor.onBackground)
            .lineLimit(1)
            .padding(.horizontal, 7)
            .frame(height: 22)
            .background(highlighted ? CrichereColor.primaryContainer : CrichereColor.surfaceVariant, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }
}

private struct PlayerProgress: View {
    let joined: Int
    let required: Int?

    var body: some View {
        HStack(spacing: 8) {
            if let required, required > 0 {
                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        CrichereColor.surfaceVariant
                        CrichereColor.primary.frame(width: proxy.size.width * min(max(CGFloat(joined) / CGFloat(required), 0), 1))
                    }
                }
                .frame(height: 5)
                .clipShape(RoundedRectangle(cornerRadius: 3))
            } else {
                Spacer()
            }
            Text(required.map { $0 > 0 ? "\(joined)/\($0)" : "\(joined) joined" } ?? "\(joined) joined")
                .crichere(.mono(11, .medium), CrichereColor.onSurfaceVariant)
        }
    }
}

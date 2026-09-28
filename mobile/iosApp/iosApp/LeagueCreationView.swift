import SwiftUI
import PhotosUI
import Shared

/// Mirrors `LeagueCreationViewModel`'s shared `StateFlow<LeagueCreationState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents. **Authored but unverified** -- see docs/PHASE9.md.
@MainActor
final class LeagueCreationViewModelWrapper: ObservableObject {
    @Published var state: LeagueCreationState
    @Published var savedLeagueId: String?

    private let viewModel: LeagueCreationViewModel

    init(editingLeagueId: String?) {
        let viewModel = KoinHelper().leagueCreationViewModel(editingLeagueId: editingLeagueId)
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
        Task { [weak self] in
            for await event in viewModel.navigationEvents {
                switch event {
                case let saved as LeagueCreationNavigationEvent.Saved:
                    self?.savedLeagueId = saved.leagueId
                default:
                    break
                }
            }
        }
    }

    func onNameChanged(_ value: String) { viewModel.onNameChanged(value: value) }
    func onDescriptionChanged(_ value: String) { viewModel.onDescriptionChanged(value: value) }
    func onLogoPicked(bytes: KotlinByteArray, contentType: String) { viewModel.onLogoPicked(bytes: bytes, contentType: contentType) }
    func onBannerPicked(bytes: KotlinByteArray, contentType: String) { viewModel.onBannerPicked(bytes: bytes, contentType: contentType) }
    func useMyLocation() { viewModel.useMyLocation() }
    func onStateSelected(_ stateDto: StateDto) { viewModel.onStateSelected(stateDto: stateDto) }
    func onDistrictSelected(_ districtDto: DistrictDto) { viewModel.onDistrictSelected(districtDto: districtDto) }
    func onCitySelected(_ cityDto: CityDto) { viewModel.onCitySelected(cityDto: cityDto) }
    func onGroundSearchQueryChanged(_ query: String) { viewModel.onGroundSearchQueryChanged(query: query) }
    func onGroundSelected(_ ground: GroundDto) { viewModel.onGroundSelected(ground: ground) }
    func onClearGround() { viewModel.onClearGround() }
    func onStartRegisteringNewGround() { viewModel.onStartRegisteringNewGround() }
    func onCancelRegisteringNewGround() { viewModel.onCancelRegisteringNewGround() }
    func onNewGroundNameChanged(_ value: String) { viewModel.onNewGroundNameChanged(value: value) }
    func onNewGroundPositionChanged(latitude: Double, longitude: Double) { viewModel.onNewGroundPositionChanged(latitude: latitude, longitude: longitude) }
    func registerNewGround() { viewModel.registerNewGround() }
    func onStartsOnChanged(_ isoDate: String) { viewModel.onStartsOnChanged(isoDate: isoDate) }
    func onFormatChanged(_ value: String) { viewModel.onFormatChanged(value: value) }
    func onFranchisesRequiredChanged(_ value: String) { viewModel.onFranchisesRequiredChanged(value: value) }
    func onPlayersRequiredChanged(_ value: String) { viewModel.onPlayersRequiredChanged(value: value) }
    func onFranchiseFeeChanged(_ value: String) { viewModel.onFranchiseFeeChanged(value: value) }
    func onPlayerFeeChanged(_ value: String) { viewModel.onPlayerFeeChanged(value: value) }
    func onOrganizerUpiIdChanged(_ value: String) { viewModel.onOrganizerUpiIdChanged(value: value) }
    func onAddAward() { viewModel.onAddAward() }
    func onRemoveAward(_ index: Int) { viewModel.onRemoveAward(index: Int32(index)) }
    func onAwardNameChanged(_ index: Int, _ value: String) { viewModel.onAwardNameChanged(index: Int32(index), value: value) }
    func onAwardCashAmountChanged(_ index: Int, _ value: String) { viewModel.onAwardCashAmountChanged(index: Int32(index), value: value) }
    func onAwardTrophyToggled(_ index: Int) { viewModel.onAwardTrophyToggled(index: Int32(index)) }
    func save() { viewModel.save() }
}

private let isoDateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.timeZone = TimeZone(identifier: "UTC")
    return formatter
}()

/// iOS equivalent of `androidApp/.../ui/LeagueCreationScreen.kt`: one continuous scrollable form
/// (Basics/Location/Ground/Schedule/Format/Capacity/Fees/Awards), reused unchanged for create and
/// edit -- `LeagueCreationState.isEditMode` only changes the heading and pre-fills fields.
struct LeagueCreationView: View {
    let editingLeagueId: String?
    let onDone: (String) -> Void
    let onCancel: () -> Void

    @StateObject private var wrapper: LeagueCreationViewModelWrapper
    @StateObject private var locationPermission = LocationPermissionRequester()
    @State private var logoPickerItem: PhotosPickerItem?
    @State private var bannerPickerItem: PhotosPickerItem?
    @State private var showStartsOnPicker = false
    @State private var pickedStartsOn = Date()

    init(editingLeagueId: String?, onDone: @escaping (String) -> Void, onCancel: @escaping () -> Void) {
        self.editingLeagueId = editingLeagueId
        self.onDone = onDone
        self.onCancel = onCancel
        _wrapper = StateObject(wrappedValue: LeagueCreationViewModelWrapper(editingLeagueId: editingLeagueId))
    }

    var body: some View {
        Group {
            if wrapper.state.isLoading {
                ProgressView()
            } else {
                Form {
                    Text(wrapper.state.isEditMode ? "Edit league" : "Create a league").font(.title2.bold())

                    Section("Basics") {
                        TextField("League name", text: Binding(get: { wrapper.state.name }, set: { wrapper.onNameChanged($0) }))
                        TextField("Description (optional)", text: Binding(get: { wrapper.state.description }, set: { wrapper.onDescriptionChanged($0) }))

                        PhotosPicker(
                            wrapper.state.logoUrl != nil || wrapper.state.hasPendingLogo ? "Logo selected" : "Choose a logo",
                            selection: $logoPickerItem, matching: .images
                        )
                        PhotosPicker(
                            wrapper.state.bannerUrl != nil || wrapper.state.hasPendingBanner ? "Banner selected" : "Choose a banner",
                            selection: $bannerPickerItem, matching: .images
                        )
                    }

                    Section("Location") {
                        Button(wrapper.state.isLocating ? "Finding your location..." : "Use my location") {
                            Task {
                                await locationPermission.requestWhenInUseAuthorization()
                                wrapper.useMyLocation()
                            }
                        }
                        .disabled(wrapper.state.isLocating)

                        Picker("State", selection: Binding(
                            get: { wrapper.state.states.first { $0.name == wrapper.state.state } },
                            set: { newValue in if let newValue { wrapper.onStateSelected(newValue) } }
                        )) {
                            ForEach(wrapper.state.states, id: \.code) { stateDto in
                                Text(stateDto.name).tag(Optional(stateDto))
                            }
                        }

                        Picker("District", selection: Binding(
                            get: { wrapper.state.districts.first { $0.name == wrapper.state.district } },
                            set: { newValue in if let newValue { wrapper.onDistrictSelected(newValue) } }
                        )) {
                            ForEach(wrapper.state.districts, id: \.id) { districtDto in
                                Text(districtDto.name).tag(Optional(districtDto))
                            }
                        }
                        .disabled(wrapper.state.state == nil)

                        Picker("City", selection: Binding(
                            get: { wrapper.state.cities.first { $0.name == wrapper.state.city } },
                            set: { newValue in if let newValue { wrapper.onCitySelected(newValue) } }
                        )) {
                            ForEach(wrapper.state.cities, id: \.name) { cityDto in
                                Text(cityDto.name).tag(Optional(cityDto))
                            }
                        }
                        .disabled(wrapper.state.district == nil)
                    }

                    Section("Ground (optional)") { groundSection }

                    Section("Schedule") {
                        Button(wrapper.state.startsOn ?? "Pick a start date") { showStartsOnPicker = true }
                    }

                    Section("Format") {
                        TextField("Format (optional, e.g. T20)", text: Binding(get: { wrapper.state.format }, set: { wrapper.onFormatChanged($0) }))
                    }

                    Section("Capacity") {
                        TextField("Franchises required (optional)", text: Binding(get: { wrapper.state.franchisesRequired }, set: { wrapper.onFranchisesRequiredChanged($0) }))
                            .keyboardType(.numberPad)
                        TextField("Players required (optional)", text: Binding(get: { wrapper.state.playersRequired }, set: { wrapper.onPlayersRequiredChanged($0) }))
                            .keyboardType(.numberPad)
                    }

                    Section("Fees") {
                        TextField("Franchise fee (optional)", text: Binding(get: { wrapper.state.franchiseFee }, set: { wrapper.onFranchiseFeeChanged($0) }))
                            .keyboardType(.decimalPad)
                        TextField("Player fee (optional)", text: Binding(get: { wrapper.state.playerFee }, set: { wrapper.onPlayerFeeChanged($0) }))
                            .keyboardType(.decimalPad)
                        let feeSet = !wrapper.state.franchiseFee.isEmpty || !wrapper.state.playerFee.isEmpty
                        TextField(feeSet ? "Your UPI ID (required to collect a fee)" : "Your UPI ID (optional)", text: Binding(get: { wrapper.state.organizerUpiId }, set: { wrapper.onOrganizerUpiIdChanged($0) }))
                    }

                    Section("Awards") { awardsSection }

                    if let error = wrapper.state.errorMessage {
                        Text(error).foregroundColor(.red)
                    }

                    Section {
                        Button("Cancel", action: onCancel)
                        Button(wrapper.state.isSaving ? "Saving..." : "Save") { wrapper.save() }
                            .disabled(!wrapper.state.isSaveEnabled)
                    }
                }
            }
        }
        .sheet(isPresented: $showStartsOnPicker) {
            NavigationStack {
                DatePicker("Starts on", selection: $pickedStartsOn, displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .padding()
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("OK") {
                                wrapper.onStartsOnChanged(isoDateFormatter.string(from: pickedStartsOn))
                                showStartsOnPicker = false
                            }
                        }
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Cancel") { showStartsOnPicker = false }
                        }
                    }
            }
            .presentationDetents([.medium])
        }
        .onChange(of: wrapper.savedLeagueId) { _, leagueId in
            if let leagueId { onDone(leagueId) }
        }
        .task(id: logoPickerItem) {
            if let item = logoPickerItem, let data = try? await item.loadTransferable(type: Data.self) {
                wrapper.onLogoPicked(bytes: toKotlinByteArray(data), contentType: "image/jpeg")
            }
        }
        .task(id: bannerPickerItem) {
            if let item = bannerPickerItem, let data = try? await item.loadTransferable(type: Data.self) {
                wrapper.onBannerPicked(bytes: toKotlinByteArray(data), contentType: "image/jpeg")
            }
        }
    }

    @ViewBuilder
    private var groundSection: some View {
        if wrapper.state.groundId != nil {
            HStack {
                Text(wrapper.state.groundDisplayName ?? "Ground selected")
                Spacer()
                Button("Clear") { wrapper.onClearGround() }
            }
        } else if wrapper.state.isRegisteringNewGround {
            TextField("New ground name", text: Binding(get: { wrapper.state.newGroundName }, set: { wrapper.onNewGroundNameChanged($0) }))
            GroundMapPickerView(
                initialLatitude: wrapper.state.newGroundLatitude,
                initialLongitude: wrapper.state.newGroundLongitude,
                onPositionChanged: { lat, lon in wrapper.onNewGroundPositionChanged(latitude: lat, longitude: lon) }
            )
            HStack {
                Button("Cancel") { wrapper.onCancelRegisteringNewGround() }
                Spacer()
                Button(wrapper.state.isRegisteringGround ? "Registering..." : "Register ground") { wrapper.registerNewGround() }
                    .disabled(wrapper.state.isRegisteringGround || wrapper.state.newGroundName.isEmpty)
            }
            if let error = wrapper.state.errorMessage {
                Text(error).foregroundColor(.red)
            }
        } else {
            TextField("Search grounds", text: Binding(get: { wrapper.state.groundSearchQuery }, set: { wrapper.onGroundSearchQueryChanged($0) }))
            if wrapper.state.isSearchingGrounds {
                ProgressView()
            } else {
                ForEach(wrapper.state.groundSearchResults, id: \.id) { ground in
                    Button("\(ground.name) -- \(ground.city)") { wrapper.onGroundSelected(ground) }
                }
            }
            Button("Can't find it? Register a new ground") { wrapper.onStartRegisteringNewGround() }
        }
    }

    @ViewBuilder
    private var awardsSection: some View {
        ForEach(Array(wrapper.state.awards.enumerated()), id: \.offset) { index, award in
            VStack(alignment: .leading, spacing: 4) {
                TextField("Award name", text: Binding(
                    get: { award.name },
                    set: { wrapper.onAwardNameChanged(index, $0) }
                ))
                TextField("Cash (optional)", text: Binding(
                    get: { award.cashAmount },
                    set: { wrapper.onAwardCashAmountChanged(index, $0) }
                ))
                .keyboardType(.decimalPad)
                HStack {
                    Toggle("Trophy", isOn: Binding(
                        get: { award.hasTrophy },
                        set: { _ in wrapper.onAwardTrophyToggled(index) }
                    ))
                    Button("Remove") { wrapper.onRemoveAward(index) }
                }
            }
        }
        Button("Add another award") { wrapper.onAddAward() }
    }
}

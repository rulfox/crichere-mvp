import SwiftUI
import PhotosUI
import Shared

/// Mirrors `ProfileSetupViewModel`'s shared `StateFlow<ProfileSetupState>` into a `@Published var`
/// SwiftUI can bind to -- same pattern `ToolchainProofViewModelWrapper` documents. Also forwards
/// `navigationEvents` (SKIE's `Flow` -> `AsyncSequence` bridging) into a plain `@Published`
/// one-shot flag `iosAppApp`'s navigation host can observe.
///
/// **Authored but unverified**: this environment has no Mac/Xcode to compile/link/run Swift code
/// (same standing note as every other `iosApp` file in this repo) -- see task-7-report.md.
@MainActor
final class ProfileSetupViewModelWrapper: ObservableObject {
    @Published var state: ProfileSetupState
    @Published var didCompleteProfile: Bool = false

    private let viewModel: ProfileSetupViewModel

    init(isEditMode: Bool) {
        let viewModel = KoinHelper().profileSetupViewModel(isEditMode: isEditMode)
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
        Task { [weak self] in
            for await _ in viewModel.navigationEvents {
                // The only event this ViewModel emits is NavigateToOwnProfile -- see its doc.
                self?.didCompleteProfile = true
            }
        }
    }

    func onNameChanged(_ value: String) { viewModel.onNameChanged(value: value) }
    func onStateSelected(_ stateDto: StateDto) { viewModel.onStateSelected(stateDto: stateDto) }
    func onDistrictSelected(_ districtDto: DistrictDto) { viewModel.onDistrictSelected(districtDto: districtDto) }
    func onCitySelected(_ cityDto: CityDto) { viewModel.onCitySelected(cityDto: cityDto) }
    func onRoleSelected(_ role: PlayingRole) { viewModel.onRoleSelected(role: role) }
    func onBattingStyleSelected(_ style: BattingStyle) { viewModel.onBattingStyleSelected(style: style) }
    func onBowlingStyleSelected(_ style: BowlingStyle) { viewModel.onBowlingStyleSelected(style: style) }
    func uploadPhoto(bytes: KotlinByteArray, contentType: String) { viewModel.uploadPhoto(bytes: bytes, contentType: contentType) }
    func useMyLocation() { viewModel.useMyLocation() }
    func save() { viewModel.save() }
}

/// iOS equivalent of `androidApp/.../ui/ProfileSetupScreen.kt`: resumable cricket-player
/// onboarding, plus the "edit" entry point from Own Profile View (`isEditMode`). Photo selection
/// uses `PhotosPicker` (iOS 16+, the SwiftUI-native equivalent of Android's Photo Picker) --
/// gallery only, no camera-capture entry point, same documented scope decision as the Android
/// side (see task-7-report.md).
struct ProfileSetupView: View {
    let isEditMode: Bool
    let onProfileComplete: () -> Void

    @StateObject private var wrapper: ProfileSetupViewModelWrapper
    @StateObject private var locationPermission = LocationPermissionRequester()
    @State private var selectedPhotoItem: PhotosPickerItem?

    init(isEditMode: Bool, onProfileComplete: @escaping () -> Void) {
        self.isEditMode = isEditMode
        self.onProfileComplete = onProfileComplete
        _wrapper = StateObject(wrappedValue: ProfileSetupViewModelWrapper(isEditMode: isEditMode))
    }

    var body: some View {
        Group {
            if wrapper.state.isLoading {
                ProgressView()
            } else {
                Form {
                    Section {
                        TextField("Full name", text: Binding(
                            get: { wrapper.state.name },
                            set: { wrapper.onNameChanged($0) }
                        ))
                    } header: {
                        // U4 F1: the subtitle stays in every state (it explains why we ask; hiding it made the form jump).
                        VStack(alignment: .leading, spacing: 4) {
                            Text("So organizers know who's joining their league.")
                                .font(.system(size: 13))
                                .foregroundColor(.secondary)
                                .textCase(nil)
                            Text("Name")
                        }
                    } footer: {
                        // U4 C11: the row is always there (empty when valid), so the error doesn't push the form down.
                        Text(wrapper.state.nameError ?? " ")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Color(red: 0xB3 / 255, green: 0x26 / 255, blue: 0x1E / 255))
                    }

                    Section("Photo") {
                        PhotosPicker("Choose a profile photo", selection: $selectedPhotoItem, matching: .images)
                        if wrapper.state.isUploadingPhoto {
                            ProgressView("Uploading...")
                        }
                        if let error = wrapper.state.photoUploadErrorMessage {
                            Text(error).foregroundColor(.red)
                        }
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

                    Section("Playing") {
                        Picker("Role", selection: Binding(
                            get: { wrapper.state.playingRole },
                            set: { newValue in if let newValue { wrapper.onRoleSelected(newValue) } }
                        )) {
                            ForEach(PlayingRole.entries, id: \.self) { role in
                                Text(role.name).tag(Optional(role))
                            }
                        }

                        Picker("Batting style", selection: Binding(
                            get: { wrapper.state.battingStyle },
                            set: { newValue in if let newValue { wrapper.onBattingStyleSelected(newValue) } }
                        )) {
                            ForEach(BattingStyle.entries, id: \.self) { style in
                                Text(style.name).tag(Optional(style))
                            }
                        }

                        if wrapper.state.isBowlingStyleApplicable {
                            Picker("Bowling style", selection: Binding(
                                get: { wrapper.state.bowlingStyle },
                                set: { newValue in if let newValue { wrapper.onBowlingStyleSelected(newValue) } }
                            )) {
                                ForEach(BowlingStyle.entries, id: \.self) { style in
                                    Text(style.name).tag(Optional(style))
                                }
                            }
                        }
                    }

                    if let error = wrapper.state.errorMessage {
                        Text(error).foregroundColor(.red)
                    }

                    Section {
                        Button(wrapper.state.isSaving ? "Saving..." : "Save") {
                            wrapper.save()
                        }
                        .disabled(!wrapper.state.isSaveEnabled)
                    }
                }
            }
        }
        .navigationTitle("Set up your profile")
        .onChange(of: selectedPhotoItem) { _, newItem in
            Task {
                guard let newItem, let data = try? await newItem.loadTransferable(type: Data.self) else { return }
                let bytes = KotlinByteArray(size: Int32(data.count))
                data.withUnsafeBytes { rawBuffer in
                    let pointer = rawBuffer.bindMemory(to: UInt8.self)
                    for index in 0..<data.count {
                        bytes.set(index: Int32(index), value: Int8(bitPattern: pointer[index]))
                    }
                }
                wrapper.uploadPhoto(bytes: bytes, contentType: "image/jpeg")
            }
        }
        .onChange(of: wrapper.didCompleteProfile) { _, completed in
            if completed { onProfileComplete() }
        }
    }
}

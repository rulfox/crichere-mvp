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
    func onRoleSelected(_ role: PlayingRole) { viewModel.onRoleSelected(role: role) }
    func onBattingStyleSelected(_ style: BattingStyle) { viewModel.onBattingStyleSelected(style: style) }
    func onBowlingStyleSelected(_ style: BowlingStyle) { viewModel.onBowlingStyleSelected(style: style) }
    func uploadPhoto(bytes: KotlinByteArray, contentType: String, fileName: String?) {
        viewModel.uploadPhoto(bytes: bytes, contentType: contentType, fileName: fileName)
    }
    func retryPhotoUpload() { viewModel.retryPhotoUpload() }
    func cancelPhotoUpload() { viewModel.cancelPhotoUpload() }
    func retry() { viewModel.retry() }
    func useMyLocation() { viewModel.useMyLocation() }
    func save() { viewModel.save() }
}

/// Profile setup (design board screen C, androidApp `ui/ProfileSetupScreen.kt`): first-time onboarding
/// (name -> photo -> state -> district -> role -> batting -> bowling-if-applicable) and the Edit profile
/// entry from My Profile, both on the shared `ProfileSetupViewModel`.
///
/// Photo: the system photo picker (native) -> circle-crop preview (C3, `PhotoCropSheet`) -> the 1024 px
/// crop uploads with visible progress (C4); a failed upload offers Retry / Choose another (C6).
/// [onBack] is non-nil only for Edit profile (C1-edit): back bar, and leaving with unsaved edits asks
/// first (C1-discard). First-time setup (C1) has no way back.
struct ProfileSetupView: View {
    let isEditMode: Bool
    let onBack: (() -> Void)?
    let onProfileComplete: () -> Void

    @StateObject private var wrapper: ProfileSetupViewModelWrapper
    @StateObject private var locationPermission = LocationPermissionRequester()
    @State private var pickerShown = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var pickedPhoto: PickedPhoto?
    @State private var localPhoto: UIImage?
    @State private var confirmDiscard = false

    init(isEditMode: Bool, onBack: (() -> Void)? = nil, onProfileComplete: @escaping () -> Void) {
        self.isEditMode = isEditMode
        self.onBack = onBack
        self.onProfileComplete = onProfileComplete
        _wrapper = StateObject(wrappedValue: ProfileSetupViewModelWrapper(isEditMode: isEditMode))
    }

    var body: some View {
        ProfileSetupContent(
            state: wrapper.state,
            showBack: onBack != nil,
            localPhoto: localPhoto,
            actions: ProfileSetupActions(
                onBack: requestBack,
                onNameChanged: wrapper.onNameChanged,
                onPickPhoto: { pickerShown = true },
                onCancelUpload: {
                    wrapper.cancelPhotoUpload()
                    localPhoto = nil
                },
                onRetryUpload: wrapper.retryPhotoUpload,
                onUseMyLocation: {
                    Task {
                        await locationPermission.requestWhenInUseAuthorization()
                        wrapper.useMyLocation()
                    }
                },
                onStateSelected: wrapper.onStateSelected,
                onDistrictSelected: wrapper.onDistrictSelected,
                onRoleSelected: wrapper.onRoleSelected,
                onBattingSelected: wrapper.onBattingStyleSelected,
                onBowlingSelected: wrapper.onBowlingStyleSelected,
                onSave: wrapper.save
            )
        )
        .crichereScreen()
        .onAppear { wrapper.retry() }
        .photosPicker(isPresented: $pickerShown, selection: $pickerItem, matching: .images)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            pickerItem = nil
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self) else { return }
                pickedPhoto = decodePickedPhoto(data, fileName: nil)
            }
        }
        .crichereSheet(item: $pickedPhoto) { photo in
            PhotoCropSheet(
                photo: photo,
                title: "Preview",
                subtitle: "Profile photo · drag or pinch to adjust",
                onDismiss: { pickedPhoto = nil },
                onChooseAnother: {
                    pickedPhoto = nil
                    pickerShown = true
                },
                onUsePhoto: { square in
                    pickedPhoto = nil
                    guard let jpeg = encodeCrop(photo, square) else { return }
                    localPhoto = UIImage(data: jpeg)
                    wrapper.uploadPhoto(bytes: toKotlinByteArray(jpeg), contentType: "image/jpeg", fileName: photo.fileName)
                }
            )
        }
        .crichereDialog(isPresented: $confirmDiscard) {
            DiscardChangesDialog(
                message: "Your edits won't be saved. Your profile stays as it was.",
                onKeepEditing: { confirmDiscard = false },
                onDiscard: {
                    confirmDiscard = false
                    onBack?()
                }
            )
        }
        .onChange(of: wrapper.didCompleteProfile) { _, completed in
            if completed { onProfileComplete() }
        }
    }

    private func requestBack() {
        if wrapper.state.isDirty { confirmDiscard = true } else { onBack?() }
    }
}

struct ProfileSetupActions {
    var onBack: () -> Void = {}
    var onNameChanged: (String) -> Void = { _ in }
    var onPickPhoto: () -> Void = {}
    var onCancelUpload: () -> Void = {}
    var onRetryUpload: () -> Void = {}
    var onUseMyLocation: () -> Void = {}
    var onStateSelected: (StateDto) -> Void = { _ in }
    var onDistrictSelected: (DistrictDto) -> Void = { _ in }
    var onRoleSelected: (PlayingRole) -> Void = { _ in }
    var onBattingSelected: (BattingStyle) -> Void = { _ in }
    var onBowlingSelected: (BowlingStyle) -> Void = { _ in }
    var onSave: () -> Void = {}
}

/// The screen, stateless for the DEBUG parity gallery.
struct ProfileSetupContent: View {
    let state: ProfileSetupState
    let showBack: Bool
    var localPhoto: UIImage? = nil
    var actions = ProfileSetupActions()

    var body: some View {
        if state.isLoading {
            CrichereSpinner(color: CrichereColor.primary, lineWidth: 4, size: 40)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(CrichereColor.background.ignoresSafeArea())
        } else {
            VStack(spacing: 0) {
                if showBack {
                    BackTitleBar(title: "Edit profile", titleSize: 17, titleLineHeight: 17, gap: 4, onBack: actions.onBack)
                }
                ScrollViewReader { reader in
                    ScrollView {
                        fields
                            .padding(.horizontal, 20)
                            // C1-edit: fields start 12 pt under the bar; the field's 7 pt notch room counts toward it.
                            .padding(.top, showBack ? 5 : 21)
                            .padding(.bottom, 20)
                    }
                    .scrollDismissesKeyboard(.interactively)
                    .onAppear { reader.scrollTo(state.initialFocusField, anchor: .top) }
                }
                ProfileSaveBar(state: state, onSave: actions.onSave)
            }
            .background(CrichereColor.background.ignoresSafeArea())
        }
    }

    private var fields: some View {
        VStack(alignment: .leading, spacing: 0) {
            if !showBack {
                Text("Set up your profile")
                    .crichere(.archivo(24, .bold, lineHeight: 26.4), CrichereColor.onBackground)
                    .accessibilityAddTraits(.isHeader)
                Spacer().frame(height: 4)
                Text("So organizers know who's joining their league.")
                    .crichere(.sans(13, lineHeight: 18.2), CrichereColor.onSurfaceVariant)
                // Fields carry 7 pt of top room for their notched label, so gaps below are design gap - 7.
                Spacer().frame(height: 8)
            }
            CrichereTextField(text: Binding(get: { state.name }, set: actions.onNameChanged), label: "Full name",
                              error: state.nameError, reserveErrorSlot: true, capitalization: .words)
                .id(ProfileField.name)
            Spacer().frame(height: 14)
            ProfilePhotoRow(state: state, localPhoto: localPhoto, onPick: actions.onPickPhoto,
                            onCancel: actions.onCancelUpload, onRetry: actions.onRetryUpload)
                .id(ProfileField.photo)
            Spacer().frame(height: 14)
            LocationPill(isLocating: state.isLocating, action: actions.onUseMyLocation)
            Spacer().frame(height: 7)
            CrichereSelectField(label: "State", options: state.states, selected: state.states.first { $0.name == state.state },
                                optionLabel: { $0.name }, onSelected: actions.onStateSelected)
                .id(ProfileField.state)
            Spacer().frame(height: 7)
            CrichereSelectField(label: "District", options: state.districts, selected: state.districts.first { $0.name == state.district },
                                optionLabel: { $0.name }, onSelected: actions.onDistrictSelected, enabled: state.state != nil)
                .id(ProfileField.district)
            Spacer().frame(height: 7)
            CrichereSelectField(label: "Playing role", options: PlayingRole.allCases, selected: state.playingRole,
                                optionLabel: { $0.label }, onSelected: actions.onRoleSelected)
                .id(ProfileField.role)
            Spacer().frame(height: 7)
            CrichereSelectField(label: "Batting style", options: BattingStyle.allCases, selected: state.battingStyle,
                                optionLabel: { $0.displayName }, onSelected: actions.onBattingSelected)
                .id(ProfileField.batting)
            if state.isBowlingStyleApplicable {
                Spacer().frame(height: 7)
                CrichereSelectField(label: "Bowling style", options: BowlingStyle.allCases, selected: state.bowlingStyle,
                                    optionLabel: { $0.displayName }, onSelected: actions.onBowlingSelected)
                    .id(ProfileField.bowling)
            }
        }
    }
}

/// Hairline, then the save error (if any) and the Save pill ("Saving…").
private struct ProfileSaveBar: View {
    let state: ProfileSetupState
    let onSave: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(CrichereColor.outlineVariant).frame(height: 1)
            VStack(alignment: .leading, spacing: 0) {
                if let error = state.errorMessage {
                    HStack(alignment: .top, spacing: 6) {
                        CrIcon("ic_error", size: 17, tint: CrichereColor.error).padding(.top, 2)
                        Text(error).crichere(.sans(12.5, .medium, lineHeight: 16.25), CrichereColor.error).padding(.top, 3)
                    }
                    Spacer().frame(height: 10)
                }
                CricherePrimaryButton("Save", enabled: state.isSaveEnabled, loading: state.isSaving, loadingText: "Saving…", action: onSave)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 26)
        }
    }
}

/// The photo row: a 76 pt circle (dashed "add" circle, photo, upload ring, failed tint) and its text
/// for each state -- add (required), uploading (C4), failed (C6), set ("Tap to change").
private struct ProfilePhotoRow: View {
    let state: ProfileSetupState
    let localPhoto: UIImage?
    let onPick: () -> Void
    let onCancel: () -> Void
    let onRetry: () -> Void

    private var failed: Bool { state.photoUploadErrorMessage != nil && !state.isUploadingPhoto }
    private var hasImage: Bool { localPhoto != nil || state.photoUrl != nil }
    private let title = CrichereTextStyle.sans(14, .semiBold, lineHeight: 16.8)
    private let link = CrichereTextStyle.sans(13, .semiBold)

    var body: some View {
        HStack(spacing: 14) {
            ProfilePhotoCircle(state: state, localPhoto: localPhoto, failed: failed, hasImage: hasImage)
            VStack(alignment: .leading, spacing: 0) { details }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
        .onTapGesture { if !state.isUploadingPhoto && !failed { onPick() } }
        .accessibilityAddTraits(.isButton)
    }

    @ViewBuilder private var details: some View {
        if state.isUploadingPhoto {
            Text("Uploading photo…").crichere(title, CrichereColor.onBackground)
            Spacer().frame(height: 4)
            Text(fileCaption(state.uploadingPhotoName ?? "profile-photo.jpg", state.uploadingPhotoSizeBytes?.int64Value))
                .crichere(.mono(12, .medium), CrichereColor.onSurfaceVariant)
            Spacer().frame(height: 4)
            Button(action: onCancel) { Text("Cancel").crichere(.sans(12.5, .semiBold), CrichereColor.onSurfaceVariant) }
                .buttonStyle(.plain)
        } else if failed {
            Text("Couldn't upload the photo").crichere(title, CrichereColor.errorStrong)
            Spacer().frame(height: 6)
            HStack(spacing: 14) {
                Button(action: onRetry) { Text("Retry").crichere(link, CrichereColor.primary) }.buttonStyle(.plain)
                Button(action: onPick) { Text("Choose another").crichere(link, CrichereColor.onSurfaceVariant) }.buttonStyle(.plain)
            }
        } else if hasImage {
            Text("Profile photo").crichere(title, CrichereColor.onBackground)
            Spacer().frame(height: 4)
            Text("Tap to change").crichere(.sans(12), CrichereColor.onSurfaceVariant)
        } else {
            (Text("Add a profile photo") + Text(" *").foregroundColor(CrichereColor.error))
                .crichere(title, CrichereColor.primary)
            Spacer().frame(height: 4)
            Text("Required to save · shown on rosters and auctions").crichere(.sans(12, lineHeight: 15.6), CrichereColor.onSurfaceVariant)
        }
    }
}

private struct ProfilePhotoCircle: View {
    let state: ProfileSetupState
    let localPhoto: UIImage?
    let failed: Bool
    let hasImage: Bool

    var body: some View {
        if !hasImage {
            ZStack {
                Circle().fill(Color.white)
                Circle().inset(by: 1).stroke(Color(hex: 0xB9C6B3), style: StrokeStyle(lineWidth: 2, dash: [6, 5]))
                CrIcon("ic_add_a_photo", size: 28, tint: CrichereColor.primary)
            }
            .frame(width: 76, height: 76)
        } else {
            let progress = CGFloat(state.photoUploadProgress)
            ZStack {
                Group {
                    if let localPhoto {
                        Image(uiImage: localPhoto).resizable().scaledToFill()
                    } else {
                        RemoteImage(url: state.photoUrl)
                    }
                }
                .frame(width: 72, height: 72)
                if state.isUploadingPhoto {
                    Color(hex: 0x0E1A11, opacity: 0x73 / 255.0)
                    Text("\(Int(progress * 100))%").crichere(.mono(13, .bold), .white)
                } else if failed {
                    Color(hex: 0x8C1D12, opacity: 0x80 / 255.0)
                    CrIcon("ic_error", size: 28, tint: .white)
                }
            }
            .frame(width: 72, height: 72)
            .clipShape(Circle())
            .padding(2)
            .overlay(Circle().strokeBorder(failed ? CrichereColor.error : CrichereColor.background, lineWidth: 2))
            .overlay {
                if state.isUploadingPhoto {
                    // Progress ring just outside the photo: track, then the green sweep from 12 o'clock.
                    ZStack {
                        Circle().stroke(CrichereColor.disabledContainer, lineWidth: 4)
                        Circle().trim(from: 0, to: progress).stroke(CrichereColor.primary, style: StrokeStyle(lineWidth: 4, lineCap: .butt))
                            .rotationEffect(.degrees(-90))
                    }
                    .frame(width: 79, height: 79)
                }
            }
            .frame(width: 76, height: 76)
            .accessibilityLabel("Profile photo")
        }
    }
}

extension BattingStyle {
    /// Profile setup's labels (ProfileSetupScreen.kt `displayName`).
    var displayName: String {
        switch self {
        case .rightHand: "Right-hand bat"
        case .leftHand: "Left-hand bat"
        }
    }
}

extension BowlingStyle {
    var displayName: String {
        switch self {
        case .rightArmFast: "Right-arm fast"
        case .rightArmMedium: "Right-arm medium"
        case .rightArmOffbreak: "Right-arm offbreak"
        case .rightArmLegbreak: "Right-arm legbreak"
        case .leftArmFast: "Left-arm fast"
        case .leftArmMedium: "Left-arm medium"
        case .leftArmOrthodox: "Left-arm orthodox"
        case .leftArmChinaman: "Left-arm chinaman"
        }
    }
}

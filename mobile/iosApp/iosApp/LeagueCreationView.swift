import SwiftUI
import PhotosUI
import Shared

/// Mirrors `LeagueCreationViewModel`'s shared `StateFlow<LeagueCreationState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents. 
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
                case let saved as LeagueCreationNavigationEventSaved:
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
    func onGroundSearchQueryChanged(_ query: String) { viewModel.onGroundSearchQueryChanged(query: query) }
    func onGroundSelected(_ ground: GroundDto) { viewModel.onGroundSelected(ground: ground) }
    func onClearGround() { viewModel.onClearGround() }
    func onChangeGround() { viewModel.onChangeGround() }
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
    func retryLoad() { viewModel.retryLoad() }
    func removeLogo() { viewModel.removeLogo() }
    func removeBanner() { viewModel.removeBanner() }
    func onFormatOptionSelected(_ option: String?) { viewModel.onFormatOptionSelected(option: option) }
}


private enum ImageTarget { case logo, banner }
private let otherFormat = "Other"

/// Create / edit league (design board screen I, androidApp `ui/LeagueCreationScreen.kt`): one
/// scrolling form -- the "How it will look" card with logo and 16:9 banner crops, then Basics,
/// Location, Ground, Schedule & format, Capacity, Fees and Awards. Save sits in the top bar; it
/// validates on tap (inline errors + a count banner, I7/I9), and leaving an edited form asks first
/// (I10). New grounds are registered on a full-screen map (I5/I11). Images upload as part of Save.
struct LeagueCreationView: View {
    let editingLeagueId: String?
    let onDone: (String) -> Void
    let onCancel: () -> Void

    @StateObject private var wrapper: LeagueCreationViewModelWrapper
    @StateObject private var locationPermission = LocationPermissionRequester()
    @State private var target = ImageTarget.logo
    @State private var pickerShown = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var pickedLogo: PickedPhoto?
    @State private var pickedBanner: PickedPhoto?
    @State private var logoPreview: UIImage?
    @State private var bannerPreview: UIImage?
    @State private var showDatePicker = false
    @State private var confirmDiscard = false

    init(editingLeagueId: String?, onDone: @escaping (String) -> Void, onCancel: @escaping () -> Void) {
        self.editingLeagueId = editingLeagueId
        self.onDone = onDone
        self.onCancel = onCancel
        _wrapper = StateObject(wrappedValue: LeagueCreationViewModelWrapper(editingLeagueId: editingLeagueId))
    }

    var body: some View {
        let state = wrapper.state
        ZStack {
            LeagueCreationContent(
                state: state,
                // Previews follow the ViewModel's pending picks, so they clear on remove / upload.
                logoPreview: state.hasPendingLogo ? logoPreview : nil,
                bannerPreview: state.hasPendingBanner ? bannerPreview : nil,
                actions: actions
            )
            if state.isRegisteringNewGround {
                GroundRegisterOverlay(state: state, actions: GroundRegisterActions(
                    onNameChanged: wrapper.onNewGroundNameChanged,
                    onPositionChanged: { wrapper.onNewGroundPositionChanged(latitude: $0, longitude: $1) },
                    onRegister: wrapper.registerNewGround,
                    onCancel: wrapper.onCancelRegisteringNewGround
                ))
                .transition(.opacity)
            }
        }
        .crichereScreen()
        .swipeBackBlocked(state.isDirty || state.isRegisteringNewGround)
        .onChange(of: wrapper.savedLeagueId) { _, leagueId in if let leagueId { onDone(leagueId) } }
        .photosPicker(isPresented: $pickerShown, selection: $pickerItem, matching: .images)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            pickerItem = nil
            let pickedFor = target
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self), let photo = decodePickedPhoto(data, fileName: "league.jpg") else { return }
                if pickedFor == .logo { pickedLogo = photo } else { pickedBanner = photo }
            }
        }
        .crichereSheet(item: $pickedLogo) { photo in
            PhotoCropSheet(
                photo: photo, title: "Preview", subtitle: "League logo · drag to reposition",
                onDismiss: { pickedLogo = nil },
                onChooseAnother: { pickedLogo = nil; choose(.logo) },
                onUsePhoto: { square in
                    pickedLogo = nil
                    guard let jpeg = encodeCrop(photo, square) else { return }
                    logoPreview = UIImage(data: jpeg)
                    wrapper.onLogoPicked(bytes: toKotlinByteArray(jpeg), contentType: "image/jpeg")
                },
                square: true,
                useLabel: "Use logo"
            )
        }
        .crichereSheet(item: $pickedBanner) { photo in
            PhotoCropSheet(
                photo: photo, title: "Preview", subtitle: "League banner · drag to reposition",
                onDismiss: { pickedBanner = nil },
                onChooseAnother: { pickedBanner = nil; choose(.banner) },
                onUsePhoto: { _ in },
                useLabel: "Use banner",
                onUseBanner: { region in
                    pickedBanner = nil
                    guard let jpeg = encodeBannerCrop(photo, region) else { return }
                    bannerPreview = UIImage(data: jpeg)
                    wrapper.onBannerPicked(bytes: toKotlinByteArray(jpeg), contentType: "image/jpeg")
                }
            )
        }
        .crichereDialog(isPresented: $showDatePicker) {
            CrichereDatePickerDialog(title: "Select start date", initial: parseIsoDay(state.startsOn), onDismiss: { showDatePicker = false }) { date in
                wrapper.onStartsOnChanged(isoDayString(date))
                showDatePicker = false
            }
        }
        .crichereDialog(isPresented: $confirmDiscard) {
            DiscardChangesDialog(
                message: state.isEditMode ? "Your edits won't be saved. The league stays as it was." : "This league won't be created.",
                onKeepEditing: { confirmDiscard = false },
                onDiscard: { confirmDiscard = false; onCancel() }
            )
        }
    }

    private var actions: LeagueCreationActions {
        LeagueCreationActions(
            onClose: { if wrapper.state.isDirty { confirmDiscard = true } else { onCancel() } },
            onSave: wrapper.save,
            onRetryLoad: wrapper.retryLoad,
            onPickLogo: { choose(.logo) },
            onPickBanner: { choose(.banner) },
            onRemoveLogo: { wrapper.removeLogo(); logoPreview = nil },
            onRemoveBanner: { wrapper.removeBanner(); bannerPreview = nil },
            onNameChanged: wrapper.onNameChanged,
            onDescriptionChanged: wrapper.onDescriptionChanged,
            onUseMyLocation: {
                Task {
                    await locationPermission.requestWhenInUseAuthorization()
                    wrapper.useMyLocation()
                }
            },
            onStateSelected: wrapper.onStateSelected,
            onDistrictSelected: wrapper.onDistrictSelected,
            onGroundQueryChanged: wrapper.onGroundSearchQueryChanged,
            onGroundSelected: wrapper.onGroundSelected,
            onClearGround: wrapper.onClearGround,
            onChangeGround: wrapper.onChangeGround,
            onRegisterGround: wrapper.onStartRegisteringNewGround,
            onPickDate: { showDatePicker = true },
            onFormatOptionSelected: wrapper.onFormatOptionSelected,
            onFormatChanged: wrapper.onFormatChanged,
            onFranchisesChanged: wrapper.onFranchisesRequiredChanged,
            onPlayersChanged: wrapper.onPlayersRequiredChanged,
            onFranchiseFeeChanged: wrapper.onFranchiseFeeChanged,
            onPlayerFeeChanged: wrapper.onPlayerFeeChanged,
            onUpiChanged: wrapper.onOrganizerUpiIdChanged,
            onAwardNameChanged: wrapper.onAwardNameChanged,
            onAwardCashChanged: wrapper.onAwardCashAmountChanged,
            onAwardTrophyToggled: wrapper.onAwardTrophyToggled,
            onRemoveAward: wrapper.onRemoveAward,
            onAddAward: wrapper.onAddAward
        )
    }

    private func choose(_ forTarget: ImageTarget) {
        target = forTarget
        pickerShown = true
    }
}

/// Everything the Create / edit league form does.
struct LeagueCreationActions {
    var onClose: () -> Void = {}
    var onSave: () -> Void = {}
    var onRetryLoad: () -> Void = {}
    var onPickLogo: () -> Void = {}
    var onPickBanner: () -> Void = {}
    var onRemoveLogo: () -> Void = {}
    var onRemoveBanner: () -> Void = {}
    var onNameChanged: (String) -> Void = { _ in }
    var onDescriptionChanged: (String) -> Void = { _ in }
    var onUseMyLocation: () -> Void = {}
    var onStateSelected: (StateDto) -> Void = { _ in }
    var onDistrictSelected: (DistrictDto) -> Void = { _ in }
    var onGroundQueryChanged: (String) -> Void = { _ in }
    var onGroundSelected: (GroundDto) -> Void = { _ in }
    var onClearGround: () -> Void = {}
    var onChangeGround: () -> Void = {}
    var onRegisterGround: () -> Void = {}
    var onPickDate: () -> Void = {}
    var onFormatOptionSelected: (String?) -> Void = { _ in }
    var onFormatChanged: (String) -> Void = { _ in }
    var onFranchisesChanged: (String) -> Void = { _ in }
    var onPlayersChanged: (String) -> Void = { _ in }
    var onFranchiseFeeChanged: (String) -> Void = { _ in }
    var onPlayerFeeChanged: (String) -> Void = { _ in }
    var onUpiChanged: (String) -> Void = { _ in }
    var onAwardNameChanged: (Int, String) -> Void = { _, _ in }
    var onAwardCashChanged: (Int, String) -> Void = { _, _ in }
    var onAwardTrophyToggled: (Int) -> Void = { _ in }
    var onRemoveAward: (Int) -> Void = { _ in }
    var onAddAward: () -> Void = {}
}

/// The form, stateless for the DEBUG parity gallery.
struct LeagueCreationContent: View {
    let state: LeagueCreationState
    var logoPreview: UIImage? = nil
    var bannerPreview: UIImage? = nil
    var actions = LeagueCreationActions()
    /// DEBUG parity boards: open scrolled to this section id.
    var startAt: AnyHashable? = nil

    var body: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                CreationTopBar(state: state, onClose: actions.onClose, onSave: actions.onSave)
                if state.isLoading {
                    CrichereSpinner(color: CrichereColor.primary, lineWidth: 4, size: 40).frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if state.loadFailed {
                    LoadErrorView(onRetry: actions.onRetryLoad)
                } else {
                    // The count banner stays pinned under the top bar while the form scrolls (I7/I9).
                    if !state.fieldErrors.isEmpty {
                        ErrorSummary(count: state.fieldErrors.count)
                            .padding(.horizontal, 17)
                            .padding(.top, 2)
                            .padding(.bottom, 4)
                    }
                    CreationForm(state: state, logoPreview: logoPreview, bannerPreview: bannerPreview, actions: actions, startAt: startAt)
                }
            }
            if let error = state.errorMessage, !state.loadFailed {
                CrichereSnackbar(message: error, actionLabel: "Retry", onAction: actions.onSave)
                    .padding(.horizontal, 13)
                    .padding(.bottom, 25)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(CrichereColor.background.ignoresSafeArea())
    }
}

/// The scrolling form; a Save tap with missing fields scrolls to the first one's section.
private struct CreationForm: View {
    let state: LeagueCreationState
    let logoPreview: UIImage?
    let bannerPreview: UIImage?
    let actions: LeagueCreationActions
    var startAt: AnyHashable? = nil

    private func error(_ field: LeagueField) -> String? { state.fieldErrors[field] }

    var body: some View {
        ScrollViewReader { reader in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    preview
                    SectionHeading(text: "Basics").id(LeagueField.name)
                    basics
                    LocationSection(state: state, actions: actions)
                    GroundSection(state: state, error: error(.ground), actions: actions)
                    SectionHeading(text: "Schedule & format").id(LeagueField.startsOn)
                    schedule
                    SectionHeading(text: "Capacity")
                    capacity
                    SectionHeading(text: "Fees").id(LeagueField.upiId)
                    fees
                    AwardsSection(awards: state.awards, actions: actions).id("awards")
                    Spacer().frame(height: state.errorMessage != nil ? 96 : 32)
                }
                .padding(.horizontal, 19)
                .padding(.top, 2)
            }
            .scrollDismissesKeyboard(.interactively)
            .onAppear { if let startAt { reader.scrollTo(startAt, anchor: .top) } }
            .onChange(of: state.validationAttempt) { _, attempt in
                guard attempt > 0, let first = LeagueField.allCases.first(where: { state.fieldErrors[$0] != nil }) else { return }
                // State / District share the Location heading.
                let anchor: LeagueField = first == .district ? .state : first
                withAnimation { reader.scrollTo(anchor, anchor: .top) }
            }
        }
    }

    @ViewBuilder private var preview: some View {
        let empty = state.name.trimmingCharacters(in: .whitespaces).isEmpty && logoPreview == nil && bannerPreview == nil
            && state.logoUrl == nil && state.bannerUrl == nil
        if empty {
            Text("HOW IT WILL LOOK").crichere(.mono(10.5, .semiBold, lineHeight: 10.5, tracking: 0.63), CrichereColor.onSurfaceVariant)
                .padding(.top, 3)
            Spacer().frame(height: 10)
        } else {
            Spacer().frame(height: 4)
        }
        PreviewCard(state: state, logoPreview: logoPreview, bannerPreview: bannerPreview, actions: actions)
        ImageActions(hasLogo: logoPreview != nil || state.logoUrl != nil, hasBanner: bannerPreview != nil || state.bannerUrl != nil,
                     enabled: !state.isSaving, actions: actions)
    }

    @ViewBuilder private var basics: some View {
        CrichereTextField(text: Binding(get: { state.name }, set: actions.onNameChanged), label: "League name", look: .form,
                          error: error(.name), capitalization: .words)
        Spacer().frame(height: 5)
        CrichereTextField(text: Binding(get: { state.description_ }, set: actions.onDescriptionChanged),
                          label: state.description_.isEmpty ? "Description (optional)" : "Description", look: .form, minLines: 3)
    }

    @ViewBuilder private var schedule: some View {
        let formats = LeagueCreationViewModel.Companion.shared.FORMAT_OPTIONS + [otherFormat]
        HStack(alignment: .top, spacing: 10) {
            CrichereTapField(label: "Starts on", value: state.startsOn.map { formatIsoDay($0, "d MMM yyyy") }, look: .form,
                             error: error(.startsOn), trailingIcon: "ic_event", onClick: actions.onPickDate)
            CrichereSelectField(label: "Format", options: formats,
                                selected: state.isFormatOther ? otherFormat : (formats.contains(state.format) ? state.format : nil),
                                optionLabel: { $0 }, onSelected: { actions.onFormatOptionSelected($0 == otherFormat ? nil : $0) },
                                fontSize: 14, look: .form)
        }
        if state.isFormatOther {
            Spacer().frame(height: 5)
            CrichereTextField(text: Binding(get: { state.format }, set: actions.onFormatChanged), label: "Format name", look: .form,
                              placeholder: "e.g. 8 overs")
        }
    }

    private var capacity: some View {
        HStack(alignment: .top, spacing: 10) {
            CrichereTextField(text: Binding(get: { state.franchisesRequired }, set: { actions.onFranchisesChanged($0.filter(\.isNumber)) }),
                              label: "Franchises", look: .form, mono: true, keyboard: .numberPad)
            CrichereTextField(text: Binding(get: { state.playersRequired }, set: { actions.onPlayersChanged($0.filter(\.isNumber)) }),
                              label: "Players", look: .form, mono: true, keyboard: .numberPad)
        }
    }

    @ViewBuilder private var fees: some View {
        HStack(alignment: .top, spacing: 10) {
            CrichereTextField(text: Binding(get: { state.franchiseFee }, set: { actions.onFranchiseFeeChanged(amountInput($0)) }),
                              label: "Franchise fee ₹", look: .form, mono: true, keyboard: .decimalPad)
            CrichereTextField(text: Binding(get: { state.playerFee }, set: { actions.onPlayerFeeChanged(amountInput($0)) }),
                              label: "Player fee ₹", look: .form, mono: true, keyboard: .decimalPad)
        }
        Spacer().frame(height: 5)
        CrichereTextField(text: Binding(get: { state.organizerUpiId }, set: { actions.onUpiChanged($0.trimmingCharacters(in: .whitespaces)) }),
                          label: "UPI ID for payments", look: .form, error: error(.upiId), placeholder: "name@bank", mono: true,
                          keyboard: .emailAddress, capitalization: .never)
    }
}

/// Keeps digits and a single decimal point -- fee and cash inputs.
private func amountInput(_ raw: String) -> String {
    let kept = raw.filter { $0.isNumber || $0 == "." }
    guard let dot = kept.firstIndex(of: ".") else { return kept }
    return String(kept[...dot]) + kept[kept.index(after: dot)...].replacingOccurrences(of: ".", with: "")
}

/// Close (or, in edit mode, "Cancel"), the title, and the Save pill (design I1/I10/I12).
private struct CreationTopBar: View {
    let state: LeagueCreationState
    let onClose: () -> Void
    let onSave: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            if state.isEditMode {
                Button(action: onClose) {
                    Text("Cancel").crichere(.sans(14, .semiBold), CrichereColor.primary).frame(width: 70, height: 40)
                }
                .buttonStyle(PressHighlight(shape: Capsule()))
                .padding(.leading, 9)
            } else {
                Button(action: onClose) {
                    CrIcon("ic_close", size: 24, tint: CrichereColor.onBackground).frame(width: 48, height: 48)
                }
                .buttonStyle(.pressCircle)
                .accessibilityLabel("Close")
                .padding(.leading, 5)
            }
            Text(state.isEditMode ? "Edit league" : "Create a league")
                .crichere(.sans(17, .semiBold, lineHeight: 17), CrichereColor.onBackground)
                .frame(maxWidth: .infinity, alignment: .leading)
            savePill
        }
        .padding(.trailing, 13)
        .frame(height: 48)
        .padding(.top, 3)
    }

    private var savePill: some View {
        let enabled = state.isSaveEnabled
        let active = enabled || state.isSaving
        let progress = state.bannerUploadProgress ?? state.logoUploadProgress
        let label = progress.map { "Uploading \(Int($0.floatValue * 100))%" } ?? (state.isSaving ? "Saving…" : "Save")
        return Button(action: onSave) {
            HStack(spacing: 8) {
                if state.isSaving { CrichereSpinner(color: .white, track: .white.opacity(0.35), lineWidth: 2, size: 19) }
                Text(label).crichere(.sans(13.5, .semiBold))
            }
            .padding(.leading, state.isSaving ? 11 : 16)
            .padding(.trailing, 16)
            .frame(height: 38)
        }
        .buttonStyle(PillButtonStyle(fill: active ? CrichereColor.primary : CrichereColor.disabledContainer, pressedFill: CrichereColor.greenPressed,
                                     content: active ? .white : CrichereColor.inkSubtle, radius: 19))
        .disabled(!enabled)
    }
}

/// "N fields need attention" (design I7/I9), 2 pt wider than the form on each side.
private struct ErrorSummary: View {
    let count: Int

    var body: some View {
        HStack(spacing: 10) {
            CrIcon("ic_error", size: 20, tint: CrichereColor.errorStrong)
            Text(count == 1 ? "1 field needs attention" : "\(count) fields need attention")
                .crichere(.sans(13, .semiBold, lineHeight: 17.55), CrichereColor.errorStrong)
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(CrichereColor.errorContainer, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct SectionHeading: View {
    let text: String
    var top: CGFloat = 16

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer().frame(height: top)
            Text(text).crichere(.archivo(15, .bold, lineHeight: 15), CrichereColor.onBackground)
            Spacer().frame(height: 5)
        }
    }
}

// MARK: - Preview card

private let bannerEmpty = Color(hex: 0xF1F4EE)
private let bannerDash = Color(hex: 0xB9C6B3)
private let logoEmpty = Color(hex: 0xE3E8DD)
private let previewPlaceholder = Color(hex: 0xA5ADA6)

/// "How it will look": the 16:9 banner, the logo tile and the name / ground (or district) · date (I1/I3).
private struct PreviewCard: View {
    let state: LeagueCreationState
    let logoPreview: UIImage?
    let bannerPreview: UIImage?
    let actions: LeagueCreationActions

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        ZStack(alignment: .topLeading) {
            VStack(spacing: 0) {
                Button(action: actions.onPickBanner) { banner }
                    .buttonStyle(.plain)
                    .allowsHitTesting(!state.isSaving)
                Spacer().frame(height: 52)
            }
            logo.offset(x: 14, y: 154)
            caption
                .padding(.leading, 90)
                .padding(.trailing, 14)
                .padding(.bottom, 16)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        }
        .padding(1)
        .background(Color.white)
        .clipShape(shape)
        .overlay(shape.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }

    private var banner: some View {
        Color.clear
            .aspectRatio(16 / 9, contentMode: .fit)
            .overlay {
                if let bannerPreview {
                    Image(uiImage: bannerPreview).resizable().scaledToFill()
                } else if state.bannerUrl != nil {
                    RemoteImage(url: state.bannerUrl)
                } else {
                    EmptyBanner()
                }
            }
            .overlay { if let progress = state.bannerUploadProgress?.floatValue { UploadProgress(progress: progress) } }
            .clipped()
            .contentShape(Rectangle())
            .accessibilityLabel("League banner")
    }

    private var logo: some View {
        let outer = RoundedRectangle(cornerRadius: 16, style: .continuous)
        return Button(action: actions.onPickLogo) {
            ZStack {
                logoEmpty
                if let logoPreview {
                    Image(uiImage: logoPreview).resizable().scaledToFill().frame(width: 56, height: 56)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                } else if state.logoUrl != nil {
                    RemoteImage(url: state.logoUrl).frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                } else {
                    CrIcon("ic_add_a_photo", size: 24, tint: CrichereColor.primary)
                }
                if state.logoUploadProgress != nil { CrichereSpinner(color: .white, lineWidth: 2, size: 22) }
            }
            .frame(width: 64, height: 64)
            .clipShape(outer)
            .overlay(outer.strokeBorder(Color.white, lineWidth: 4))
        }
        .buttonStyle(.plain)
        .allowsHitTesting(!state.isSaving)
        .accessibilityLabel(logoPreview != nil || state.logoUrl != nil ? "League logo" : "Add logo")
    }

    private var caption: some View {
        let blank = state.name.trimmingCharacters(in: .whitespaces).isEmpty
        // I1/I3: "<ground> · <date>", falling back to the district until a ground is chosen.
        let parts = [state.groundDisplayName ?? state.district, state.startsOn.map { formatIsoDay($0, "d MMM") }].compactMap { $0 }
        return VStack(alignment: .leading, spacing: 3) {
            Text(blank ? "League name" : state.name)
                .crichere(.archivo(15, .bold, lineHeight: 16.5), blank ? previewPlaceholder : CrichereColor.onBackground)
                .lineLimit(2)
            Text(parts.isEmpty ? "Ground name · start date" : parts.joined(separator: " · "))
                .crichere(.sans(11.5, lineHeight: 11.5), parts.isEmpty ? previewPlaceholder : CrichereColor.onSurfaceVariant)
        }
    }
}

private struct EmptyBanner: View {
    var body: some View {
        VStack(spacing: 0) {
            CrIcon("ic_wallpaper", size: 28, tint: CrichereColor.primary)
            Spacer().frame(height: 8)
            Text("Add banner").crichere(.sans(12.5, .semiBold, lineHeight: 12.5), CrichereColor.primary)
            Spacer().frame(height: 4)
            Text("16:9 · optional").crichere(.sans(11, lineHeight: 11), CrichereColor.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(bannerDash, style: StrokeStyle(lineWidth: 2, dash: [6, 4])))
        .padding(8)
        .background(bannerEmpty)
    }
}

/// I3: "Uploading 42%" chip plus a bar along the banner's bottom edge.
private struct UploadProgress: View {
    let progress: Float

    var body: some View {
        ZStack {
            Text("Uploading \(Int(progress * 100 + 0.0001))%")
                .crichere(.mono(10.5, .semiBold, lineHeight: 10.5), .white)
                .padding(.horizontal, 9)
                .frame(height: 24)
                .background(Color(hex: 0x0E1A11, opacity: 0xBF / 255.0), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .padding(8)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Color.black.opacity(0.2)
                    CrichereColor.primary.frame(width: proxy.size.width * CGFloat(min(max(progress, 0), 1)))
                }
            }
            .frame(height: 4)
            .frame(maxHeight: .infinity, alignment: .bottom)
        }
    }
}

private struct ImageActions: View {
    let hasLogo: Bool
    let hasBanner: Bool
    let enabled: Bool
    let actions: LeagueCreationActions

    var body: some View {
        if hasLogo || hasBanner {
            Spacer().frame(height: 12)
            FlowRow(spacing: 14, lineSpacing: 8) {
                if hasLogo {
                    action("Change logo", CrichereColor.primary, actions.onPickLogo)
                    action("Remove logo", CrichereColor.errorStrong, actions.onRemoveLogo)
                }
                if hasBanner {
                    action("Change banner", CrichereColor.primary, actions.onPickBanner)
                    action("Remove banner", CrichereColor.errorStrong, actions.onRemoveBanner)
                }
            }
        }
    }

    private func action(_ text: String, _ color: Color, _ onClick: @escaping () -> Void) -> some View {
        Button(action: onClick) { Text(text).crichere(.sans(12, .semiBold, lineHeight: 12), color) }
            .buttonStyle(.plain)
            .allowsHitTesting(enabled)
    }
}

/// Compose `FlowRow`: items left to right, wrapping onto new lines.
private struct FlowRow: Layout {
    var spacing: CGFloat
    var lineSpacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: rows.last.map { $0.y + $0.height } ?? 0)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.items {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: bounds.minY + row.y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
        }
    }

    private struct Row { var items: [Int] = []; var width: CGFloat = 0; var y: CGFloat = 0; var height: CGFloat = 0 }

    private func arrange(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            var row = rows[rows.count - 1]
            if !row.items.isEmpty && row.width + spacing + size.width > width {
                rows.append(Row(y: row.y + row.height + lineSpacing))
                row = rows[rows.count - 1]
            }
            row.width += (row.items.isEmpty ? 0 : spacing) + size.width
            row.height = max(row.height, size.height)
            row.items.append(index)
            rows[rows.count - 1] = row
        }
        return rows
    }
}

// MARK: - Location, ground, awards

/// I9: helper line, "Use my location", then State / District.
private struct LocationSection: View {
    let state: LeagueCreationState
    let actions: LeagueCreationActions

    var body: some View {
        SectionHeading(text: "Location").id(LeagueField.state)
        Spacer().frame(height: 3)
        Text("Players near this place see the league first.").crichere(.sans(12.5, lineHeight: 17.5), CrichereColor.onSurfaceVariant)
        Spacer().frame(height: 12)
        LocationPill(isLocating: state.isLocating, iconSize: 18, fontSize: 13.5, action: actions.onUseMyLocation)
        Spacer().frame(height: 5)
        CrichereSelectField(label: "State", options: state.states, selected: state.states.first { $0.name == state.state },
                            optionLabel: { $0.name }, onSelected: actions.onStateSelected, look: .form, error: state.fieldErrors[.state])
        Spacer().frame(height: 5)
        CrichereSelectField(label: "District", options: state.districts, selected: state.districts.first { $0.name == state.district },
                            optionLabel: { $0.name }, onSelected: actions.onDistrictSelected, enabled: state.state != nil, look: .form,
                            error: state.fieldErrors[.district])
    }
}

/// I4: search existing grounds, or the selected one; "Register a new ground" opens the map (I5).
private struct GroundSection: View {
    let state: LeagueCreationState
    let error: String?
    let actions: LeagueCreationActions

    var body: some View {
        SectionHeading(text: "Ground").id(LeagueField.ground)
        if state.groundId != nil && !state.isChangingGround {
            Spacer().frame(height: 7)
            selected
        } else {
            CrichereTextField(text: Binding(get: { state.groundSearchQuery }, set: actions.onGroundQueryChanged), label: "Search grounds",
                              look: .form, error: error, trailingIcon: "ic_search", capitalization: .words)
            let results = state.groundSearchQuery.trimmingCharacters(in: .whitespaces).isEmpty ? [] : state.groundSearchResults
            if !results.isEmpty {
                Spacer().frame(height: 10)
                resultList(results)
            }
            Spacer().frame(height: 9)
            Button(action: actions.onRegisterGround) {
                HStack(spacing: 6) {
                    CrIcon("ic_add_location_alt", size: 19, tint: CrichereColor.primary).padding(.leading, 2)
                    Text("Can't find it? Register a new ground").crichere(.sans(13.5, .semiBold, lineHeight: 13.5), CrichereColor.primary)
                    Spacer(minLength: 0)
                }
                .frame(height: 31)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private var selected: some View {
        let card = RoundedRectangle(cornerRadius: 14, style: .continuous)
        return HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Selected").crichere(.sans(11, .medium, lineHeight: 11), CrichereColor.onSurfaceVariant)
                Text(state.groundDisplayName ?? "Ground selected").crichere(.sans(14, .semiBold, lineHeight: 16.8), CrichereColor.onBackground)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            // Design update #6 (I10): a league always has a ground, so edit mode offers Change, never Clear.
            Button(action: state.isEditMode ? actions.onChangeGround : actions.onClearGround) {
                Text(state.isEditMode ? "Change" : "Clear").crichere(.sans(13, .semiBold, lineHeight: 13), CrichereColor.primary)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 15)
        .frame(height: 58)
        .background(Color.white, in: card)
        .overlay(card.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }

    private func resultList(_ results: [GroundDto]) -> some View {
        let card = RoundedRectangle(cornerRadius: 14, style: .continuous)
        return VStack(spacing: 0) {
            ForEach(Array(results.enumerated()), id: \.element.id) { index, ground in
                Button { actions.onGroundSelected(ground) } label: {
                    HStack(spacing: 10) {
                        CrIcon("ic_stadium", size: 19, tint: CrichereColor.primary)
                        Text("\(ground.name) -- \(ground.district)").crichere(.sans(13.5, .medium, lineHeight: 16.2), CrichereColor.onBackground)
                            .lineLimit(2)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 13)
                    .frame(height: 49)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.pressHighlight)
                if index < results.count - 1 { Rectangle().fill(CrichereColor.surfaceVariant).frame(height: 1) }
            }
        }
        .padding(1)
        .background(Color.white)
        .clipShape(card)
        .overlay(card.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }
}

/// I8: one card per award -- name, cash, trophy, Remove -- then "Add another award".
private struct AwardsSection: View {
    let awards: [AwardDraft]
    let actions: LeagueCreationActions

    var body: some View {
        SectionHeading(text: "Awards")
        Spacer().frame(height: 3)
        Text("Shown on the league page. Leave the name blank to drop an award.").crichere(.sans(12.5, lineHeight: 17.5), CrichereColor.onSurfaceVariant)
        ForEach(Array(awards.enumerated()), id: \.offset) { index, award in
            Spacer().frame(height: 12)
            AwardCard(award: award, index: index, actions: actions)
        }
        Spacer().frame(height: 12)
        Button(action: actions.onAddAward) {
            HStack(spacing: 6) {
                CrIcon("ic_add", size: 19, tint: CrichereColor.primary).padding(.leading, 2)
                Text("Add another award").crichere(.sans(13.5, .semiBold, lineHeight: 13.5), CrichereColor.primary)
                Spacer(minLength: 0)
            }
            .frame(height: 27)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct AwardCard: View {
    let award: AwardDraft
    let index: Int
    let actions: LeagueCreationActions

    var body: some View {
        let card = RoundedRectangle(cornerRadius: 14, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            CrichereTextField(text: Binding(get: { award.name }, set: { actions.onAwardNameChanged(index, $0) }), label: "Award name",
                              look: .formOnCard, capitalization: .words)
            Spacer().frame(height: 3)
            CrichereTextField(text: Binding(get: { award.cashAmount }, set: { actions.onAwardCashChanged(index, amountInput($0)) }),
                              label: "Cash (optional)", look: .formOnCard, mono: true, keyboard: .decimalPad)
            Spacer().frame(height: 10)
            HStack(spacing: 0) {
                Button { actions.onAwardTrophyToggled(index) } label: {
                    HStack(spacing: 10) {
                        trophyBox
                        Text("Trophy").crichere(.sans(14, .medium, lineHeight: 14), CrichereColor.onBackground)
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(award.hasTrophy ? .isSelected : [])
                Button { actions.onRemoveAward(index) } label: {
                    Text("Remove").crichere(.sans(13, .semiBold, lineHeight: 13), CrichereColor.errorStrong)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 13)
        .padding(.top, 6)
        .padding(.bottom, 13)
        .background(Color.white, in: card)
        .overlay(card.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }

    private var trophyBox: some View {
        let box = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return ZStack {
            box.fill(award.hasTrophy ? CrichereColor.primary : .white)
            if award.hasTrophy {
                CrIcon("ic_check", size: 17, tint: .white)
            } else {
                box.strokeBorder(CrichereColor.outline, lineWidth: 2)
            }
        }
        .frame(width: 22, height: 22)
    }
}

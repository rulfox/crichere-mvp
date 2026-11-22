import SwiftUI
import PhotosUI
import Shared

/// Mirrors `OwnProfileViewModel`'s shared `StateFlow<OwnProfileState>` and `navigationEvents`
/// into SwiftUI-observable state -- same pattern `ProfileSetupViewModelWrapper` documents.
@MainActor
final class OwnProfileViewModelWrapper: ObservableObject {
    @Published var state: OwnProfileState
    @Published var navigationEvent: OwnProfileNavigationEvent?

    private let viewModel: OwnProfileViewModel

    init() {
        let viewModel = KoinHelper().ownProfileViewModel
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
        Task { [weak self] in
            for await event in viewModel.navigationEvents {
                self?.navigationEvent = event
            }
        }
    }

    func retry() { viewModel.retry() }
    func editProfile() { viewModel.editProfile() }
    func logout() { viewModel.logout() }
    func changePhoto(bytes: KotlinByteArray, contentType: String) { viewModel.changePhoto(bytes: bytes, contentType: contentType) }
}

/// My profile (design N1-N5, androidApp `ui/OwnProfileScreen.kt`): photo (or initials) with a camera
/// badge, name, the profile rows, Blocked people, Edit profile, Log out (confirmed first), Delete
/// account and the T2 legal links. Tapping the photo opens the N2 sheet (View / Choose new -- no
/// Remove, a photo is required); with no photo it goes straight to the picker.
struct OwnProfileView: View {
    let onNavigateToEditProfile: () -> Void
    let onNavigateToPhoneEntry: () -> Void
    var onViewPhoto: (String) -> Void = { _ in }
    let onDeleteAccount: () -> Void
    let onBlockedPeople: () -> Void

    @StateObject private var wrapper = OwnProfileViewModelWrapper()
    @State private var showPhotoSheet = false
    @State private var showLogoutConfirm = false
    @State private var pickerShown = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var pickedPhoto: PickedPhoto?
    /// The cropped image, shown while it uploads so the change feels immediate.
    @State private var localPhoto: UIImage?
    @State private var legalPage: URL?

    var body: some View {
        OwnProfileContent(
            state: wrapper.state,
            localPhoto: localPhoto,
            actions: OwnProfileActions(
                onPhotoTap: { if wrapper.state.photoUrl != nil { showPhotoSheet = true } else { pickerShown = true } },
                onEdit: wrapper.editProfile,
                onLogout: { showLogoutConfirm = true },
                onDeleteAccount: onDeleteAccount,
                onBlockedPeople: onBlockedPeople,
                onRetry: wrapper.retry,
                onOpenLegalPage: { legalPage = $0 }
            )
        )
        .onAppear { wrapper.retry() }
        .onChange(of: wrapper.state.isUploadingPhoto) { _, uploading in if !uploading { localPhoto = nil } }
        .onReceive(wrapper.$navigationEvent) { event in
            switch onEnum(of: event) {
            case .navigateToEditProfile: onNavigateToEditProfile()
            case .navigateToPhoneEntry: onNavigateToPhoneEntry()
            case nil: break
            }
        }
        .sheet(item: $legalPage) { SafariView(url: $0).ignoresSafeArea() }
        .photosPicker(isPresented: $pickerShown, selection: $pickerItem, matching: .images)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            pickerItem = nil
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self) else { return }
                pickedPhoto = decodePickedPhoto(data, fileName: nil)
            }
        }
        .crichereSheet(isPresented: $showPhotoSheet) {
            PhotoSheet(photoUrl: wrapper.state.photoUrl, name: wrapper.state.name ?? "",
                       onView: {
                           showPhotoSheet = false
                           if let url = wrapper.state.photoUrl { onViewPhoto(url) }
                       },
                       onChoose: {
                           showPhotoSheet = false
                           pickerShown = true
                       })
        }
        .crichereSheet(item: $pickedPhoto) { photo in
            PhotoCropSheet(
                photo: photo, title: "Preview", subtitle: "Profile photo · drag or pinch to adjust",
                onDismiss: { pickedPhoto = nil },
                onChooseAnother: {
                    pickedPhoto = nil
                    pickerShown = true
                },
                onUsePhoto: { square in
                    pickedPhoto = nil
                    guard let jpeg = encodeCrop(photo, square) else { return }
                    localPhoto = UIImage(data: jpeg)
                    wrapper.changePhoto(bytes: toKotlinByteArray(jpeg), contentType: "image/jpeg")
                }
            )
        }
        .crichereDialog(isPresented: $showLogoutConfirm) {
            CrichereDialog(title: "Log out of Crichere?", message: "You'll need an SMS code to sign back in.", buttons: [
                DialogButton(label: "Cancel") { showLogoutConfirm = false },
                DialogButton(label: "Log out", color: CrichereColor.errorStrong) {
                    showLogoutConfirm = false
                    wrapper.logout()
                },
            ])
        }
    }
}

struct OwnProfileActions {
    var onPhotoTap: () -> Void = {}
    var onEdit: () -> Void = {}
    var onLogout: () -> Void = {}
    var onDeleteAccount: () -> Void = {}
    var onBlockedPeople: () -> Void = {}
    var onRetry: () -> Void = {}
    var onOpenLegalPage: (URL) -> Void = { _ in }
}

/// The screen, stateless for the DEBUG parity gallery.
struct OwnProfileContent: View {
    let state: OwnProfileState
    var localPhoto: UIImage? = nil
    var actions = OwnProfileActions()

    var body: some View {
        Group {
            if !state.hasProfile && state.isLoading {
                CrichereSpinner(color: CrichereColor.primary, lineWidth: 4, size: 40)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if !state.hasProfile {
                LoadErrorView(title: "Couldn't load your profile", onRetry: actions.onRetry)
            } else {
                ScrollView { profile }
            }
        }
        .background(CrichereColor.background.ignoresSafeArea())
    }

    private var profile: some View {
        let name = state.name ?? ""
        return VStack(spacing: 0) {
            Button(action: actions.onPhotoTap) {
                ZStack(alignment: .bottomTrailing) {
                    ProfilePhoto(url: state.photoUrl, local: localPhoto, name: name, size: 104)
                    if state.isUploadingPhoto {
                        ZStack {
                            Circle().stroke(Color.white.opacity(0.6), lineWidth: 3)
                            Circle().trim(from: 0, to: CGFloat(min(max(state.photoUploadProgress, 0.05), 1)))
                                .stroke(CrichereColor.primary, style: StrokeStyle(lineWidth: 3, lineCap: .butt))
                                .rotationEffect(.degrees(-90))
                        }
                        .padding(1.5)
                        .frame(width: 104, height: 104)
                    }
                    CrIcon(state.photoUrl != nil ? "ic_photo_camera" : "ic_add_a_photo", size: 17, tint: .white)
                        .frame(width: 28, height: 28)
                        .background(CrichereColor.primary, in: Circle())
                        .padding(3)
                        .background(CrichereColor.background, in: Circle())
                }
                .frame(width: 104, height: 104)
            }
            .buttonStyle(.plain)
            .disabled(state.isUploadingPhoto)
            .accessibilityLabel(state.photoUrl != nil ? "Profile photo options" : "Add a photo")
            Spacer().frame(height: 10)
            Text(name).crichere(.archivo(22, .extraBold, lineHeight: 24.2), CrichereColor.onBackground).multilineTextAlignment(.center)
            if state.photoUrl == nil {
                Spacer().frame(height: 14)
                Button(action: actions.onPhotoTap) {
                    Text("Add a photo so organizers recognise you").crichere(.sans(13, .semiBold, lineHeight: 13), CrichereColor.primary)
                }
                .buttonStyle(.plain)
                .disabled(state.isUploadingPhoto)
            }
            if let error = state.photoError {
                Spacer().frame(height: 10)
                Text(error).crichere(.sans(12.5, .medium, lineHeight: 16), CrichereColor.error).multilineTextAlignment(.center)
            }
            Spacer().frame(height: 16)
            ProfileRows(state: state)
            Spacer().frame(height: 16)
            BlockedPeopleRow(action: actions.onBlockedPeople)
            Spacer().frame(height: 16)
            Button(action: actions.onEdit) {
                Text("Edit profile").crichere(.sans(14.5, .semiBold, lineHeight: 14.5)).frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
            }
            .buttonStyle(PillButtonStyle(fill: CrichereColor.primary, pressedFill: CrichereColor.greenPressed, content: .white, radius: 24))
            Spacer().frame(height: 16)
            Button(action: actions.onLogout) {
                Text("Log out").crichere(.sans(14.5, .semiBold, lineHeight: 14.5)).frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
            }
            .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.errorStrong.opacity(0.08), content: CrichereColor.errorStrong,
                                         border: CrichereColor.outline, radius: 24))
            // Design D1: a plain 48 pt text row, quieter than Log out.
            Spacer().frame(height: 24)
            Button(action: actions.onDeleteAccount) {
                Text("Delete account").crichere(.sans(14, .semiBold, lineHeight: 14)).frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
            }
            .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.errorStrong.opacity(0.08), content: CrichereColor.errorStrong, radius: 24))
            // Board T2: quiet footer links, so the policies stay reachable after sign in.
            HStack(spacing: 0) {
                legalLink("Terms", termsUrl)
                Text("·").crichere(.sans(13), CrichereColor.onSurfaceVariant).padding(.horizontal, 4)
                legalLink("Privacy Policy", privacyUrl)
            }
            .padding(.top, 8)
        }
        .padding(.horizontal, 19)
        .padding(.top, 19)
        .padding(.bottom, 24)
    }

    private func legalLink(_ label: String, _ url: URL) -> some View {
        Button { actions.onOpenLegalPage(url) } label: {
            Text(label).crichere(.sans(13), CrichereColor.onSurfaceVariant).padding(.horizontal, 6).padding(.vertical, 14)
        }
        .buttonStyle(.plain)
    }
}

/// The profile facts card: label / value rows (37.5 pt), hairline dividers, "--" for missing values.
private struct ProfileRows: View {
    let state: OwnProfileState

    var body: some View {
        var rows: [(String, String?)] = [
            ("State", state.state),
            ("District", state.district),
            ("Playing role", state.playingRole?.label),
            ("Batting style", state.battingStyle?.displayName),
        ]
        if let bowling = state.bowlingStyle { rows.append(("Bowling style", bowling.displayName)) }
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        return VStack(spacing: 0) {
            ForEach(rows.indices, id: \.self) { index in
                if index > 0 { Rectangle().fill(CrichereColor.surfaceVariant).frame(height: 1) }
                HStack(spacing: 12) {
                    Text(rows[index].0).crichere(.sans(13.5, lineHeight: 13.5), CrichereColor.onSurfaceVariant)
                    Text(rows[index].1 ?? "--").crichere(.sans(13.5, lineHeight: 13.5), CrichereColor.onBackground)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
                .padding(.horizontal, 14)
                .frame(height: 37.5)
            }
        }
        .padding(1)
        .background(CrichereColor.surface, in: shape)
        .overlay(shape.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
    }
}

/// Board R10a: a list row, not a button, so it reads as "go somewhere".
private struct BlockedPeopleRow: View {
    let action: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        Button(action: action) {
            HStack(spacing: 12) {
                CrIcon("ic_block", size: 20, tint: CrichereColor.onSurfaceVariant)
                Text("Blocked people").crichere(.sans(15), CrichereColor.onBackground).frame(maxWidth: .infinity, alignment: .leading)
                CrIcon("ic_arrow_forward", size: 18, tint: CrichereColor.onSurfaceVariant)
            }
            .padding(.leading, 16)
            .padding(.trailing, 14)
            .frame(height: 52)
            .background(CrichereColor.surface, in: shape)
            .overlay(shape.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
        }
        .buttonStyle(PressHighlight(shape: shape))
    }
}

#if DEBUG
/// The N2 sheet with fixture content, for the parity gallery.
struct PhotoSheetPreview: View {
    var body: some View { PhotoSheet(photoUrl: nil, name: "Aarav Pawar", onView: {}, onChoose: {}) }
}
#endif

/// N2: the photo sheet -- current photo, View photo, Choose new photo, and why there's no Remove.
private struct PhotoSheet: View {
    let photoUrl: String?
    let name: String
    let onView: () -> Void
    let onChoose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer().frame(height: 10)
            HStack(spacing: 14) {
                ProfilePhoto(url: photoUrl, local: nil, name: name, size: 56)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Profile photo").crichere(.sans(16, .semiBold, lineHeight: 16), CrichereColor.onBackground)
                    Text("Visible to organizers and franchises").crichere(.sans(12, lineHeight: 12), CrichereColor.onSurfaceVariant)
                }
            }
            .padding(.horizontal, 20)
            Spacer().frame(height: 14)
            sheetAction("ic_visibility", "View photo", onView)
            sheetAction("ic_photo_library", "Choose new photo", onChoose)
            Spacer().frame(height: 8)
            Text("A photo is required for a complete profile, so it can be replaced but not removed.")
                .crichere(.sans(12, lineHeight: 17), CrichereColor.onSurfaceVariant)
                .padding(.horizontal, 22)
        }
        .padding(.bottom, 18)
    }

    private func sheetAction(_ icon: String, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                CrIcon(icon, size: 22, tint: CrichereColor.primary)
                Text(label).crichere(.sans(15, .medium, lineHeight: 15), CrichereColor.onBackground)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 22)
            .frame(height: 52)
        }
        .buttonStyle(.pressHighlight)
    }
}

/// Photo, the just-cropped local image while it uploads, or initials on green (N3).
struct ProfilePhoto: View {
    let url: String?
    let local: UIImage?
    let name: String
    let size: CGFloat

    var body: some View {
        ZStack {
            Circle().fill(CrichereColor.greenContainer)
            Text(initials(name)).crichere(.archivo(size * 0.327, .extraBold, lineHeight: size * 0.327), CrichereColor.primary)
            if let local {
                Image(uiImage: local).resizable().scaledToFill()
            } else {
                RemoteImage(url: url)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityLabel(name)
    }
}

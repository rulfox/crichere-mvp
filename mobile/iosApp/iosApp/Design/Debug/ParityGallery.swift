#if DEBUG
import SwiftUI
import Shared

/// DEBUG-only parity gallery: `xcrun simctl launch booted com.crichere.app -parity <board-id>` renders
/// one screen state from fixture data, so iOS captures can be compared with the Android ones
/// (docs/IOS-REDESIGN-PLAN.md §5) without logging in. Screens are stateless content views (state in,
/// actions out); the fixtures are built from the same shared state classes Android renders.
enum ParityGallery {
    static var requestedBoard: String? {
        let args = ProcessInfo.processInfo.arguments
        guard let index = args.firstIndex(of: "-parity"), index + 1 < args.count else { return nil }
        return args[index + 1]
    }

    @MainActor @ViewBuilder
    static func view(for board: String) -> some View {
        if let screen = boards[board] {
            screen()
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text("Unknown board \(board)").crichere(.archivo(22, .bold), CrichereColor.error)
                Text(boards.keys.sorted().joined(separator: "  ")).crichere(.mono(12), CrichereColor.inkMuted)
            }
            .padding(20)
        }
    }

    @MainActor static let boards: [String: () -> AnyView] = [signIn, verify, profileSetup, dashboard, myLeagues, myProfile, leagueDetail, join, claim, viewer, creation].reduce(into: [:]) { $0.merge($1) { a, _ in a } }

    // MARK: - A Sign in (PhoneEntryScreen.kt)

    private static func phoneState(_ number: String = "", submitting: Bool = false, error: String? = nil, terms: Bool = false) -> PhoneEntryState {
        PhoneEntryState(phoneNumber: number, country: Countries.shared.`default`, isSubmitting: submitting, errorMessage: error, termsAccepted: terms)
    }

    private static func signInScreen(_ state: PhoneEntryState, lockout: Bool = false, deletion: String? = nil, focus: Bool = false) -> AnyView {
        AnyView(PhoneEntryContent(state: state, showLockoutNotice: lockout, deletionScheduledOn: deletion,
                                  onPhoneNumberChanged: { _ in }, onTermsAcceptedChanged: { _ in }, onSendCode: {}, autoFocus: focus))
    }

    @MainActor private static let signIn: [String: () -> AnyView] = [
        "A1": { signInScreen(phoneState()) },
        "A2": { signInScreen(phoneState(), focus: true) },
        "A3": { signInScreen(phoneState("9876543210", terms: true)) },
        "A4": { signInScreen(phoneState("98765", error: "Enter a valid 10-digit mobile number.", terms: true)) },
        "A5": { signInScreen(phoneState("9876543210", submitting: true, terms: true)) },
        "T1a": { signInScreen(phoneState("9876543210")) },
        "B6": { signInScreen(phoneState(), lockout: true) },
        "D7": { signInScreen(phoneState(), deletion: "25 Oct 2026") },
    ]

    // MARK: - B Verify code (OtpVerifyScreen.kt)

    private static func otpState(_ code: String = "", verifying: Bool = false, resending: Bool = false, error: String? = nil,
                                 attempts: Int32 = 5, used: Int32 = 0, cooldown: Int32 = 42, canResend: Bool = false) -> OtpVerifyState {
        OtpVerifyState(code: code, isVerifying: verifying, isResending: resending, errorMessage: error, attemptsRemaining: attempts,
                       resendsUsed: used, maxResends: 3, cooldownSecondsRemaining: cooldown, canResend: canResend)
    }

    private static func verifyScreen(_ state: OtpVerifyState, resent: Bool = false, focus: Bool = false) -> AnyView {
        AnyView(OtpVerifyContent(state: state, phoneNumber: "+919876543210", onCodeChanged: { _ in }, onVerify: {}, onResend: {},
                                 onStartOver: {}, showResentInitially: resent, autoFocus: focus))
    }

    @MainActor private static let verify: [String: () -> AnyView] = [
        "B1": { verifyScreen(otpState(), focus: true) },
        "B2": { verifyScreen(otpState("482913")) },
        "B3": { verifyScreen(otpState("482910", error: "Incorrect code. 4 attempts remaining.", attempts: 4)) },
        "B4": { verifyScreen(otpState("482913", verifying: true)) },
        "B5": { verifyScreen(otpState(cooldown: 0, canResend: true)) },
        "B5b": { verifyScreen(otpState(used: 1, cooldown: 58), resent: true) },
        "B7": { verifyScreen(otpState(used: 3, cooldown: 0)) },
        "S1": { AnyView(AppStartContent(isOffline: false, onRetry: {})) },
        "S2": { AnyView(AppStartContent(isOffline: true, onRetry: {})) },
    ]

    // MARK: - C Profile setup (ProfileSetupScreen.kt)

    static let states = [StateDto(code: "MH", name: "Maharashtra"), StateDto(code: "KA", name: "Karnataka"), StateDto(code: "GA", name: "Goa")]
    static let districts = [DistrictDto(id: "d1", name: "Kolhapur"), DistrictDto(id: "d2", name: "Pune"), DistrictDto(id: "d3", name: "Sangli")]

    private static func profileState(name: String = "", photo: Bool = false, place: Bool = false, role: PlayingRole? = nil,
                                     batting: BattingStyle? = nil, bowling: BowlingStyle? = nil, uploading: Float? = nil,
                                     uploadError: String? = nil, saving: Bool = false, error: String? = nil, nameError: String? = nil,
                                     loaded: Bool = false) -> ProfileSetupState {
        // A saved photo needs a URL for isSaveEnabled; the gallery shows the local image over it.
        let url: String? = photo ? "https://fixtures.invalid/photo.jpg" : nil
        let values = ProfileFormValues(name: name, photoUrl: url, state: place ? "Maharashtra" : nil, district: place ? "Kolhapur" : nil,
                                       playingRole: role, battingStyle: batting, bowlingStyle: bowling)
        return ProfileSetupState(
            isLoading: false, name: name, photoUrl: url, state: place ? "Maharashtra" : nil, district: place ? "Kolhapur" : nil,
            playingRole: role, battingStyle: batting, bowlingStyle: bowling, states: states, districts: place ? districts : [],
            isUploadingPhoto: uploading != nil, photoUploadProgress: uploading ?? 0,
            uploadingPhotoName: uploading != nil ? "IMG_2041.jpg" : nil, uploadingPhotoSizeBytes: uploading != nil ? KotlinLong(value: 412_000) : nil,
            isLocating: false, isSaving: saving, errorMessage: error, nameError: nameError, photoUploadErrorMessage: uploadError,
            initialFocusField: .name, loadedValues: loaded ? values : nil)
    }

    private static func profileScreen(_ state: ProfileSetupState, edit: Bool = false, photo: Bool = false) -> AnyView {
        AnyView(ProfileSetupContent(state: state, showBack: edit, localPhoto: photo ? DesignGallery.samplePhoto.image : nil))
    }

    @MainActor private static let profileSetup: [String: () -> AnyView] = [
        "C1": { profileScreen(profileState()) },
        "C1-error": { profileScreen(profileState(name: "A", nameError: "Enter your full name.")) },
        "C2": { profileScreen(profileState(name: "Aarav Pawar", photo: true, place: true, role: .bowler, batting: .rightHand, bowling: .rightArmFast), photo: true) },
        "C4": { profileScreen(profileState(name: "Aarav Pawar", uploading: 0.42), photo: true) },
        "C6": { profileScreen(profileState(name: "Aarav Pawar", uploadError: "Couldn't upload photo. Please try again."), photo: true) },
        "C7": { profileScreen(profileState(name: "Aarav Pawar", photo: true, place: true, role: .batsman, batting: .leftHand, saving: true), photo: true) },
        "C8": { profileScreen(profileState(name: "Aarav Pawar", photo: true, place: true, role: .batsman, batting: .leftHand,
                                           error: "Couldn't save your profile. Please try again."), photo: true) },
        "C1-edit": { profileScreen(profileState(name: "Aarav Pawar", photo: true, place: true, role: .allRounder, batting: .rightHand, bowling: .leftArmOrthodox, loaded: true), edit: true, photo: true) },
    ]

    // MARK: - D Dashboard (LeagueDashboardScreen.kt) inside the tab shell

    /// A tab screen above the board's tab bar, like `MainTabView`.
    static func inTabShell<Content: View>(_ tab: MainTab, @ViewBuilder _ content: () -> Content) -> AnyView {
        AnyView(VStack(spacing: 0) {
            content().frame(maxWidth: .infinity, maxHeight: .infinity)
            CrichereTabBar(tabs: MainTabView.tabs, selected: tab) { _ in }
        }.background(CrichereColor.background.ignoresSafeArea()))
    }

    private static func dashboardState(leagues: [LeagueDto] = Fixtures.dashboardLeagues, loading: Bool = false, state: String? = nil,
                                       district: String? = nil, near: Bool = false, locating: Bool = false, error: String? = nil) -> LeagueDashboardState {
        LeagueDashboardState(isLoading: loading, leagues: leagues, states: states, districts: districts, selectedState: state,
                             selectedDistrict: district, isNearMode: near, isLocating: locating, errorMessage: error,
                             viewerName: "Aarav Pawar", viewerPhotoUrl: nil)
    }

    private static func dashboardScreen(_ state: LeagueDashboardState, locationOff: Bool = false) -> AnyView {
        inTabShell(.dashboard) { LeagueDashboardContent(state: state, showLocationOff: locationOff) }
    }

    @MainActor private static let dashboard: [String: () -> AnyView] = [
        "D1": { dashboardScreen(dashboardState()) },
        "D1-filtered": { dashboardScreen(dashboardState(state: "Maharashtra", district: "Kolhapur")) },
        "D3": { dashboardScreen(dashboardState(near: true)) },
        "D3-locating": { dashboardScreen(dashboardState(locating: true)) },
        "D4": { dashboardScreen(dashboardState(), locationOff: true) },
        "D5": { dashboardScreen(dashboardState(leagues: [])) },
        "D5-filtered": { dashboardScreen(dashboardState(leagues: [], state: "Goa")) },
        "D-loading": { dashboardScreen(dashboardState(leagues: [], loading: true)) },
        "D-error": { dashboardScreen(dashboardState(error: "Couldn't load leagues. Check your connection.")) },
    ]

    // MARK: - M My leagues (MyLeaguesScreen.kt)

    private static func summary(_ league: LeagueDto) -> LeagueSummaryDto {
        LeagueSummaryDto(id: league.id, name: league.name, logoUrl: nil, district: league.district, state: league.state,
                         groundName: league.groundName, startsOn: league.startsOn, status: league.status, logoRemoved: false)
    }

    private static func myLeaguesScreen(_ state: MyLeaguesState) -> AnyView {
        inTabShell(.myLeagues) { MyLeaguesContent(state: state) }
    }

    @MainActor private static let myLeagues: [String: () -> AnyView] = [
        "M1": {
            let all = Fixtures.dashboardLeagues.map(summary)
            return myLeaguesScreen(MyLeaguesState(isLoading: false, data: MyLeaguesDto(organizing: [all[0]], playing: [all[1], all[2]],
                                                                                         franchiseOwner: [all[3]], following: [all[2]]), errorMessage: nil))
        },
        "M2": { myLeaguesScreen(MyLeaguesState(isLoading: false, data: MyLeaguesDto(organizing: [], playing: [], franchiseOwner: [], following: []), errorMessage: nil)) },
        "M3": { myLeaguesScreen(MyLeaguesState(isLoading: false, data: nil, errorMessage: "Couldn't load your leagues.")) },
        "M-loading": { myLeaguesScreen(MyLeaguesState(isLoading: true, data: nil, errorMessage: nil)) },
    ]

    // MARK: - N My profile (OwnProfileScreen.kt)

    private static func ownState(photo: Bool = true, uploading: Float? = nil, photoError: String? = nil, failed: Bool = false,
                                 loading: Bool = false) -> OwnProfileState {
        OwnProfileState(isLoading: loading, name: failed || loading ? nil : "Aarav Pawar",
                        photoUrl: photo ? "https://fixtures.invalid/photo.jpg" : nil, country: "IN", state: "Maharashtra",
                        district: "Kolhapur", playingRole: .allRounder, battingStyle: .rightHand, bowlingStyle: .leftArmOrthodox,
                        loadFailed: failed, isUploadingPhoto: uploading != nil, photoUploadProgress: uploading ?? 0, photoError: photoError)
    }

    private static func ownScreen(_ state: OwnProfileState, local: Bool = true) -> AnyView {
        inTabShell(.myProfile) { OwnProfileContent(state: state, localPhoto: local ? DesignGallery.samplePhoto.image : nil) }
    }

    @MainActor private static let myProfile: [String: () -> AnyView] = [
        "N1": { ownScreen(ownState()) },
        "N3": { ownScreen(ownState(photo: false), local: false) },
        "N4": { ownScreen(ownState(uploading: 0.6)) },
        "N5": { ownScreen(ownState(photoError: "Couldn't update your photo. Please try again.")) },
        "N-error": { ownScreen(ownState(failed: true), local: false) },
        "N-loading": { ownScreen(ownState(loading: true), local: false) },
        "N2": {
            AnyView(ZStack(alignment: .bottom) {
                ownScreen(ownState())
                ParitySheetPreview { PhotoSheetPreview() }
            })
        },
        "N-logout": {
            AnyView(ZStack {
                ownScreen(ownState())
                ParityDialogPreview {
                    CrichereDialog(title: "Log out of Crichere?", message: "You'll need an SMS code to sign back in.", buttons: [
                        DialogButton(label: "Cancel") {}, DialogButton(label: "Log out", color: CrichereColor.errorStrong) {},
                    ])
                }
            })
        },
    ]

    // MARK: - E League detail (LeagueDetailScreen.kt)

    private static func detailState(_ league: LeagueDto?, me: String? = "u-visitor", organizer: Bool = false, loading: Bool = false,
                                    completing: Bool = false, removing: Set<String> = []) -> LeagueDetailState {
        LeagueDetailState(isLoading: loading, league: league, currentUserId: me, isOrganizer: organizer, isCompleting: completing,
                          isJoining: false, isClaiming: false, isTogglingFollow: false, isLeaveRequesting: false, removingIds: removing,
                          respondingToLeaveRequestIds: [], errorMessage: nil, completionNotice: nil)
    }

    private static func detailScreen(_ state: LeagueDetailState, blocked: Set<String> = []) -> AnyView {
        AnyView(LeagueDetailContent(state: state, blocked: blocked))
    }

    @MainActor private static let leagueDetail: [String: () -> AnyView] = [
        "E1": { detailScreen(detailState(Fixtures.league(banner: true, awards: Fixtures.awards))) },
        "E1-compact": { detailScreen(detailState(Fixtures.league())) },
        "E2": { detailScreen(detailState(Fixtures.league(banner: true), me: "u1")) },
        "E3": {
            var players = Fixtures.players
            players[0] = Fixtures.player(1, name: "Aarav Pawar", role: .allRounder, screenshot: true, leaveRequested: true)
            return detailScreen(detailState(Fixtures.league(players: players, banner: true), me: "u1"))
        },
        "E4": {
            var players = Fixtures.players
            players[2] = Fixtures.player(3, name: "Sahil Kulkarni", role: .bowler, leaveRequested: true)
            return detailScreen(detailState(Fixtures.league(players: players), me: "u-organizer", organizer: true, removing: ["p4"]))
        },
        "E14": { detailScreen(detailState(Fixtures.league(auctionStatus: .inProgress), me: "u-organizer", organizer: true)) },
        "E5": { detailScreen(detailState(Fixtures.league(playersRequired: 5, franchisesRequired: 2, following: true))) },
        "E13": { detailScreen(detailState(Fixtures.league(status: .completed, awards: Fixtures.awards), me: "u-organizer", organizer: true)) },
        "D9": { detailScreen(detailState(Fixtures.league(handover: true), me: "u-organizer", organizer: true)) },
        "R9a": { detailScreen(detailState(Fixtures.league()), blocked: ["u-organizer"]) },
        "R9b": { detailScreen(detailState(Fixtures.league(banner: true), me: "u1"), blocked: ["u-organizer"]) },
        "E-loading": { detailScreen(detailState(nil, loading: true)) },
        "E-error": { detailScreen(detailState(nil)) },
        "E2-leave": {
            AnyView(ZStack {
                detailScreen(detailState(Fixtures.league(banner: true), me: "u1"))
                ParityDialogPreview {
                    CrichereDialog(title: "Request to leave?",
                                   message: "The organizer can approve or dismiss it. Any refund is between you and the organizer; Crichere doesn't handle payments.",
                                   icon: "ic_logout", iconTint: CrichereColor.errorStrong, iconSize: 26,
                                   buttons: [DialogButton(label: "Cancel") {}, DialogButton(label: "Send request", color: CrichereColor.errorStrong) {}])
                }
            })
        },
        "E10": {
            AnyView(ZStack {
                detailScreen(detailState(Fixtures.league(), me: "u-organizer", organizer: true))
                ParityDialogPreview {
                    DestructiveConfirmDialog(title: "Mark Kolhapur Premier League as completed?",
                                             message: "This can't be undone. Rosters, auction results and awards stay visible to everyone. Nobody can join, edit the league or run the auction again.",
                                             confirmLabel: "Mark completed", submittingLabel: "Completing…", submitting: false, onDismiss: {}, onConfirm: {})
                }
            })
        },
    ]

    // MARK: - F Join as player (JoinLeagueScreen.kt)

    private static func joinState(_ league: LeagueDto? = Fixtures.league(), uploading: Float? = nil, attached: Bool = false, failed: Bool = false,
                                  errorTitle: String? = nil, error: String? = nil, loadFailed: Bool = false) -> JoinLeagueState {
        JoinLeagueState(isLoading: false, league: league, loadFailed: loadFailed, screenshotUrl: attached ? "https://fixtures.invalid/proof.jpg" : nil,
                        isUploadingScreenshot: uploading != nil, uploadProgress: uploading ?? 0, uploadFileName: "payment-proof.jpg",
                        uploadSizeBytes: KotlinLong(value: 421_000), uploadFailed: failed, isSubmitting: false, errorTitle: errorTitle,
                        errorMessage: error, joined: false, viewerName: "Aarav Pawar", viewerRole: .allRounder, viewerBatting: .rightHand)
    }

    private static func joinScreen(_ state: JoinLeagueState, noUpi: Bool = false, copied: Bool = false, thumb: Bool = false) -> AnyView {
        AnyView(JoinLeagueContent(state: state, noUpiApp: noUpi, copied: copied, thumbnail: thumb ? DesignGallery.samplePhoto.image : nil))
    }

    @MainActor private static let join: [String: () -> AnyView] = [
        "F1": { joinScreen(joinState()) },
        "F2": { joinScreen(joinState(), noUpi: true, copied: true) },
        "F3": {
            AnyView(ZStack(alignment: .bottom) {
                joinScreen(joinState())
                ParitySheetPreview {
                    ScreenshotCheckSheet(image: DesignGallery.samplePhoto.image, checklist: ["Amount ₹500", "UPI ID sachin@okaxis", "Reference / UTR no."],
                                         onDismiss: {}, onChooseAnother: {}, onUse: {})
                }
            })
        },
        "F5": { joinScreen(joinState(uploading: 0.42), thumb: true) },
        "F5-attached": { joinScreen(joinState(attached: true), thumb: true) },
        "F5-failed": { joinScreen(joinState(failed: true), thumb: true) },
        "F6": { joinScreen(joinState(Fixtures.league(playerFee: nil))) },
        "F7": { joinScreen(joinState(attached: true, errorTitle: "Couldn't join", error: "Registration just filled up. Ask the organizer if a spot opens."), thumb: true) },
        "F9": { joinScreen(joinState(Fixtures.league(upiId: nil))) },
        "F-error": { joinScreen(joinState(nil, loadFailed: true)) },
    ]
    // MARK: - G Claim a franchise (ClaimFranchiseScreen.kt)

    private static func claimState(_ league: LeagueDto? = Fixtures.league(), name: String = "", nameError: Bool = false, logo: Bool = false,
                                   uploadingLogo: Bool = false, uploading: Float? = nil, attached: Bool = false, failed: Bool = false,
                                   submitting: Bool = false, errorTitle: String? = nil, error: String? = nil,
                                   loadFailed: Bool = false) -> ClaimFranchiseState {
        ClaimFranchiseState(isLoading: false, league: league, loadFailed: loadFailed, name: name, nameError: nameError,
                            logoUrl: logo ? "https://fixtures.invalid/logo.jpg" : nil, isUploadingLogo: uploadingLogo,
                            screenshotUrl: attached ? "https://fixtures.invalid/proof.jpg" : nil, isUploadingScreenshot: uploading != nil,
                            uploadProgress: uploading ?? 0, uploadFileName: "payment-proof.jpg", uploadSizeBytes: KotlinLong(value: 397_000),
                            uploadFailed: failed, isSubmitting: submitting, errorTitle: errorTitle, errorMessage: error, claimed: false)
    }

    private static func claimScreen(_ state: ClaimFranchiseState, logo: Bool = false, noUpi: Bool = false, thumb: Bool = false) -> AnyView {
        AnyView(ClaimFranchiseContent(state: state, logoPreview: logo ? DesignGallery.samplePhoto.image : nil, noUpiApp: noUpi, copied: noUpi,
                                      thumbnail: thumb ? DesignGallery.samplePhoto.image : nil))
    }

    @MainActor private static let claim: [String: () -> AnyView] = [
        "G1": { claimScreen(claimState()) },
        "G1-error": { claimScreen(claimState(nameError: true)) },
        "G1-initials": { claimScreen(claimState(name: "Kolhapur Kings")) },
        "G2": {
            AnyView(ZStack(alignment: .bottom) {
                claimScreen(claimState(name: "Kolhapur Kings"))
                ParitySheetPreview {
                    PhotoCropSheet(photo: DesignGallery.samplePhoto, title: "Preview", subtitle: "Franchise logo · drag to reposition",
                                   onDismiss: {}, onChooseAnother: {}, onUsePhoto: { _ in }, square: true, useLabel: "Use logo")
                }
            })
        },
        "G3": { claimScreen(claimState(name: "Kolhapur Kings", logo: true, attached: true), logo: true, thumb: true) },
        "G3-uploading": { claimScreen(claimState(name: "Kolhapur Kings", logo: true, uploadingLogo: true, uploading: 0.42), logo: true, thumb: true) },
        "G3-failed": { claimScreen(claimState(name: "Kolhapur Kings", failed: true), thumb: true) },
        "G3-noupi": { claimScreen(claimState(name: "Kolhapur Kings"), noUpi: true) },
        "G4": { claimScreen(claimState(Fixtures.league(franchiseFee: nil), name: "Kolhapur Kings")) },
        "G5": { claimScreen(claimState(name: "Kolhapur Kings", logo: true, attached: true, submitting: true), logo: true, thumb: true) },
        "G6": { claimScreen(claimState(name: "Kolhapur Kings", attached: true, errorTitle: "All franchise slots are taken.",
                                       error: "Another team claimed the last spot. Ask the organizer if more franchises open."), thumb: true) },
        "G7": { claimScreen(claimState(nil, loadFailed: true)) },
        "G9": { claimScreen(claimState(Fixtures.league(upiId: nil), name: "Kolhapur Kings")) },
    ]
    // MARK: - H Screenshot viewer (ScreenshotViewerScreen.kt)

    private static func viewerBoard(_ view: ScreenshotViewerContent) -> AnyView { AnyView(view.lightStatusBarIcons(true)) }

    @MainActor private static let viewer: [String: () -> AnyView] = [
        "H1": { viewerBoard(ScreenshotViewerContent(image: DesignGallery.samplePhoto.image, failed: false)) },
        "H1-profile": { viewerBoard(ScreenshotViewerContent(image: DesignGallery.samplePhoto.image, failed: false, title: "Profile photo")) },
        "H2": { viewerBoard(ScreenshotViewerContent(image: DesignGallery.samplePhoto.image, failed: false, initialScale: 2.4,
                                                initialOffset: CGSize(width: 120, height: -60))) },
        "H3": { viewerBoard(ScreenshotViewerContent(image: nil, failed: true)) },
        "H-loading": { viewerBoard(ScreenshotViewerContent(image: nil, failed: false)) },
    ]
    // MARK: - I Create / edit league (LeagueCreationScreen.kt, GroundMapPicker.kt)

    private static let leagueStates = [StateDto(code: "MH", name: "Maharashtra"), StateDto(code: "KA", name: "Karnataka")]
    private static let leagueDistricts = [DistrictDto(id: "d1", name: "Kolhapur"), DistrictDto(id: "d2", name: "Sangli")]
    private static let grounds = [
        GroundDto(id: "g1", name: "Shahu Stadium", state: "Maharashtra", district: "Kolhapur", latitude: 16.7, longitude: 74.24),
        GroundDto(id: "g2", name: "Shivaji University Ground", state: "Maharashtra", district: "Kolhapur", latitude: 16.68, longitude: 74.25),
        GroundDto(id: "g3", name: "Shastri Nagar Maidan", state: "Maharashtra", district: "Kolhapur", latitude: 16.71, longitude: 74.22),
    ]

    private static func creationState(edit: Bool = false, dirty: Bool = false, validation: Bool = false, name: String = "",
                                      description: String = "", pendingLogo: Bool = false, pendingBanner: Bool = false,
                                      logoProgress: Float? = nil, bannerProgress: Float? = nil, located: Bool = false,
                                      ground: Bool = false, groundQuery: String = "", registering: Bool = false,
                                      newGroundName: String = "", pin: Bool = false, groundNameError: Bool = false,
                                      groundError: Bool = false, startsOn: String? = nil, format: String = "", other: Bool = false,
                                      fees: Bool = false, upi: String = "", awards: [AwardDraft] = [], saving: Bool = false,
                                      error: String? = nil) -> LeagueCreationState {
        LeagueCreationState(
            isLoading: false, loadFailed: false, isEditMode: edit, isDirty: dirty, showValidation: validation, validationAttempt: 0,
            name: name, description: description, logoUrl: nil, bannerUrl: nil, isUploadingLogo: logoProgress != nil,
            isUploadingBanner: bannerProgress != nil, hasPendingLogo: pendingLogo, hasPendingBanner: pendingBanner,
            logoUploadProgress: logoProgress.map { KotlinFloat(value: $0) }, bannerUploadProgress: bannerProgress.map { KotlinFloat(value: $0) },
            state: located ? "Maharashtra" : nil, district: located ? "Kolhapur" : nil, states: leagueStates, districts: located ? leagueDistricts : [],
            isLocating: false, groundId: ground ? "g1" : nil, groundDisplayName: ground ? "Shahu Stadium -- Kolhapur" : nil,
            isChangingGround: false, groundSearchQuery: groundQuery, groundSearchResults: groundQuery.isEmpty ? [] : grounds,
            isSearchingGrounds: false, isRegisteringNewGround: registering, newGroundName: newGroundName,
            newGroundLatitude: pin ? KotlinDouble(value: 16.6913) : nil, newGroundLongitude: pin ? KotlinDouble(value: 74.2449) : nil,
            isRegisteringGround: false, newGroundNameError: groundNameError,
            groundErrorTitle: groundError ? "A ground with this name already exists nearby." : nil,
            groundErrorMessage: groundError ? "Search for it instead, or use a different name." : nil,
            startsOn: startsOn, format: format, isFormatOther: other, franchisesRequired: fees ? "8" : "", playersRequired: fees ? "120" : "",
            franchiseFee: fees ? "5000" : "", playerFee: fees ? "500" : "", organizerUpiId: upi, awards: awards, isSaving: saving,
            errorMessage: error)
    }

    private static let filledState = creationState(dirty: true, name: "Kolhapur Premier League", pendingLogo: true, pendingBanner: true,
                                                   located: true, ground: true, startsOn: "2026-10-12", format: "T20", fees: true,
                                                   upi: "sachin@okaxis")
    private static let sampleAwards = [
        AwardDraft(id: nil, name: "Winners", cashAmount: "25000", hasTrophy: true),
        AwardDraft(id: nil, name: "Runners-up", cashAmount: "", hasTrophy: false),
    ]

    private static func creation(_ state: LeagueCreationState, images: Bool = false, startAt: AnyHashable? = nil) -> AnyView {
        AnyView(LeagueCreationContent(state: state, logoPreview: images ? DesignGallery.samplePhoto.image : nil,
                                      bannerPreview: images ? DesignGallery.samplePhoto.image : nil, startAt: startAt))
    }

    @MainActor private static let creation: [String: () -> AnyView] = [
        "I1": { creation(creationState()) },
        "I1-filled": { creation(filledState, images: true) },
        "I3": { creation(creationState(dirty: true, name: "Kolhapur Premier League", pendingLogo: true, pendingBanner: true,
                                       bannerProgress: 0.42, located: true, ground: true, startsOn: "2026-10-12", saving: true), images: true) },
        "I4": { creation(creationState(dirty: true, name: "Kolhapur Premier League", located: true, groundQuery: "Sh"), startAt: LeagueField.ground) },
        "I4-selected": { creation(filledState, images: true, startAt: LeagueField.ground) },
        "I5": { AnyView(GroundRegisterOverlay(state: creationState(dirty: true, located: true, registering: true, pin: true))) },
        "I6": {
            AnyView(ZStack {
                creation(creationState(dirty: true, name: "Kolhapur Premier League", located: true), startAt: LeagueField.startsOn)
                ParityDialogPreview {
                    CrichereDatePickerDialog(title: "Select start date", initial: parseIsoDay("2026-10-12"), onDismiss: {}, onPicked: { _ in })
                }
            })
        },
        "I7": { creation(creationState(dirty: true, validation: true, fees: true)) },
        "I7-fees": { creation(creationState(dirty: true, validation: true, fees: true), startAt: LeagueField.upiId) },
        "I8": { creation(creationState(dirty: true, name: "Kolhapur Premier League", awards: sampleAwards), startAt: "awards") },
        "I9": { creation(creationState(dirty: true, validation: true, name: "Kolhapur Premier League"), startAt: LeagueField.state) },
        "I10": {
            AnyView(ZStack {
                creation(creationState(edit: true, dirty: true, name: "Kolhapur Premier League", pendingLogo: true, pendingBanner: true,
                                       located: true, ground: true, startsOn: "2026-10-12", format: "T20"), images: true)
                ParityDialogPreview {
                    DiscardChangesDialog(message: "Your edits won't be saved. The league stays as it was.", onKeepEditing: {}, onDiscard: {})
                }
            })
        },
        "I11": { AnyView(GroundRegisterOverlay(state: creationState(dirty: true, located: true, registering: true, newGroundName: "Shahu Stadium",
                                                                    pin: true, groundError: true))) },
        "I12": { creation(creationState(edit: true, name: "Kolhapur Premier League", located: true, ground: true, startsOn: "2026-10-12",
                                        format: "8 overs", other: true)) },
        "I16": { AnyView(GroundRegisterOverlay(state: creationState(dirty: true, registering: true))) },
        "I-error": { creation(creationState(dirty: true, name: "Kolhapur Premier League", located: true, ground: true, startsOn: "2026-10-12",
                                            error: "Couldn't save the league. Check your connection and try again.")) },
    ]
}

/// A sheet drawn in place (scrim + 28 pt panel + handle) for still captures; the real one animates in.
struct ParitySheetPreview<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack(alignment: .bottom) {
            Color(hex: 0x0A120C, opacity: 0.5).ignoresSafeArea()
            VStack(spacing: 0) {
                Capsule().fill(Color(hex: 0xB9C1B4)).frame(width: 32, height: 4).padding(.top, 10)
                content()
            }
            .frame(maxWidth: .infinity)
            .background(UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28).fill(CrichereColor.background).ignoresSafeArea(edges: .bottom))
        }
    }
}

/// A dialog drawn in place for still captures.
struct ParityDialogPreview<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            Color.black.opacity(0.32).ignoresSafeArea()
            content().padding(.horizontal, 23)
        }
    }
}
#endif

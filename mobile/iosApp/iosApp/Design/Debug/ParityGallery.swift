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

    @MainActor static let boards: [String: () -> AnyView] = [signIn, verify, profileSetup, dashboard].reduce(into: [:]) { $0.merge($1) { a, _ in a } }

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
}
#endif

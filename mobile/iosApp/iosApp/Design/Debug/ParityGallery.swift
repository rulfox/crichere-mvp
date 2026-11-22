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

    @MainActor static let boards: [String: () -> AnyView] = signIn.merging(verify) { a, _ in a }

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
}
#endif

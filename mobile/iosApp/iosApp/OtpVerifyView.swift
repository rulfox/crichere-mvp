import SwiftUI
import Shared

/// Mirrors `OtpVerifyViewModel`'s shared `StateFlow<OtpVerifyState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents. **Authored but unverified** -- see docs/PHASE9.md.
@MainActor
final class OtpVerifyViewModelWrapper: ObservableObject {
    @Published var state: OtpVerifyState
    @Published var navigationEvent: AuthNavigationEvent?

    private let viewModel: OtpVerifyViewModel

    init(phoneNumber: String, verificationId: String, resendToken: Any?) {
        let viewModel = KoinHelper().otpVerifyViewModel(
            phoneNumber: phoneNumber, verificationId: verificationId, resendToken: resendToken
        )
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

    func onCodeChanged(_ code: String) { viewModel.onCodeChanged(code: code) }
    func verifyCode() { viewModel.verifyCode() }
    func resendCode() { viewModel.resendCode() }
    func startOver() { viewModel.startOver() }
}

/// iOS equivalent of `androidApp/.../ui/OtpVerifyScreen.kt`: 6-digit code entry, 60s resend
/// cooldown countdown, max 3 resends, max 5 wrong attempts (all enforced by
/// `OtpVerifyViewModel`, this view only renders its state). Navigation
/// (Profile Setup / Own Profile / forced-back-to-Phone-Entry) is handled by the caller
/// (`AppRootView`) via `wrapper.navigationEvent`.
struct OtpVerifyView: View {
    let onNavigate: (AuthNavigationEvent) -> Void

    @StateObject private var wrapper: OtpVerifyViewModelWrapper

    init(phoneNumber: String, verificationId: String, resendToken: Any?, onNavigate: @escaping (AuthNavigationEvent) -> Void) {
        self.onNavigate = onNavigate
        _wrapper = StateObject(wrappedValue: OtpVerifyViewModelWrapper(
            phoneNumber: phoneNumber, verificationId: verificationId, resendToken: resendToken
        ))
    }

    var body: some View {
        Form {
            Section {
                Text("Enter verification code").font(.title2.bold())
                Text("We sent a 6-digit code by SMS.").foregroundColor(.secondary)
            }
            Section {
                TextField("6-digit code", text: Binding(
                    get: { wrapper.state.code },
                    set: { newValue in if newValue.count <= 6 { wrapper.onCodeChanged(newValue) } }
                ))
                .keyboardType(.numberPad)

                if let error = wrapper.state.errorMessage {
                    Text(error).foregroundColor(.red)
                }
            }
            Section {
                Button(wrapper.state.isVerifying ? "Verifying..." : "Verify") {
                    wrapper.verifyCode()
                }
                .disabled(wrapper.state.isVerifying)

                if wrapper.state.resendsExhausted {
                    // No further resend is ever possible again for this verification session (see
                    // OtpVerifyState.resendsExhausted's doc) -- a real, reachable path back to
                    // Phone Entry, same as Android's equivalent branch.
                    Text("No more codes available for this number.").foregroundColor(.red)
                    Button("Request a new code") { wrapper.startOver() }
                } else {
                    Button(
                        wrapper.state.canResend
                            ? "Resend code (\(wrapper.state.resendsUsed)/\(wrapper.state.maxResends) used)"
                            : "Resend in \(wrapper.state.cooldownSecondsRemaining)s"
                    ) {
                        wrapper.resendCode()
                    }
                    .disabled(!wrapper.state.canResend || wrapper.state.isResending)
                }
            }
        }
        .onChange(of: wrapper.navigationEvent) { _, event in
            if let event { onNavigate(event) }
        }
    }
}

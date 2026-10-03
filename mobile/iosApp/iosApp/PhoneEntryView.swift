import SwiftUI
import Shared

/// Mirrors `PhoneEntryViewModel`'s shared `StateFlow<PhoneEntryState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents. **Authored but unverified** -- see docs/PHASE9.md.
@MainActor
final class PhoneEntryViewModelWrapper: ObservableObject {
    @Published var state: PhoneEntryState
    @Published var navigationEvent: PhoneEntryNavigationEvent?

    private let viewModel: PhoneEntryViewModel

    init() {
        let viewModel = KoinHelper().phoneEntryViewModel
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

    func onPhoneNumberChanged(_ value: String) { viewModel.onPhoneNumberChanged(value: value) }
    func requestCode() { viewModel.requestCode() }
}

/// iOS equivalent of `androidApp/.../ui/PhoneEntryScreen.kt`: phone number input, "Send code"
/// button. Navigation to OTP Verify is handled by the caller (`AppRootView`) via
/// `wrapper.navigationEvent`, same as the Android `Route`'s `navigationEvents.collect`.
struct PhoneEntryView: View {
    let onNavigateToOtpVerify: (_ phoneNumber: String, _ verificationId: String, _ resendToken: Any?) -> Void

    @StateObject private var wrapper = PhoneEntryViewModelWrapper()

    var body: some View {
        Form {
            Section {
                Text("Sign in").font(.title2.bold())
                Text("Enter your phone number to receive a verification code.")
                    .foregroundColor(.secondary)
            }
            Section {
                TextField("10-digit mobile number", text: Binding(
                    get: { wrapper.state.phoneNumber },
                    set: { wrapper.onPhoneNumberChanged($0) }
                ))
                .keyboardType(.phonePad)

                if let error = wrapper.state.errorMessage {
                    Text(error).foregroundColor(.red)
                }
            }
            Section {
                Button(wrapper.state.isSubmitting ? "Sending..." : "Send code") {
                    wrapper.requestCode()
                }
                .disabled(wrapper.state.isSubmitting)
            }
        }
        .onChange(of: wrapper.navigationEvent) { _, event in
            // SKIE exports this single-case sealed interface as a Swift enum with the case's
            // properties as associated values -- same convention `OwnProfileView`'s multi-case
            // `OwnProfileNavigationEvent` switch already follows.
            switch event {
            case .navigateToOtpVerify(let phoneNumber, let verificationId, let resendToken):
                onNavigateToOtpVerify(phoneNumber, verificationId, resendToken)
            case nil:
                break
            }
        }
    }
}

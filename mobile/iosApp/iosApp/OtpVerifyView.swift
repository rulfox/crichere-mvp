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

/// Verify code (design board screen B, androidApp `ui/OtpVerifyScreen.kt`): 6-digit code, 60 s resend
/// cooldown with a countdown, max 3 resends, max 5 wrong attempts -- all enforced by the shared
/// `OtpVerifyViewModel`; this view renders its state. Navigation is the caller's (`AppRootView`); back
/// and "Edit" both go through `startOver`.
struct OtpVerifyView: View {
    /// The second argument is true for the 5-wrong-attempts lockout (Android's `OtpVerifyRoute`).
    let onNavigate: (AuthNavigationEvent, Bool) -> Void
    let phoneNumber: String

    @StateObject private var wrapper: OtpVerifyViewModelWrapper

    init(phoneNumber: String, verificationId: String, resendToken: Any?, onNavigate: @escaping (AuthNavigationEvent, Bool) -> Void) {
        self.onNavigate = onNavigate
        self.phoneNumber = phoneNumber
        _wrapper = StateObject(wrappedValue: OtpVerifyViewModelWrapper(
            phoneNumber: phoneNumber, verificationId: verificationId, resendToken: resendToken
        ))
    }

    var body: some View {
        OtpVerifyContent(
            state: wrapper.state,
            phoneNumber: phoneNumber,
            onCodeChanged: wrapper.onCodeChanged,
            onVerify: wrapper.verifyCode,
            onResend: wrapper.resendCode,
            onStartOver: wrapper.startOver
        )
        .onReceive(wrapper.$navigationEvent) { event in
            if let event { onNavigate(event, wrapper.state.attemptsRemaining == 0) }
        }
    }
}

/// The screen, stateless for the DEBUG parity gallery.
struct OtpVerifyContent: View {
    let state: OtpVerifyState
    let phoneNumber: String
    let onCodeChanged: (String) -> Void
    let onVerify: () -> Void
    let onResend: () -> Void
    let onStartOver: () -> Void
    /// DEBUG parity gallery: show the "New code sent" bar from the start.
    var showResentInitially: Bool = false
    var autoFocus: Bool = true

    @State private var showResent = false
    @FocusState private var codeFocused: Bool

    private var otpLength: Int { Int(OtpVerifyViewModel.companion.OTP_LENGTH) }
    private var displayPhone: String { PhoneNumberInput.shared.displayNational(e164: phoneNumber, country: Countries.shared.`default`) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            BackArrowButton(tint: state.isVerifying ? CrichereColor.inkDisabled : CrichereColor.onBackground, action: onStartOver)
                .disabled(state.isVerifying)
                .padding(.leading, 4)
                .padding(.top, 5)
            content
                .padding(.horizontal, 22)
                .padding(.top, 8)
                .padding(.bottom, 30)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(CrichereColor.background.ignoresSafeArea())
        .onChange(of: state.resendsUsed) { _, used in if used > 0 { flashResent() } }
        .onAppear {
            if showResentInitially { showResent = true }
            if autoFocus { codeFocused = true }
        }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Enter verification code")
                .crichere(.archivo(24, .bold, lineHeight: 26.4), CrichereColor.onBackground)
                .accessibilityAddTraits(.isHeader)
            Spacer().frame(height: 10)
            sentToText
            Spacer().frame(height: 18)
            OtpCodeField(
                code: Binding(get: { state.code }, set: { onCodeChanged(String($0.filter(\.isNumber).prefix(otpLength))) }),
                length: otpLength,
                isVerifying: state.isVerifying,
                isError: state.errorMessage != nil && !state.resendsExhausted,
                focus: $codeFocused
            )
            if let error = state.errorMessage, !state.resendsExhausted {
                Spacer().frame(height: 10)
                HStack(alignment: .top, spacing: 6) {
                    CrIcon("ic_error", size: 18, tint: CrichereColor.error)
                    Text(error).crichere(.sans(13, .medium, lineHeight: 16.9), CrichereColor.error)
                }
                .padding(.vertical, 2.5)
            }
            if state.resendsExhausted {
                Spacer().frame(height: 20)
                NoticeCard(
                    icon: "ic_block",
                    title: "No more codes available for this number.",
                    message: "You can still enter the last code sent, or start over.",
                    containerColor: CrichereColor.errorContainer,
                    titleColor: CrichereColor.errorStrong,
                    bodyColor: CrichereColor.errorBody
                )
            } else {
                Spacer().frame(height: 16)
                resendRow
            }
            Spacer().frame(height: 20)
            Spacer(minLength: 0)
            if showResent {
                Text("New code sent to \(displayPhone)")
                    .crichere(.sans(14, .medium, lineHeight: 18.2), CrichereColor.onPrimary)
                    .padding(.horizontal, 16)
                    .frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48, alignment: .leading)
                    .background(CrichereColor.onBackground, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .padding(.bottom, 22)
                    .transition(.opacity)
            }
            if state.resendsExhausted {
                CrichereOutlinedButton("Request a new code", action: onStartOver)
                Spacer().frame(height: 20)
            }
            CricherePrimaryButton("Verify", enabled: state.code.count == otpLength, loading: state.isVerifying, loadingText: "Verifying…", action: onVerify)
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .animation(.easeOut(duration: 0.2), value: showResent)
    }

    private var sentToText: some View {
        var text = AttributedString("We sent a 6-digit code by SMS to ")
        var phone = AttributedString(displayPhone)
        phone.font = CrichereTextStyle.mono(13.5, .normal).font
        phone.foregroundColor = CrichereColor.onBackground
        text += phone
        text += AttributedString(".")
        if !state.isVerifying {
            var edit = AttributedString("Edit")
            edit.link = URL(string: "crichere-action://edit")
            edit.font = CrichereTextStyle.sans(13.5, .semiBold).font
            edit.foregroundColor = CrichereColor.primary
            text += AttributedString(" ")
            text += edit
        }
        return Text(text)
            .crichere(.sans(13.5, lineHeight: 18.9), CrichereColor.onSurfaceVariant)
            .tint(CrichereColor.primary)
            .environment(\.openURL, OpenURLAction { _ in
                onStartOver()
                return .handled
            })
    }

    @ViewBuilder private var resendRow: some View {
        let style = CrichereTextStyle.sans(13, .medium, lineHeight: 13)
        if state.canResend {
            Button {
                if !state.isResending { onResend() }
            } label: {
                Text("Resend code (\(state.resendsUsed)/\(state.maxResends) used)").crichere(style.weight(.semiBold), CrichereColor.primary)
            }
            .buttonStyle(.plain)
        } else {
            let seconds = Int(state.cooldownSecondsRemaining)
            let timer = String(format: "%d:%02d", seconds / 60, seconds % 60)
            (Text("Resend in ") + Text(timer).font(CrichereTextStyle.mono(13, .medium).font).foregroundColor(CrichereColor.onBackground)
                + Text(state.resendsUsed > 0 ? " · \(state.resendsUsed)/\(state.maxResends) used" : ""))
                .crichere(style, CrichereColor.onSurfaceVariant)
        }
    }

    private func flashResent() {
        showResent = true
        Task {
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            showResent = false
        }
    }
}

/// The board's code field: 56 pt box (6 pt notch room above), "6-digit code" in the notch, mono 20 with
/// 6 pt spacing, a "n/6" counter; idle / focused / error / verifying looks.
private struct OtpCodeField: View {
    @Binding var code: String
    let length: Int
    let isVerifying: Bool
    let isError: Bool
    var focus: FocusState<Bool>.Binding

    var body: some View {
        let look: (CGFloat, Color, Color, Color) = isVerifying ? (1, CrichereColor.outlineDisabled, CrichereColor.fieldDisabled, CrichereColor.onSurfaceVariant)
            : isError ? (2, CrichereColor.error, CrichereColor.errorField, CrichereColor.error)
            : focus.wrappedValue ? (2, CrichereColor.primary, CrichereColor.surface, CrichereColor.primary)
            : (1, CrichereColor.outline, CrichereColor.surface, CrichereColor.onSurfaceVariant)
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        HStack(spacing: 0) {
            TextField("", text: $code)
                .crichere(.mono(20, .semiBold, tracking: 6), isError ? CrichereColor.errorStrong : CrichereColor.onBackground)
                .tint(CrichereColor.primary)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .focused(focus)
                .disabled(isVerifying)
                .accessibilityLabel("6-digit code")
            Text("\(code.count)/\(length)").crichere(.mono(11, .medium), CrichereColor.inkSubtle).accessibilityHidden(true)
        }
        .padding(.horizontal, 16 + look.0)
        .frame(height: 56)
        .background(look.2, in: shape)
        .overlay(shape.strokeBorder(look.1, lineWidth: look.0))
        .overlay(alignment: .topLeading) {
            Text("6-digit code")
                .crichere(.sans(11.5, .medium, lineHeight: 11.5), look.3)
                .padding(.horizontal, 4)
                .background(CrichereColor.background)
                .offset(x: 14, y: -6)
                .accessibilityHidden(true)
        }
        .padding(.top, 6)
        .contentShape(shape)
        .onTapGesture { if !isVerifying { focus.wrappedValue = true } }
    }
}

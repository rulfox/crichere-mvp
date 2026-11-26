import SwiftUI
import Shared

/// Mirrors `PhoneEntryViewModel`'s shared `StateFlow<PhoneEntryState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents.
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
    func onTermsAcceptedChanged(_ value: Bool) { viewModel.onTermsAcceptedChanged(accepted: value) }
    func requestCode() { viewModel.requestCode() }
}

/// Sign in (design board screen A, androidApp `ui/PhoneEntryScreen.kt`). Navigation to OTP Verify is the
/// caller's (`AppRootView`), via `wrapper.navigationEvent`. [wrapper] is owned by the caller so a
/// voluntary back from OTP Verify finds the typed number still there, like Android's back stack.
struct PhoneEntryView: View {
    @ObservedObject var wrapper: PhoneEntryViewModelWrapper
    /// Design B6: bounced here after 5 wrong OTP attempts.
    var showLockoutNotice: Bool = false
    let onNavigateToOtpVerify: (_ phoneNumber: String, _ verificationId: String, _ resendToken: Any?) -> Void

    @StateObject private var notices = AccountNoticesWrapper()
    @State private var legalPage: URL?

    var body: some View {
        PhoneEntryContent(
            state: wrapper.state,
            showLockoutNotice: showLockoutNotice,
            deletionScheduledOn: notices.deletionScheduledOn.map(eraseDateLabel),
            onPhoneNumberChanged: wrapper.onPhoneNumberChanged,
            onTermsAcceptedChanged: wrapper.onTermsAcceptedChanged,
            onSendCode: wrapper.requestCode,
            onDismissDeletionNotice: notices.dismissScheduled,
            onOpenLegalPage: { legalPage = $0 }
        )
        .sheet(item: $legalPage) { SafariView(url: $0).ignoresSafeArea() }
        .onReceive(wrapper.$navigationEvent) { event in
            // SKIE exposes the sealed event as an enum via onEnum(of:); each case carries the Kotlin subtype.
            switch onEnum(of: event) {
            case .navigateToOtpVerify(let payload):
                onNavigateToOtpVerify(payload.phoneNumber, payload.verificationId, payload.resendToken)
            case nil:
                break
            }
        }
    }
}

/// The screen itself, stateless (state in, actions out) so the DEBUG parity gallery can render every
/// board state from fixtures. The green hero only shows on first launch (empty, unfocused field); once
/// the field is engaged it collapses to the compact wordmark header (A1 vs A2-A4).
struct PhoneEntryContent: View {
    let state: PhoneEntryState
    var showLockoutNotice: Bool = false
    /// Design D7: "25 Oct 2026" -- the account was just scheduled for deletion.
    var deletionScheduledOn: String? = nil
    let onPhoneNumberChanged: (String) -> Void
    let onTermsAcceptedChanged: (Bool) -> Void
    let onSendCode: () -> Void
    var onDismissDeletionNotice: () -> Void = {}
    var onOpenLegalPage: (URL) -> Void = { _ in }
    /// DEBUG parity gallery: open with the field focused (board A2).
    var autoFocus: Bool = false

    @FocusState private var phoneFocused: Bool

    private var showHero: Bool { state.phoneNumber.isEmpty && !phoneFocused && !showLockoutNotice }

    var body: some View {
        // Laid out edge to edge from the insets, like Compose's statusBarsPadding / imePadding: the hero
        // runs under the status bar; the bottom inset includes the keyboard.
        GeometryReader { proxy in
            VStack(spacing: 0) {
                if showHero { PhoneEntryHero(topInset: proxy.safeAreaInsets.top) }
                form
                    .padding(.horizontal, 22)
                    .padding(.top, showHero ? 26 : proxy.safeAreaInsets.top + 41)
                    .padding(.bottom, proxy.safeAreaInsets.bottom + 30)
            }
            .frame(width: proxy.size.width, height: proxy.size.height + proxy.safeAreaInsets.top + proxy.safeAreaInsets.bottom, alignment: .top)
            .ignoresSafeArea(.container, edges: .vertical)
        }
        .background(CrichereColor.background.ignoresSafeArea())
        .lightStatusBarIcons(showHero)
        .animation(.easeOut(duration: 0.2), value: showHero)
        .onAppear { if autoFocus { phoneFocused = true } }
    }

    private var form: some View {
        VStack(alignment: .leading, spacing: 0) {
            if !showHero {
                CrichereWordmark()
                Spacer().frame(height: 14)
            }
            if let deletionScheduledOn {
                DismissibleNotice(
                    icon: "ic_schedule",
                    text: "Your account will be deleted on \(deletionScheduledOn). Sign in before then to keep it.",
                    container: CrichereColor.warningContainer,
                    ink: CrichereColor.onWarning,
                    onDismiss: onDismissDeletionNotice
                )
                Spacer().frame(height: 14)
            }
            if showLockoutNotice {
                NoticeCard(
                    icon: "ic_lock_clock",
                    title: "Too many incorrect attempts",
                    message: "For your security, request a new code to continue.",
                    containerColor: CrichereColor.warningContainer,
                    titleColor: CrichereColor.onWarning,
                    bodyColor: CrichereColor.onWarning
                )
                Spacer().frame(height: 14)
            }
            Text("Sign in")
                .crichere(.archivo(22, .bold, lineHeight: 24.2), CrichereColor.onBackground)
                .accessibilityAddTraits(.isHeader)
            Spacer().frame(height: 14)
            if !showLockoutNotice {
                Text("Enter your phone number to receive a verification code.")
                    .crichere(.sans(13.5, lineHeight: 18.9), CrichereColor.onSurfaceVariant)
                Spacer().frame(height: 14)
            }
            PhoneNumberField(
                text: Binding(get: { state.phoneNumber }, set: onPhoneNumberChanged),
                isSubmitting: state.isSubmitting,
                isError: state.errorMessage != nil,
                focus: $phoneFocused,
                onDone: { phoneFocused = false }
            )
            if let error = state.errorMessage {
                Spacer().frame(height: 6)
                Text(error).crichere(.sans(12.5, .medium, lineHeight: 16.5), CrichereColor.error)
            }
            Spacer().frame(height: 20)
            Spacer(minLength: 0)
            TermsCheckbox(checked: state.termsAccepted, onChange: onTermsAcceptedChanged, onOpenLegalPage: onOpenLegalPage)
            Spacer().frame(height: 14)
            CricherePrimaryButton(
                "Send code",
                enabled: !state.phoneNumber.trimmingCharacters(in: .whitespaces).isEmpty && state.termsAccepted,
                loading: state.isSubmitting,
                loadingText: "Sending code…",
                action: onSendCode
            )
        }
        .frame(maxHeight: .infinity, alignment: .top)
    }
}

/// The first-launch green hero: 298 pt under the status bar (green behind it too), a decorative ring
/// (240 pt, 34 pt stroke, white at 6%) at (178, 40) from the hero's top edge, the 40 pt wordmark and
/// the tagline.
private struct PhoneEntryHero: View {
    let topInset: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            CrichereColor.primary
            Circle()
                .strokeBorder(Color.white.opacity(0.06), lineWidth: 34)
                .frame(width: 240, height: 240)
                .offset(x: 178, y: 40)
            VStack(alignment: .leading, spacing: 0) {
                Text("Crichere")
                    .crichere(.archivo(40, .extraBold, lineHeight: 40, tracking: -1.2), .white)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                Text("Local leagues, franchise auctions and your cricket profile.")
                    .crichere(.archivo(20, .medium, lineHeight: 25), CrichereColor.primaryContainer)
                    .frame(maxWidth: 250, alignment: .leading)
            }
            .padding(.horizontal, 26)
            .padding(.top, 56)
            .padding(.bottom, 26)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: 298)
            .padding(.top, topInset)
        }
        .frame(height: topInset + 298)
        .clipped()
        .transition(.opacity)
    }
}

/// The board's phone field: 56 pt, white (grey while sending), 12 pt corners, 1 pt outline / 2 pt green
/// focused / 2 pt red with an error icon; JetBrains Mono digits, "10-digit mobile number" hint.
private struct PhoneNumberField: View {
    @Binding var text: String
    let isSubmitting: Bool
    let isError: Bool
    var focus: FocusState<Bool>.Binding
    let onDone: () -> Void

    var body: some View {
        let (width, color): (CGFloat, Color) = isError ? (2, CrichereColor.error)
            : isSubmitting ? (1, CrichereColor.outline)
            : focus.wrappedValue ? (2, CrichereColor.primary) : (1, CrichereColor.outline)
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        HStack(spacing: 0) {
            ZStack(alignment: .leading) {
                if text.isEmpty {
                    Text("10-digit mobile number").crichere(.sans(15), CrichereColor.inkSubtle).accessibilityHidden(true)
                }
                TextField("", text: $text)
                    .crichere(.mono(16, .medium), CrichereColor.onBackground)
                    .tint(CrichereColor.primary)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                    .focused(focus)
                    .disabled(isSubmitting)
                    .accessibilityLabel("Phone number")
            }
            if isError {
                CrIcon("ic_error", size: 22, tint: CrichereColor.error)
            }
        }
        .padding(.horizontal, 16 + width)
        .frame(height: 56)
        .background(isSubmitting ? CrichereColor.fieldDisabled : CrichereColor.surface, in: shape)
        .overlay(shape.strokeBorder(color, lineWidth: width))
        .contentShape(shape)
        .onTapGesture { if !isSubmitting { focus.wrappedValue = true } }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done", action: onDone).tint(CrichereColor.primary)
            }
        }
    }
}

/// Boards T1a and T1b: unticked to start; Send code waits for it. The links open over this screen, so
/// the typed number and the tick stay.
private struct TermsCheckbox: View {
    let checked: Bool
    let onChange: (Bool) -> Void
    let onOpenLegalPage: (URL) -> Void

    private var text: AttributedString {
        var result = AttributedString("I agree to the ")
        var terms = AttributedString("Terms")
        terms.link = termsUrl
        var privacy = AttributedString("Privacy Policy")
        privacy.link = privacyUrl
        terms.font = CrichereTextStyle.sans(14, .medium).font
        privacy.font = CrichereTextStyle.sans(14, .medium).font
        terms.foregroundColor = CrichereColor.primary
        privacy.foregroundColor = CrichereColor.primary
        result += terms
        result += AttributedString(" and ")
        result += privacy
        result += AttributedString(", and I am 18 or older.")
        return result
    }

    var body: some View {
        HStack(spacing: 12) {
            CrichereCheckbox(checked: checked, label: "I agree to the Terms and Privacy Policy, and I am 18 or older", size: 24, onChange: onChange)
                .allowsHitTesting(false)
            Text(text)
                .crichere(.sans(14, lineHeight: 21), CrichereColor.onSurfaceVariant)
                .tint(CrichereColor.primary)
                .environment(\.openURL, OpenURLAction { url in
                    onOpenLegalPage(url)
                    return .handled
                })
            Spacer(minLength: 0)
        }
        .frame(minHeight: 44)
        .contentShape(Rectangle())
        .onTapGesture { onChange(!checked) }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityValue(checked ? "Checked" : "Unchecked")
    }
}

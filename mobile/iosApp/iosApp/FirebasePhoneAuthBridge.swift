import Foundation
import FirebaseAuth
import Shared

/// Real Firebase iOS Auth SDK integration, conforming to the Kotlin `IosPhoneAuthBridge`
/// protocol exported from `Shared.framework`
/// (`shared/src/iosMain/kotlin/com/crichere/app/auth/IosPhoneAuthBridge.kt`). Written against the
/// real, documented Firebase iOS Auth SDK API surface (`PhoneAuthProvider`, `Auth.signIn(with:)`,
/// `User.getIDToken`) -- but never compiled here: this environment has no Mac/Xcode/CocoaPods, so
/// there is no way to add the Firebase iOS SDK as a real dependency or run the Swift compiler at
/// all (same status as every other Swift file in this repo -- see task-5-report.md /
/// task-6-report.md). The exact spelling of a couple of Kotlin/Native <-> Swift interop details
/// below (constructing a `KotlinThrowable`/`InvalidOtpCodeException` from Swift) is a best-effort
/// guess at SKIE's generated API, not something verified against a real build.
///
/// `verifyCode`'s `mapVerifyError` distinguishes a real wrong-code error
/// (`AuthErrorCode.invalidVerificationCode`) from every other failure, per
/// `IosPhoneAuthBridge.kt`'s contract -- `OtpVerifyViewModel` only burns one of the 5 allowed
/// wrong-code attempts for the former.
final class FirebasePhoneAuthBridgeImpl: IosPhoneAuthBridge {

    func sendVerificationCode(
        phoneNumber: String,
        resendToken: Any?,
        onResult: @escaping (String?, Any?, KotlinThrowable?) -> Void
    ) {
        // Firebase's iOS SDK has no separate "force resend token" concept the way Android's does
        // -- resending is just calling verifyPhoneNumber again, so `resendToken` is accepted (to
        // satisfy the shared interface) but unused here.
        PhoneAuthProvider.provider().verifyPhoneNumber(phoneNumber, uiDelegate: nil) { verificationID, error in
            if let error = error {
                onResult(nil, nil, KotlinThrowable(message: error.localizedDescription, cause: nil))
            } else {
                onResult(verificationID, nil, nil)
            }
        }
    }

    func verifyCode(
        verificationId: String,
        code: String,
        onResult: @escaping (String?, KotlinThrowable?) -> Void
    ) {
        let credential = PhoneAuthProvider.provider().credential(
            withVerificationID: verificationId,
            verificationCode: code
        )
        Auth.auth().signIn(with: credential) { authResult, error in
            if let error = error {
                onResult(nil, Self.mapVerifyError(error))
                return
            }
            authResult?.user.getIDToken { idToken, tokenError in
                if let tokenError = tokenError {
                    onResult(nil, Self.mapVerifyError(tokenError))
                } else {
                    onResult(idToken, nil)
                }
            }
        }
    }

    /// Translates a `verifyCode` failure into the platform-agnostic shape
    /// `OtpVerifyViewModel` (commonMain) needs: `InvalidOtpCodeException` specifically for "the
    /// code was wrong" (Firebase iOS SDK's real `AuthErrorCode.invalidVerificationCode`), a plain
    /// `KotlinThrowable` for everything else (network error, expired session, ...) -- mirrors
    /// `FirebasePhoneAuthClient.android.kt`'s real `FirebaseAuthInvalidCredentialsException`
    /// handling on the Android side. Unverified here (no Mac/Xcode/Firebase SDK to compile
    /// against), but `AuthErrorCode.invalidVerificationCode` is the real, documented Firebase iOS
    /// error code for this case.
    private static func mapVerifyError(_ error: Error) -> KotlinThrowable {
        let nsError = error as NSError
        if nsError.domain == AuthErrorDomain, nsError.code == AuthErrorCode.invalidVerificationCode.rawValue {
            return InvalidOtpCodeException(message: "The code you entered is incorrect.")
        }
        return KotlinThrowable(message: error.localizedDescription, cause: nil)
    }
}

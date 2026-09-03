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
/// below (constructing a `KotlinThrowable` from Swift) is a best-effort guess at SKIE's generated
/// API, not something verified against a real build.
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
                onResult(nil, KotlinThrowable(message: error.localizedDescription, cause: nil))
                return
            }
            authResult?.user.getIDToken { idToken, tokenError in
                if let tokenError = tokenError {
                    onResult(nil, KotlinThrowable(message: tokenError.localizedDescription, cause: nil))
                } else {
                    onResult(idToken, nil)
                }
            }
        }
    }
}

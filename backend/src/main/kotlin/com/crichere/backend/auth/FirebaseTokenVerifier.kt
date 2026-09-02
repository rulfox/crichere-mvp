package com.crichere.backend.auth

/**
 * The facts we trust about a caller once Firebase has vouched for their ID token.
 *
 * @property uid Firebase's own stable identifier for the account. Not used as our primary key
 *   (we key on the phone number so the account survives a Firebase project migration), but
 *   worth carrying for diagnostics.
 * @property phoneNumber The verified phone number in E.164 form. This is the only claim that
 *   actually establishes identity in this app.
 */
data class VerifiedFirebaseToken(
    val uid: String,
    val phoneNumber: String,
)

/**
 * Verifies a Firebase ID token that the mobile client obtained by completing the phone-OTP
 * flow *client-side*. The backend never sends an OTP and has no "send OTP" endpoint; its only
 * job is to decide whether the token the client presents is genuine.
 *
 * This is an interface rather than a direct call to the Firebase Admin SDK for two reasons:
 * it keeps [AuthService] testable without network access or service-account credentials, and
 * it confines the vendor SDK to a single implementation class.
 */
interface FirebaseTokenVerifier {
    /**
     * @throws AuthenticationFailedException.InvalidFirebaseIdTokenException if the token is
     *   malformed, expired, revoked, issued for another project, or carries no phone number.
     */
    fun verify(idToken: String): VerifiedFirebaseToken
}

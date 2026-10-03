package com.crichere.app.auth

import com.crichere.app.notification.DeviceTokenProvider
import com.crichere.app.notification.DeviceTokenRepository
import com.crichere.app.storage.SecureStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** Keys [KtorAuthRepository] persists tokens under -- shared with `di/AppModule.kt`'s `AuthTokenProvider` wiring. */
object SecureStorageKeys {
    const val ACCESS_TOKEN = "auth_access_token"
    const val REFRESH_TOKEN = "auth_refresh_token"

    /**
     * Added for Phase 2: League Detail needs to know the signed-in user's own id to decide
     * whether to show organizer-only actions (edit/complete/awards) -- Phase 1 never needed this
     * since every profile mutation was already implicitly self-scoped. Persisted alongside the
     * tokens on every session exchange/refresh, cleared on logout.
     */
    const val USER_ID = "auth_user_id"
}

/** Thrown by [AuthRepository.exchangeSession] when `/auth/session` doesn't return 2xx. */
class SessionExchangeFailedException(message: String) : Exception(message)

/** Thrown by [AuthRepository.refresh] for anything other than a clean success or a 401. */
class SessionRefreshFailedException(message: String) : Exception(message)

/**
 * `commonMain` auth use cases: wraps [PhoneAuthClient] for the OTP leg and the real Ktor
 * `HttpClient` for the backend leg (`POST /api/v1/auth/session|refresh|logout`).
 */
interface AuthRepository {
    /**
     * Sends (or resends) an OTP through whichever provider the backend currently selects
     * (`GET /auth/config`; Firebase when that can't be reached) -- see docs/PHASE12.md.
     * A fresh send (null [resendToken]) re-reads the provider; a resend sticks with the provider
     * its code was sent through.
     */
    suspend fun sendOtp(phoneNumber: String, resendToken: Any? = null): Result<PhoneVerificationHandle>

    /**
     * Checks the code with the provider [sendOtp] used. Firebase yields an ID token the caller
     * must still [exchangeSession]; the backend-driven provider yields an already-issued session
     * whose tokens this method has persisted -- see [OtpVerification].
     */
    suspend fun verifyOtp(verificationId: String, code: String): Result<OtpVerification>

    /**
     * Exchanges a real Firebase ID token for a Crichere session, persisting the returned tokens
     * to [SecureStore]. Throws [SessionExchangeFailedException] on anything other than 2xx --
     * unlike [refresh], there is no "expected" failure mode here worth modeling as `null`.
     */
    suspend fun exchangeSession(idToken: String): AuthResult

    /**
     * Exchanges the stored refresh token for a fresh pair, persisting the new tokens. Returns
     * `null` when the backend says the refresh token itself is invalid (401 -- unknown, revoked,
     * or expired, or none is stored yet), which is the caller's/`Auth` plugin's signal to give up
     * on this request rather than retry: the standard fix for that case is a fresh phone-OTP
     * sign-in, which is not something this method can do on its own. Any other failure (network
     * error, 5xx) throws [SessionRefreshFailedException] instead, since that's a transient
     * problem, not "log the user out."
     */
    suspend fun refresh(): AuthResult?

    /** Revokes the stored refresh token backend-side (best-effort) and clears local storage regardless. */
    suspend fun logout()

    /** The signed-in user's own id, read from local storage (no network call) -- `null` if nobody is signed in. See [SecureStorageKeys.USER_ID]. */
    suspend fun getCurrentUserId(): String?
}

@Serializable
private data class SessionRequestBody(val idToken: String)

@Serializable
private data class RefreshRequestBody(val refreshToken: String)

@Serializable
private data class LogoutRequestBody(val refreshToken: String)

/**
 * Real implementation. [authHttpClient] is deliberately the *un-authenticated* Ktor client
 * (`HttpClientFactory.createAuthClient()`), not the shared client with the `Auth` bearer plugin
 * installed -- see `HttpClientFactory.kt` for why routing these calls through the authenticated
 * client would be a real (not theoretical) infinite-recursion risk.
 */
internal class KtorAuthRepository(
    private val authHttpClient: HttpClient,
    private val phoneAuthClient: PhoneAuthClient,
    private val secureStorage: SecureStore,
    /**
     * Lazy provider for the *authenticated* client (`HttpClientFactory.create`'s instance, the one
     * carrying the `Auth` bearer plugin every other repository shares) -- deferred the same way
     * [com.crichere.app.auth.AuthTokenProvider]'s `authRepositoryProvider` is, so this doesn't
     * force that client's own registration (which itself depends on [AuthTokenProvider], not this
     * class, so no real cycle -- just consistent with the established pattern).
     *
     * Ktor's `bearer` auth provider caches whatever [AuthTokenProvider.loadTokens] returned the
     * *first* time it was needed and only calls it again after a 401 triggers `refreshTokens` --
     * it does not re-read `SecureStore` before every request. Without clearing that cache here,
     * switching accounts within the same app process (log out, sign in as someone else) leaves
     * every subsequent request silently using the *previous* user's access token: real bug found
     * on-device, surfacing as organizer-only actions failing with a 403 right after switching to
     * the actual organizer's account, with no other visible symptom pointing at the cause.
     */
    private val authenticatedHttpClientProvider: () -> HttpClient,
    /**
     * Push notifications (docs/PHASE8.md) -- registered on sign-in, unregistered on sign-out.
     * Both best-effort; see that doc's Decisions Made. Defaulted to a no-op pair so every existing
     * call site (this class's own extensive pre-Phase-8 test coverage included) that doesn't care
     * about push notifications doesn't need updating -- `currentToken()` returning `null` already
     * means "nothing to register" on a real platform, so a no-op provider is a legitimate,
     * behavior-preserving default, not a test-only shortcut.
     */
    private val deviceTokenProvider: DeviceTokenProvider = NoopDeviceTokenProvider,
    private val deviceTokenRepository: DeviceTokenRepository = NoopDeviceTokenRepository,
    /**
     * The backend-driven (MSG91) OTP endpoints; see docs/PHASE12.md. Firebase's [phoneAuthClient]
     * stays fully wired alongside it. `null` (the default) means Firebase only -- no provider
     * lookup and no extra network call -- which is what every pre-existing caller wants;
     * `di/AppModule.kt` passes a real client.
     */
    private val backendOtpClient: BackendOtpClient? = null,
) : AuthRepository {

    /**
     * Provider the in-flight OTP was sent through, so a resend and the verify that follows use the
     * same one even if the backend's config is flipped mid-flow. Set by every fresh send.
     */
    private var activeProvider: OtpProvider = OtpProvider.FIREBASE

    private fun clearCachedBearerToken() {
        authenticatedHttpClientProvider().authProvider<BearerAuthProvider>()?.clearToken()
    }

    override suspend fun sendOtp(phoneNumber: String, resendToken: Any?): Result<PhoneVerificationHandle> {
        val backend = backendOtpClient
        if (backend == null) {
            activeProvider = OtpProvider.FIREBASE
            return phoneAuthClient.sendVerificationCode(phoneNumber, resendToken)
        }
        // A resend of a backend-driven code goes to that challenge, whatever the config says now.
        if (resendToken is BackendResendToken) {
            activeProvider = OtpProvider.MSG91
            return backend.resend(resendToken.challengeId)
        }
        // A fresh send picks the provider; a Firebase resend (platform token or null) stays on Firebase.
        val provider = if (resendToken == null) {
            backend.fetchProvider() ?: OtpProvider.FIREBASE
        } else {
            OtpProvider.FIREBASE
        }
        activeProvider = provider
        return when (provider) {
            OtpProvider.MSG91 -> backend.send(phoneNumber)
            OtpProvider.FIREBASE -> phoneAuthClient.sendVerificationCode(phoneNumber, resendToken)
        }
    }

    override suspend fun verifyOtp(verificationId: String, code: String): Result<OtpVerification> {
        val backend = backendOtpClient
        return when {
            activeProvider == OtpProvider.FIREBASE || backend == null ->
                phoneAuthClient.verifyCode(verificationId, code).map { OtpVerification.FirebaseIdToken(it) }
            else ->
                backend.verify(verificationId, code).fold(
                    onSuccess = { session ->
                        try {
                            onSessionEstablished(session)
                            Result.success(OtpVerification.BackendSession(session))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // A local storage/Keystore failure: same flat message the Firebase path
                            // shows for a failed exchange; the cause is not actionable for the user.
                            Result.failure(OtpRequestFailedException(OtpVerifyViewModel.SIGN_IN_FAILED_MESSAGE))
                        }
                    },
                    onFailure = { Result.failure(it) },
                )
        }
    }

    override suspend fun exchangeSession(idToken: String): AuthResult {
        val response = authHttpClient.post("/api/v1/auth/session") {
            contentType(ContentType.Application.Json)
            setBody(SessionRequestBody(idToken))
        }
        if (!response.status.isSuccess()) {
            throw SessionExchangeFailedException("Session exchange failed with status ${response.status}")
        }
        val result: AuthResult = response.body()
        onSessionEstablished(result)
        return result
    }

    /** Everything that follows a fresh sign-in, whichever provider proved the phone. */
    private suspend fun onSessionEstablished(result: AuthResult) {
        persistTokens(result)
        // A fresh sign-in -- possibly as a different user than whoever was last signed in on this
        // same app process -- so any bearer token Ktor's Auth plugin already has cached must be
        // dropped, not just SecureStore's copy. See [authenticatedHttpClientProvider]'s doc.
        clearCachedBearerToken()
        // Best-effort, never blocks sign-in on failure -- see docs/PHASE8.md. `null` on iOS (no
        // FCM wired yet) is a normal, expected outcome, not an error.
        deviceTokenProvider.currentToken()?.let { deviceTokenRepository.register(it, "ANDROID") }
    }

    override suspend fun refresh(): AuthResult? {
        val storedRefreshToken = secureStorage.get(SecureStorageKeys.REFRESH_TOKEN) ?: return null

        val response = authHttpClient.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequestBody(storedRefreshToken))
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            // The stored refresh token itself is invalid (unknown/revoked/expired) -- clear it
            // (and any now-orphaned access token) rather than leave a dead token sitting in
            // SecureStore. This is what Task 7's app-start routing relies on to distinguish "no
            // session" (Phone Entry) without needing its own duplicate clearing logic; the
            // `Auth` bearer plugin's own refresh path benefits the same way for a live request's
            // 401 -- see AuthTokenProvider.refreshTokens.
            secureStorage.remove(SecureStorageKeys.ACCESS_TOKEN)
            secureStorage.remove(SecureStorageKeys.REFRESH_TOKEN)
            return null
        }
        if (!response.status.isSuccess()) {
            throw SessionRefreshFailedException("Refresh failed with status ${response.status}")
        }

        val result: AuthResult = response.body()
        persistTokens(result)
        return result
    }

    override suspend fun logout() {
        // Must run before the bearer token is cleared below -- unregister is itself an
        // authenticated call (docs/PHASE8.md). Best-effort: a failure here must not block signing
        // out, same posture as the backend /auth/logout call just below.
        runCatching { deviceTokenProvider.currentToken()?.let { deviceTokenRepository.unregister(it) } }

        val storedRefreshToken = secureStorage.get(SecureStorageKeys.REFRESH_TOKEN)
        if (storedRefreshToken != null) {
            // Best-effort: the backend answers 204 regardless of whether the token existed, and
            // even if this call fails outright (offline logout), the user still expects to be
            // signed out locally -- so a network error here must not block clearing storage below.
            runCatching {
                authHttpClient.post("/api/v1/auth/logout") {
                    contentType(ContentType.Application.Json)
                    setBody(LogoutRequestBody(storedRefreshToken))
                }
            }
        }
        secureStorage.remove(SecureStorageKeys.ACCESS_TOKEN)
        secureStorage.remove(SecureStorageKeys.REFRESH_TOKEN)
        secureStorage.remove(SecureStorageKeys.USER_ID)
        // See [authenticatedHttpClientProvider]'s doc -- without this, the next sign-in's first
        // request(s) would still carry this session's now-revoked access token.
        clearCachedBearerToken()
    }

    override suspend fun getCurrentUserId(): String? = secureStorage.get(SecureStorageKeys.USER_ID)

    private suspend fun persistTokens(result: AuthResult) {
        secureStorage.set(SecureStorageKeys.ACCESS_TOKEN, result.accessToken)
        secureStorage.set(SecureStorageKeys.REFRESH_TOKEN, result.refreshToken)
        secureStorage.set(SecureStorageKeys.USER_ID, result.userId)
    }
}

/** See [KtorAuthRepository]'s constructor doc -- the default when a caller doesn't care about push notifications. */
private object NoopDeviceTokenProvider : DeviceTokenProvider {
    override suspend fun currentToken(): String? = null
}

/** Never actually invoked in practice -- [NoopDeviceTokenProvider] always returns `null`, which short-circuits before this would be called. Exists only to satisfy the type. */
private object NoopDeviceTokenRepository : DeviceTokenRepository {
    override suspend fun register(token: String, platform: String): Boolean = false
    override suspend fun unregister(token: String): Boolean = false
}

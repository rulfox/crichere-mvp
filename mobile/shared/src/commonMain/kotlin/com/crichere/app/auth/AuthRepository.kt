package com.crichere.app.auth

import com.crichere.app.storage.SecureStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
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
    /** Wraps [PhoneAuthClient.sendVerificationCode]. */
    suspend fun sendOtp(phoneNumber: String, resendToken: Any? = null): Result<PhoneVerificationHandle>

    /** Wraps [PhoneAuthClient.verifyCode]. */
    suspend fun verifyOtp(verificationId: String, code: String): Result<String>

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
) : AuthRepository {

    override suspend fun sendOtp(phoneNumber: String, resendToken: Any?): Result<PhoneVerificationHandle> =
        phoneAuthClient.sendVerificationCode(phoneNumber, resendToken)

    override suspend fun verifyOtp(verificationId: String, code: String): Result<String> =
        phoneAuthClient.verifyCode(verificationId, code)

    override suspend fun exchangeSession(idToken: String): AuthResult {
        val response = authHttpClient.post("/api/v1/auth/session") {
            contentType(ContentType.Application.Json)
            setBody(SessionRequestBody(idToken))
        }
        if (!response.status.isSuccess()) {
            throw SessionExchangeFailedException("Session exchange failed with status ${response.status}")
        }
        val result: AuthResult = response.body()
        persistTokens(result)
        return result
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
    }

    override suspend fun getCurrentUserId(): String? = secureStorage.get(SecureStorageKeys.USER_ID)

    private suspend fun persistTokens(result: AuthResult) {
        secureStorage.set(SecureStorageKeys.ACCESS_TOKEN, result.accessToken)
        secureStorage.set(SecureStorageKeys.REFRESH_TOKEN, result.refreshToken)
        secureStorage.set(SecureStorageKeys.USER_ID, result.userId)
    }
}

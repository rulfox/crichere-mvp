package com.crichere.app.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Talks to the backend-driven OTP endpoints (`/api/v1/auth/config`, `/auth/otp/send|resend|verify`;
 * docs/PHASE12.md). Uses the *un-authenticated* client, like [KtorAuthRepository]'s other auth
 * calls.
 *
 * Response bodies are decoded here, not by the client's content negotiation: error bodies are
 * `application/problem+json`, which that plugin does not claim, and one parsing path is easier to
 * reason about than two.
 *
 * Every failure is returned as a [Result.failure] carrying one of our own user-safe messages
 * ([OtpRequestFailedException] etc.); raw server text and exception messages never reach a view
 * model. Cancellation is always rethrown.
 */
internal class BackendOtpClient(private val httpClient: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /** `null` when the config can't be fetched or names an unknown provider; the caller then falls back to Firebase. */
    suspend fun fetchProvider(): OtpProvider? =
        try {
            val response = httpClient.get("/api/v1/auth/config")
            if (!response.status.isSuccess()) {
                null
            } else {
                when (json.decodeFromString<AuthConfigBody>(response.bodyAsText()).otpProvider) {
                    "msg91" -> OtpProvider.MSG91
                    "firebase" -> OtpProvider.FIREBASE
                    else -> null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    suspend fun send(phoneNumber: String): Result<PhoneVerificationHandle> =
        challengeCall("/api/v1/auth/otp/send") { setBody(SendBody(phoneNumber)) }

    suspend fun resend(challengeId: String): Result<PhoneVerificationHandle> =
        challengeCall("/api/v1/auth/otp/resend") { setBody(ResendBody(challengeId)) }

    /** On success the backend has already issued the session; the caller persists it. */
    suspend fun verify(challengeId: String, code: String): Result<AuthResult> =
        call {
            val response = httpClient.post("/api/v1/auth/otp/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyBody(challengeId, code))
            }
            if (response.status.isSuccess()) {
                Result.success(json.decodeFromString<AuthResult>(response.bodyAsText()))
            } else {
                Result.failure(failureFor(response))
            }
        }

    private suspend fun challengeCall(path: String, configure: HttpRequestBuilder.() -> Unit): Result<PhoneVerificationHandle> =
        call {
            val response = httpClient.post(path) {
                contentType(ContentType.Application.Json)
                configure()
            }
            if (response.status.isSuccess()) {
                val challenge = json.decodeFromString<ChallengeBody>(response.bodyAsText())
                Result.success(
                    PhoneVerificationHandle(
                        verificationId = challenge.challengeId,
                        resendToken = BackendResendToken(challenge.challengeId),
                    ),
                )
            } else {
                Result.failure(failureFor(response))
            }
        }

    private suspend fun <T> call(block: suspend () -> Result<T>): Result<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.failure(OtpRequestFailedException(NETWORK_MESSAGE))
        }

    /**
     * Maps an error response to a user-safe exception. The body is `application/problem+json`,
     * which the shared client's JSON content negotiation doesn't claim, so it is decoded by hand.
     */
    private suspend fun failureFor(response: HttpResponse): Exception {
        val problem = runCatching { json.decodeFromString<ProblemBody>(response.bodyAsText()) }.getOrNull()
        return when (problem?.code) {
            "INVALID_OTP" -> InvalidOtpCodeException(attemptsRemaining = problem.attemptsRemaining)
            "OTP_EXPIRED" -> OtpExpiredException()
            "INVALID_PHONE_NUMBER" -> OtpRequestFailedException("Enter a valid Indian mobile number.")
            "OTP_RESEND_LIMIT" -> OtpRequestFailedException("No more resends available. Please request a new code.")
            "RATE_LIMIT_EXCEEDED" -> OtpRequestFailedException(rateLimitMessage(problem.retryAfterSeconds))
            "APP_CHECK_FAILED" ->
                OtpRequestFailedException("This request couldn't be verified. Please update the app and try again.")
            "OTP_UNAVAILABLE" -> OtpRequestFailedException(UNAVAILABLE_MESSAGE)
            else ->
                if (response.status == HttpStatusCode.ServiceUnavailable) {
                    OtpRequestFailedException(UNAVAILABLE_MESSAGE)
                } else {
                    OtpRequestFailedException("Couldn't complete that. Please try again.")
                }
        }
    }

    private fun rateLimitMessage(retryAfterSeconds: Long?): String =
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            "Too many attempts. Please try again in $retryAfterSeconds seconds."
        } else {
            "Too many attempts. Please try again later."
        }

    @Serializable
    private data class AuthConfigBody(val otpProvider: String)

    @Serializable
    private data class SendBody(val phoneNumber: String)

    @Serializable
    private data class ResendBody(val challengeId: String)

    @Serializable
    private data class VerifyBody(val challengeId: String, val code: String)

    @Serializable
    private data class ChallengeBody(val challengeId: String)

    @Serializable
    private data class ProblemBody(
        val code: String? = null,
        val attemptsRemaining: Int? = null,
        val retryAfterSeconds: Long? = null,
    )

    private companion object {
        const val NETWORK_MESSAGE = "Couldn't reach the server. Check your connection and try again."
        const val UNAVAILABLE_MESSAGE = "We couldn't send a code right now. Please try again later."
    }
}

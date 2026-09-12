@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `OtpVerifyViewModel`'s state machine, per task-6-brief.md's exact (non-negotiable) numbers and
 * boundary cases: 60s resend cooldown with a visible countdown, max 3 resends (3rd succeeds, 4th
 * blocked), max 5 wrong attempts (5th forces navigation back to Phone Entry, not the 6th).
 * [FakePhoneAuthClient] substitutes for the real Firebase SDK boundary; [AuthRepository] is real.
 */
class OtpVerifyViewModelTest {

    private fun unusedHttpClient(): HttpClient = HttpClient(MockEngine) {
        engine { addHandler { error("no HTTP call expected") } }
    }

    private fun sessionHttpClient(profileComplete: Boolean): HttpClient = HttpClient(MockEngine) {
        install(ContentNegotiation) { json() }
        defaultRequest { url("http://localhost/") }
        engine {
            addHandler {
                respond(
                    content = """
                        {
                            "userId": "11111111-1111-1111-1111-111111111111",
                            "accessToken": "session-access",
                            "tokenType": "Bearer",
                            "accessTokenExpiresAt": "2026-09-03T12:00:00Z",
                            "refreshToken": "session-refresh",
                            "profileComplete": $profileComplete
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
    }

    private fun newViewModel(
        phoneAuthClient: FakePhoneAuthClient = FakePhoneAuthClient(),
        httpClient: HttpClient = unusedHttpClient(),
        secureStorage: FakeSecureStorage = FakeSecureStorage(),
    ): Triple<OtpVerifyViewModel, FakePhoneAuthClient, FakeSecureStorage> {
        val repository = KtorAuthRepository(httpClient, phoneAuthClient, secureStorage, authenticatedHttpClientProvider = { unusedHttpClient() })
        val viewModel = OtpVerifyViewModel(
            phoneNumber = "+919876543210",
            initialVerificationId = "initial-verification-id",
            initialResendToken = "initial-resend-token",
            authRepository = repository,
        )
        return Triple(viewModel, phoneAuthClient, secureStorage)
    }

    @Test
    fun `resend stays disabled through the 60s cooldown and enables exactly at 60s`() = viewModelTest {
        val (viewModel, _, _) = newViewModel()

        // t=0: cooldown just started.
        assertEquals(60, viewModel.state.value.cooldownSecondsRemaining)
        assertFalse(viewModel.state.value.canResend)

        advanceTimeBy(59_000)
        runCurrent()
        assertEquals(1, viewModel.state.value.cooldownSecondsRemaining)
        assertFalse(viewModel.state.value.canResend)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, viewModel.state.value.cooldownSecondsRemaining)
        assertTrue(viewModel.state.value.canResend)
    }

    @Test
    fun `a third resend succeeds - a fourth is blocked without ever calling Firebase again`() = viewModelTest {
        val (viewModel, phoneAuthClient, _) = newViewModel()

        repeat(3) { index ->
            advanceTimeBy(60_000)
            runCurrent()
            assertTrue(viewModel.state.value.canResend, "expected resend to be enabled before resend #${index + 1}")

            viewModel.resendCode()
            advanceUntilIdle()

            assertEquals(index + 1, viewModel.state.value.resendsUsed)
        }
        assertEquals(3, phoneAuthClient.sendCallCount)
        // The 3rd resend's own cooldown must finish before we can observe canResend staying false.
        advanceTimeBy(60_000)
        runCurrent()
        assertFalse(viewModel.state.value.canResend, "no further resends after the 3rd")

        viewModel.resendCode()
        advanceUntilIdle()

        assertEquals(3, phoneAuthClient.sendCallCount, "the 4th resend must never reach the phone auth client")
        assertEquals(3, viewModel.state.value.resendsUsed)
        assertTrue(viewModel.state.value.errorMessage != null)
    }

    @Test
    fun `the fifth wrong attempt forces navigation back to Phone Entry not the sixth`() = viewModelTest {
        val phoneAuthClient = FakePhoneAuthClient().apply {
            verifyCodeResult = Result.failure(InvalidOtpCodeException())
        }
        val (viewModel, _, _) = newViewModel(phoneAuthClient = phoneAuthClient)

        val observedEvents = mutableListOf<AuthNavigationEvent>()
        val collectorJob = launch { viewModel.navigationEvents.toList(observedEvents) }

        repeat(4) { attemptIndex ->
            viewModel.onCodeChanged("000000")
            viewModel.verifyCode()
            advanceUntilIdle()
            assertEquals(
                OtpVerifyViewModel.MAX_WRONG_ATTEMPTS - (attemptIndex + 1),
                viewModel.state.value.attemptsRemaining,
                "attempt #${attemptIndex + 1}",
            )
        }
        assertTrue(observedEvents.isEmpty(), "no forced navigation before the 5th wrong attempt")

        viewModel.onCodeChanged("000000")
        viewModel.verifyCode()
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.attemptsRemaining)
        assertEquals(1, observedEvents.size)
        assertEquals(AuthNavigationEvent.NavigateToPhoneEntry, observedEvents.single())
        assertEquals(5, phoneAuthClient.verifyCallCount)

        collectorJob.cancel()
    }

    @Test
    fun `a correct code with an incomplete profile navigates to Profile Setup and persists tokens`() = viewModelTest {
        val phoneAuthClient = FakePhoneAuthClient().apply {
            verifyCodeResult = Result.success("real-firebase-id-token")
        }
        val storage = FakeSecureStorage()
        val (viewModel, _, _) = newViewModel(
            phoneAuthClient = phoneAuthClient,
            httpClient = sessionHttpClient(profileComplete = false),
            secureStorage = storage,
        )

        viewModel.onCodeChanged("123456")
        viewModel.verifyCode()
        advanceUntilIdle()

        assertEquals(AuthNavigationEvent.NavigateToProfileSetup, viewModel.navigationEvents.first())
        assertEquals("session-access", storage.snapshot()[SecureStorageKeys.ACCESS_TOKEN])
        assertEquals("session-refresh", storage.snapshot()[SecureStorageKeys.REFRESH_TOKEN])
        assertFalse(viewModel.state.value.isVerifying)
    }

    @Test
    fun `a correct code with a complete profile navigates to Own Profile`() = viewModelTest {
        val phoneAuthClient = FakePhoneAuthClient().apply {
            verifyCodeResult = Result.success("real-firebase-id-token")
        }
        val (viewModel, _, _) = newViewModel(
            phoneAuthClient = phoneAuthClient,
            httpClient = sessionHttpClient(profileComplete = true),
        )

        viewModel.onCodeChanged("123456")
        viewModel.verifyCode()
        advanceUntilIdle()

        assertEquals(AuthNavigationEvent.NavigateToOwnProfile, viewModel.navigationEvents.first())
    }

    @Test
    fun `an incomplete code is rejected client-side without calling Firebase`() = viewModelTest {
        val (viewModel, phoneAuthClient, _) = newViewModel()

        viewModel.onCodeChanged("123")
        viewModel.verifyCode()
        advanceUntilIdle()

        assertEquals(0, phoneAuthClient.verifyCallCount)
        assertTrue(viewModel.state.value.errorMessage != null)
    }

    @Test
    fun `a network failure while verifying does not burn a wrong-code attempt - only an actual wrong code does`() =
        viewModelTest {
            val phoneAuthClient = FakePhoneAuthClient().apply {
                verifyCodeResult = Result.failure(RuntimeException("network blip, please retry"))
            }
            val (viewModel, _, _) = newViewModel(phoneAuthClient = phoneAuthClient)

            viewModel.onCodeChanged("123456")
            viewModel.verifyCode()
            advanceUntilIdle()

            assertEquals(
                OtpVerifyViewModel.MAX_WRONG_ATTEMPTS,
                viewModel.state.value.attemptsRemaining,
                "a transient/network failure must not consume a wrong-code attempt",
            )
            assertEquals("network blip, please retry", viewModel.state.value.errorMessage)

            // Now an actual wrong code -- this one DOES burn an attempt.
            phoneAuthClient.verifyCodeResult = Result.failure(InvalidOtpCodeException())
            viewModel.onCodeChanged("000000")
            viewModel.verifyCode()
            advanceUntilIdle()

            assertEquals(OtpVerifyViewModel.MAX_WRONG_ATTEMPTS - 1, viewModel.state.value.attemptsRemaining)
        }

    @Test
    fun `once resends are exhausted startOver offers a real path back to Phone Entry`() = viewModelTest {
        val (viewModel, phoneAuthClient, _) = newViewModel()

        repeat(3) {
            advanceTimeBy(60_000)
            runCurrent()
            viewModel.resendCode()
            advanceUntilIdle()
        }
        assertEquals(3, phoneAuthClient.sendCallCount)
        assertTrue(viewModel.state.value.resendsExhausted, "resends must be exhausted after the 3rd")

        val observedEvents = mutableListOf<AuthNavigationEvent>()
        val collectorJob = launch { viewModel.navigationEvents.toList(observedEvents) }

        viewModel.startOver()
        advanceUntilIdle()

        assertEquals(1, observedEvents.size)
        assertEquals(AuthNavigationEvent.NavigateToPhoneEntry, observedEvents.single())
        collectorJob.cancel()
    }
}

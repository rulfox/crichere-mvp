@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `PhoneEntryViewModel`'s success/failure paths, per task-6-brief.md's Testing Strategy: "success
 * path navigates with the right data, failure path surfaces an error state." [FakePhoneAuthClient]
 * substitutes for the real Firebase SDK boundary; everything else (`AuthRepository`) is real.
 */
class PhoneEntryViewModelTest {

    private fun unusedHttpClient(): HttpClient = HttpClient(MockEngine) {
        engine { addHandler { error("no HTTP call expected for phone entry") } }
    }

    @Test
    fun `a valid phone number that Firebase accepts navigates to OTP Verify with the real handle`() = viewModelTest {
        val phoneAuthClient = FakePhoneAuthClient().apply {
            sendVerificationCodeResult = Result.success(PhoneVerificationHandle("real-verification-id", resendToken = "token-42"))
        }
        val repository = KtorAuthRepository(unusedHttpClient(), phoneAuthClient, FakeSecureStorage(), authenticatedHttpClientProvider = { unusedHttpClient() })
        val viewModel = PhoneEntryViewModel(repository)

        viewModel.onPhoneNumberChanged("+919876543210")
        viewModel.requestCode()
        advanceUntilIdle()

        val event = viewModel.navigationEvents.first() as PhoneEntryNavigationEvent.NavigateToOtpVerify
        assertEquals("+919876543210", event.phoneNumber)
        assertEquals("real-verification-id", event.verificationId)
        assertEquals("token-42", event.resendToken)
        assertNull(viewModel.state.value.errorMessage)
        assertTrue(!viewModel.state.value.isSubmitting)
    }

    @Test
    fun `an implausible phone number is rejected before ever calling Firebase`() = viewModelTest {
        val phoneAuthClient = FakePhoneAuthClient()
        val repository = KtorAuthRepository(unusedHttpClient(), phoneAuthClient, FakeSecureStorage(), authenticatedHttpClientProvider = { unusedHttpClient() })
        val viewModel = PhoneEntryViewModel(repository)

        viewModel.onPhoneNumberChanged("not-a-phone-number")
        viewModel.requestCode()
        advanceUntilIdle()

        assertEquals(0, phoneAuthClient.sendCallCount)
        assertTrue(viewModel.state.value.errorMessage != null)
    }

    @Test
    fun `a Firebase failure surfaces as a clear error state instead of leaving the user stuck`() = viewModelTest {
        val phoneAuthClient = FakePhoneAuthClient().apply {
            sendVerificationCodeResult = Result.failure(IllegalStateException("Firebase quota exceeded"))
        }
        val repository = KtorAuthRepository(unusedHttpClient(), phoneAuthClient, FakeSecureStorage(), authenticatedHttpClientProvider = { unusedHttpClient() })
        val viewModel = PhoneEntryViewModel(repository)

        viewModel.onPhoneNumberChanged("+919876543210")
        viewModel.requestCode()
        advanceUntilIdle()

        assertEquals("Firebase quota exceeded", viewModel.state.value.errorMessage)
        assertTrue(!viewModel.state.value.isSubmitting)
    }

    @Test
    fun `clearing the phone number clears any previous error`() = viewModelTest {
        val phoneAuthClient = FakePhoneAuthClient().apply {
            sendVerificationCodeResult = Result.failure(IllegalStateException("boom"))
        }
        val repository = KtorAuthRepository(unusedHttpClient(), phoneAuthClient, FakeSecureStorage(), authenticatedHttpClientProvider = { unusedHttpClient() })
        val viewModel = PhoneEntryViewModel(repository)
        viewModel.onPhoneNumberChanged("+919876543210")
        viewModel.requestCode()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.errorMessage != null)

        viewModel.onPhoneNumberChanged("+919876543211")

        assertNull(viewModel.state.value.errorMessage)
    }
}

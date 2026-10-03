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

        viewModel.onPhoneNumberChanged("9876543210")
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

        viewModel.onPhoneNumberChanged("9876543210")
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
        viewModel.onPhoneNumberChanged("9876543210")
        viewModel.requestCode()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.errorMessage != null)

        viewModel.onPhoneNumberChanged("9876543211")

        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `the user types only the national number and the country code is added when sending`() = viewModelTest {
        val phoneAuthClient = FakePhoneAuthClient()
        val repository = KtorAuthRepository(unusedHttpClient(), phoneAuthClient, FakeSecureStorage(), authenticatedHttpClientProvider = { unusedHttpClient() })
        val viewModel = PhoneEntryViewModel(repository)

        viewModel.onPhoneNumberChanged("98765 43210")
        assertEquals("9876543210", viewModel.state.value.phoneNumber, "the field holds the national digits only")
        viewModel.requestCode()
        advanceUntilIdle()

        assertEquals(listOf("+919876543210"), phoneAuthClient.sentPhoneNumbers, "the provider is always given E.164")
        val event = viewModel.navigationEvents.first() as PhoneEntryNavigationEvent.NavigateToOtpVerify
        assertEquals("+919876543210", event.phoneNumber, "OTP Verify and resend keep working in E.164")
    }

    @Test
    fun `a pasted international number is cleaned to the national number`() = viewModelTest {
        val repository = KtorAuthRepository(unusedHttpClient(), FakePhoneAuthClient(), FakeSecureStorage(), authenticatedHttpClientProvider = { unusedHttpClient() })
        val viewModel = PhoneEntryViewModel(repository)

        viewModel.onPhoneNumberChanged("+91 98765 43210")

        assertEquals("9876543210", viewModel.state.value.phoneNumber)
    }

    @Test
    fun `a number that is not a valid 10-digit mobile gets a clear message and never reaches the provider`() = viewModelTest {
        val phoneAuthClient = FakePhoneAuthClient()
        val repository = KtorAuthRepository(unusedHttpClient(), phoneAuthClient, FakeSecureStorage(), authenticatedHttpClientProvider = { unusedHttpClient() })
        val viewModel = PhoneEntryViewModel(repository)

        listOf("987654321", "5876543210", "").forEach { typed ->
            viewModel.onPhoneNumberChanged(typed)
            viewModel.requestCode()
            advanceUntilIdle()
            assertEquals("Enter a valid 10-digit mobile number.", viewModel.state.value.errorMessage, "typed: '$typed'")
        }
        assertEquals(0, phoneAuthClient.sendCallCount)
    }
}

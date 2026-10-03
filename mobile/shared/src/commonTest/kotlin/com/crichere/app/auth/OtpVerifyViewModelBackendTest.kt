@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [OtpVerifyViewModel] with the backend-driven OTP (docs/PHASE12.md): the server's attempt count
 * wins, an expired challenge sends the user back to Phone Entry, and a backend-issued session
 * navigates without a second exchange. Uses [FakeAuthRepository], so the Firebase-path tests in
 * `OtpVerifyViewModelTest` remain the proof that path is unchanged.
 */
class OtpVerifyViewModelBackendTest {

    private val session = AuthResult(
        userId = "u-1",
        accessToken = "acc",
        accessTokenExpiresAt = "2026-01-01T00:15:00Z",
        refreshToken = "ref",
        profileComplete = true,
    )

    private fun viewModel(repository: FakeAuthRepository) =
        OtpVerifyViewModel("+919876543210", "chal-1", BackendResendToken("chal-1"), repository)

    @Test
    fun `the server's attempts remaining overrides the local count`() = viewModelTest {
        val repository = FakeAuthRepository().apply {
            nextVerifyOtpResult = Result.failure(InvalidOtpCodeException(attemptsRemaining = 2))
        }
        val viewModel = viewModel(repository)

        viewModel.onCodeChanged("000000")
        viewModel.verifyCode()
        advanceUntilIdle()

        // Locally this would be 4 (first wrong attempt); the server said 2.
        assertEquals(2, viewModel.state.value.attemptsRemaining)
        assertEquals("Incorrect code. 2 attempts remaining.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `the server reporting zero remaining forces the user back to Phone Entry`() = viewModelTest {
        val repository = FakeAuthRepository().apply {
            nextVerifyOtpResult = Result.failure(InvalidOtpCodeException(attemptsRemaining = 0))
        }
        val viewModel = viewModel(repository)

        viewModel.onCodeChanged("000000")
        viewModel.verifyCode()
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.attemptsRemaining)
        assertEquals(AuthNavigationEvent.NavigateToPhoneEntry, viewModel.navigationEvents.first())
    }

    @Test
    fun `an expired code explains itself and returns to Phone Entry`() = viewModelTest {
        val repository = FakeAuthRepository().apply {
            nextVerifyOtpResult = Result.failure(OtpExpiredException())
        }
        val viewModel = viewModel(repository)

        viewModel.onCodeChanged("123456")
        viewModel.verifyCode()
        advanceUntilIdle()

        assertEquals("This code has expired. Please request a new one.", viewModel.state.value.errorMessage)
        assertEquals(AuthNavigationEvent.NavigateToPhoneEntry, viewModel.navigationEvents.first())
    }

    @Test
    fun `a backend-issued session navigates directly and does not exchange anything`() = viewModelTest {
        val repository = FakeAuthRepository().apply {
            nextVerifyOtpResult = Result.success(OtpVerification.BackendSession(session))
        }
        val viewModel = viewModel(repository)

        viewModel.onCodeChanged("123456")
        viewModel.verifyCode()
        advanceUntilIdle()

        assertEquals(AuthNavigationEvent.NavigateToOwnProfile, viewModel.navigationEvents.first())
        assertEquals(emptyList(), repository.exchangeSessionCalls)
        assertEquals(false, viewModel.state.value.isVerifying)
    }

    @Test
    fun `a failure that is not a wrong code does not burn an attempt`() = viewModelTest {
        val repository = FakeAuthRepository().apply {
            nextVerifyOtpResult = Result.failure(OtpRequestFailedException("We couldn't send a code right now. Please try again later."))
        }
        val viewModel = viewModel(repository)

        viewModel.onCodeChanged("123456")
        viewModel.verifyCode()
        advanceUntilIdle()

        assertEquals(OtpVerifyViewModel.MAX_WRONG_ATTEMPTS, viewModel.state.value.attemptsRemaining)
        assertEquals("We couldn't send a code right now. Please try again later.", viewModel.state.value.errorMessage)
    }
}

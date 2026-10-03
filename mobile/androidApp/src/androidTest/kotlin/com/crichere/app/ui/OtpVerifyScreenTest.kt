package com.crichere.app.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.crichere.app.auth.AuthResult
import com.crichere.app.auth.FakeAuthRepository
import com.crichere.app.auth.OtpVerification
import com.crichere.app.auth.OtpVerifyViewModel
import org.junit.Rule
import org.junit.Test

class OtpVerifyScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enteringTheCorrectSixDigitCodeExchangesASession() {
        val authRepository = FakeAuthRepository().apply {
            nextVerifyOtpResult = Result.success(OtpVerification.FirebaseIdToken("firebase-id-token"))
            nextExchangeSessionResult = AuthResult(
                userId = "user-1",
                accessToken = "access-1",
                accessTokenExpiresAt = "2026-10-01T00:00:00Z",
                refreshToken = "refresh-1",
                profileComplete = true,
            )
        }
        val viewModel = OtpVerifyViewModel(
            phoneNumber = "+919876543210",
            initialVerificationId = "verification-1",
            initialResendToken = null,
            authRepository = authRepository,
        )

        composeRule.setContent { OtpVerifyScreen(viewModel, phoneNumber = "+919876543210") }

        composeRule.onNodeWithText("6-digit code").performTextInput("123456")
        composeRule.onNodeWithText("Verify").performClick()
        composeRule.waitForIdle()

        assert(authRepository.verifyOtpCalls == listOf("verification-1" to "123456")) {
            "expected verifyOtp(verification-1, 123456), got ${authRepository.verifyOtpCalls}"
        }
        assert(authRepository.exchangeSessionCalls == listOf("firebase-id-token"))
    }

    @Test
    fun theNumberIsShownWithoutTheCountryCode() {
        val viewModel = OtpVerifyViewModel(
            phoneNumber = "+919876543210",
            initialVerificationId = "verification-1",
            initialResendToken = null,
            authRepository = FakeAuthRepository(),
        )

        composeRule.setContent { OtpVerifyScreen(viewModel, phoneNumber = "+919876543210") }

        composeRule.onNodeWithText("98765 43210", substring = true).assertExists()
        composeRule.onNodeWithText("+91", substring = true).assertDoesNotExist()
    }

    @Test
    fun verifyStaysDisabledUntilSixDigitsAreEntered() {
        val authRepository = FakeAuthRepository()
        val viewModel = OtpVerifyViewModel(
            phoneNumber = "+919876543210",
            initialVerificationId = "verification-1",
            initialResendToken = null,
            authRepository = authRepository,
        )

        composeRule.setContent { OtpVerifyScreen(viewModel, phoneNumber = "+919876543210") }

        composeRule.onNodeWithText("6-digit code").performTextInput("123")
        composeRule.onNodeWithText("Verify").assertIsNotEnabled()
        composeRule.onNodeWithText("6-digit code").performTextInput("456")
        composeRule.onNodeWithText("Verify").assertIsEnabled()
        assert(authRepository.verifyOtpCalls.isEmpty())
    }
}

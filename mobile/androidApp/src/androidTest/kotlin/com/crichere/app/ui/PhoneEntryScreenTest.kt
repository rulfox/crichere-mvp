package com.crichere.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.crichere.app.auth.FakeAuthRepository
import com.crichere.app.auth.PhoneEntryViewModel
import com.crichere.app.auth.PhoneVerificationHandle
import org.junit.Rule
import org.junit.Test

/**
 * Compose instrumented test -- real device/emulator, real Compose rendering, no screenshots (see
 * docs/ARCHITECTURE.md's Testing section: this asserts against the semantics tree, same speed
 * philosophy as the web-viewer's Playwright suite's `browser_snapshot` preference).
 */
class PhoneEntryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enteringAPhoneNumberAndSubmittingCallsSendOtp() {
        val authRepository = FakeAuthRepository().apply {
            nextSendOtpResult = Result.success(PhoneVerificationHandle(verificationId = "verification-1"))
        }
        val viewModel = PhoneEntryViewModel(authRepository)

        composeRule.setContent { PhoneEntryScreen(viewModel) }

        composeRule.onNodeWithText("Phone number (e.g. +919876543210)").performTextInput("+919876543210")
        composeRule.onNodeWithText("Send code").performClick()
        composeRule.waitForIdle()

        assert(authRepository.sendOtpCalls == listOf("+919876543210")) {
            "expected sendOtp to be called with +919876543210, got ${authRepository.sendOtpCalls}"
        }
    }

    @Test
    fun anImplausibleNumberIsRejectedWithoutCallingSendOtp() {
        val authRepository = FakeAuthRepository()
        val viewModel = PhoneEntryViewModel(authRepository)

        composeRule.setContent { PhoneEntryScreen(viewModel) }

        composeRule.onNodeWithText("Phone number (e.g. +919876543210)").performTextInput("12345")
        composeRule.onNodeWithText("Send code").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Enter a valid phone number with country code, e.g. +919876543210.").assertExists()
        assert(authRepository.sendOtpCalls.isEmpty())
    }
}

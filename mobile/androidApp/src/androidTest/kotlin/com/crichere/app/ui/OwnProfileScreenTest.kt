package com.crichere.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.crichere.app.auth.FakeAuthRepository
import com.crichere.app.profile.FakeProfileRepository
import com.crichere.app.profile.OwnProfileViewModel
import com.crichere.app.profile.ProfileDto
import org.junit.Rule
import org.junit.Test

class OwnProfileScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTheLoadedProfileAndLogsOutOnTap() {
        val profileRepository = FakeProfileRepository(
            profile = ProfileDto(userId = "user-1", name = "Rahul Sharma", state = "Karnataka", city = "Bengaluru"),
        )
        val authRepository = FakeAuthRepository(currentUserId = "user-1")
        val viewModel = OwnProfileViewModel(profileRepository, authRepository)
        viewModel.retry()

        composeRule.setContent { OwnProfileScreen(viewModel, onNavigateToEditProfile = {}, onNavigateToPhoneEntry = {}) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Name: Rahul Sharma").assertExists()
        composeRule.onNodeWithText("City: Bengaluru").assertExists()

        composeRule.onNodeWithText("Log out").performClick()
        composeRule.waitForIdle()

        assert(authRepository.logoutCallCount == 1)
    }
}

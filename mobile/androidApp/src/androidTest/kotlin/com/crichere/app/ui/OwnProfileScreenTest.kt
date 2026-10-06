package com.crichere.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
            profile = ProfileDto(userId = "user-1", name = "Rahul Sharma", state = "Karnataka"),
        )
        val authRepository = FakeAuthRepository(currentUserId = "user-1")
        val viewModel = OwnProfileViewModel(profileRepository, authRepository)
        viewModel.retry()

        composeRule.setContent { OwnProfileScreen(viewModel, onNavigateToEditProfile = {}, onNavigateToPhoneEntry = {}, onViewPhoto = {}) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Rahul Sharma").assertExists()
        composeRule.onNodeWithText("Bengaluru").assertExists()

        // Design N4: Log out asks first.
        composeRule.onNodeWithText("Log out").performClick()
        composeRule.onNodeWithText("Log out of Crichere?").assertExists()
        assert(authRepository.logoutCallCount == 0)
        composeRule.onAllNodesWithText("Log out")[1].performClick()
        composeRule.waitForIdle()

        assert(authRepository.logoutCallCount == 1)
    }
}

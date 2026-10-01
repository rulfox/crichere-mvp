package com.crichere.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.crichere.app.ground.FakeGroundRepository
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.league.LeagueCreationViewModel
import com.crichere.app.location.FakeLocationProvider
import com.crichere.app.reference.FakeReferenceRepository
import org.junit.Rule
import org.junit.Test

class LeagueCreationScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel() = LeagueCreationViewModel(
        editingLeagueId = null,
        leagueRepository = FakeLeagueRepository(),
        groundRepository = FakeGroundRepository(),
        referenceRepository = FakeReferenceRepository(),
        locationProvider = FakeLocationProvider(),
    )

    @Test
    fun createModeRendersAnEmptyFormAndAcceptsTypedInput() {
        val viewModel = viewModel()
        composeRule.setContent { LeagueCreationRoute(editingLeagueId = null, onDone = {}, onCancel = {}, viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction() and hasText("League name")).performTextInput("Riverside Premier League")
        composeRule.waitForIdle()

        assert(viewModel.state.value.name == "Riverside Premier League") {
            "expected typed name to reach ViewModel state, got ${viewModel.state.value.name}"
        }
    }

    @Test
    fun closingAnUntouchedFormLeavesStraightAway() {
        var cancelled = false
        composeRule.setContent {
            LeagueCreationRoute(editingLeagueId = null, onDone = {}, onCancel = { cancelled = true }, viewModel = viewModel())
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Close").performClick()
        assert(cancelled)
    }

    @Test
    fun closingAnEditedFormAsksBeforeDiscarding() {
        var cancelled = false
        val viewModel = viewModel()
        composeRule.setContent {
            LeagueCreationRoute(editingLeagueId = null, onDone = {}, onCancel = { cancelled = true }, viewModel = viewModel)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { viewModel.onNameChanged("Riverside Premier League") }

        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        assert(!cancelled)
        composeRule.onNodeWithText("Discard").performClick()
        assert(cancelled)
    }

    @Test
    fun savingWithMissingFieldsShowsWhatNeedsAttention() {
        val viewModel = viewModel()
        composeRule.setContent { LeagueCreationRoute(editingLeagueId = null, onDone = {}, onCancel = {}, viewModel = viewModel) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { viewModel.onNameChanged("Riverside Premier League") }

        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("4 fields need attention").assertIsDisplayed()
    }
}

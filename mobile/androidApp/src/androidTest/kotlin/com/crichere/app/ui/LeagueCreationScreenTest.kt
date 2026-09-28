package com.crichere.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    @Test
    fun createModeRendersAnEmptyFormAndAcceptsTypedInput() {
        val viewModel = LeagueCreationViewModel(
            editingLeagueId = null,
            leagueRepository = FakeLeagueRepository(),
            groundRepository = FakeGroundRepository(),
            referenceRepository = FakeReferenceRepository(),
            locationProvider = FakeLocationProvider(),
        )

        composeRule.setContent { LeagueCreationRoute(editingLeagueId = null, onDone = {}, onCancel = {}, viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("League name").assertExists()
        composeRule.onNodeWithText("League name").performTextInput("Riverside Premier League")
        composeRule.waitForIdle()

        assert(viewModel.state.value.name == "Riverside Premier League") {
            "expected typed name to reach ViewModel state, got ${viewModel.state.value.name}"
        }
    }

    @Test
    fun tappingCancelInvokesTheCallback() {
        val viewModel = LeagueCreationViewModel(
            editingLeagueId = null,
            leagueRepository = FakeLeagueRepository(),
            groundRepository = FakeGroundRepository(),
            referenceRepository = FakeReferenceRepository(),
            locationProvider = FakeLocationProvider(),
        )

        var cancelled = false
        composeRule.setContent {
            LeagueCreationRoute(editingLeagueId = null, onDone = {}, onCancel = { cancelled = true }, viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // The Cancel button sits below the fold in this long scrollable form (verticalScroll,
        // not LazyColumn -- the whole form is laid out at once, just visually clipped), so it
        // needs a scroll-into-view before a synthetic tap can actually land on it.
        composeRule.onNodeWithText("Cancel").performScrollTo().performClick()
        assert(cancelled)
    }
}

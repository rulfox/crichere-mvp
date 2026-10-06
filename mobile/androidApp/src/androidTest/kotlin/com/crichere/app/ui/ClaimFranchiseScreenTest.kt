package com.crichere.app.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.crichere.app.league.ClaimFranchiseViewModel
import com.crichere.app.league.FakeFranchiseRepository
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueFranchiseDto
import com.crichere.app.league.LeagueStatus
import org.junit.Rule
import org.junit.Test

class ClaimFranchiseScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fixtureLeague = LeagueDto(
        id = "league-1",
        organizerUserId = "organizer-1",
        name = "Riverside Premier League",
        state = "Karnataka",
        district = "Bengaluru Urban", groundId = "g1", groundName = "Test Ground",
        startsOn = "2026-10-04",
        status = LeagueStatus.ANNOUNCED,
        // No franchiseFee -- keeps this test focused on the claim action, not the screenshot sub-flow.
    )

    @Test
    fun claimingAFranchiseWithANameCallsFranchiseRepository() {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(fixtureLeague))
        val franchiseRepository = FakeFranchiseRepository().apply {
            nextClaimed = LeagueFranchiseDto(id = "franchise-1", ownerUserId = "user-1", name = "Thunder Kings", joinedAt = "2026-09-28T10:00:00Z")
        }
        val viewModel = ClaimFranchiseViewModel(leagueId = "league-1", leagueRepository, franchiseRepository)

        var done = false
        composeRule.setContent {
            ClaimFranchiseRoute(leagueId = "league-1", onDone = { done = true }, onCancel = {}, viewModel = viewModel)
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction()).performTextInput("Thunder Kings")
        composeRule.onNodeWithText("Claim Franchise").performClick()
        composeRule.waitForIdle()

        assert(franchiseRepository.claimRequests.size == 1) { "expected one claim request, got ${franchiseRepository.claimRequests}" }
        assert(franchiseRepository.claimRequests.first().second.name == "Thunder Kings")
        assert(done)
    }

    @Test
    fun clearingTheNameShowsAValidationErrorAndBlocksTheClaim() {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(fixtureLeague))
        val franchiseRepository = FakeFranchiseRepository()
        val viewModel = ClaimFranchiseViewModel(leagueId = "league-1", leagueRepository, franchiseRepository)

        composeRule.setContent { ClaimFranchiseRoute(leagueId = "league-1", onDone = {}, onCancel = {}, viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction()).performTextInput("T")
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Enter a franchise name").assertExists()
        composeRule.onNodeWithText("Claim Franchise").assertIsNotEnabled()
        assert(franchiseRepository.claimRequests.isEmpty())
    }
}

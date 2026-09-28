package com.crichere.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.league.FakePlayerRepository
import com.crichere.app.league.JoinLeagueViewModel
import com.crichere.app.league.LeaguePlayerDto
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueStatus
import org.junit.Rule
import org.junit.Test

class JoinLeagueScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fixtureLeague = LeagueDto(
        id = "league-1",
        organizerUserId = "organizer-1",
        name = "Riverside Premier League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = "2026-10-04",
        status = LeagueStatus.ANNOUNCED,
        // No playerFee -- submit() doesn't require a payment screenshot in that case, keeping
        // this test focused on the join action itself, not the screenshot-upload sub-flow.
    )

    @Test
    fun joiningAFreeLeagueCallsPlayerRepositoryAndSignalsDone() {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(fixtureLeague))
        val playerRepository = FakePlayerRepository().apply {
            nextJoined = LeaguePlayerDto(id = "player-1", userId = "user-1", joinedAt = "2026-09-28T10:00:00Z")
        }
        val viewModel = JoinLeagueViewModel(leagueId = "league-1", leagueRepository, playerRepository)

        var done = false
        composeRule.setContent {
            JoinLeagueRoute(leagueId = "league-1", onDone = { done = true }, onCancel = {}, viewModel = viewModel)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Join Riverside Premier League as a Player").assertExists()
        composeRule.onNodeWithText("Join").performClick()
        composeRule.waitForIdle()

        assert(playerRepository.joinRequests.size == 1) { "expected one join request, got ${playerRepository.joinRequests}" }
        assert(done) { "expected onDone to fire after a successful join" }
    }
}

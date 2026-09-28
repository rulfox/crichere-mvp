package com.crichere.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.crichere.app.auth.FakeAuthRepository
import com.crichere.app.league.AuctionStateDto
import com.crichere.app.league.AuctionStatus
import com.crichere.app.league.AuctionViewModel
import com.crichere.app.league.FakeAuctionRepository
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class AuctionLiveScreenTest {

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
    )

    @Test
    fun organizerCanStartTheAuction() {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(fixtureLeague))
        val auctionRepository = FakeAuctionRepository().apply {
            nextState = AuctionStateDto(auctionStatus = AuctionStatus.IN_PROGRESS)
            // AuctionViewModel only flips isLoading to false once the SSE stream delivers its
            // first value (see AuctionViewModel.onAuctionState) -- the real backend always sends
            // one immediately on subscribe (docs/PHASE5.md), so the fake needs to as well, or the
            // screen sits on its loading spinner forever.
            stream = flowOf(AuctionStateDto(auctionStatus = AuctionStatus.NOT_STARTED))
        }
        val authRepository = FakeAuthRepository(currentUserId = "organizer-1")
        val viewModel = AuctionViewModel(leagueId = "league-1", leagueRepository, auctionRepository, authRepository)

        composeRule.setContent { AuctionLiveRoute(leagueId = "league-1", onBack = {}, viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Status: NOT_STARTED").assertExists()
        composeRule.onNodeWithText("Start Auction").performClick()
        composeRule.waitForIdle()

        assert(auctionRepository.actionCalls == listOf("start")) {
            "expected the start action to fire, got ${auctionRepository.actionCalls}"
        }
        composeRule.onNodeWithText("Status: IN_PROGRESS").assertExists()
    }

    @Test
    fun nonOrganizerDoesNotSeeOrganizerControls() {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(fixtureLeague))
        val auctionRepository = FakeAuctionRepository().apply {
            stream = flowOf(AuctionStateDto(auctionStatus = AuctionStatus.NOT_STARTED))
        }
        val authRepository = FakeAuthRepository(currentUserId = "some-other-user")
        val viewModel = AuctionViewModel(leagueId = "league-1", leagueRepository, auctionRepository, authRepository)

        composeRule.setContent { AuctionLiveRoute(leagueId = "league-1", onBack = {}, viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Live Auction").assertExists()
        assert(auctionRepository.actionCalls.isEmpty())
    }
}

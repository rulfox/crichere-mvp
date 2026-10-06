package com.crichere.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.crichere.app.league.AuctionSettingsViewModel
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueStatus
import org.junit.Rule
import org.junit.Test

class AuctionSettingsScreenTest {

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
    )

    @Test
    fun fillingAllFiveFieldsAndSavingCallsUpdateAuctionSettings() {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(fixtureLeague)).apply {
            nextAuctionSettingsUpdated = fixtureLeague.copy(auctionBasePrice = 2000.0)
        }
        val viewModel = AuctionSettingsViewModel(leagueId = "league-1", leagueRepository)

        composeRule.setContent { AuctionSettingsRoute(leagueId = "league-1", onBack = {}, viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Base price").performTextInput("2000")
        composeRule.onNodeWithText("Purse per franchise").performTextInput("100000")
        composeRule.onNodeWithText("Squad size (min)").performTextInput("11")
        composeRule.onNodeWithText("Squad size (max)").performTextInput("15")
        composeRule.onNodeWithText("Bid increment").performTextInput("500")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        assert(leagueRepository.updateAuctionSettingsRequests.size == 1) {
            "expected one save request, got ${leagueRepository.updateAuctionSettingsRequests}"
        }
    }

    @Test
    fun anInvalidAmountShowsItsErrorAndSaveSendsNothing() {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(fixtureLeague))
        val viewModel = AuctionSettingsViewModel(leagueId = "league-1", leagueRepository)

        composeRule.setContent { AuctionSettingsRoute(leagueId = "league-1", onBack = {}, viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Purse per franchise").performTextInput("75k")
        composeRule.onNodeWithText("Enter a number").assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        assert(leagueRepository.updateAuctionSettingsRequests.isEmpty()) {
            "expected no save request, got ${leagueRepository.updateAuctionSettingsRequests}"
        }
    }

    @Test
    fun aScheduledTimeCanBeClearedBeforeSaving() {
        val scheduled = fixtureLeague.copy(
            auctionBasePrice = 2000.0, auctionPurse = 100000.0, auctionSquadMin = 11, auctionSquadMax = 15, auctionBidIncrement = 500.0,
            auctionScheduledAt = "2026-10-12T13:30:00Z",
        )
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(scheduled)).apply { nextAuctionSettingsUpdated = scheduled }
        val viewModel = AuctionSettingsViewModel(leagueId = "league-1", leagueRepository)

        composeRule.setContent { AuctionSettingsRoute(leagueId = "league-1", onBack = {}, viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Auction date & time (optional)").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Clear auction time").performClick()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        assert(leagueRepository.updateAuctionSettingsRequests.single().scheduledAt == null) {
            "expected the cleared time to be sent as null, got ${leagueRepository.updateAuctionSettingsRequests}"
        }
    }
}

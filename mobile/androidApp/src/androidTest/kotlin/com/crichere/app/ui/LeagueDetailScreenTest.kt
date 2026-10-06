package com.crichere.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.crichere.app.auth.FakeAuthRepository
import com.crichere.app.league.FakeFranchiseRepository
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.league.FakePlayerRepository
import com.crichere.app.league.LeagueDetailViewModel
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueStatus
import org.junit.Rule
import org.junit.Test

class LeagueDetailScreenTest {

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
    fun rendersTheLoadedLeagueAndGoesBackOnTap() {
        val viewModel = LeagueDetailViewModel(
            leagueId = "league-1",
            leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(fixtureLeague)),
            authRepository = FakeAuthRepository(currentUserId = "organizer-1"),
            playerRepository = FakePlayerRepository(),
            franchiseRepository = FakeFranchiseRepository(),
        )

        var backTapped = false
        composeRule.setContent {
            LeagueDetailRoute(
                leagueId = "league-1",
                onBack = { backTapped = true },
                onEditLeague = {},
                onJoinLeague = {},
                onClaimFranchise = {},
                onViewScreenshot = {},
                onAuctionSettings = {},
                onAuctionLive = {},
                onManageRoles = {},
                viewModel = viewModel,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Riverside Premier League").assertExists()
        composeRule.onNodeWithText("Bengaluru, Bengaluru Urban, Karnataka").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assert(backTapped)
    }
}

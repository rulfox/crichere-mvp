package com.crichere.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.crichere.app.league.LeagueDashboardViewModel
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueStatus
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.location.FakeLocationProvider
import com.crichere.app.reference.FakeReferenceRepository
import org.junit.Rule
import org.junit.Test

class LeagueDashboardScreenTest {

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
    fun rendersLeaguesAndOpensOneOnTap() {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(fixtureLeague))
        val viewModel = LeagueDashboardViewModel(leagueRepository, FakeReferenceRepository(), FakeLocationProvider())

        var openedLeagueId: String? = null
        composeRule.setContent {
            LeagueDashboardRoute(onOpenLeague = { openedLeagueId = it }, onCreateLeague = {}, viewModel = viewModel)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Riverside Premier League").assertExists()
        composeRule.onNodeWithText("Riverside Premier League").performClick()
        composeRule.waitForIdle()

        assert(openedLeagueId == "league-1") { "expected league-1 to open, got $openedLeagueId" }
    }

    @Test
    fun tappingCreateALeagueInvokesTheCallback() {
        val leagueRepository = FakeLeagueRepository()
        val viewModel = LeagueDashboardViewModel(leagueRepository, FakeReferenceRepository(), FakeLocationProvider())

        var createTapped = false
        composeRule.setContent {
            LeagueDashboardRoute(onOpenLeague = {}, onCreateLeague = { createTapped = true }, viewModel = viewModel)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Create a league").performClick()
        assert(createTapped)
    }
}

package com.crichere.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.league.FakeRoleRepository
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueStatus
import com.crichere.app.league.ManageRolesViewModel
import com.crichere.app.league.RoleLookupResultDto
import org.junit.Rule
import org.junit.Test

class ManageRolesScreenTest {

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
    fun lookingUpAPhoneNumberShowsTheMatchedUser() {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(fixtureLeague))
        val roleRepository = FakeRoleRepository().apply {
            nextLookupResult = RoleLookupResultDto(userId = "user-2", name = "Priya Nair")
        }
        val viewModel = ManageRolesViewModel(leagueId = "league-1", leagueRepository, roleRepository)

        composeRule.setContent { ManageRolesRoute(leagueId = "league-1", onBack = {}, viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Phone number").performTextInput("+919876500000")
        composeRule.onNodeWithText("Look up").performClick()
        composeRule.waitForIdle()

        assert(roleRepository.lookupCalls == listOf("league-1" to "+919876500000")) {
            "expected a lookup call, got ${roleRepository.lookupCalls}"
        }
        composeRule.onNodeWithText("Found: Priya Nair").assertExists()
    }

    @Test
    fun lookingUpAnUnknownNumberShowsNoUserFound() {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(fixtureLeague))
        val roleRepository = FakeRoleRepository()
        val viewModel = ManageRolesViewModel(leagueId = "league-1", leagueRepository, roleRepository)

        composeRule.setContent { ManageRolesRoute(leagueId = "league-1", onBack = {}, viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Phone number").performTextInput("+919876500000")
        composeRule.onNodeWithText("Look up").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No user found with that phone number.").assertExists()
    }
}

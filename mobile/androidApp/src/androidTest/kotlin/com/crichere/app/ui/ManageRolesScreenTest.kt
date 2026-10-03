package com.crichere.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.league.FakeRoleRepository
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueRoleDto
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

        composeRule.onNodeWithText("Phone number").performTextInput("9876500000")
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

        composeRule.onNodeWithText("Phone number").performTextInput("9876500000")
        composeRule.onNodeWithText("Look up").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No user found with that phone number.").assertExists()
    }

    @Test
    fun revokeAsksForConfirmationBeforeRevoking() {
        val withDelegate = fixtureLeague.copy(coOrganizers = listOf(LeagueRoleDto(id = "r1", userId = "u2", name = "Amit Jadhav", grantedAt = "2026-09-13T00:00:00Z")))
        val roleRepository = FakeRoleRepository().apply { nextLeague = fixtureLeague }
        val viewModel = ManageRolesViewModel(leagueId = "league-1", FakeLeagueRepository(leaguesByArea = listOf(withDelegate)), roleRepository)

        composeRule.setContent { ManageRolesRoute(leagueId = "league-1", onBack = {}, viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Revoke").performClick()
        composeRule.onNodeWithText("Revoke Amit Jadhav?").assertExists()
        assert(roleRepository.revokeCalls.isEmpty()) { "revoked before confirming" }

        // Dialog open: the row's pill and the dialog's button both read "Revoke"; the dialog's is last.
        val revokeButtons = composeRule.onAllNodesWithText("Revoke")
        revokeButtons[revokeButtons.fetchSemanticsNodes().size - 1].performClick()
        composeRule.waitForIdle()

        assert(roleRepository.revokeCalls == listOf("r1")) { "expected one revoke of r1, got ${roleRepository.revokeCalls}" }
        composeRule.onNodeWithText("No co-organizers yet.").assertExists()
    }
}

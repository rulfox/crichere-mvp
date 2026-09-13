@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.league

import com.crichere.app.auth.viewModelTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManageRolesViewModelTest {

    private fun sampleLeague(coOrganizers: List<LeagueRoleDto> = emptyList()) = LeagueDto(
        id = "l1",
        organizerUserId = "organizer-1",
        name = "Weekend League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = "2026-10-12",
        status = LeagueStatus.ANNOUNCED,
        coOrganizers = coOrganizers,
    )

    @Test
    fun `retry loads the league`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val viewModel = ManageRolesViewModel("l1", leagueRepository, FakeRoleRepository())

        viewModel.retry()
        advanceUntilIdle()

        assertEquals("Weekend League", viewModel.state.value.league?.name)
        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `a successful lookup surfaces the matched user, ready for grant`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val roleRepository = FakeRoleRepository().apply { nextLookupResult = RoleLookupResultDto(userId = "u2", name = "Delegate Name") }
        val viewModel = ManageRolesViewModel("l1", leagueRepository, roleRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onPhoneNumberChanged("+919876543210")
        viewModel.lookup()
        advanceUntilIdle()

        assertEquals("u2", viewModel.state.value.lookupResult?.userId)
        assertTrue(viewModel.state.value.lookupAttempted)
        assertEquals(listOf("l1" to "+919876543210"), roleRepository.lookupCalls)
    }

    @Test
    fun `a lookup that finds nobody clears the result but still marks lookupAttempted`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val roleRepository = FakeRoleRepository().apply { nextLookupResult = null }
        val viewModel = ManageRolesViewModel("l1", leagueRepository, roleRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onPhoneNumberChanged("+919876543210")
        viewModel.lookup()
        advanceUntilIdle()

        assertNull(viewModel.state.value.lookupResult)
        assertTrue(viewModel.state.value.lookupAttempted)
    }

    @Test
    fun `grant calls the repository with the looked-up user id and replaces league state with the response`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val grantedLeague = sampleLeague(coOrganizers = listOf(LeagueRoleDto(id = "r1", userId = "u2", name = "Delegate Name", grantedAt = "2026-09-13T00:00:00Z")))
        val roleRepository = FakeRoleRepository().apply {
            nextLookupResult = RoleLookupResultDto(userId = "u2", name = "Delegate Name")
            nextLeague = grantedLeague
        }
        val viewModel = ManageRolesViewModel("l1", leagueRepository, roleRepository)
        viewModel.retry()
        advanceUntilIdle()
        viewModel.onPhoneNumberChanged("+919876543210")
        viewModel.lookup()
        advanceUntilIdle()

        viewModel.grant()
        advanceUntilIdle()

        assertEquals(listOf("u2"), roleRepository.grantCalls)
        assertEquals(1, viewModel.state.value.league?.coOrganizers?.size)
        assertEquals("", viewModel.state.value.phoneNumberInput)
        assertNull(viewModel.state.value.lookupResult)
    }

    @Test
    fun `revoke calls the repository with the role id and replaces league state with the response`() = viewModelTest {
        val existing = sampleLeague(coOrganizers = listOf(LeagueRoleDto(id = "r1", userId = "u2", name = "Delegate Name", grantedAt = "2026-09-13T00:00:00Z")))
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(existing))
        val roleRepository = FakeRoleRepository().apply { nextLeague = sampleLeague() }
        val viewModel = ManageRolesViewModel("l1", leagueRepository, roleRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.revoke("r1")
        advanceUntilIdle()

        assertEquals(listOf("r1"), roleRepository.revokeCalls)
        assertEquals(0, viewModel.state.value.league?.coOrganizers?.size)
        assertTrue(viewModel.state.value.revokingRoleIds.isEmpty())
    }

    @Test
    fun `a grant failure surfaces the server's error message instead of crashing`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val roleRepository = FakeRoleRepository().apply {
            nextLookupResult = RoleLookupResultDto(userId = "u2", name = "Delegate Name")
            actionError = RuntimeException("This user already has that role on this league")
        }
        val viewModel = ManageRolesViewModel("l1", leagueRepository, roleRepository)
        viewModel.retry()
        advanceUntilIdle()
        viewModel.onPhoneNumberChanged("+919876543210")
        viewModel.lookup()
        advanceUntilIdle()

        viewModel.grant()
        advanceUntilIdle()

        assertEquals("This user already has that role on this league", viewModel.state.value.errorMessage)
        assertTrue(!viewModel.state.value.isGranting)
    }
}

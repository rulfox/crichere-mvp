@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.league

import com.crichere.app.auth.viewModelTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertNull(viewModel.state.value.lookupError)
        assertEquals(listOf("l1" to "+919876543210"), roleRepository.lookupCalls)
    }

    @Test
    fun `a lookup that finds nobody shows the inline not-found error`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val roleRepository = FakeRoleRepository().apply { nextLookupResult = null }
        val viewModel = ManageRolesViewModel("l1", leagueRepository, roleRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onPhoneNumberChanged("+919876543210")
        viewModel.lookup()
        advanceUntilIdle()

        assertNull(viewModel.state.value.lookupResult)
        assertEquals("No user found with that phone number.", viewModel.state.value.lookupError)
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

    private suspend fun kotlinx.coroutines.test.TestScope.lookedUp(roleRepository: FakeRoleRepository, league: LeagueDto = sampleLeague()): ManageRolesViewModel {
        val viewModel = ManageRolesViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(league)), roleRepository)
        viewModel.retry()
        advanceUntilIdle()
        viewModel.onPhoneNumberChanged("+919876543210")
        viewModel.lookup()
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `a rate-limited lookup says so inline`() = viewModelTest {
        val viewModel = lookedUp(FakeRoleRepository().apply { lookupError = RoleActionFailedException("429", code = "RATE_LIMIT_EXCEEDED") })

        assertEquals("Too many lookups. Try again in a few minutes.", viewModel.state.value.lookupError)
        assertFalse(viewModel.state.value.isLookingUp)
    }

    @Test
    fun `a lookup that fails on the network asks to check the connection`() = viewModelTest {
        val viewModel = lookedUp(FakeRoleRepository().apply { lookupError = RuntimeException("timeout") })

        assertEquals("Couldn't look up that number. Check your connection and try again.", viewModel.state.value.lookupError)
    }

    @Test
    fun `editing the number clears the found user and any lookup or grant error`() = viewModelTest {
        val viewModel = lookedUp(FakeRoleRepository().apply { nextLookupResult = null })

        viewModel.onPhoneNumberChanged("+91987654321")

        assertNull(viewModel.state.value.lookupError)
        assertNull(viewModel.state.value.grantError)
    }

    @Test
    fun `a blank number is never looked up`() = viewModelTest {
        val roleRepository = FakeRoleRepository()
        val viewModel = ManageRolesViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())), roleRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onPhoneNumberChanged("   ")
        viewModel.lookup()
        advanceUntilIdle()

        assertTrue(roleRepository.lookupCalls.isEmpty())
    }

    @Test
    fun `a grant that fails on the network shows the K8 banner and keeps the found user`() = viewModelTest {
        val roleRepository = FakeRoleRepository().apply {
            nextLookupResult = RoleLookupResultDto(userId = "u2", name = "Delegate Name")
            actionError = RuntimeException("timeout")
        }
        val viewModel = lookedUp(roleRepository)

        viewModel.grant()
        advanceUntilIdle()

        assertEquals(RoleNotice("Couldn't grant access.", "Check your connection and try again."), viewModel.state.value.grantError)
        assertEquals("u2", viewModel.state.value.lookupResult?.userId)
        assertFalse(viewModel.state.value.isGranting)
    }

    @Test
    fun `granting someone who already has access says so by name`() = viewModelTest {
        val roleRepository = FakeRoleRepository().apply {
            nextLookupResult = RoleLookupResultDto(userId = "u2", name = "Delegate Name")
            actionError = RoleActionFailedException("409", code = "ROLE_ALREADY_GRANTED")
        }
        val viewModel = lookedUp(roleRepository)

        viewModel.grant()
        advanceUntilIdle()

        assertEquals(RoleNotice("Already a co-organizer.", "Delegate Name already has access to this league."), viewModel.state.value.grantError)
    }

    @Test
    fun `granting the league's own organizer explains why not`() = viewModelTest {
        val roleRepository = FakeRoleRepository().apply {
            nextLookupResult = RoleLookupResultDto(userId = "organizer-1", name = "Owner")
            actionError = RoleActionFailedException("400", code = "CANNOT_GRANT_ROLE_TO_ORGANIZER")
        }
        val viewModel = lookedUp(roleRepository)

        viewModel.grant()
        advanceUntilIdle()

        assertEquals("That's the league's organizer.", viewModel.state.value.grantError?.title)
    }

    @Test
    fun `a revoke that fails on the network shows a banner and clears Revoking`() = viewModelTest {
        val existing = sampleLeague(coOrganizers = listOf(LeagueRoleDto(id = "r1", userId = "u2", name = "Delegate Name", grantedAt = "2026-09-13T00:00:00Z")))
        val roleRepository = FakeRoleRepository().apply { actionError = RuntimeException("timeout") }
        val viewModel = ManageRolesViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(existing)), roleRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.revoke("r1")
        advanceUntilIdle()

        assertEquals(RoleNotice("Couldn't revoke access.", "Check your connection and try again."), viewModel.state.value.revokeError)
        assertTrue(viewModel.state.value.revokingRoleIds.isEmpty())
        assertEquals(1, viewModel.state.value.league?.coOrganizers?.size)
    }

    @Test
    fun `revoking a grant someone already removed just refreshes the list`() = viewModelTest {
        val existing = sampleLeague(coOrganizers = listOf(LeagueRoleDto(id = "r1", userId = "u2", name = "Delegate Name", grantedAt = "2026-09-13T00:00:00Z")))
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(existing))
        val roleRepository = FakeRoleRepository().apply { actionError = RoleActionFailedException("404", code = "NOT_FOUND") }
        val viewModel = ManageRolesViewModel("l1", leagueRepository, roleRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.revoke("r1")
        advanceUntilIdle()

        assertNull(viewModel.state.value.revokeError)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `a load failure is flagged instead of crashing`() = viewModelTest {
        val viewModel = ManageRolesViewModel("missing", FakeLeagueRepository(leaguesByArea = emptyList()), FakeRoleRepository())

        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.isLoading)
    }
}

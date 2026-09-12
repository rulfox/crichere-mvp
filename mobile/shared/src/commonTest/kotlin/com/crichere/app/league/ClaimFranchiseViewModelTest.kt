@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.league

import com.crichere.app.auth.viewModelTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [ClaimFranchiseViewModel] coverage: name requirement, screenshot requirement gating on `franchiseFee`, logo upload sequencing. */
class ClaimFranchiseViewModelTest {

    private fun sampleLeague(franchiseFee: Double? = null) = LeagueDto(
        id = "l1",
        organizerUserId = "organizer-1",
        name = "Weekend League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = "2026-10-12",
        franchiseFee = franchiseFee,
        organizerUpiId = if (franchiseFee != null) "organizer@upi" else null,
        status = LeagueStatus.ANNOUNCED,
    )

    @Test
    fun `submitting without a name is rejected`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val franchiseRepository = FakeFranchiseRepository()
        val viewModel = ClaimFranchiseViewModel("l1", leagueRepository, franchiseRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.claimed)
        assertTrue(franchiseRepository.claimRequests.isEmpty())
    }

    @Test
    fun `a free league -- no franchise fee -- claims without needing a screenshot`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val franchiseRepository = FakeFranchiseRepository().apply {
            nextClaimed = LeagueFranchiseDto(id = "f1", ownerUserId = "u1", name = "Chennai Kings", joinedAt = "2026-09-12T00:00:00Z")
        }
        val viewModel = ClaimFranchiseViewModel("l1", leagueRepository, franchiseRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onNameChanged("Chennai Kings")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.claimed)
        assertEquals("Chennai Kings", franchiseRepository.claimRequests.single().second.name)
    }

    @Test
    fun `a league with a franchise fee refuses to submit without a screenshot`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague(franchiseFee = 5000.0)))
        val franchiseRepository = FakeFranchiseRepository()
        val viewModel = ClaimFranchiseViewModel("l1", leagueRepository, franchiseRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onNameChanged("Chennai Kings")
        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.claimed)
        assertTrue(franchiseRepository.claimRequests.isEmpty())
    }

    @Test
    fun `uploading a logo before claiming carries the resulting URL into the claim request`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val franchiseRepository = FakeFranchiseRepository().apply {
            nextClaimed = LeagueFranchiseDto(id = "f1", ownerUserId = "u1", name = "Chennai Kings", joinedAt = "2026-09-12T00:00:00Z")
        }
        val viewModel = ClaimFranchiseViewModel("l1", leagueRepository, franchiseRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onNameChanged("Chennai Kings")
        viewModel.uploadLogo(ByteArray(0), "image/jpeg", "logo.jpg")
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(leagueRepository.uploadedPhotoUrl, franchiseRepository.claimRequests.single().second.logoUrl)
    }
}

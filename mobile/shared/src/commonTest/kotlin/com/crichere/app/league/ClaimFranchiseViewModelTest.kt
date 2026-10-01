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
        assertTrue(viewModel.state.value.nameError)
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

    @Test
    fun `clearing the name flags it, and submitting without one flags it too`() = viewModelTest {
        val viewModel = ClaimFranchiseViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())), FakeFranchiseRepository())
        viewModel.retry()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.nameError)

        viewModel.onNameChanged("C")
        assertFalse(viewModel.state.value.nameError)
        viewModel.onNameChanged("")
        assertTrue(viewModel.state.value.nameError)

        viewModel.onNameChanged("Chennai Kings")
        assertFalse(viewModel.state.value.nameError)
    }

    @Test
    fun `a removed logo is left out of the claim`() = viewModelTest {
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
        viewModel.removeLogo()
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(null, franchiseRepository.claimRequests.single().second.logoUrl)
    }

    @Test
    fun `a full league explains that every franchise slot is taken`() = viewModelTest {
        val franchiseRepository = FakeFranchiseRepository().apply {
            claimError = LeagueFranchiseActionFailedException("Claim failed with status 409 Conflict", code = "CAPACITY_FULL")
        }
        val viewModel = ClaimFranchiseViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())), franchiseRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onNameChanged("Chennai Kings")
        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.claimed)
        assertEquals("All franchise slots are taken.", viewModel.state.value.errorTitle)
        assertEquals("This league has no franchises left to claim.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `a claim failure shows friendly copy, never the raw cause`() = viewModelTest {
        val franchiseRepository = FakeFranchiseRepository().apply { claimError = RuntimeException("Claim failed with status 500") }
        val viewModel = ClaimFranchiseViewModel("l1", FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())), franchiseRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onNameChanged("Chennai Kings")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Couldn't claim a franchise.", viewModel.state.value.errorTitle)
        assertEquals("Check your connection and try again.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `a failed logo upload drops the logo with a friendly message`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())).apply { uploadPhotoError = RuntimeException("S3 down") }
        val viewModel = ClaimFranchiseViewModel("l1", leagueRepository, FakeFranchiseRepository())
        viewModel.retry()
        advanceUntilIdle()

        viewModel.uploadLogo(ByteArray(10), "image/jpeg", "logo.jpg")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isUploadingLogo)
        assertEquals(null, viewModel.state.value.logoUrl)
        assertEquals("Couldn't upload the logo.", viewModel.state.value.errorTitle)
    }

    @Test
    fun `a failed screenshot upload can be retried, and an attached one removed`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague(franchiseFee = 5000.0))).apply {
            uploadPhotoError = RuntimeException("S3 down")
        }
        val viewModel = ClaimFranchiseViewModel("l1", leagueRepository, FakeFranchiseRepository())
        viewModel.retry()
        advanceUntilIdle()

        viewModel.uploadScreenshot(ByteArray(10), "image/jpeg", "proof.jpg")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.uploadFailed)

        leagueRepository.uploadPhotoError = null
        viewModel.retryUpload()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.uploadFailed)
        assertEquals(leagueRepository.uploadedPhotoUrl, viewModel.state.value.screenshotUrl)

        viewModel.removeScreenshot()
        assertEquals(null, viewModel.state.value.screenshotUrl)
    }

    @Test
    fun `a league that fails to load is flagged separately from a failed claim`() = viewModelTest {
        val viewModel = ClaimFranchiseViewModel("l1", FakeLeagueRepository(leaguesByArea = emptyList()), FakeFranchiseRepository())
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(null, viewModel.state.value.errorTitle)
    }
}

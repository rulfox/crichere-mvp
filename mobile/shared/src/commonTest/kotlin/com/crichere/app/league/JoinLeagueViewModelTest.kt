@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.league

import com.crichere.app.auth.viewModelTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [JoinLeagueViewModel] coverage: screenshot requirement gating on `playerFee`, upload-then-submit sequencing. */
class JoinLeagueViewModelTest {

    private fun sampleLeague(playerFee: Double? = null) = LeagueDto(
        id = "l1",
        organizerUserId = "organizer-1",
        name = "Weekend League",
        state = "Karnataka",
        district = "Bengaluru Urban", groundId = "g1", groundName = "Test Ground",
        startsOn = "2026-10-12",
        playerFee = playerFee,
        organizerUpiId = if (playerFee != null) "organizer@upi" else null,
        status = LeagueStatus.ANNOUNCED,
    )

    @Test
    fun `a free league -- no player fee -- joins without needing a screenshot`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val playerRepository = FakePlayerRepository().apply {
            nextJoined = LeaguePlayerDto(id = "p1", userId = "u1", joinedAt = "2026-09-12T00:00:00Z")
        }
        val viewModel = JoinLeagueViewModel("l1", leagueRepository, playerRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.joined)
        assertEquals(1, playerRepository.joinRequests.size)
    }

    @Test
    fun `a league with a player fee refuses to submit without a screenshot`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague(playerFee = 100.0)))
        val playerRepository = FakePlayerRepository()
        val viewModel = JoinLeagueViewModel("l1", leagueRepository, playerRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.joined)
        assertTrue(playerRepository.joinRequests.isEmpty())
        assertTrue(viewModel.state.value.errorMessage != null)
    }

    @Test
    fun `uploading a screenshot then submitting succeeds when a fee is set`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague(playerFee = 100.0)))
        val playerRepository = FakePlayerRepository().apply {
            nextJoined = LeaguePlayerDto(id = "p1", userId = "u1", joinedAt = "2026-09-12T00:00:00Z")
        }
        val viewModel = JoinLeagueViewModel("l1", leagueRepository, playerRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.uploadScreenshot(ByteArray(0), "image/jpeg", "proof.jpg")
        advanceUntilIdle()
        assertFalse(viewModel.state.value.screenshotUrl.isNullOrBlank())

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.joined)
        assertEquals(leagueRepository.uploadedPhotoUrl, playerRepository.joinRequests.single().second.paymentScreenshotUrl)
    }

    @Test
    fun `a join failure shows friendly copy, never the raw cause`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val playerRepository = FakePlayerRepository().apply { joinError = RuntimeException("Join failed with status 500") }
        val viewModel = JoinLeagueViewModel("l1", leagueRepository, playerRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.joined)
        assertEquals("Couldn't join.", viewModel.state.value.errorTitle)
        assertEquals("Check your connection and try again.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `a full league explains that registration is full`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val playerRepository = FakePlayerRepository().apply {
            joinError = LeaguePlayerActionFailedException("Join failed with status 409 Conflict", code = "CAPACITY_FULL")
        }
        val viewModel = JoinLeagueViewModel("l1", leagueRepository, playerRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Registration is full.", viewModel.state.value.errorTitle)
        assertEquals("This league isn't taking more players.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `a failed screenshot upload can be retried, and an attached one removed`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague(playerFee = 100.0))).apply {
            uploadPhotoError = RuntimeException("S3 down")
        }
        val viewModel = JoinLeagueViewModel("l1", leagueRepository, FakePlayerRepository())
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
        assertEquals(2, leagueRepository.uploadPhotoCallCount)

        viewModel.removeScreenshot()
        assertEquals(null, viewModel.state.value.screenshotUrl)
    }

    @Test
    fun `a league that fails to load is flagged separately from a failed join`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = emptyList())
        val viewModel = JoinLeagueViewModel("l1", leagueRepository, FakePlayerRepository())
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(null, viewModel.state.value.errorTitle)
    }
}

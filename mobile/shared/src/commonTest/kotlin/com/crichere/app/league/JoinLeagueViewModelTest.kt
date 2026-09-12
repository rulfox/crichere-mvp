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
        district = "Bengaluru Urban",
        city = "Bengaluru",
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
    fun `a join failure surfaces an error message instead of crashing`() = viewModelTest {
        val leagueRepository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val playerRepository = FakePlayerRepository().apply { joinError = RuntimeException("already joined") }
        val viewModel = JoinLeagueViewModel("l1", leagueRepository, playerRepository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.joined)
        assertEquals("already joined", viewModel.state.value.errorMessage)
    }
}

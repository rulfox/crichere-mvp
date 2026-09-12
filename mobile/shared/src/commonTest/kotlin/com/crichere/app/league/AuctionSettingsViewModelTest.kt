@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.league

import com.crichere.app.auth.viewModelTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [AuctionSettingsViewModel] coverage: pre-fills from the loaded league, saves and reloads, surfaces server errors without duplicating validation client-side. */
class AuctionSettingsViewModelTest {

    private fun sampleLeague(
        auctionBasePrice: Double? = null,
        auctionPurse: Double? = null,
        auctionSquadMin: Int? = null,
        auctionSquadMax: Int? = null,
        auctionBidIncrement: Double? = null,
    ) = LeagueDto(
        id = "l1",
        organizerUserId = "organizer-1",
        name = "Weekend League",
        state = "Karnataka",
        district = "Bengaluru Urban",
        city = "Bengaluru",
        startsOn = "2026-10-12",
        status = LeagueStatus.ANNOUNCED,
        auctionBasePrice = auctionBasePrice,
        auctionPurse = auctionPurse,
        auctionSquadMin = auctionSquadMin,
        auctionSquadMax = auctionSquadMax,
        auctionBidIncrement = auctionBidIncrement,
    )

    @Test
    fun `retry pre-fills the fields from whatever is already configured`() = viewModelTest {
        val repository = FakeLeagueRepository(
            leaguesByArea = listOf(sampleLeague(auctionBasePrice = 500.0, auctionPurse = 10000.0, auctionSquadMin = 5, auctionSquadMax = 15, auctionBidIncrement = 100.0)),
        )
        val viewModel = AuctionSettingsViewModel("l1", repository)

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("500.0", state.basePrice)
        assertEquals("10000.0", state.purse)
        assertEquals("5", state.squadMin)
        assertEquals("15", state.squadMax)
        assertEquals("100.0", state.bidIncrement)
    }

    @Test
    fun `retry leaves fields blank when nothing is configured yet`() = viewModelTest {
        val repository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val viewModel = AuctionSettingsViewModel("l1", repository)

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("", state.basePrice)
        assertEquals("", state.squadMax)
    }

    @Test
    fun `submit sends the entered values and reloads on success`() = viewModelTest {
        val repository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())).apply {
            nextAuctionSettingsUpdated = sampleLeague(auctionBasePrice = 500.0, auctionPurse = 10000.0, auctionSquadMin = 5, auctionSquadMax = 15, auctionBidIncrement = 100.0)
        }
        val viewModel = AuctionSettingsViewModel("l1", repository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onBasePriceChanged("500")
        viewModel.onPurseChanged("10000")
        viewModel.onSquadMinChanged("5")
        viewModel.onSquadMaxChanged("15")
        viewModel.onBidIncrementChanged("100")
        viewModel.submit()
        advanceUntilIdle()

        val request = repository.updateAuctionSettingsRequests.single()
        assertEquals(500.0, request.basePrice)
        assertEquals(15, request.squadMax)
        assertFalse(viewModel.state.value.isSaving)
        assertTrue(viewModel.state.value.errorMessage == null)
    }

    @Test
    fun `submit with an incomplete field surfaces an error without calling the repository`() = viewModelTest {
        val repository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague()))
        val viewModel = AuctionSettingsViewModel("l1", repository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onBasePriceChanged("500")
        // Every other field left blank.
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(repository.updateAuctionSettingsRequests.isEmpty())
        assertTrue(viewModel.state.value.errorMessage != null)
    }

    @Test
    fun `a save failure surfaces the server's error message instead of crashing`() = viewModelTest {
        val repository = FakeLeagueRepository(leaguesByArea = listOf(sampleLeague())).apply {
            updateAuctionSettingsError = RuntimeException("squad_min cannot be greater than squad_max")
        }
        val viewModel = AuctionSettingsViewModel("l1", repository)
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onBasePriceChanged("500")
        viewModel.onPurseChanged("10000")
        viewModel.onSquadMinChanged("15")
        viewModel.onSquadMaxChanged("5")
        viewModel.onBidIncrementChanged("100")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("squad_min cannot be greater than squad_max", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `a load failure surfaces an error message instead of crashing`() = viewModelTest {
        val repository = FakeLeagueRepository(leaguesByArea = emptyList())
        val viewModel = AuctionSettingsViewModel("missing-id", repository)

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.errorMessage != null)
    }
}

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.league

import com.crichere.app.auth.viewModelTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [AuctionSettingsViewModel] coverage: pre-fill, the mirrored field rules and when they show (design J2), the live squad warning, save success / failure (J3-J5). */
class AuctionSettingsViewModelTest {

    private fun sampleLeague(
        auctionBasePrice: Double? = null,
        auctionPurse: Double? = null,
        auctionSquadMin: Int? = null,
        auctionSquadMax: Int? = null,
        auctionBidIncrement: Double? = null,
        auctionScheduledAt: String? = null,
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
        auctionScheduledAt = auctionScheduledAt,
    )

    private val configured = sampleLeague(auctionBasePrice = 1000.0, auctionPurse = 75000.0, auctionSquadMin = 11, auctionSquadMax = 12, auctionBidIncrement = 500.0)

    private fun loaded(league: LeagueDto = configured, configure: FakeLeagueRepository.() -> Unit = {}): Pair<AuctionSettingsViewModel, FakeLeagueRepository> {
        val repository = FakeLeagueRepository(leaguesByArea = listOf(league)).apply(configure)
        return AuctionSettingsViewModel("l1", repository) to repository
    }

    @Test
    fun `retry pre-fills the fields, whole amounts without a decimal`() = viewModelTest {
        val (viewModel, _) = loaded(sampleLeague(auctionBasePrice = 500.0, auctionPurse = 10000.0, auctionSquadMin = 5, auctionSquadMax = 15, auctionBidIncrement = 12.5))

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("500", state.basePrice)
        assertEquals("10000", state.purse)
        assertEquals("5", state.squadMin)
        assertEquals("15", state.squadMax)
        assertEquals("12.5", state.bidIncrement)
    }

    @Test
    fun `an unconfigured league opens with blank fields and no errors showing`() = viewModelTest {
        val (viewModel, _) = loaded(sampleLeague())

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("", state.basePrice)
        assertEquals(5, state.allErrors.size)
        assertTrue(state.fieldErrors.isEmpty())
        assertTrue(state.canSave)
    }

    @Test
    fun `an edited field shows its own error at once and greys Save out`() = viewModelTest {
        val (viewModel, _) = loaded(sampleLeague())
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onPurseChanged("75k")

        val state = viewModel.state.value
        assertEquals(mapOf(AuctionField.Purse to "Enter a number"), state.fieldErrors)
        assertFalse(state.canSave)
    }

    @Test
    fun `a Save tap with errors shows every field's error and sends nothing`() = viewModelTest {
        val (viewModel, repository) = loaded(sampleLeague())
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onBasePriceChanged("1000")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(repository.updateAuctionSettingsRequests.isEmpty())
        val errors = viewModel.state.value.fieldErrors
        assertEquals(setOf(AuctionField.Purse, AuctionField.SquadMin, AuctionField.SquadMax, AuctionField.BidIncrement), errors.keys)
        assertEquals("Required", errors[AuctionField.BidIncrement])
    }

    @Test
    fun `the server's rules are mirrored -- positive values, whole squad sizes, max not below min`() {
        val valid = AuctionSettingsState(basePrice = "1000", purse = "75000", squadMin = "11", squadMax = "12", bidIncrement = "500")
        assertTrue(validate(valid).isEmpty())
        assertEquals("Must be more than 0", validate(valid.copy(basePrice = "0"))[AuctionField.BasePrice])
        assertEquals("Must be more than 0", validate(valid.copy(bidIncrement = "-5"))[AuctionField.BidIncrement])
        assertEquals("Enter a whole number", validate(valid.copy(squadMin = "11.5"))[AuctionField.SquadMin])
        assertEquals("Enter a number", validate(valid.copy(squadMax = "twelve"))[AuctionField.SquadMax])
        assertEquals("Can't be less than the min", validate(valid.copy(squadMin = "15", squadMax = "12"))[AuctionField.SquadMax])
        assertTrue(validate(valid.copy(squadMin = "12", squadMax = "12")).isEmpty())
    }

    @Test
    fun `the squad warning follows the typed max against the league's capacity`() = viewModelTest {
        val (viewModel, _) = loaded(configured.copy(franchisesRequired = 8, playersRequired = 80))
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(SquadWarning(squadMax = 12, franchises = 8, playersRequired = 80), viewModel.state.value.squadWarning)
        assertEquals(96, viewModel.state.value.squadWarning?.total)

        viewModel.onSquadMaxChanged("10")
        assertNull(viewModel.state.value.squadWarning)
    }

    @Test
    fun `no squad warning when the league has no capacity targets`() = viewModelTest {
        val (viewModel, _) = loaded()
        viewModel.retry()
        advanceUntilIdle()

        assertNull(viewModel.state.value.squadWarning)
    }

    @Test
    fun `a successful save sends the values, takes the returned league and shows the saved notice`() = viewModelTest {
        val (viewModel, repository) = loaded(sampleLeague()) {
            nextAuctionSettingsUpdated = sampleLeague(auctionBasePrice = 500.0, auctionPurse = 10000.0, auctionSquadMin = 5, auctionSquadMax = 15, auctionBidIncrement = 100.0)
        }
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onBasePriceChanged("500")
        viewModel.onPurseChanged("10000")
        viewModel.onSquadMinChanged("5")
        viewModel.onSquadMaxChanged(" 15")
        viewModel.onBidIncrementChanged("100")
        viewModel.submit()
        advanceUntilIdle()

        val request = repository.updateAuctionSettingsRequests.single()
        assertEquals(500.0, request.basePrice)
        assertEquals(15, request.squadMax)
        val state = viewModel.state.value
        assertFalse(state.isSaving)
        assertTrue(state.showSavedNotice)
        assertEquals(500.0, state.league?.auctionBasePrice)
        assertTrue(state.touched.isEmpty())

        viewModel.onSavedNoticeShown()
        assertFalse(viewModel.state.value.showSavedNotice)
    }

    @Test
    fun `the scheduled time pre-fills, is sent with the save, and can be cleared`() = viewModelTest {
        val (viewModel, repository) = loaded(
            sampleLeague(auctionBasePrice = 500.0, auctionPurse = 10000.0, auctionSquadMin = 5, auctionSquadMax = 15, auctionBidIncrement = 100.0, auctionScheduledAt = "2026-10-12T13:30:00Z"),
        ) { nextAuctionSettingsUpdated = configured }
        viewModel.retry()
        advanceUntilIdle()
        assertEquals("2026-10-12T13:30:00Z", viewModel.state.value.scheduledAt)

        viewModel.onScheduledAtChanged("2026-10-13T14:00:00Z")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals("2026-10-13T14:00:00Z", repository.updateAuctionSettingsRequests.last().scheduledAt)

        viewModel.onScheduledAtChanged(null)
        assertNull(viewModel.state.value.scheduledAt)
        viewModel.submit()
        advanceUntilIdle()
        assertNull(repository.updateAuctionSettingsRequests.last().scheduledAt)
    }

    @Test
    fun `a network failure offers Retry, which saves again`() = viewModelTest {
        val (viewModel, repository) = loaded { updateAuctionSettingsError = RuntimeException("timeout") }
        viewModel.retry()
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(AuctionSaveError("Couldn't save settings. Check your connection and try again.", canRetry = true), viewModel.state.value.saveError)

        repository.updateAuctionSettingsError = null
        repository.nextAuctionSettingsUpdated = configured
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(2, repository.updateAuctionSettingsRequests.size)
        assertNull(viewModel.state.value.saveError)
        assertTrue(viewModel.state.value.showSavedNotice)
    }

    @Test
    fun `an auction that has started explains why and offers no Retry`() = viewModelTest {
        val (viewModel, _) = loaded {
            updateAuctionSettingsError = LeagueSaveFailedException("Auction settings save failed with status 409", code = "AUCTION_ALREADY_STARTED")
        }
        viewModel.retry()
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        val error = viewModel.state.value.saveError
        assertEquals("The auction has started, so these settings can't change now.", error?.message)
        assertFalse(error!!.canRetry)
    }

    @Test
    fun `editing a field clears the save error`() = viewModelTest {
        val (viewModel, _) = loaded { updateAuctionSettingsError = RuntimeException("timeout") }
        viewModel.retry()
        advanceUntilIdle()
        viewModel.submit()
        advanceUntilIdle()

        viewModel.onBidIncrementChanged("600")

        assertNull(viewModel.state.value.saveError)
    }

    @Test
    fun `a load failure is flagged instead of crashing`() = viewModelTest {
        val viewModel = AuctionSettingsViewModel("missing-id", FakeLeagueRepository(leaguesByArea = emptyList()))

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
    }
}

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
        district = "Bengaluru Urban", groundId = "g1", groundName = "Test Ground",
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
        return AuctionSettingsViewModel("l1", repository, nowMillis = { NOW }) to repository
    }

    /** 2026-10-04T06:00:00Z -- the fixed "now" every test's ViewModel sees. */
    private val NOW = 1_791_093_600_000L

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

    // ---------------------------------------------------------------- bid steps (docs/PHASE22.md, design S1-S4)

    private val validSteps = AuctionSettingsState(
        basePrice = "250", purse = "100000", squadMin = "8", squadMax = "12", bidIncrement = "2500",
        ranges = listOf(BidRangeRow("1000", "250"), BidRangeRow("3000", "500"), BidRangeRow("15000", "1000")),
    )

    @Test
    fun `the worked example is valid, each row starts where the last ended, and the preview lists the bids`() {
        assertTrue(validate(validSteps).isEmpty())
        assertTrue(validateRanges(validSteps).isEmpty())
        assertEquals(listOf("₹250", "₹1,000", "₹3,000", "₹15,000"), (0..3).map { validSteps.rangeStartText(it) })
        assertEquals("₹15,000", validSteps.aboveStartText)
        assertEquals(
            listOf(
                LadderPreviewLine("+250", "250 → 500 → 750 → 1,000"),
                LadderPreviewLine("+500", "1,500 → 2,000 → 2,500 → 3,000"),
                LadderPreviewLine("+1,000", "4,000 → 5,000 → 6,000 … 15,000"),
                LadderPreviewLine("+2,500", "17,500 → 20,000 → 22,500 …"),
            ),
            validSteps.ladderPreview?.map { it.copy(longer = emptyList()) },
        )
    }

    @Test
    fun `no ranges is one flat step`() {
        val flat = validSteps.copy(ranges = emptyList(), bidIncrement = "250")
        assertTrue(validate(flat).isEmpty())
        assertEquals("₹250", flat.aboveStartText)
        assertEquals(listOf(LadderPreviewLine("+250", "250 → 500 → 750 …")), flat.ladderPreview?.map { it.copy(longer = emptyList()) })
    }

    @Test
    fun `row errors -- end not above start, uneven split with suggestions, steps that don't grow, whole rupees`() {
        val uneven = validateRanges(validSteps.copy(ranges = listOf(BidRangeRow("1000", "250"), BidRangeRow("3000", "300"))))
        assertEquals(
            RangeRowError(RangeField.Step, "Adding ₹300 each time won’t land exactly on ₹3,000.", listOf(400L, 500L)),
            uneven[1],
        )
        val backwards = validateRanges(validSteps.copy(ranges = listOf(BidRangeRow("1000", "250"), BidRangeRow("3000", "500"), BidRangeRow("2500", "1000"))))
        assertEquals(RangeRowError(RangeField.UpTo, "End price must be more than ₹3,000."), backwards[2])
        val shrinking = validateRanges(validSteps.copy(ranges = listOf(BidRangeRow("1000", "250"), BidRangeRow("3000", "200"))))
        assertEquals(RangeRowError(RangeField.Step, "Higher prices should add more. Make this more than ₹250."), shrinking[1])
        assertEquals(RangeRowError(RangeField.UpTo, "Use whole rupees"), validateRanges(validSteps.copy(ranges = listOf(BidRangeRow("1000.5", "250"))))[0])
        assertEquals("Higher prices should add more. Make this more than ₹1,000.", validate(validSteps.copy(bidIncrement = "500"))[AuctionField.BidIncrement])
        assertEquals("Use whole rupees", validate(validSteps.copy(basePrice = "250.5"))[AuctionField.BasePrice])
        assertNull(validSteps.copy(bidIncrement = "500").ladderPreview)
    }

    @Test
    fun `rows can be added up to ten and removed, and a save sends them`() = viewModelTest {
        val (viewModel, repository) = loaded(configured.copy(auctionBasePrice = 250.0, auctionBidIncrement = 2500.0)) { nextAuctionSettingsUpdated = configured }
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onAddRange() // only in "Changes with price"
        assertTrue(viewModel.state.value.ranges.isEmpty())
        viewModel.onBidModeChanged(BidMode.ChangesWithPrice) // starts one blank row
        viewModel.onRangeUpToChanged(0, "1000")
        viewModel.onRangeStepChanged(0, "250")
        viewModel.onAddRange()
        assertEquals(mapOf(), viewModel.state.value.rangeErrors) // the blank new row hasn't been touched yet
        viewModel.onRemoveRange(1)
        assertEquals(listOf(BidRangeRow("1000", "250")), viewModel.state.value.ranges)

        repeat(12) { viewModel.onAddRange() }
        assertEquals(MAX_BID_RANGES, viewModel.state.value.ranges.size)
        assertFalse(viewModel.state.value.canAddRange)
        repeat(9) { viewModel.onRemoveRange(1) }

        viewModel.submit()
        advanceUntilIdle()
        assertEquals(listOf(BidRangeDto(1000.0, 250.0)), repository.updateAuctionSettingsRequests.single().bidRanges)
    }

    @Test
    fun `a Save tap with a broken row shows the row error and sends nothing`() = viewModelTest {
        val (viewModel, repository) = loaded(configured.copy(auctionBasePrice = 250.0, auctionBidIncrement = 2500.0))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onBidModeChanged(BidMode.ChangesWithPrice)
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(repository.updateAuctionSettingsRequests.isEmpty())
        assertEquals(RangeRowError(RangeField.UpTo, "Required"), viewModel.state.value.rangeErrorFor(0))
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun `saved ranges pre-fill, and once the auction has started the screen is locked with a read-only summary`() = viewModelTest {
        val started = configured.copy(
            auctionBasePrice = 250.0, auctionBidIncrement = 2500.0, auctionStatus = AuctionStatus.IN_PROGRESS,
            auctionBidRanges = listOf(BidRangeDto(1000.0, 250.0), BidRangeDto(3000.0, 500.0)),
        )
        val (viewModel, repository) = loaded(started)
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf(BidRangeRow("1000", "250"), BidRangeRow("3000", "500")), state.ranges)
        assertEquals(BidMode.ChangesWithPrice, state.bidMode)
        assertTrue(state.isLocked)
        assertFalse(state.canSave)
        assertFalse(state.canAddRange)
        assertEquals(
            listOf(
                LadderPreviewLine("add ₹250", "₹250 to ₹1,000"),
                LadderPreviewLine("add ₹500", "₹1,000 to ₹3,000"),
                LadderPreviewLine("add ₹2,500", "Above ₹3,000"),
            ),
            state.lockedSteps,
        )
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(repository.updateAuctionSettingsRequests.isEmpty())
    }

    @Test
    fun `switching to same amount sets the ranges aside and switching back brings them back`() = viewModelTest {
        val saved = configured.copy(auctionBasePrice = 250.0, auctionBidIncrement = 2500.0, auctionBidRanges = listOf(BidRangeDto(1000.0, 250.0)))
        val (viewModel, repository) = loaded(saved) { nextAuctionSettingsUpdated = configured }
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onBidModeChanged(BidMode.SameAlways)
        assertEquals(BidMode.SameAlways, viewModel.state.value.bidMode)
        assertTrue(viewModel.state.value.ranges.isEmpty())
        assertFalse(viewModel.state.value.canAddRange)
        viewModel.onBidModeChanged(BidMode.ChangesWithPrice)
        assertEquals(listOf(BidRangeRow("1000", "250")), viewModel.state.value.ranges)

        viewModel.onRemoveRange(0) // the last range gone: one amount again
        assertEquals(BidMode.SameAlways, viewModel.state.value.bidMode)
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(emptyList(), repository.updateAuctionSettingsRequests.single().bidRanges)
    }

    @Test
    fun `a suggestion chip fills the row's amount and clears the error`() = viewModelTest {
        val (viewModel, _) = loaded(configured.copy(auctionBasePrice = 250.0, auctionBidIncrement = 2500.0, auctionBidRanges = listOf(BidRangeDto(1000.0, 250.0), BidRangeDto(3000.0, 500.0))))
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onRangeStepChanged(1, "300")
        assertEquals(listOf(400L, 500L), viewModel.state.value.rangeErrorFor(1)?.suggestions)
        viewModel.onRangeSuggestionPicked(1, 400)
        assertEquals("400", viewModel.state.value.ranges[1].step)
        assertNull(viewModel.state.value.rangeErrorFor(1))
    }

    @Test
    fun `examples, placeholder and summary follow what's typed`() {
        assertEquals("e.g. 250 → 500 …", AuctionSettingsState().sameExample)
        assertEquals("e.g. 100 → 150 …", AuctionSettingsState(basePrice = "100", bidIncrement = "50").sameExample)
        assertEquals("Enter a starting price and an amount to see how bids will go.", AuctionSettingsState().previewPlaceholder)
        assertEquals("Fix the highlighted rows to see how bids will go.", validSteps.previewPlaceholder)
        assertEquals("No players or franchises have joined yet", AuctionSettingsState(league = configured.copy(players = emptyList(), franchises = emptyList())).joinedSummary)
        assertEquals(
            "2 franchises with up to 10 players each need 20 players, but the league is set for 4. Some squads may not fill.",
            SquadWarning(squadMax = 10, franchises = 2, playersRequired = 4).message,
        )
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
    fun `a newly picked time that isn't in the future blocks Save with an inline error`() = viewModelTest {
        val (viewModel, repository) = loaded { nextAuctionSettingsUpdated = configured }
        viewModel.retry()
        advanceUntilIdle()

        viewModel.onScheduledAtChanged("2026-10-04T05:00:00Z") // an hour ago
        assertEquals(AuctionSettingsViewModel.SCHEDULED_TIME_PAST, viewModel.state.value.scheduledAtError)
        assertFalse(viewModel.state.value.canSave)
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(repository.updateAuctionSettingsRequests.isEmpty())

        viewModel.onScheduledAtChanged("2026-10-04T07:00:00Z")
        assertNull(viewModel.state.value.scheduledAtError)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun `a saved time that has since passed only warns, and Save still works`() = viewModelTest {
        val (viewModel, repository) = loaded(
            sampleLeague(auctionBasePrice = 500.0, auctionPurse = 10000.0, auctionSquadMin = 5, auctionSquadMax = 15, auctionBidIncrement = 100.0, auctionScheduledAt = "2026-10-02T13:00:00Z"),
        ) { nextAuctionSettingsUpdated = configured }
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.scheduledAtPassed)
        assertNull(viewModel.state.value.scheduledAtError)
        assertTrue(viewModel.state.value.canSave)

        // Clear then Undo puts the saved time back: still just the warning.
        viewModel.onScheduledAtChanged(null)
        assertFalse(viewModel.state.value.scheduledAtPassed)
        viewModel.onScheduledAtChanged("2026-10-02T13:00:00Z")
        assertTrue(viewModel.state.value.scheduledAtPassed)
        assertNull(viewModel.state.value.scheduledAtError)

        viewModel.submit()
        advanceUntilIdle()
        assertEquals(1, repository.updateAuctionSettingsRequests.size)
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

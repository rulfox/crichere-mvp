@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.league

import com.crichere.app.auth.viewModelTest
import com.crichere.app.ground.FakeGroundRepository
import com.crichere.app.ground.GroundDto
import com.crichere.app.location.FakeLocationProvider
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.FakeReferenceRepository
import com.crichere.app.reference.StateDto
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [LeagueCreationViewModel]: validate-on-tap, the edited flag, Format options, ground errors and save retries. */
class LeagueCreationViewModelTest {

    private val maharashtra = StateDto(code = "MH", name = "Maharashtra")
    private val kolhapurDistrict = DistrictDto(id = "d1", name = "Kolhapur")
    private val shahu = GroundDto("g1", "Shahu Stadium", "Maharashtra", "Kolhapur", 16.7, 74.2)

    private fun references() = FakeReferenceRepository(
        states = listOf(maharashtra),
        districtsByStateCode = mapOf("MH" to listOf(kolhapurDistrict)),
    )

    private fun league(format: String? = "T20") = LeagueDto(
        id = "l1",
        organizerUserId = "u1",
        name = "Kolhapur Premier League",
        state = "Maharashtra",
        district = "Kolhapur", groundId = "g1", groundName = "Test Ground",
        startsOn = "2026-10-12",
        format = format,
        franchiseFee = 5000.0,
        organizerUpiId = "kpl@okaxis",
        status = LeagueStatus.ANNOUNCED,
    )

    private fun viewModel(
        editingLeagueId: String? = null,
        leagues: FakeLeagueRepository = FakeLeagueRepository(),
        grounds: FakeGroundRepository = FakeGroundRepository(),
    ) = LeagueCreationViewModel(editingLeagueId, leagues, grounds, references(), FakeLocationProvider())

    private fun LeagueCreationViewModel.fillRequired() {
        onNameChanged("Kolhapur Premier League")
        onStateSelected(maharashtra)
        onDistrictSelected(kolhapurDistrict)
        onGroundSelected(shahu)
        onStartsOnChanged("2026-10-12")
    }

    @Test
    fun `save stays disabled until the form is edited`() = viewModelTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.state.value.isSaveEnabled)

        vm.onNameChanged("K")
        assertTrue(vm.state.value.isSaveEnabled)
        vm.onNameChanged("")
        assertFalse(vm.state.value.isDirty)
    }

    @Test
    fun `a save tap with missing fields shows them instead of saving`() = viewModelTest {
        val leagues = FakeLeagueRepository()
        val vm = viewModel(leagues = leagues)
        advanceUntilIdle()
        vm.onNameChanged("Kolhapur Premier League")
        vm.onFranchiseFeeChanged("5000")
        assertTrue(vm.state.value.fieldErrors.isEmpty())

        vm.save()
        advanceUntilIdle()

        assertTrue(leagues.createdRequests.isEmpty())
        val errors = vm.state.value.fieldErrors
        assertEquals(listOf(LeagueField.State, LeagueField.District, LeagueField.Ground, LeagueField.StartsOn, LeagueField.UpiId), errors.keys.toList())
        assertEquals("Required when a fee is set", errors[LeagueField.UpiId])
        assertEquals(1, vm.state.value.validationAttempt)

        vm.onOrganizerUpiIdChanged("kpl@okaxis")
        assertFalse(LeagueField.UpiId in vm.state.value.fieldErrors)
    }

    @Test
    fun `a failed upload after the league was created retries as an update, not a second create`() = viewModelTest {
        val leagues = FakeLeagueRepository().apply {
            nextCreated = league()
            uploadPhotoError = RuntimeException("S3 down")
        }
        val vm = viewModel(leagues = leagues)
        advanceUntilIdle()
        vm.fillRequired()
        vm.onLogoPicked(ByteArray(4), "image/jpeg")

        vm.save()
        advanceUntilIdle()
        assertEquals("Couldn't save the league. Check your connection and try again.", vm.state.value.errorMessage)
        assertNull(vm.state.value.logoUploadProgress)

        leagues.uploadPhotoError = null
        vm.save()
        advanceUntilIdle()

        assertEquals(1, leagues.createdRequests.size)
        assertTrue(leagues.updateLeagueCalls.any { it.first == "l1" })
        assertEquals(leagues.uploadedPhotoUrl, vm.state.value.logoUrl)
    }

    @Test
    fun `edit mode loads amounts without a trailing decimal and a known format as a dropdown choice`() = viewModelTest {
        val vm = viewModel(editingLeagueId = "l1", leagues = FakeLeagueRepository(leaguesByArea = listOf(league())))
        advanceUntilIdle()

        assertEquals("5000", vm.state.value.franchiseFee)
        assertEquals("T20", vm.state.value.format)
        assertFalse(vm.state.value.isFormatOther)
        assertFalse(vm.state.value.isDirty)
    }

    @Test
    fun `a stored format outside the list loads as Other with its text`() = viewModelTest {
        val vm = viewModel(editingLeagueId = "l1", leagues = FakeLeagueRepository(leaguesByArea = listOf(league(format = "8 overs"))))
        advanceUntilIdle()

        assertTrue(vm.state.value.isFormatOther)
        assertEquals("8 overs", vm.state.value.format)

        vm.onFormatOptionSelected("T10")
        assertEquals("T10", vm.state.value.format)
        assertFalse(vm.state.value.isFormatOther)
        vm.onFormatOptionSelected(null)
        assertTrue(vm.state.value.isFormatOther)
        assertEquals("", vm.state.value.format)
    }

    @Test
    fun `a league that fails to load can be retried`() = viewModelTest {
        val leagues = FakeLeagueRepository()
        val vm = viewModel(editingLeagueId = "l1", leagues = leagues)
        advanceUntilIdle()
        assertTrue(vm.state.value.loadFailed)

        leagues.leaguesByArea = listOf(league())
        vm.retryLoad()
        advanceUntilIdle()
        assertFalse(vm.state.value.loadFailed)
        assertEquals("Kolhapur Premier League", vm.state.value.name)
    }

    @Test
    fun `registering a ground flags a missing name, then a missing location`() = viewModelTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onStartRegisteringNewGround()

        vm.registerNewGround()
        assertTrue(vm.state.value.newGroundNameError)

        vm.onNewGroundNameChanged("Rajaram College Ground")
        assertFalse(vm.state.value.newGroundNameError)
        vm.registerNewGround()
        assertEquals(LeagueCreationViewModel.GROUND_LOCATION_MISSING_MESSAGE, vm.state.value.groundErrorTitle)
        assertFalse(vm.state.value.canRegisterGround)
    }

    @Test
    fun `State and District are enough to register a ground (design update #6, I16)`() = viewModelTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onStateSelected(maharashtra)
        advanceUntilIdle()
        assertFalse(vm.state.value.canRegisterGround)
        vm.onDistrictSelected(kolhapurDistrict)
        assertTrue(vm.state.value.canRegisterGround)
    }

    @Test
    fun `save without a ground shows the ground error (I15)`() = viewModelTest {
        val leagues = FakeLeagueRepository()
        val vm = viewModel(leagues = leagues)
        advanceUntilIdle()
        vm.fillRequired()
        vm.onClearGround()

        vm.save()
        advanceUntilIdle()

        assertTrue(leagues.createdRequests.isEmpty())
        assertEquals(mapOf(LeagueField.Ground to "Select a ground or register a new one"), vm.state.value.fieldErrors)
    }

    @Test
    fun `edit mode Change keeps the current ground until another is picked (I10)`() = viewModelTest {
        val vm = viewModel(editingLeagueId = "l1", leagues = FakeLeagueRepository(leaguesByArea = listOf(league())))
        advanceUntilIdle()

        vm.onChangeGround()
        assertTrue(vm.state.value.isChangingGround)
        assertEquals("g1", vm.state.value.groundId)
        assertFalse(vm.state.value.isDirty)

        vm.onGroundSelected(GroundDto("g2", "Rajaram College Ground", "Maharashtra", "Kolhapur", 16.69, 74.23))
        assertFalse(vm.state.value.isChangingGround)
        assertEquals("g2", vm.state.value.groundId)
        assertTrue(vm.state.value.isDirty)
    }

    @Test
    fun `a failed ground registration shows friendly copy`() = viewModelTest {
        val grounds = FakeGroundRepository() // nextRegistered not stubbed: the call throws
        val vm = viewModel(grounds = grounds)
        advanceUntilIdle()
        vm.fillRequired()
        vm.onStartRegisteringNewGround()
        vm.onNewGroundNameChanged("Rajaram College Ground")
        vm.onNewGroundPositionChanged(16.69, 74.23)

        vm.registerNewGround()
        advanceUntilIdle()

        assertEquals("Couldn't register this ground.", vm.state.value.groundErrorTitle)
        assertEquals("Check your connection and try again.", vm.state.value.groundErrorMessage)
        assertTrue(vm.state.value.isRegisteringNewGround)
    }

    @Test
    fun `removing a picked logo drops it from the form`() = viewModelTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onLogoPicked(ByteArray(4), "image/jpeg")
        assertTrue(vm.state.value.isDirty)

        vm.removeLogo()
        assertFalse(vm.state.value.hasPendingLogo)
        assertFalse(vm.state.value.isDirty)
    }

    @Test
    fun `selecting a ground marks the form edited`() = viewModelTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onGroundSelected(GroundDto("g1", "Shahu Stadium", "Maharashtra", "Kolhapur", 16.7, 74.2))
        assertEquals("Shahu Stadium", vm.state.value.groundDisplayName)
        assertTrue(vm.state.value.isDirty)
    }
}

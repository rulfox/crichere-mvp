@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.profile

import com.crichere.app.auth.viewModelTest
import com.crichere.app.location.FakeLocationProvider
import com.crichere.app.location.GeoPoint
import com.crichere.app.location.GeocodedLocation
import com.crichere.app.reference.CityDto
import com.crichere.app.reference.FakeReferenceRepository
import com.crichere.app.reference.StateDto
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ProfileSetupViewModel] coverage per this task's Testing Strategy: first-missing-field
 * resumability for every one of the 7 positions plus the bowling-skip case; edit-mode entry
 * starting at the top instead; role-conditional bowling-style visibility and its clear-on-
 * role-change behavior; save-button-enabled logic (mirrored separately, exhaustively, in
 * [ProfileSetupStateIsSaveEnabledTest]); GPS-match-against-fetched-list logic using a fake
 * [com.crichere.app.location.LocationProvider]; the `503` photo-upload-unavailable path.
 */
class ProfileSetupViewModelTest {

    private val karnataka = StateDto(code = "KA", name = "Karnataka")
    private val maharashtra = StateDto(code = "MH", name = "Maharashtra")
    private val bengaluru = CityDto(name = "Bengaluru")
    private val mysuru = CityDto(name = "Mysuru")

    private fun newViewModel(
        isEditMode: Boolean = false,
        profileRepository: FakeProfileRepository = FakeProfileRepository(),
        referenceRepository: FakeReferenceRepository = FakeReferenceRepository(
            states = listOf(karnataka, maharashtra),
            citiesByStateCode = mapOf("KA" to listOf(bengaluru, mysuru)),
        ),
        locationProvider: FakeLocationProvider = FakeLocationProvider(),
    ): ProfileSetupViewModel =
        ProfileSetupViewModel(isEditMode, profileRepository, referenceRepository, locationProvider)

    private fun emptyProfile() = ProfileDto(userId = "u1")

    // ---- Resumability: first-missing-field, one test per position, plus the bowling-skip case ----

    @Test
    fun `first missing field NAME when nothing is filled`() = viewModelTest {
        val viewModel = newViewModel(profileRepository = FakeProfileRepository(emptyProfile()))
        advanceUntilIdle()
        assertEquals(ProfileField.NAME, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `first missing field PHOTO once name is set`() = viewModelTest {
        val profile = emptyProfile().copy(name = "Rahul")
        val viewModel = newViewModel(profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()
        assertEquals(ProfileField.PHOTO, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `first missing field STATE once name and photo are set`() = viewModelTest {
        val profile = emptyProfile().copy(name = "Rahul", photoUrl = "https://x/y.jpg")
        val viewModel = newViewModel(profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()
        assertEquals(ProfileField.STATE, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `first missing field CITY once name photo and state are set`() = viewModelTest {
        val profile = emptyProfile().copy(name = "Rahul", photoUrl = "https://x/y.jpg", state = "Karnataka")
        val viewModel = newViewModel(profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()
        assertEquals(ProfileField.CITY, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `first missing field ROLE once name photo state and city are set`() = viewModelTest {
        val profile = emptyProfile().copy(name = "Rahul", photoUrl = "https://x/y.jpg", state = "Karnataka", city = "Bengaluru")
        val viewModel = newViewModel(profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()
        assertEquals(ProfileField.ROLE, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `first missing field BATTING once role is set but batting style is not`() = viewModelTest {
        val profile = emptyProfile().copy(
            name = "Rahul", photoUrl = "https://x/y.jpg", state = "Karnataka", city = "Bengaluru",
            playingRole = PlayingRole.BATSMAN,
        )
        val viewModel = newViewModel(profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()
        assertEquals(ProfileField.BATTING, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `first missing field BOWLING when role is BOWLER and bowling style is not set`() = viewModelTest {
        val profile = emptyProfile().copy(
            name = "Rahul", photoUrl = "https://x/y.jpg", state = "Karnataka", city = "Bengaluru",
            playingRole = PlayingRole.BOWLER, battingStyle = BattingStyle.RIGHT_HAND,
        )
        val viewModel = newViewModel(profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()
        assertEquals(ProfileField.BOWLING, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `bowling is skipped for a BATSMAN - nothing missing - defaults to NAME`() = viewModelTest {
        val profile = emptyProfile().copy(
            name = "Rahul", photoUrl = "https://x/y.jpg", state = "Karnataka", city = "Bengaluru",
            playingRole = PlayingRole.BATSMAN, battingStyle = BattingStyle.RIGHT_HAND,
        )
        val viewModel = newViewModel(profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()
        // Nothing is missing (BATSMAN never needs a bowling style) -- firstMissingField returns
        // null, which falls back to NAME (the "nothing left to compute" default).
        assertEquals(ProfileField.NAME, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `edit mode always starts at NAME even though an intermediate field is technically missing`() = viewModelTest {
        // name and photo present, state missing -- onboarding mode would land on STATE. Edit mode
        // must NOT run the first-missing-field calculation at all; per the brief, it always starts
        // at the top.
        val profile = emptyProfile().copy(name = "Rahul", photoUrl = "https://x/y.jpg")
        val viewModel = newViewModel(isEditMode = true, profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()
        assertEquals(ProfileField.NAME, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `edit mode pre-fills the form from the existing complete profile`() = viewModelTest {
        val profile = ProfileDto(
            userId = "u1", name = "Rahul Sharma", photoUrl = "https://x/y.jpg", state = "Karnataka",
            city = "Bengaluru", playingRole = PlayingRole.WICKETKEEPER, battingStyle = BattingStyle.LEFT_HAND,
            profileComplete = true,
        )
        val viewModel = newViewModel(isEditMode = true, profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Rahul Sharma", state.name)
        assertEquals("Karnataka", state.state)
        assertEquals("Bengaluru", state.city)
        assertEquals(PlayingRole.WICKETKEEPER, state.playingRole)
    }

    // ---- Role-conditional bowling style ----

    @Test
    fun `bowling style is applicable for BOWLER and ALL_ROUNDER only`() = viewModelTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.onRoleSelected(PlayingRole.BOWLER)
        assertTrue(viewModel.state.value.isBowlingStyleApplicable)

        viewModel.onRoleSelected(PlayingRole.ALL_ROUNDER)
        assertTrue(viewModel.state.value.isBowlingStyleApplicable)

        viewModel.onRoleSelected(PlayingRole.BATSMAN)
        assertFalse(viewModel.state.value.isBowlingStyleApplicable)

        viewModel.onRoleSelected(PlayingRole.WICKETKEEPER)
        assertFalse(viewModel.state.value.isBowlingStyleApplicable)
    }

    @Test
    fun `changing role away from BOWLER clears a previously-selected bowling style`() = viewModelTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.onRoleSelected(PlayingRole.BOWLER)
        viewModel.onBowlingStyleSelected(BowlingStyle.RIGHT_ARM_FAST)
        assertEquals(BowlingStyle.RIGHT_ARM_FAST, viewModel.state.value.bowlingStyle)

        viewModel.onRoleSelected(PlayingRole.BATSMAN)

        assertNull(viewModel.state.value.bowlingStyle)
    }

    @Test
    fun `switching between BOWLER and ALL_ROUNDER keeps the previously-selected bowling style`() = viewModelTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.onRoleSelected(PlayingRole.BOWLER)
        viewModel.onBowlingStyleSelected(BowlingStyle.LEFT_ARM_ORTHODOX)

        viewModel.onRoleSelected(PlayingRole.ALL_ROUNDER)

        assertEquals(BowlingStyle.LEFT_ARM_ORTHODOX, viewModel.state.value.bowlingStyle)
    }

    // ---- State/city selection ----

    @Test
    fun `selecting a state clears the previous city and fetches that state's cities`() = viewModelTest {
        val referenceRepository = FakeReferenceRepository(
            states = listOf(karnataka, maharashtra),
            citiesByStateCode = mapOf("KA" to listOf(bengaluru, mysuru)),
        )
        val viewModel = newViewModel(referenceRepository = referenceRepository)
        advanceUntilIdle()

        viewModel.onStateSelected(karnataka)
        advanceUntilIdle()

        assertEquals("Karnataka", viewModel.state.value.state)
        assertEquals(listOf(bengaluru, mysuru), viewModel.state.value.cities)
        assertEquals(listOf("KA"), referenceRepository.getCitiesForStateCalls)

        viewModel.onCitySelected(bengaluru)
        assertEquals("Bengaluru", viewModel.state.value.city)

        // Changing the state again must clear the previously-selected city and re-fetch.
        viewModel.onStateSelected(maharashtra)
        assertNull(viewModel.state.value.city)
        assertTrue(viewModel.state.value.cities.isEmpty())
    }

    // ---- GPS auto-fill / matching (fake LocationProvider) ----

    @Test
    fun `useMyLocation pre-selects state and city when both reverse-geocoded names match`() = viewModelTest {
        val locationProvider = FakeLocationProvider(
            location = GeoPoint(12.9716, 77.5946),
            geocoded = GeocodedLocation(administrativeArea = "Karnataka", locality = "Bengaluru"),
        )
        val viewModel = newViewModel(locationProvider = locationProvider)
        advanceUntilIdle()

        viewModel.useMyLocation()
        advanceUntilIdle()

        assertEquals("Karnataka", viewModel.state.value.state)
        assertEquals("Bengaluru", viewModel.state.value.city)
        assertFalse(viewModel.state.value.isLocating)
    }

    @Test
    fun `useMyLocation matching is case-insensitive`() = viewModelTest {
        val locationProvider = FakeLocationProvider(
            location = GeoPoint(12.9716, 77.5946),
            geocoded = GeocodedLocation(administrativeArea = "karnataka", locality = "BENGALURU"),
        )
        val viewModel = newViewModel(locationProvider = locationProvider)
        advanceUntilIdle()

        viewModel.useMyLocation()
        advanceUntilIdle()

        assertEquals("Karnataka", viewModel.state.value.state)
        assertEquals("Bengaluru", viewModel.state.value.city)
    }

    @Test
    fun `useMyLocation is a no-op when the geocoded state name matches nothing seeded`() = viewModelTest {
        val locationProvider = FakeLocationProvider(
            location = GeoPoint(51.5072, -0.1276),
            geocoded = GeocodedLocation(administrativeArea = "Greater London", locality = "London"),
        )
        val viewModel = newViewModel(locationProvider = locationProvider)
        advanceUntilIdle()

        viewModel.useMyLocation()
        advanceUntilIdle()

        assertNull(viewModel.state.value.state)
        assertNull(viewModel.state.value.city)
        assertFalse(viewModel.state.value.isLocating)
    }

    @Test
    fun `useMyLocation pre-selects the state even when the city doesn't match`() = viewModelTest {
        val locationProvider = FakeLocationProvider(
            location = GeoPoint(12.9716, 77.5946),
            geocoded = GeocodedLocation(administrativeArea = "Karnataka", locality = "Hubballi"),
        )
        val viewModel = newViewModel(locationProvider = locationProvider)
        advanceUntilIdle()

        viewModel.useMyLocation()
        advanceUntilIdle()

        assertEquals("Karnataka", viewModel.state.value.state)
        assertNull(viewModel.state.value.city)
        assertEquals(listOf(bengaluru, mysuru), viewModel.state.value.cities)
    }

    @Test
    fun `useMyLocation is a no-op when permission is denied -- getCurrentLocation returns null`() = viewModelTest {
        val locationProvider = FakeLocationProvider(location = null)
        val viewModel = newViewModel(locationProvider = locationProvider)
        advanceUntilIdle()

        viewModel.useMyLocation()
        advanceUntilIdle()

        assertNull(viewModel.state.value.state)
        assertNull(viewModel.state.value.city)
        assertFalse(viewModel.state.value.isLocating)
    }

    @Test
    fun `useMyLocation is not triggered automatically on screen load`() = viewModelTest {
        val locationProvider = FakeLocationProvider(
            location = GeoPoint(12.9716, 77.5946),
            geocoded = GeocodedLocation(administrativeArea = "Karnataka", locality = "Bengaluru"),
        )
        newViewModel(locationProvider = locationProvider)
        advanceUntilIdle()

        assertEquals(0, locationProvider.getCurrentLocationCallCount)
    }

    // ---- Photo upload, including the 503 environment case ----

    @Test
    fun `uploadPhoto succeeds and stores the computed photoUrl`() = viewModelTest {
        val profileRepository = FakeProfileRepository()
        val viewModel = newViewModel(profileRepository = profileRepository)
        advanceUntilIdle()

        viewModel.uploadPhoto(byteArrayOf(1, 2, 3), "image/jpeg")
        advanceUntilIdle()

        assertEquals(profileRepository.uploadedPhotoUrl, viewModel.state.value.photoUrl)
        assertNull(viewModel.state.value.photoUploadErrorMessage)
        assertFalse(viewModel.state.value.isUploadingPhoto)
    }

    @Test
    fun `uploadPhoto surfaces a clear non-crashing error on the real 503 photo-upload-unavailable response`() = viewModelTest {
        val profileRepository = FakeProfileRepository().apply {
            requestPhotoUploadUrlError = PhotoUploadUnavailableException()
        }
        val viewModel = newViewModel(profileRepository = profileRepository)
        advanceUntilIdle()

        viewModel.uploadPhoto(byteArrayOf(1, 2, 3), "image/jpeg")
        advanceUntilIdle()

        assertNull(viewModel.state.value.photoUrl)
        assertEquals(
            "Photo upload is unavailable right now. Please try again later.",
            viewModel.state.value.photoUploadErrorMessage,
        )
        assertFalse(viewModel.state.value.isUploadingPhoto)
    }

    // ---- Save flow ----

    @Test
    fun `save sends the full accumulated snapshot and navigates when the profile becomes complete`() = viewModelTest {
        val profileRepository = FakeProfileRepository().apply { nextProfileComplete = true }
        val viewModel = newViewModel(profileRepository = profileRepository)
        advanceUntilIdle()

        viewModel.onNameChanged("Rahul Sharma")
        viewModel.uploadPhoto(byteArrayOf(1), "image/jpeg")
        advanceUntilIdle()
        viewModel.onStateSelected(karnataka)
        advanceUntilIdle()
        viewModel.onCitySelected(bengaluru)
        viewModel.onRoleSelected(PlayingRole.BATSMAN)
        viewModel.onBattingStyleSelected(BattingStyle.RIGHT_HAND)

        assertTrue(viewModel.state.value.isSaveEnabled)

        val observedEvents = mutableListOf<ProfileSetupNavigationEvent>()
        val collectorJob = launch { viewModel.navigationEvents.toList(observedEvents) }

        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, profileRepository.saveProfileCallCount)
        val snapshot = profileRepository.savedSnapshots.single()
        assertEquals("Rahul Sharma", snapshot.name)
        assertEquals("Karnataka", snapshot.state)
        assertEquals("Bengaluru", snapshot.city)
        assertEquals(PlayingRole.BATSMAN, snapshot.playingRole)
        assertNull(snapshot.bowlingStyle)
        assertEquals(1, observedEvents.size)
        assertEquals(ProfileSetupNavigationEvent.NavigateToOwnProfile, observedEvents.single())

        collectorJob.cancel()
    }

    @Test
    fun `save does not navigate when the resulting profile is still incomplete`() = viewModelTest {
        val profileRepository = FakeProfileRepository().apply { nextProfileComplete = false }
        val viewModel = newViewModel(profileRepository = profileRepository)
        advanceUntilIdle()

        viewModel.onNameChanged("Rahul Sharma")
        viewModel.uploadPhoto(byteArrayOf(1), "image/jpeg")
        advanceUntilIdle()
        viewModel.onStateSelected(karnataka)
        advanceUntilIdle()
        viewModel.onCitySelected(bengaluru)
        viewModel.onRoleSelected(PlayingRole.BATSMAN)
        viewModel.onBattingStyleSelected(BattingStyle.RIGHT_HAND)

        val observedEvents = mutableListOf<ProfileSetupNavigationEvent>()
        val collectorJob = launch { viewModel.navigationEvents.toList(observedEvents) }

        viewModel.save()
        advanceUntilIdle()

        assertTrue(observedEvents.isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `save is a no-op while required fields are still missing`() = viewModelTest {
        val profileRepository = FakeProfileRepository()
        val viewModel = newViewModel(profileRepository = profileRepository)
        advanceUntilIdle()

        viewModel.onNameChanged("Rahul Sharma")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(0, profileRepository.saveProfileCallCount)
    }

    @Test
    fun `a BOWLER must have a bowling style included in the save snapshot`() = viewModelTest {
        val profileRepository = FakeProfileRepository().apply { nextProfileComplete = true }
        val viewModel = newViewModel(profileRepository = profileRepository)
        advanceUntilIdle()

        viewModel.onNameChanged("Rahul Sharma")
        viewModel.uploadPhoto(byteArrayOf(1), "image/jpeg")
        advanceUntilIdle()
        viewModel.onStateSelected(karnataka)
        advanceUntilIdle()
        viewModel.onCitySelected(bengaluru)
        viewModel.onRoleSelected(PlayingRole.BOWLER)
        viewModel.onBattingStyleSelected(BattingStyle.RIGHT_HAND)

        assertFalse(viewModel.state.value.isSaveEnabled, "bowling style still missing for a BOWLER")

        viewModel.onBowlingStyleSelected(BowlingStyle.RIGHT_ARM_OFFBREAK)
        assertTrue(viewModel.state.value.isSaveEnabled)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(BowlingStyle.RIGHT_ARM_OFFBREAK, profileRepository.savedSnapshots.single().bowlingStyle)
    }
}

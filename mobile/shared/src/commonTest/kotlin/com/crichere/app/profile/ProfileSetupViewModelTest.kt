@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.profile

import com.crichere.app.auth.viewModelTest
import com.crichere.app.location.FakeLocationProvider
import com.crichere.app.location.GeoPoint
import com.crichere.app.location.GeocodedLocation
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.FakeReferenceRepository
import com.crichere.app.reference.StateDto
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ProfileSetupViewModel] coverage per this task's Testing Strategy: first-missing-field
 * resumability for every one of the 7 positions (name, photo, state, district, role, batting,
 * bowling; no city since design update #6) plus the bowling-skip case; edit-mode entry starting at the top instead;
 * role-conditional bowling-style visibility and its clear-on-role-change behavior; save-button-
 * enabled logic (mirrored separately, exhaustively, in [ProfileSetupStateIsSaveEnabledTest]);
 * GPS-match-against-fetched-list logic using a fake [com.crichere.app.location.LocationProvider];
 * the `503` photo-upload-unavailable path.
 */
class ProfileSetupViewModelTest {

    private val karnataka = StateDto(code = "KA", name = "Karnataka")
    private val maharashtra = StateDto(code = "MH", name = "Maharashtra")
    private val bengaluruUrban = DistrictDto(id = "d-ka-1", name = "Bengaluru Urban")
    private val mysuruDistrict = DistrictDto(id = "d-ka-2", name = "Mysuru")

    private fun newViewModel(
        isEditMode: Boolean = false,
        profileRepository: FakeProfileRepository = FakeProfileRepository(),
        referenceRepository: FakeReferenceRepository = FakeReferenceRepository(
            states = listOf(karnataka, maharashtra),
            districtsByStateCode = mapOf("KA" to listOf(bengaluruUrban, mysuruDistrict)),
        ),
        locationProvider: FakeLocationProvider = FakeLocationProvider(),
    ): ProfileSetupViewModel =
        ProfileSetupViewModel(isEditMode, profileRepository, referenceRepository, locationProvider).apply { retry() }

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
    fun `first missing field DISTRICT once name photo and state are set`() = viewModelTest {
        val profile = emptyProfile().copy(name = "Rahul", photoUrl = "https://x/y.jpg", state = "Karnataka")
        val viewModel = newViewModel(profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()
        assertEquals(ProfileField.DISTRICT, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `first missing field ROLE once name photo state and district are set`() = viewModelTest {
        val profile = emptyProfile().copy(
            name = "Rahul", photoUrl = "https://x/y.jpg", state = "Karnataka", district = "Bengaluru Urban",
        )
        val viewModel = newViewModel(profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()
        assertEquals(ProfileField.ROLE, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `first missing field BATTING once role is set but batting style is not`() = viewModelTest {
        val profile = emptyProfile().copy(
            name = "Rahul", photoUrl = "https://x/y.jpg", state = "Karnataka", district = "Bengaluru Urban", playingRole = PlayingRole.BATSMAN,
        )
        val viewModel = newViewModel(profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()
        assertEquals(ProfileField.BATTING, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `first missing field BOWLING when role is BOWLER and bowling style is not set`() = viewModelTest {
        val profile = emptyProfile().copy(
            name = "Rahul", photoUrl = "https://x/y.jpg", state = "Karnataka", district = "Bengaluru Urban", playingRole = PlayingRole.BOWLER, battingStyle = BattingStyle.RIGHT_HAND,
        )
        val viewModel = newViewModel(profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()
        assertEquals(ProfileField.BOWLING, viewModel.state.value.initialFocusField)
    }

    @Test
    fun `bowling is skipped for a BATSMAN - nothing missing - defaults to NAME`() = viewModelTest {
        val profile = emptyProfile().copy(
            name = "Rahul", photoUrl = "https://x/y.jpg", state = "Karnataka", district = "Bengaluru Urban", playingRole = PlayingRole.BATSMAN, battingStyle = BattingStyle.RIGHT_HAND,
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
            district = "Bengaluru Urban", playingRole = PlayingRole.WICKETKEEPER,
            battingStyle = BattingStyle.LEFT_HAND, profileComplete = true,
        )
        val viewModel = newViewModel(isEditMode = true, profileRepository = FakeProfileRepository(profile))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Rahul Sharma", state.name)
        assertEquals("Karnataka", state.state)
        assertEquals("Bengaluru Urban", state.district)
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

    // ---- State/district selection ----

    @Test
    fun `selecting a state clears the previous district and fetches that state's districts`() = viewModelTest {
        val referenceRepository = FakeReferenceRepository(
            states = listOf(karnataka, maharashtra),
            districtsByStateCode = mapOf("KA" to listOf(bengaluruUrban, mysuruDistrict)),
        )
        val viewModel = newViewModel(referenceRepository = referenceRepository)
        advanceUntilIdle()

        viewModel.onStateSelected(karnataka)
        advanceUntilIdle()

        assertEquals("Karnataka", viewModel.state.value.state)
        assertEquals(listOf(bengaluruUrban, mysuruDistrict), viewModel.state.value.districts)
        assertEquals(listOf("KA"), referenceRepository.getDistrictsForStateCalls)

        viewModel.onDistrictSelected(bengaluruUrban)
        advanceUntilIdle()

        // Changing the state again must clear the previously-selected district and re-fetch.
        viewModel.onStateSelected(maharashtra)
        assertNull(viewModel.state.value.district)
        assertTrue(viewModel.state.value.districts.isEmpty())
    }

    @Test
    fun `selecting a district replaces the previous one`() = viewModelTest {
        val referenceRepository = FakeReferenceRepository(
            states = listOf(karnataka),
            districtsByStateCode = mapOf("KA" to listOf(bengaluruUrban)),
        )
        val viewModel = newViewModel(referenceRepository = referenceRepository)
        advanceUntilIdle()
        viewModel.onStateSelected(karnataka)
        advanceUntilIdle()

        viewModel.onDistrictSelected(bengaluruUrban)
        advanceUntilIdle()

        assertEquals("Bengaluru Urban", viewModel.state.value.district)

        viewModel.onDistrictSelected(mysuruDistrict)
        assertEquals("Mysuru", viewModel.state.value.district)
    }

    // ---- Edit profile discard (design update #6, C1-discard) ----

    @Test
    fun `a freshly loaded profile is not dirty, an edit makes it dirty and undoing it clears that`() = viewModelTest {
        val profileRepository = FakeProfileRepository(
            profile = ProfileDto(
                userId = "u1", name = "Rahul", photoUrl = "https://x/y.jpg", state = "Karnataka",
                district = "Bengaluru Urban", playingRole = PlayingRole.BATSMAN, battingStyle = BattingStyle.RIGHT_HAND,
            ),
        )
        val viewModel = newViewModel(profileRepository = profileRepository, isEditMode = true)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isDirty)

        viewModel.onNameChanged("Rahul S")
        assertTrue(viewModel.state.value.isDirty)

        viewModel.onNameChanged("Rahul")
        assertFalse(viewModel.state.value.isDirty)
    }

    // ---- GPS auto-fill / matching (fake LocationProvider) ----

    @Test
    fun `useMyLocation pre-selects state and district when both reverse-geocoded names match`() = viewModelTest {
        val locationProvider = FakeLocationProvider(
            location = GeoPoint(12.9716, 77.5946),
            geocoded = GeocodedLocation(
                administrativeArea = "Karnataka",
                subAdministrativeArea = "Bengaluru Urban",
            ),
        )
        val viewModel = newViewModel(locationProvider = locationProvider)
        advanceUntilIdle()

        viewModel.useMyLocation()
        advanceUntilIdle()

        assertEquals("Karnataka", viewModel.state.value.state)
        assertEquals("Bengaluru Urban", viewModel.state.value.district)
        assertFalse(viewModel.state.value.isLocating)
    }

    @Test
    fun `useMyLocation matching is case-insensitive`() = viewModelTest {
        val locationProvider = FakeLocationProvider(
            location = GeoPoint(12.9716, 77.5946),
            geocoded = GeocodedLocation(
                administrativeArea = "karnataka",
                subAdministrativeArea = "BENGALURU URBAN",
            ),
        )
        val viewModel = newViewModel(locationProvider = locationProvider)
        advanceUntilIdle()

        viewModel.useMyLocation()
        advanceUntilIdle()

        assertEquals("Karnataka", viewModel.state.value.state)
        assertEquals("Bengaluru Urban", viewModel.state.value.district)
    }

    @Test
    fun `useMyLocation is a no-op when the geocoded state name matches nothing seeded`() = viewModelTest {
        val locationProvider = FakeLocationProvider(
            location = GeoPoint(51.5072, -0.1276),
            geocoded = GeocodedLocation(
                administrativeArea = "Greater London",
                subAdministrativeArea = null,
            ),
        )
        val viewModel = newViewModel(locationProvider = locationProvider)
        advanceUntilIdle()

        viewModel.useMyLocation()
        advanceUntilIdle()

        assertNull(viewModel.state.value.state)
        assertNull(viewModel.state.value.district)
        assertFalse(viewModel.state.value.isLocating)
    }

    @Test
    fun `useMyLocation pre-selects the state even when the district doesn't match`() = viewModelTest {
        val locationProvider = FakeLocationProvider(
            location = GeoPoint(12.9716, 77.5946),
            geocoded = GeocodedLocation(
                administrativeArea = "Karnataka",
                subAdministrativeArea = "Somewhere Unseeded",
            ),
        )
        val viewModel = newViewModel(locationProvider = locationProvider)
        advanceUntilIdle()

        viewModel.useMyLocation()
        advanceUntilIdle()

        assertEquals("Karnataka", viewModel.state.value.state)
        assertNull(viewModel.state.value.district)
        assertEquals(listOf(bengaluruUrban, mysuruDistrict), viewModel.state.value.districts)
    }

    @Test
    fun `useMyLocation is a no-op when permission is denied -- getCurrentLocation returns null`() = viewModelTest {
        val locationProvider = FakeLocationProvider(location = null)
        val viewModel = newViewModel(locationProvider = locationProvider)
        advanceUntilIdle()

        viewModel.useMyLocation()
        advanceUntilIdle()

        assertNull(viewModel.state.value.state)
        assertNull(viewModel.state.value.district)
        assertFalse(viewModel.state.value.isLocating)
    }

    @Test
    fun `useMyLocation is not triggered automatically on screen load`() = viewModelTest {
        val locationProvider = FakeLocationProvider(
            location = GeoPoint(12.9716, 77.5946),
            geocoded = GeocodedLocation(
                administrativeArea = "Karnataka",
                subAdministrativeArea = "Bengaluru Urban",
            ),
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

    @Test
    fun `uploadPhoto reports progress and the file caption while in flight`() = viewModelTest {
        val profileRepository = FakeProfileRepository().apply {
            uploadProgressSteps = listOf(0.63f, 1f)
            uploadDelayMillis = 1_000
        }
        val viewModel = newViewModel(profileRepository = profileRepository)
        advanceUntilIdle()

        viewModel.uploadPhoto(ByteArray(2_048), "image/jpeg", fileName = "profile-photo.jpg")
        runCurrent()

        val inFlight = viewModel.state.value
        assertTrue(inFlight.isUploadingPhoto)
        assertEquals(0.63f, inFlight.photoUploadProgress)
        assertEquals("profile-photo.jpg", inFlight.uploadingPhotoName)
        assertEquals(2_048L, inFlight.uploadingPhotoSizeBytes)

        advanceUntilIdle()
        assertFalse(viewModel.state.value.isUploadingPhoto)
        assertEquals(profileRepository.uploadedPhotoUrl, viewModel.state.value.photoUrl)
    }

    @Test
    fun `cancelPhotoUpload abandons the upload without an error and keeps the previous photo`() = viewModelTest {
        val profileRepository = FakeProfileRepository(
            profile = ProfileDto(userId = "11111111-1111-1111-1111-111111111111", photoUrl = "https://example.com/old.jpg"),
        ).apply { uploadDelayMillis = 1_000 }
        val viewModel = newViewModel(profileRepository = profileRepository)
        advanceUntilIdle()

        viewModel.uploadPhoto(byteArrayOf(1), "image/jpeg")
        runCurrent()
        viewModel.cancelPhotoUpload()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isUploadingPhoto)
        assertNull(state.photoUploadErrorMessage)
        assertEquals("https://example.com/old.jpg", state.photoUrl)
    }

    @Test
    fun `retryPhotoUpload re-sends the photo whose upload failed`() = viewModelTest {
        val profileRepository = FakeProfileRepository().apply { uploadPhotoError = RuntimeException("S3 down") }
        val viewModel = newViewModel(profileRepository = profileRepository)
        advanceUntilIdle()

        viewModel.uploadPhoto(byteArrayOf(1, 2), "image/jpeg")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.photoUploadErrorMessage != null)

        profileRepository.uploadPhotoError = null
        viewModel.retryPhotoUpload()
        advanceUntilIdle()

        assertEquals(2, profileRepository.uploadPhotoCallCount)
        assertEquals(profileRepository.uploadedPhotoUrl, viewModel.state.value.photoUrl)
        assertNull(viewModel.state.value.photoUploadErrorMessage)
    }

    // ---- Save flow ----

    @Test
    fun `a failed save shows a friendly message, never the raw cause`() = viewModelTest {
        val profileRepository = FakeProfileRepository().apply {
            saveProfileError = ProfileSaveFailedException("Profile save failed with status 500 Internal Server Error")
        }
        val viewModel = newViewModel(profileRepository = profileRepository)
        advanceUntilIdle()
        viewModel.onNameChanged("Rahul Sharma")
        viewModel.uploadPhoto(byteArrayOf(1), "image/jpeg")
        advanceUntilIdle()
        viewModel.onStateSelected(karnataka)
        advanceUntilIdle()
        viewModel.onDistrictSelected(bengaluruUrban)
        advanceUntilIdle()
        viewModel.onRoleSelected(PlayingRole.BATSMAN)
        viewModel.onBattingStyleSelected(BattingStyle.RIGHT_HAND)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(ProfileSetupViewModel.SAVE_FAILED_MESSAGE, viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `a one-letter name is caught on save with an inline error that clears on the next keystroke`() = viewModelTest {
        val profileRepository = FakeProfileRepository().apply { nextProfileComplete = true }
        val viewModel = newViewModel(profileRepository = profileRepository)
        advanceUntilIdle()
        viewModel.onNameChanged(" R ")
        viewModel.uploadPhoto(byteArrayOf(1), "image/jpeg")
        advanceUntilIdle()
        viewModel.onStateSelected(karnataka)
        advanceUntilIdle()
        viewModel.onDistrictSelected(bengaluruUrban)
        advanceUntilIdle()
        viewModel.onRoleSelected(PlayingRole.BATSMAN)
        viewModel.onBattingStyleSelected(BattingStyle.RIGHT_HAND)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(ProfileSetupViewModel.NAME_TOO_SHORT_MESSAGE, viewModel.state.value.nameError)
        assertFalse(viewModel.state.value.isSaving)
        assertEquals(0, profileRepository.saveProfileCallCount)

        viewModel.onNameChanged(" Ra")
        assertEquals(null, viewModel.state.value.nameError)
    }

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
        viewModel.onDistrictSelected(bengaluruUrban)
        advanceUntilIdle()
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
        assertEquals("Bengaluru Urban", snapshot.district)
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
        viewModel.onDistrictSelected(bengaluruUrban)
        advanceUntilIdle()
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
        viewModel.onDistrictSelected(bengaluruUrban)
        advanceUntilIdle()
        viewModel.onRoleSelected(PlayingRole.BOWLER)
        viewModel.onBattingStyleSelected(BattingStyle.RIGHT_HAND)

        assertFalse(viewModel.state.value.isSaveEnabled, "bowling style still missing for a BOWLER")

        viewModel.onBowlingStyleSelected(BowlingStyle.RIGHT_ARM_OFFBREAK)
        assertTrue(viewModel.state.value.isSaveEnabled)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(BowlingStyle.RIGHT_ARM_OFFBREAK, profileRepository.savedSnapshots.single().bowlingStyle)
    }

    @Test
    fun `a stale in-flight retry from an earlier user is cancelled, not left free to overwrite a newer user's state`() = viewModelTest {
        // Reproduces a real on-device bug: this ViewModel instance outlives any single visit
        // (Koin's koinViewModel(key = "profile-setup:$isEditMode") returns the same instance every
        // time), so logging out and signing in as someone else and revisiting this screen can
        // leave an earlier retry() still in flight when a newer one starts. Without cancelling the
        // older one, its slower response could land after the newer one's and silently repopulate
        // the form with the previous user's name/location/photo.
        val profileRepository = FakeProfileRepository(emptyProfile().copy(name = "User A"))
        val viewModel = newViewModel(profileRepository = profileRepository)
        advanceUntilIdle() // the helper's own construction-time retry() -- let it settle first

        profileRepository.getProfileDelayMillis = 1000
        viewModel.retry() // stale visit, still in flight, never advanced

        profileRepository.profile = emptyProfile().copy(name = "User B")
        profileRepository.getProfileDelayMillis = 0
        viewModel.retry() // fresh visit -- must win

        advanceUntilIdle()

        assertEquals("User B", viewModel.state.value.name)
    }
}

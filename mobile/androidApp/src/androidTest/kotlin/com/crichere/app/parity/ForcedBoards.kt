package com.crichere.app.parity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import com.crichere.app.auth.FakeAuthRepository
import com.crichere.app.auth.OtpVerifyState
import com.crichere.app.auth.OtpVerifyViewModel
import com.crichere.app.auth.PhoneEntryState
import com.crichere.app.auth.PhoneEntryViewModel
import com.crichere.app.location.FakeLocationProvider
import com.crichere.app.moderation.BlockState
import com.crichere.app.moderation.BlockTarget
import com.crichere.app.moderation.BlockViewModel
import com.crichere.app.moderation.BlockedPeopleState
import com.crichere.app.moderation.BlockedPeopleViewModel
import com.crichere.app.moderation.BlockedPersonDto
import com.crichere.app.moderation.BlockedRow
import com.crichere.app.moderation.FakeModerationRepository
import com.crichere.app.moderation.ReportReason
import com.crichere.app.moderation.ReportState
import com.crichere.app.moderation.ReportStep
import com.crichere.app.moderation.ReportSubject
import com.crichere.app.moderation.ReportTargetType
import com.crichere.app.moderation.ReportViewModel
import com.crichere.app.profile.BattingStyle
import com.crichere.app.profile.BowlingStyle
import com.crichere.app.profile.FakeProfileRepository
import com.crichere.app.profile.OwnProfileState
import com.crichere.app.profile.OwnProfileViewModel
import com.crichere.app.profile.PlayingRole
import com.crichere.app.profile.ProfileDto
import com.crichere.app.profile.ProfileSetupState
import com.crichere.app.profile.ProfileSetupViewModel
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.FakeReferenceRepository
import com.crichere.app.reference.StateDto
import com.crichere.app.ui.BlockDialog
import com.crichere.app.ui.BlockedPeopleScreen
import com.crichere.app.ui.MainBottomBar
import com.crichere.app.ui.OtpVerifyScreen
import com.crichere.app.ui.OwnProfileScreen
import com.crichere.app.ui.PhoneEntryScreen
import com.crichere.app.ui.ProfileSetupScreen
import com.crichere.app.ui.ReportSheet
import com.crichere.app.ui.navigation.MainTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

// Boards for screens that take only a ViewModel: the ViewModel's background work (load, OTP ticker) is
// cancelled and its private `_state` set to the board's state, the same state the iOS gallery passes in.

@Suppress("UNCHECKED_CAST")
private fun <T> ViewModel.force(state: T) {
    javaClass.declaredFields.filter { Job::class.java.isAssignableFrom(it.type) }.forEach { field ->
        field.isAccessible = true
        (field.get(this) as Job?)?.cancel()
    }
    val field = javaClass.getDeclaredField("_state").apply { isAccessible = true }
    (field.get(this) as MutableStateFlow<T>).value = state
}

/**
 * [make]'s ViewModel showing [state]: set when built, and again after the first frame in case init work lands late.
 * With [opening], that state shows first and [state] follows once a sheet has had time to open (a sheet that
 * refuses to move while submitting can't open straight into that state).
 */
@Composable
private fun <V : ViewModel, T> forced(state: T, opening: T? = null, make: () -> V): V {
    val viewModel = remember { make().also { it.force(opening ?: state) } }
    LaunchedEffect(Unit) {
        if (opening != null) delay(800)
        viewModel.force(state)
    }
    return viewModel
}

// A Sign in, B Verify

private fun signIn(state: PhoneEntryState): @Composable () -> Unit = {
    PhoneEntryScreen(forced(state) { PhoneEntryViewModel(FakeAuthRepository()) }, showLockoutNotice = false, deletionScheduledOn = null)
}

private fun otp(code: String = "", verifying: Boolean = false, error: String? = null, attempts: Int = 5, used: Int = 0, cooldown: Int = 42,
                canResend: Boolean = false) = OtpVerifyState(code = code, isVerifying = verifying, errorMessage = error, attemptsRemaining = attempts,
    resendsUsed = used, maxResends = 3, cooldownSecondsRemaining = cooldown, canResend = canResend)

private fun verify(state: OtpVerifyState): @Composable () -> Unit = {
    OtpVerifyScreen(forced(state) { OtpVerifyViewModel("+919876543210", "verification-id", null, FakeAuthRepository()) }, phoneNumber = "+919876543210")
}

// C Profile setup

private val states = listOf(StateDto("MH", "Maharashtra"), StateDto("KA", "Karnataka"), StateDto("GA", "Goa"))
private val districts = listOf(DistrictDto("d1", "Kolhapur"), DistrictDto("d2", "Pune"), DistrictDto("d3", "Sangli"))

private fun profile(name: String = "", photo: Boolean = false, place: Boolean = false, role: PlayingRole? = null, batting: BattingStyle? = null,
                    bowling: BowlingStyle? = null, uploading: Float? = null, uploadError: String? = null, saving: Boolean = false,
                    error: String? = null, nameError: String? = null) = ProfileSetupState(
    isLoading = false, name = name, photoUrl = if (photo) "https://fixtures.invalid/photo.jpg" else null,
    state = if (place) "Maharashtra" else null, district = if (place) "Kolhapur" else null,
    playingRole = role, battingStyle = batting, bowlingStyle = bowling, states = states, districts = if (place) districts else emptyList(),
    isUploadingPhoto = uploading != null, photoUploadProgress = uploading ?: 0f,
    uploadingPhotoName = if (uploading != null || uploadError != null) "IMG_2041.jpg" else null,
    uploadingPhotoSizeBytes = if (uploading != null || uploadError != null) 412_000 else null,
    isSaving = saving, errorMessage = error, nameError = nameError, photoUploadErrorMessage = uploadError,
)

private fun profileSetup(state: ProfileSetupState): @Composable () -> Unit = {
    val viewModel = forced(state) {
        ProfileSetupViewModel(false, FakeProfileRepository(ProfileDto(userId = "u1")), FakeReferenceRepository(states, mapOf("MH" to districts)),
            FakeLocationProvider())
    }
    ProfileSetupScreen(viewModel, onNavigateToOwnProfile = {}, onBack = null)
}

// N My profile

private fun own(photo: Boolean = true, uploading: Float? = null, photoError: String? = null, failed: Boolean = false, loading: Boolean = false) =
    OwnProfileState(isLoading = loading, name = if (failed || loading) null else "Aarav Pawar",
        photoUrl = if (photo) "https://fixtures.invalid/photo.jpg" else null, country = "IN", state = "Maharashtra", district = "Kolhapur",
        playingRole = PlayingRole.ALL_ROUNDER, battingStyle = BattingStyle.RIGHT_HAND, bowlingStyle = BowlingStyle.LEFT_ARM_ORTHODOX,
        loadFailed = failed, isUploadingPhoto = uploading != null, photoUploadProgress = uploading ?: 0f, photoError = photoError)

private fun ownProfile(state: OwnProfileState): @Composable () -> Unit = {
    val viewModel = forced(state) { OwnProfileViewModel(FakeProfileRepository(ProfileDto(userId = "u1")), FakeAuthRepository("u1")) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = { MainBottomBar(selected = MainTab.MY_PROFILE, onSelect = {}) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { OwnProfileScreen(viewModel, {}, {}, {}, {}, {}) }
    }
}

// R Moderation

private val person = ReportSubject(ReportTargetType.USER, "u3", "Sahil Kulkarni", "Player in Kolhapur Premier League", "league-kpl")

private fun report(state: ReportState): @Composable () -> Unit = {
    ReportSheet(forced(state, opening = state.copy(isSubmitting = false).takeIf { state.isSubmitting }) { ReportViewModel(FakeModerationRepository()) })
}

private fun blocked(state: BlockedPeopleState): @Composable () -> Unit = {
    BlockedPeopleScreen(onBack = {}, viewModel = forced(state) { BlockedPeopleViewModel(FakeModerationRepository()) })
}

private val blockedRows = listOf(
    BlockedRow(BlockedPersonDto("u3", "Sahil Kulkarni", "2026-10-12T10:00:00Z")),
    BlockedRow(BlockedPersonDto("u5", "Omkar Shinde", "2026-11-02T10:00:00Z"), failed = true),
)

fun forcedBoards(): Map<String, @Composable () -> Unit> = mapOf(
    "A4" to signIn(PhoneEntryState(phoneNumber = "98765", errorMessage = "Enter a valid 10-digit mobile number.", termsAccepted = true)),
    "A5" to signIn(PhoneEntryState(phoneNumber = "9876543210", isSubmitting = true, termsAccepted = true)),
    "B3" to verify(otp("482910", error = "Incorrect code. 4 attempts remaining.", attempts = 4)),
    "B4" to verify(otp("482913", verifying = true)),
    "B5" to verify(otp(cooldown = 0, canResend = true)),
    "B7" to verify(otp(used = 3, cooldown = 0)),
    "C1-error" to profileSetup(profile(name = "A", nameError = "Enter your full name.")),
    "C2" to profileSetup(profile(name = "Aarav Pawar", photo = true, place = true, role = PlayingRole.BOWLER, batting = BattingStyle.RIGHT_HAND,
        bowling = BowlingStyle.RIGHT_ARM_FAST)),
    "C4" to profileSetup(profile(name = "Aarav Pawar", uploading = 0.42f)),
    "C6" to profileSetup(profile(name = "Aarav Pawar", uploadError = "Couldn't upload photo. Please try again.")),
    "C7" to profileSetup(profile(name = "Aarav Pawar", photo = true, place = true, role = PlayingRole.BATSMAN, batting = BattingStyle.LEFT_HAND, saving = true)),
    "C8" to profileSetup(profile(name = "Aarav Pawar", photo = true, place = true, role = PlayingRole.BATSMAN, batting = BattingStyle.LEFT_HAND,
        error = "Couldn't save your profile. Please try again.")),
    "N4" to ownProfile(own(uploading = 0.6f)),
    "N5" to ownProfile(own(photoError = "Couldn't update your photo. Please try again.")),
    "N-error" to ownProfile(own(failed = true)),
    "N-loading" to ownProfile(own(loading = true)),
    "R5c" to report(ReportState(person, ReportStep.DETAILS, ReportReason.SOMETHING_ELSE, "Keeps posting abusive messages in the league group.", failed = true)),
    "R5e" to report(ReportState(person, ReportStep.DETAILS, ReportReason.INAPPROPRIATE_IMAGE, "Profile photo is offensive.", alsoBlock = true, isSubmitting = true)),
    "R6a" to report(ReportState(person, ReportStep.SENT, ReportReason.INAPPROPRIATE_IMAGE)),
    "R6b" to report(ReportState(person, ReportStep.ALREADY_REPORTED, ReportReason.SOMETHING_ELSE, "Keeps posting abusive messages.")),
    "R7c" to {
        BlockDialog(forced(BlockState(confirming = BlockTarget("u3", "Sahil Kulkarni"), failed = true)) { BlockViewModel(FakeModerationRepository()) },
            coOrganizerOfThisLeague = { false })
    },
    "R11a" to blocked(BlockedPeopleState(isLoading = false, rows = blockedRows)),
    "R11b" to blocked(BlockedPeopleState(isLoading = false)),
    "R11c" to blocked(BlockedPeopleState(isLoading = false, loadFailed = true)),
)

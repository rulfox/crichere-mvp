package com.crichere.app.parity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.crichere.app.auth.FakeAuthRepository
import com.crichere.app.auth.OtpVerifyViewModel
import com.crichere.app.auth.PhoneEntryViewModel
import com.crichere.app.ground.FakeGroundRepository
import com.crichere.app.ground.GroundDto
import com.crichere.app.league.AwardDraft
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.league.LeagueCreationState
import com.crichere.app.league.LeagueCreationViewModel
import com.crichere.app.league.LeagueDashboardState
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueDetailState
import com.crichere.app.league.LeagueStatus
import com.crichere.app.league.MyLeaguesDto
import com.crichere.app.league.MyLeaguesState
import com.crichere.app.league.LeagueSummaryDto
import com.crichere.app.location.FakeLocationProvider
import com.crichere.app.moderation.BlockTarget
import com.crichere.app.moderation.BlockViewModel
import com.crichere.app.moderation.FakeModerationRepository
import com.crichere.app.moderation.ModerationNoticeDto
import com.crichere.app.moderation.ModerationNoticeKind
import com.crichere.app.moderation.ReportReason
import com.crichere.app.moderation.ReportSubject
import com.crichere.app.moderation.ReportTargetType
import com.crichere.app.moderation.ReportViewModel
import com.crichere.app.profile.BattingStyle
import com.crichere.app.profile.BowlingStyle
import com.crichere.app.profile.FakeProfileRepository
import com.crichere.app.profile.OwnProfileViewModel
import com.crichere.app.profile.PlayingRole
import com.crichere.app.profile.ProfileDto
import com.crichere.app.profile.ProfileSetupViewModel
import com.crichere.app.reference.DistrictDto
import com.crichere.app.reference.FakeReferenceRepository
import com.crichere.app.reference.StateDto
import com.crichere.app.ui.BlockDialog
import com.crichere.app.ui.LeagueCreationScreen
import com.crichere.app.ui.LeagueDashboardScreen
import com.crichere.app.ui.LeagueDetailActions
import com.crichere.app.ui.LeagueDetailScreen
import com.crichere.app.ui.LeagueModeration
import com.crichere.app.ui.MainBottomBar
import com.crichere.app.ui.ModerationNoticeDialog
import com.crichere.app.ui.MyLeaguesScreen
import com.crichere.app.ui.OtpVerifyScreen
import com.crichere.app.ui.OwnProfileScreen
import com.crichere.app.ui.PhoneEntryScreen
import com.crichere.app.ui.ProfileSetupScreen
import com.crichere.app.ui.ReportSheet
import com.crichere.app.ui.ScreenshotViewerScreen
import com.crichere.app.ui.SuspendedScreen
import com.crichere.app.ui.navigation.MainTab

private val states = listOf(StateDto("MH", "Maharashtra"), StateDto("KA", "Karnataka"), StateDto("GA", "Goa"))
private val districts = listOf(DistrictDto("d1", "Kolhapur"), DistrictDto("d2", "Pune"), DistrictDto("d3", "Sangli"))

// A Sign in, B Verify (ViewModel-driven: those screens take only a ViewModel)

private fun signIn(number: String = "", terms: Boolean = false, lockout: Boolean = false, deletion: String? = null): @Composable () -> Unit = {
    val viewModel = remember {
        PhoneEntryViewModel(FakeAuthRepository()).apply {
            if (number.isNotEmpty()) onPhoneNumberChanged(number)
            if (terms) onTermsAcceptedChanged(true)
        }
    }
    PhoneEntryScreen(viewModel, showLockoutNotice = lockout, deletionScheduledOn = deletion)
}

private fun verify(code: String = ""): @Composable () -> Unit = {
    val viewModel = remember {
        OtpVerifyViewModel("+919876543210", "verification-id", null, FakeAuthRepository()).apply { if (code.isNotEmpty()) onCodeChanged(code) }
    }
    OtpVerifyScreen(viewModel, phoneNumber = "+919876543210")
}

// C Profile setup

private fun profileSetup(edit: Boolean): @Composable () -> Unit = {
    val viewModel = remember {
        val profile = if (edit) ProfileDto(userId = "u1", name = "Aarav Pawar", photoUrl = SamplePhoto.url, country = "IN", state = "Maharashtra", district = "Kolhapur",
            playingRole = PlayingRole.ALL_ROUNDER, battingStyle = BattingStyle.RIGHT_HAND, bowlingStyle = BowlingStyle.LEFT_ARM_ORTHODOX,
            profileComplete = true) else ProfileDto(userId = "u1")
        ProfileSetupViewModel(edit, FakeProfileRepository(profile), FakeReferenceRepository(states, mapOf("MH" to districts)), FakeLocationProvider()).also { it.retry() }
    }
    ProfileSetupScreen(viewModel, onNavigateToOwnProfile = {}, onBack = if (edit) ({}) else null)
}

// Tab shell (D, M, N): the screen above MainBottomBar, as AuthNavHost's Scaffold draws it

@Composable
private fun TabShell(tab: MainTab, content: @Composable () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = { MainBottomBar(selected = tab, onSelect = {}) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

private fun dashboardLeagues() = listOf(
    ParityFixtures.league(),
    ParityFixtures.league(id = "league-scl", name = "Sangli Champions League", ground = "Willingdon College Ground", district = "Sangli",
        startsOn = "2026-10-19", format = "T10", playersRequired = 24, playerFee = null, players = ParityFixtures.players.take(3)),
    ParityFixtures.league(id = "league-ptc", name = "Pune Tennis Cricket Cup 2026", ground = "PYC Gymkhana", district = "Pune", startsOn = "2026-11-02",
        format = null, playersRequired = null, playerFee = 1500.0, players = ParityFixtures.players.take(2)),
    ParityFixtures.league(id = "league-sbt", name = "Satara Box Turf Bash", ground = "Shivaji Turf", district = "Satara", startsOn = "2026-11-09",
        format = "Box cricket", playersRequired = 12, playerFee = 300.0, players = ParityFixtures.players.take(5)),
)

private fun dashboard(leagues: List<LeagueDto> = dashboardLeagues(), loading: Boolean = false, state: String? = null, district: String? = null,
                      near: Boolean = false, locating: Boolean = false, error: String? = null, locationOff: Boolean = false): @Composable () -> Unit = {
    TabShell(MainTab.DASHBOARD) {
        LeagueDashboardScreen(
            state = LeagueDashboardState(isLoading = loading, leagues = leagues, states = states, districts = districts, selectedState = state,
                selectedDistrict = district, isNearMode = near, isLocating = locating, errorMessage = error, viewerName = "Aarav Pawar"),
            showLocationOff = locationOff, onStateSelected = {}, onDistrictSelected = {}, onClearAreaFilters = {}, onToggleNearMe = {},
            onOpenLocationSettings = {}, onDismissLocationOff = {}, onRefresh = {}, onOpenLeague = {}, onCreateLeague = {}, onOpenProfile = {},
        )
    }
}

private fun summary(league: LeagueDto) = LeagueSummaryDto(league.id, league.name, null, league.district, league.state, league.groundName, league.startsOn, league.status)

private fun myLeagues(state: MyLeaguesState): @Composable () -> Unit = {
    TabShell(MainTab.MY_LEAGUES) { MyLeaguesScreen(state, onOpenLeague = {}, onRetry = {}, onBrowseLeagues = {}, onCreateLeague = {}) }
}

private fun ownProfile(photo: Boolean): @Composable () -> Unit = {
    val viewModel = remember {
        OwnProfileViewModel(FakeProfileRepository(ProfileDto(userId = "u1", name = "Aarav Pawar", photoUrl = if (photo) SamplePhoto.url else null,
            country = "IN", state = "Maharashtra", district = "Kolhapur", playingRole = PlayingRole.ALL_ROUNDER, battingStyle = BattingStyle.RIGHT_HAND,
            bowlingStyle = BowlingStyle.LEFT_ARM_ORTHODOX, profileComplete = true)), FakeAuthRepository("u1")).also { it.retry() }
    }
    TabShell(MainTab.MY_PROFILE) { OwnProfileScreen(viewModel, {}, {}, {}, {}, {}) }
}

// E League detail

private val noActions = LeagueDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

internal fun detail(league: LeagueDto?, me: String? = "u-visitor", organizer: Boolean = false, loading: Boolean = false,
                   removing: Set<String> = emptySet(), blocked: Set<String> = emptySet()): @Composable () -> Unit = {
    LeagueDetailScreen(
        state = LeagueDetailState(isLoading = loading, league = league, currentUserId = me, isOrganizer = organizer, removingIds = removing),
        moderation = LeagueModeration(blocked = blocked), actions = noActions, notice = null, onNoticeShown = {},
    )
}

// I Create / edit league

private val grounds = listOf(
    GroundDto("g1", "Shahu Stadium", "Maharashtra", "Kolhapur", 16.7, 74.24),
    GroundDto("g2", "Shivaji University Ground", "Maharashtra", "Kolhapur", 16.68, 74.25),
    GroundDto("g3", "Shastri Nagar Maidan", "Maharashtra", "Kolhapur", 16.71, 74.22),
)

internal fun creationState(edit: Boolean = false, dirty: Boolean = false, validation: Boolean = false, name: String = "", located: Boolean = false,
                          ground: Boolean = false, groundQuery: String = "", registering: Boolean = false, newGroundName: String = "",
                          pin: Boolean = false, groundError: Boolean = false, startsOn: String? = null, format: String = "", other: Boolean = false,
                          fees: Boolean = false, upi: String = "", awards: List<AwardDraft> = emptyList(), bannerProgress: Float? = null,
                          saving: Boolean = false, error: String? = null) = LeagueCreationState(
    isLoading = false, isEditMode = edit, isDirty = dirty, showValidation = validation, name = name,
    isUploadingBanner = bannerProgress != null, bannerUploadProgress = bannerProgress,
    state = if (located) "Maharashtra" else null, district = if (located) "Kolhapur" else null,
    states = listOf(StateDto("MH", "Maharashtra"), StateDto("KA", "Karnataka")),
    districts = if (located) listOf(DistrictDto("d1", "Kolhapur"), DistrictDto("d2", "Sangli")) else emptyList(),
    groundId = if (ground) "g1" else null, groundDisplayName = if (ground) "Shahu Stadium -- Kolhapur" else null,
    groundSearchQuery = groundQuery, groundSearchResults = if (groundQuery.isEmpty()) emptyList() else grounds,
    isRegisteringNewGround = registering, newGroundName = newGroundName,
    newGroundLatitude = if (pin) 16.6913 else null, newGroundLongitude = if (pin) 74.2449 else null,
    groundErrorTitle = if (groundError) "A ground with this name already exists nearby." else null,
    groundErrorMessage = if (groundError) "Search for it instead, or use a different name." else null,
    startsOn = startsOn, format = format, isFormatOther = other,
    franchisesRequired = if (fees) "8" else "", playersRequired = if (fees) "120" else "",
    franchiseFee = if (fees) "5000" else "", playerFee = if (fees) "500" else "", organizerUpiId = upi, awards = awards,
    isSaving = saving, errorMessage = error,
)

internal fun creation(state: LeagueCreationState): @Composable () -> Unit = {
    val viewModel = remember { LeagueCreationViewModel(null, FakeLeagueRepository(), FakeGroundRepository(), FakeReferenceRepository(), FakeLocationProvider()) }
    LeagueCreationScreen(state = state, viewModel = viewModel, onCancel = {})
}

// R Moderation

private val person = ReportSubject(ReportTargetType.USER, "u3", "Sahil Kulkarni", "Player in Kolhapur Premier League", "league-kpl")
private val leagueSubject = ReportSubject(ReportTargetType.LEAGUE, "league-kpl", "Kolhapur Premier League", "Organized by Sachin Gaikwad", "league-kpl")

private fun report(subject: ReportSubject, reason: ReportReason? = null, details: Boolean = false, text: String = "", alsoBlock: Boolean = false): @Composable () -> Unit = {
    val viewModel = remember {
        ReportViewModel(FakeModerationRepository()).apply {
            open(subject)
            reason?.let(::selectReason)
            if (details) next()
            if (text.isNotEmpty()) onDetailsChanged(text)
            if (alsoBlock) setAlsoBlock(true)
        }
    }
    ReportSheet(viewModel)
}

private fun block(coOrganizer: Boolean): @Composable () -> Unit = {
    val viewModel = remember { BlockViewModel(FakeModerationRepository()).apply { askToBlock(BlockTarget("u3", "Sahil Kulkarni")) } }
    BlockDialog(viewModel, coOrganizerOfThisLeague = { coOrganizer })
}

// H Screenshot viewer: a 4:3 green-to-gold gradient, like the iOS gallery's sample photo

private fun samplePainter(): BitmapPainter {
    val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawRect(0f, 0f, 1200f, 900f, Paint().apply {
        shader = LinearGradient(0f, 0f, 1200f, 900f, 0xFF1B5E20.toInt(), 0xFFF2B544.toInt(), Shader.TileMode.CLAMP)
    })
    return BitmapPainter(bitmap.asImageBitmap())
}

fun shellBoards(): Map<String, @Composable () -> Unit> = mapOf(
    "A1" to signIn(),
    "A3" to signIn("9876543210", terms = true),
    "T1a" to signIn("9876543210"),
    "B6" to signIn(lockout = true),
    "D7" to signIn(deletion = "25 Oct 2026"),
    "B2" to verify("482913"),
    "C1" to profileSetup(edit = false),
    "C1-edit" to profileSetup(edit = true),
    "D1" to dashboard(),
    "D1-filtered" to dashboard(state = "Maharashtra", district = "Kolhapur"),
    "D3" to dashboard(near = true),
    "D3-locating" to dashboard(locating = true),
    "D4" to dashboard(locationOff = true),
    "D5" to dashboard(leagues = emptyList()),
    "D5-filtered" to dashboard(leagues = emptyList(), state = "Goa"),
    "D-loading" to dashboard(leagues = emptyList(), loading = true),
    "D-error" to dashboard(error = "Couldn't load leagues. Check your connection."),
    "M1" to dashboardLeagues().map(::summary).let { all ->
        myLeagues(MyLeaguesState(false, MyLeaguesDto(listOf(all[0]), listOf(all[1], all[2]), listOf(all[3]), listOf(all[2]))))
    },
    "M2" to myLeagues(MyLeaguesState(false, MyLeaguesDto())),
    "M3" to myLeagues(MyLeaguesState(false, null, "Couldn't load your leagues.")),
    "M-loading" to myLeagues(MyLeaguesState(true)),
    "N1" to ownProfile(photo = true),
    "N3" to ownProfile(photo = false),
    "E1" to detail(ParityFixtures.league(banner = true, awards = ParityFixtures.awards)),
    "E1-compact" to detail(ParityFixtures.league()),
    "E2" to detail(ParityFixtures.league(banner = true), me = "u1"),
    "E3" to detail(ParityFixtures.league(banner = true, players = ParityFixtures.players.toMutableList().also {
        it[0] = ParityFixtures.player(1, "Aarav Pawar", PlayingRole.ALL_ROUNDER, screenshot = true, leaveRequested = true)
    }), me = "u1"),
    "E4" to detail(ParityFixtures.league(players = ParityFixtures.players.toMutableList().also {
        it[2] = ParityFixtures.player(3, "Sahil Kulkarni", PlayingRole.BOWLER, leaveRequested = true)
    }), me = "u-organizer", organizer = true, removing = setOf("p4")),
    "E14" to detail(ParityFixtures.league(auctionStatus = com.crichere.app.league.AuctionStatus.IN_PROGRESS), me = "u-organizer", organizer = true),
    "E5" to detail(ParityFixtures.league(playersRequired = 5, franchisesRequired = 2, following = true)),
    "E13" to detail(ParityFixtures.league(status = LeagueStatus.COMPLETED, awards = ParityFixtures.awards), me = "u-organizer", organizer = true),
    "D9" to detail(ParityFixtures.league(handover = true), me = "u-organizer", organizer = true),
    "R9a" to detail(ParityFixtures.league(), blocked = setOf("u-organizer")),
    "R9b" to detail(ParityFixtures.league(banner = true), me = "u1", blocked = setOf("u-organizer")),
    "E-loading" to detail(null, loading = true),
    "E-error" to detail(null),
    "H1" to { ScreenshotViewerScreen(samplePainter(), loaded = true, failed = false, onRetry = {}, onBack = {}) },
    "H1-profile" to { ScreenshotViewerScreen(samplePainter(), loaded = true, failed = false, onRetry = {}, onBack = {}, title = "Profile photo") },
    "H3" to { ScreenshotViewerScreen(samplePainter(), loaded = false, failed = true, onRetry = {}, onBack = {}) },
    "H-loading" to { ScreenshotViewerScreen(samplePainter(), loaded = false, failed = false, onRetry = {}, onBack = {}) },
    "I1" to creation(creationState()),
    "I4" to creation(creationState(dirty = true, name = "Kolhapur Premier League", located = true, groundQuery = "Sh")),
    "I5" to creation(creationState(dirty = true, located = true, registering = true, pin = true)),
    "I7" to creation(creationState(dirty = true, validation = true, fees = true)),
    "I11" to creation(creationState(dirty = true, located = true, registering = true, newGroundName = "Shahu Stadium", pin = true, groundError = true)),
    "I12" to creation(creationState(edit = true, name = "Kolhapur Premier League", located = true, ground = true, startsOn = "2026-10-12", format = "8 overs", other = true)),
    "I16" to creation(creationState(dirty = true, registering = true)),
    "I-error" to creation(creationState(dirty = true, name = "Kolhapur Premier League", located = true, ground = true, startsOn = "2026-10-12",
        error = "Couldn't save the league. Check your connection and try again.")),
    "R4a" to report(person),
    "R4b" to report(leagueSubject, ReportReason.FAKE_OR_SCAM_LEAGUE),
    "R5a" to report(person, ReportReason.INAPPROPRIATE_IMAGE, details = true, alsoBlock = true),
    "R7a" to block(coOrganizer = false),
    "R7e" to block(coOrganizer = true),
    "R12b" to { ModerationNoticeDialog(ModerationNoticeDto("n1", ModerationNoticeKind.LEAGUE_LOGO, "Kolhapur Premier League"), onDismiss = {}) },
    "R13a" to { SuspendedScreen("+91 98•••••210", onBackToSignIn = {}) },
)

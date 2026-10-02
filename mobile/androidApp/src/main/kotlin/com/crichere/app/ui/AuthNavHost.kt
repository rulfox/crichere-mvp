package com.crichere.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crichere.app.R
import com.crichere.app.ui.theme.InstrumentSansFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.auth.AppStartDestination
import com.crichere.app.auth.AppStartViewModel
import com.crichere.app.auth.AuthNavigationEvent
import com.crichere.app.auth.OtpVerifyViewModel
import com.crichere.app.auth.PhoneEntryNavigationEvent
import com.crichere.app.auth.PhoneEntryViewModel
import com.crichere.app.profile.OwnProfileViewModel
import com.crichere.app.profile.ProfileSetupViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The full navigation contract:
 *  - [Starting] -> [PhoneEntry] / [ProfileSetup] / [Main]: the app-start routing check
 *    (`AppStartViewModel`), routed by its `profileComplete`.
 *  - [PhoneEntry] -> [OtpVerify]: real.
 *  - [OtpVerify] -> [ProfileSetup] / [Main]: real, driven by [AuthNavigationEvent].
 *  - [OtpVerify] -> [PhoneEntry]: real (the 5th-wrong-attempt forced bounce-back).
 *  - [ProfileSetup] -> [Main]: real, on a save that completes the profile.
 *  - [Main] (My Profile tab) -> [ProfileSetup] (`isEditMode = true`) / [PhoneEntry] (logout): real.
 *
 * [Main] replaced a top-level `OwnProfile` destination once Phase 2 gave the app a real landing
 * screen (League Dashboard) to put alongside it -- see [MainRoute]'s own doc for that shell.
 *
 * Still a plain `remember { mutableStateOf(...) }` state switcher rather than Jetpack Navigation
 * Compose -- see Task 6's doc on this file for why; the destination count has grown but the shape
 * (linear flow with a couple of branches, no real back-stack needs) hasn't changed enough to
 * justify a new nav-library dependency yet.
 *
 * [pendingDeepLinkLeagueId] (Phase 3, see docs/PHASE3.md): a league id extracted from a
 * `crichere://leagues/{id}` intent, read once by `MainActivity` at Activity-intent-read time and
 * passed in here. It's held as local state (not threaded through [PhoneEntry]/[OtpVerify]/
 * [ProfileSetup] at all) and only consumed once [Main] is actually reached -- see [MainRoute]'s
 * own doc for the consume-once handoff. This satisfies "not logged in -> goes through the normal
 * flow first, then resumes into the league" for free, since every branch above already funnels
 * into [Main] eventually.
 */
private sealed interface AuthDestination {
    data object Starting : AuthDestination
    /** [lockedOut]: arrived via the 5-wrong-attempts bounce-back, so show the lockout notice (design B6). */
    data class PhoneEntry(val lockedOut: Boolean = false) : AuthDestination
    data class OtpVerify(val phoneNumber: String, val verificationId: String, val resendToken: Any?) : AuthDestination
    data class ProfileSetup(val isEditMode: Boolean) : AuthDestination
    // initialTab exists so that returning from an edit-mode ProfileSetup (always reached from
    // MainRoute's own My Profile tab) lands back on My Profile, not the default Dashboard --
    // MainRoute's own tab state is destroyed and recreated every time this destination is
    // re-entered (this `when` only keeps one branch composed at a time), so without this the tab
    // selection couldn't survive the round trip through ProfileSetup.
    data class Main(val initialTab: MainTab = MainTab.DASHBOARD) : AuthDestination
}

internal enum class MainTab { DASHBOARD, MY_LEAGUES, MY_PROFILE }

@Composable
fun AuthNavHost(pendingDeepLinkLeagueId: String? = null) {
    var destination by remember { mutableStateOf<AuthDestination>(AuthDestination.Starting) }
    // Keyed on the incoming parameter (not a bare `remember { }`) so a new crichere://leagues/{id}
    // intent arriving via MainActivity.onNewIntent while the app is already running -- which
    // updates this same composable's `pendingDeepLinkLeagueId` argument on recomposition, not a
    // fresh AuthNavHost call -- actually resets this state to the new id, rather than being
    // silently ignored by a `remember` that already ran once.
    var pendingLeagueId by remember(pendingDeepLinkLeagueId) { mutableStateOf(pendingDeepLinkLeagueId) }

    when (val current = destination) {
        AuthDestination.Starting -> AppStartRoute { resolved ->
            destination = when (resolved) {
                AppStartDestination.PhoneEntry -> AuthDestination.PhoneEntry()
                AppStartDestination.ProfileSetup -> AuthDestination.ProfileSetup(isEditMode = false)
                AppStartDestination.Main -> AuthDestination.Main()
            }
        }

        is AuthDestination.PhoneEntry -> PhoneEntryRoute(showLockoutNotice = current.lockedOut) { event ->
            when (event) {
                is PhoneEntryNavigationEvent.NavigateToOtpVerify -> {
                    destination = AuthDestination.OtpVerify(
                        phoneNumber = event.phoneNumber,
                        verificationId = event.verificationId,
                        resendToken = event.resendToken,
                    )
                }
            }
        }

        is AuthDestination.OtpVerify -> OtpVerifyRoute(
            phoneNumber = current.phoneNumber,
            verificationId = current.verificationId,
            resendToken = current.resendToken,
        ) { event, lockedOut ->
            destination = when (event) {
                AuthNavigationEvent.NavigateToProfileSetup -> AuthDestination.ProfileSetup(isEditMode = false)
                AuthNavigationEvent.NavigateToOwnProfile -> AuthDestination.Main()
                AuthNavigationEvent.NavigateToPhoneEntry -> AuthDestination.PhoneEntry(lockedOut = lockedOut)
            }
        }

        is AuthDestination.ProfileSetup -> ProfileSetupRoute(isEditMode = current.isEditMode) {
            // Only an edit (reached from My Profile) returns there -- first-time setup completion
            // has no prior tab to return to, so it lands on the default Dashboard.
            destination = AuthDestination.Main(initialTab = if (current.isEditMode) MainTab.MY_PROFILE else MainTab.DASHBOARD)
        }

        is AuthDestination.Main -> MainRoute(
            initialTab = current.initialTab,
            pendingLeagueId = pendingLeagueId,
            onPendingLeagueIdConsumed = { pendingLeagueId = null },
            onNavigateToEditProfile = { destination = AuthDestination.ProfileSetup(isEditMode = true) },
            onNavigateToPhoneEntry = { destination = AuthDestination.PhoneEntry() },
        )
    }
}

/** Bottom navigation per the design board (screen D): tinted bar, pill indicator, filled icon when selected. */
@Composable
private fun MainBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val selectedInk = MaterialTheme.colorScheme.onPrimaryContainer
    val unselectedInk = Color(0xFF4A564D)
    NavigationBar(containerColor = Color(0xFFEDF0E8), tonalElevation = 0.dp) {
        listOf(
            Triple(MainTab.DASHBOARD, "Dashboard", R.drawable.ic_sports_cricket to R.drawable.ic_sports_cricket_filled),
            Triple(MainTab.MY_LEAGUES, "My Leagues", R.drawable.ic_groups to R.drawable.ic_groups_filled),
            Triple(MainTab.MY_PROFILE, "My Profile", R.drawable.ic_account_circle to R.drawable.ic_account_circle_filled),
        ).forEach { (tab, label, icons) ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        painterResource(if (isSelected) icons.second else icons.first),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = {
                    Text(
                        label,
                        style = TextStyle(
                            fontFamily = InstrumentSansFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp,
                        ),
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = selectedInk,
                    selectedTextColor = selectedInk,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = unselectedInk,
                    unselectedTextColor = unselectedInk,
                ),
            )
        }
    }
}

/** The minimal loading/splash state while the silent app-start check is in flight -- not a design task. */
@Composable
private fun AppStartRoute(onResolved: (AppStartDestination) -> Unit) {
    val viewModel: AppStartViewModel = koinViewModel()
    val resolvedDestination by viewModel.destination.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()

    LaunchedEffect(resolvedDestination) {
        resolvedDestination?.let(onResolved)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isOffline) {
            // The stored session is fine; the backend just couldn't be reached (see AppStartViewModel).
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_cloud_off), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(14.dp))
                Text(
                    "Couldn't connect",
                    style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Check your internet connection and try again.",
                    style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 13.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                CrichereOutlinedButton(text = "Try again", onClick = viewModel::retry)
            }
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PhoneEntryRoute(showLockoutNotice: Boolean, onNavigate: (PhoneEntryNavigationEvent) -> Unit) {
    val viewModel: PhoneEntryViewModel = koinViewModel()
    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event -> onNavigate(event) }
    }
    PhoneEntryScreen(viewModel, showLockoutNotice = showLockoutNotice)
}

/** [onNavigate]'s second argument: true when the bounce-back is the 5-wrong-attempts lockout, not a voluntary "start over". */
@Composable
private fun OtpVerifyRoute(
    phoneNumber: String,
    verificationId: String,
    resendToken: Any?,
    onNavigate: (AuthNavigationEvent, Boolean) -> Unit,
) {
    val viewModel: OtpVerifyViewModel = koinViewModel(
        key = "otp-verify:$phoneNumber:$verificationId",
        parameters = { parametersOf(phoneNumber, verificationId, resendToken) },
    )
    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event -> onNavigate(event, viewModel.state.value.attemptsRemaining == 0) }
    }
    OtpVerifyScreen(viewModel, phoneNumber = phoneNumber)
}

@Composable
private fun ProfileSetupRoute(isEditMode: Boolean, onNavigateToOwnProfile: () -> Unit) {
    val viewModel: ProfileSetupViewModel = koinViewModel(
        key = "profile-setup:$isEditMode",
        parameters = { parametersOf(isEditMode) },
    )
    LaunchedEffect(Unit) { viewModel.retry() }
    ProfileSetupScreen(viewModel, onNavigateToOwnProfile)
}

/**
 * The 3-tab bottom-nav shell (Dashboard, My Leagues, My Profile) that hosts everything reachable
 * after a complete profile. League Detail/Creation/Join/Claim/Screenshot-viewer are pushed as
 * sibling states *above* the tab container -- same plain `sealed interface` +
 * `remember { mutableStateOf(...) }` pattern [AuthNavHost] itself uses, not a new nav-library
 * dependency -- so opening any of them temporarily replaces the tab bar rather than nesting
 * inside it, matching how [AuthDestination.OtpVerify]/[AuthDestination.ProfileSetup] already
 * replace the whole screen rather than living inside some enclosing chrome.
 *
 * My Leagues (Phase 3, see docs/PHASE3.md) is a 3rd persistent tab, not a pushed destination like
 * League Detail/Creation -- it's a routinely-revisited list (closer in spirit to Dashboard) rather
 * than a one-off task, so it earns a permanent tab.
 *
 * [pendingLeagueId] (Phase 3 deep-link target, see [AuthNavHost]'s own doc) is consumed exactly
 * once via the `LaunchedEffect` below, the first time this composable is reached with a non-null
 * value -- it immediately pushes [MainDestination.LeagueDetail] and calls
 * [onPendingLeagueIdConsumed] so a later recomposition (e.g. a config change) doesn't re-push it.
 *
 * The My Profile tab reuses [OwnProfileRoute] completely unchanged from before Phase 2 -- only
 * what hosts it changed, not the screen or its `ViewModel`.
 */
private sealed interface MainDestination {
    data class Tabs(val tab: MainTab) : MainDestination
    data class LeagueDetail(val leagueId: String) : MainDestination
    data class LeagueCreation(val editingLeagueId: String?) : MainDestination
    data class JoinLeagueFlow(val leagueId: String) : MainDestination
    data class ClaimFranchiseFlow(val leagueId: String) : MainDestination
    data class ScreenshotViewer(val imageUrl: String) : MainDestination
    data class AuctionSettings(val leagueId: String) : MainDestination
    data class AuctionLive(val leagueId: String) : MainDestination
    data class ManageRoles(val leagueId: String) : MainDestination
}

@Composable
private fun MainRoute(
    initialTab: MainTab,
    pendingLeagueId: String?,
    onPendingLeagueIdConsumed: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToPhoneEntry: () -> Unit,
) {
    var destination by remember { mutableStateOf<MainDestination>(MainDestination.Tabs(initialTab)) }
    var screenshotBackTarget by remember { mutableStateOf<MainDestination>(MainDestination.Tabs(initialTab)) }

    // Push notifications (docs/PHASE8.md) -- requested once per app entry into the authenticated
    // area, not only right after a fresh login: a no-op if already granted or already permanently
    // denied (Android itself makes repeat requests harmless -- no dialog shows a second time), and
    // this also catches "granted before, revoked later in system settings" without extra state.
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(pendingLeagueId) {
        if (pendingLeagueId != null) {
            destination = MainDestination.LeagueDetail(pendingLeagueId)
            onPendingLeagueIdConsumed()
        }
    }

    when (val current = destination) {
        is MainDestination.Tabs -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                MainBottomBar(selected = current.tab, onSelect = { tab -> destination = MainDestination.Tabs(tab) })
            },
        ) { contentPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                when (current.tab) {
                    MainTab.DASHBOARD -> LeagueDashboardRoute(
                        onOpenLeague = { leagueId -> destination = MainDestination.LeagueDetail(leagueId) },
                        onCreateLeague = { destination = MainDestination.LeagueCreation(editingLeagueId = null) },
                        onOpenProfile = { destination = MainDestination.Tabs(MainTab.MY_PROFILE) },
                    )
                    MainTab.MY_LEAGUES -> MyLeaguesRoute(
                        onOpenLeague = { leagueId -> destination = MainDestination.LeagueDetail(leagueId) },
                        onBrowseLeagues = { destination = MainDestination.Tabs(MainTab.DASHBOARD) },
                        onCreateLeague = { destination = MainDestination.LeagueCreation(editingLeagueId = null) },
                    )
                    MainTab.MY_PROFILE -> OwnProfileRoute(onNavigateToEditProfile, onNavigateToPhoneEntry)
                }
            }
        }

        is MainDestination.LeagueDetail -> LeagueDetailRoute(
            leagueId = current.leagueId,
            onBack = { destination = MainDestination.Tabs(MainTab.DASHBOARD) },
            onEditLeague = { leagueId -> destination = MainDestination.LeagueCreation(editingLeagueId = leagueId) },
            onJoinLeague = { leagueId -> destination = MainDestination.JoinLeagueFlow(leagueId) },
            onClaimFranchise = { leagueId -> destination = MainDestination.ClaimFranchiseFlow(leagueId) },
            onViewScreenshot = { imageUrl ->
                screenshotBackTarget = current
                destination = MainDestination.ScreenshotViewer(imageUrl)
            },
            onAuctionSettings = { leagueId -> destination = MainDestination.AuctionSettings(leagueId) },
            onAuctionLive = { leagueId -> destination = MainDestination.AuctionLive(leagueId) },
            onManageRoles = { leagueId -> destination = MainDestination.ManageRoles(leagueId) },
        )

        is MainDestination.LeagueCreation -> LeagueCreationRoute(
            editingLeagueId = current.editingLeagueId,
            onDone = { savedLeagueId -> destination = MainDestination.LeagueDetail(savedLeagueId) },
            onCancel = { destination = MainDestination.Tabs(MainTab.DASHBOARD) },
        )

        is MainDestination.AuctionSettings -> AuctionSettingsRoute(
            leagueId = current.leagueId,
            onBack = { destination = MainDestination.LeagueDetail(current.leagueId) },
        )

        is MainDestination.AuctionLive -> AuctionLiveRoute(
            leagueId = current.leagueId,
            onBack = { destination = MainDestination.LeagueDetail(current.leagueId) },
        )

        is MainDestination.ManageRoles -> ManageRolesRoute(
            leagueId = current.leagueId,
            onBack = { destination = MainDestination.LeagueDetail(current.leagueId) },
        )

        is MainDestination.JoinLeagueFlow -> JoinLeagueRoute(
            leagueId = current.leagueId,
            onDone = { destination = MainDestination.LeagueDetail(current.leagueId) },
            onCancel = { destination = MainDestination.LeagueDetail(current.leagueId) },
        )

        is MainDestination.ClaimFranchiseFlow -> ClaimFranchiseRoute(
            leagueId = current.leagueId,
            onDone = { destination = MainDestination.LeagueDetail(current.leagueId) },
            onCancel = { destination = MainDestination.LeagueDetail(current.leagueId) },
        )

        is MainDestination.ScreenshotViewer -> ScreenshotViewerRoute(
            imageUrl = current.imageUrl,
            onBack = { destination = screenshotBackTarget },
        )
    }
}

@Composable
private fun OwnProfileRoute(onNavigateToEditProfile: () -> Unit, onNavigateToPhoneEntry: () -> Unit) {
    val viewModel: OwnProfileViewModel = koinViewModel()
    LaunchedEffect(Unit) { viewModel.retry() }
    OwnProfileScreen(viewModel, onNavigateToEditProfile, onNavigateToPhoneEntry)
}

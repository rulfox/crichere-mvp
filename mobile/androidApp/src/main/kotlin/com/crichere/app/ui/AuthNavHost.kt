package com.crichere.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.crichere.app.R
import com.crichere.app.auth.AppStartDestination
import com.crichere.app.auth.AppStartViewModel
import com.crichere.app.auth.AuthNavigationEvent
import com.crichere.app.auth.OtpVerifyViewModel
import com.crichere.app.auth.PhoneEntryNavigationEvent
import com.crichere.app.auth.PhoneEntryViewModel
import com.crichere.app.profile.OwnProfileViewModel
import com.crichere.app.profile.ProfileSetupViewModel
import com.crichere.app.ui.navigation.AppNavigator
import com.crichere.app.ui.navigation.AppRoute
import com.crichere.app.ui.navigation.MainTab
import com.crichere.app.ui.navigation.backTarget
import com.crichere.app.ui.navigation.imeBackGuardDecorator
import com.crichere.app.ui.theme.InstrumentSansFamily
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The whole Android navigation graph, on Navigation 3 (docs/PHASE10.md Part B). One back stack of
 * [AppRoute] keys, saved across configuration changes and process death by [rememberNavBackStack];
 * [NavDisplay] renders the top entry and pops on hardware back / predictive back.
 *
 *  - [AppRoute.Starting] -> PhoneEntry / ProfileSetup / Main: the app-start routing check
 *    (`AppStartViewModel`), routed by its `profileComplete`. Replaces the stack.
 *  - PhoneEntry -> OtpVerify: pushed, so back returns to Phone Entry with the number kept.
 *  - OtpVerify -> ProfileSetup / Main, the lockout bounce-back, and logout: replace the whole stack,
 *    so back can never cross an auth boundary.
 *  - Main: the tab shell ([MainRoute]); everything else (League Detail, Creation, Join, Claim,
 *    Auction, Co-organizers, Screenshot viewer, Edit Profile) is pushed above it and pops back.
 *
 * Back at the stack root (Dashboard, Phone Entry, first-time Profile Setup) isn't handled here, so
 * it falls through to the system and exits. Entry decorators give every entry its own saveable
 * state and its own `ViewModelStore` -- `koinViewModel()` reads `LocalViewModelStoreOwner`, so
 * ViewModels are cleared when their entry is popped -- plus [imeBackGuardDecorator].
 *
 * [NavDisplay] only composes the top entry, so a screen's `LaunchedEffect(Unit) { retry() }` runs
 * again whenever it's returned to: League Detail, Dashboard and My Leagues refresh after an edit,
 * join or claim exactly as before.
 *
 * [pendingDeepLinkLeagueId] (Phase 3, see docs/PHASE3.md): a league id from a
 * `crichere://leagues/{id}` intent, passed in by `MainActivity`. It's held here and only consumed
 * once [AppRoute.Main] is on the stack, so "not logged in -> normal flow first, then the league"
 * works for free: every auth branch funnels into Main eventually.
 */
@Composable
fun AuthNavHost(pendingDeepLinkLeagueId: String? = null) {
    val backStack = rememberNavBackStack(AppRoute.Starting)
    val navigator = remember(backStack) { AppNavigator(backStack) }
    // One-off messages for a league page to show when it is next on screen (U4 K10: a self-revoke on Manage
    // co-organizers pops back here). Not saved: a message lost to process death is only a confirmation.
    val leagueNotices = remember { mutableStateMapOf<String, String>() }
    // Keyed on the incoming parameter (not a bare `remember { }`) so a new crichere://leagues/{id}
    // intent arriving via MainActivity.onNewIntent while the app is already running -- which
    // updates this same composable's `pendingDeepLinkLeagueId` argument on recomposition, not a
    // fresh AuthNavHost call -- actually resets this state to the new id.
    var pendingLeagueId by remember(pendingDeepLinkLeagueId) { mutableStateOf(pendingDeepLinkLeagueId) }
    val inMainArea = AppRoute.Main in backStack

    LaunchedEffect(pendingLeagueId, inMainArea) {
        val leagueId = pendingLeagueId
        if (leagueId != null && inMainArea) {
            navigator.navigate(AppRoute.LeagueDetail(leagueId))
            pendingLeagueId = null
        }
    }

    // Push notifications (docs/PHASE8.md) -- requested once per entry into the authenticated area
    // (keyed on Main being on the stack, so it doesn't re-fire every time Main is returned to): a
    // no-op if already granted or permanently denied, and it also catches "granted before, revoked
    // later in system settings" without extra state.
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(inMainArea) {
        if (inMainArea &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { navigator.back() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            imeBackGuardDecorator(),
        ),
        entryProvider = entryProvider {
            entry<AppRoute.Starting> {
                AppStartRoute { resolved ->
                    navigator.replaceAll(
                        when (resolved) {
                            AppStartDestination.PhoneEntry -> AppRoute.PhoneEntry()
                            AppStartDestination.ProfileSetup -> AppRoute.ProfileSetup(isEditMode = false)
                            AppStartDestination.Main -> AppRoute.Main
                        },
                    )
                }
            }

            entry<AppRoute.PhoneEntry> { route ->
                PhoneEntryRoute(showLockoutNotice = route.lockedOut) { event ->
                    when (event) {
                        is PhoneEntryNavigationEvent.NavigateToOtpVerify -> navigator.navigate(
                            AppRoute.OtpVerify(
                                phoneNumber = event.phoneNumber,
                                verificationId = event.verificationId,
                                resendToken = event.resendToken,
                            ),
                        )
                    }
                }
            }

            entry<AppRoute.OtpVerify> { route ->
                OtpVerifyRoute(
                    phoneNumber = route.phoneNumber,
                    verificationId = route.verificationId,
                    resendToken = route.resendToken,
                ) { event, lockedOut ->
                    when (event) {
                        AuthNavigationEvent.NavigateToProfileSetup -> navigator.replaceAll(AppRoute.ProfileSetup(isEditMode = false))
                        AuthNavigationEvent.NavigateToOwnProfile -> navigator.replaceAll(AppRoute.Main)
                        // The on-screen back/"Edit" behaves like hardware back (Phone Entry keeps
                        // the typed number); the lockout always gets a fresh Phone Entry with its notice.
                        AuthNavigationEvent.NavigateToPhoneEntry ->
                            if (lockedOut || !navigator.back()) navigator.replaceAll(AppRoute.PhoneEntry(lockedOut = lockedOut))
                    }
                }
            }

            entry<AppRoute.ProfileSetup> { route ->
                ProfileSetupRoute(
                    isEditMode = route.isEditMode,
                    // Design update #6: only Edit profile (C1-edit) has a back arrow; first-time setup (C1) has none.
                    onBack = if (route.isEditMode) ({ navigator.back() }) else null,
                ) {
                    // An edit (pushed above Main from My Profile) pops back there; first-time setup
                    // completion has nothing below it, so it starts the main area fresh.
                    if (route.isEditMode) navigator.back() else navigator.replaceAll(AppRoute.Main)
                }
            }

            entry<AppRoute.Main> {
                MainRoute(
                    onOpenLeague = { leagueId -> navigator.navigate(AppRoute.LeagueDetail(leagueId)) },
                    onCreateLeague = { navigator.navigate(AppRoute.LeagueCreation(editingLeagueId = null)) },
                    onNavigateToEditProfile = { navigator.navigate(AppRoute.ProfileSetup(isEditMode = true)) },
                    onNavigateToPhoneEntry = { navigator.replaceAll(AppRoute.PhoneEntry()) },
                    onViewPhoto = { photoUrl -> navigator.navigate(AppRoute.ScreenshotViewer(photoUrl, title = "Profile photo")) },
                )
            }

            entry<AppRoute.LeagueDetail> { route ->
                LeagueDetailRoute(
                    leagueId = route.leagueId,
                    notice = leagueNotices[route.leagueId],
                    onNoticeShown = { leagueNotices.remove(route.leagueId) },
                    onBack = { navigator.back() },
                    onEditLeague = { leagueId -> navigator.navigate(AppRoute.LeagueCreation(editingLeagueId = leagueId)) },
                    onJoinLeague = { leagueId -> navigator.navigate(AppRoute.JoinLeague(leagueId)) },
                    onClaimFranchise = { leagueId -> navigator.navigate(AppRoute.ClaimFranchise(leagueId)) },
                    onViewScreenshot = { imageUrl -> navigator.navigate(AppRoute.ScreenshotViewer(imageUrl)) },
                    onAuctionSettings = { leagueId -> navigator.navigate(AppRoute.AuctionSettings(leagueId)) },
                    onAuctionLive = { leagueId -> navigator.navigate(AppRoute.AuctionLive(leagueId)) },
                    onManageRoles = { leagueId -> navigator.navigate(AppRoute.ManageRoles(leagueId)) },
                )
            }

            entry<AppRoute.LeagueCreation> { route ->
                LeagueCreationRoute(
                    editingLeagueId = route.editingLeagueId,
                    // An edit returns to the League Detail it was opened from (which refetches); a
                    // new league replaces the creation form with its own detail screen.
                    onDone = { savedLeagueId ->
                        if (route.editingLeagueId != null) navigator.back() else navigator.replaceTop(AppRoute.LeagueDetail(savedLeagueId))
                    },
                    onCancel = { navigator.back() },
                )
            }

            entry<AppRoute.AuctionSettings> { route ->
                AuctionSettingsRoute(leagueId = route.leagueId, onBack = { navigator.back() })
            }

            entry<AppRoute.AuctionLive> { route ->
                AuctionLiveRoute(leagueId = route.leagueId, onBack = { navigator.back() })
            }

            entry<AppRoute.ManageRoles> { route ->
                ManageRolesRoute(
                    leagueId = route.leagueId,
                    onBack = { navigator.back() },
                    onAccessRevoked = { leagueName ->
                        leagueNotices[route.leagueId] = "You're no longer a co-organizer of $leagueName."
                        navigator.back()
                    },
                )
            }

            entry<AppRoute.JoinLeague> { route ->
                JoinLeagueRoute(leagueId = route.leagueId, onDone = { navigator.back() }, onCancel = { navigator.back() })
            }

            entry<AppRoute.ClaimFranchise> { route ->
                ClaimFranchiseRoute(leagueId = route.leagueId, onDone = { navigator.back() }, onCancel = { navigator.back() })
            }

            entry<AppRoute.ScreenshotViewer> { route ->
                ScreenshotViewerRoute(imageUrl = route.imageUrl, onBack = { navigator.back() }, title = route.title)
            }
        },
    )
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
private fun ProfileSetupRoute(isEditMode: Boolean, onBack: (() -> Unit)?, onNavigateToOwnProfile: () -> Unit) {
    val viewModel: ProfileSetupViewModel = koinViewModel(
        key = "profile-setup:$isEditMode",
        parameters = { parametersOf(isEditMode) },
    )
    LaunchedEffect(Unit) { viewModel.retry() }
    ProfileSetupScreen(viewModel, onNavigateToOwnProfile, onBack)
}

/**
 * The 3-tab bottom-nav shell (Dashboard, My Leagues, My Profile) -- the [AppRoute.Main] entry.
 * Pushed destinations (League Detail etc.) are separate entries above it on the back stack, so
 * opening one replaces the tab bar rather than nesting inside it, matching iOS (docs/PHASE9.md).
 *
 * The selected tab is `rememberSaveable` inside this entry: it survives pushes/pops (back from a
 * League Detail opened from My Leagues lands on My Leagues), rotation and process death. Back on
 * My Leagues / My Profile returns to Dashboard ([MainTab.backTarget]); on Dashboard this handler is
 * disabled, so back falls through and exits.
 */
@Composable
private fun MainRoute(
    onOpenLeague: (String) -> Unit,
    onCreateLeague: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToPhoneEntry: () -> Unit,
    onViewPhoto: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }
    val backTarget = tab.backTarget()
    BackHandler(enabled = backTarget != null) { backTarget?.let { tab = it } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { MainBottomBar(selected = tab, onSelect = { tab = it }) },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (tab) {
                MainTab.DASHBOARD -> LeagueDashboardRoute(
                    onOpenLeague = onOpenLeague,
                    onCreateLeague = onCreateLeague,
                    onOpenProfile = { tab = MainTab.MY_PROFILE },
                )
                MainTab.MY_LEAGUES -> MyLeaguesRoute(
                    onOpenLeague = onOpenLeague,
                    onBrowseLeagues = { tab = MainTab.DASHBOARD },
                    onCreateLeague = onCreateLeague,
                )
                MainTab.MY_PROFILE -> OwnProfileRoute(
                    onNavigateToEditProfile = onNavigateToEditProfile,
                    onNavigateToPhoneEntry = onNavigateToPhoneEntry,
                    onViewPhoto = onViewPhoto,
                )
            }
        }
    }
}

@Composable
private fun OwnProfileRoute(onNavigateToEditProfile: () -> Unit, onNavigateToPhoneEntry: () -> Unit, onViewPhoto: (String) -> Unit) {
    val viewModel: OwnProfileViewModel = koinViewModel()
    LaunchedEffect(Unit) { viewModel.retry() }
    OwnProfileScreen(viewModel, onNavigateToEditProfile, onNavigateToPhoneEntry, onViewPhoto)
}

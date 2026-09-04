package com.crichere.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 */
private sealed interface AuthDestination {
    data object Starting : AuthDestination
    data object PhoneEntry : AuthDestination
    data class OtpVerify(val phoneNumber: String, val verificationId: String, val resendToken: Any?) : AuthDestination
    data class ProfileSetup(val isEditMode: Boolean) : AuthDestination
    data object Main : AuthDestination
}

@Composable
fun AuthNavHost() {
    var destination by remember { mutableStateOf<AuthDestination>(AuthDestination.Starting) }

    when (val current = destination) {
        AuthDestination.Starting -> AppStartRoute { resolved ->
            destination = when (resolved) {
                AppStartDestination.PhoneEntry -> AuthDestination.PhoneEntry
                AppStartDestination.ProfileSetup -> AuthDestination.ProfileSetup(isEditMode = false)
                AppStartDestination.Main -> AuthDestination.Main
            }
        }

        is AuthDestination.PhoneEntry -> PhoneEntryRoute { event ->
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
        ) { event ->
            destination = when (event) {
                AuthNavigationEvent.NavigateToProfileSetup -> AuthDestination.ProfileSetup(isEditMode = false)
                AuthNavigationEvent.NavigateToOwnProfile -> AuthDestination.Main
                AuthNavigationEvent.NavigateToPhoneEntry -> AuthDestination.PhoneEntry
            }
        }

        is AuthDestination.ProfileSetup -> ProfileSetupRoute(isEditMode = current.isEditMode) {
            destination = AuthDestination.Main
        }

        AuthDestination.Main -> MainRoute(
            onNavigateToEditProfile = { destination = AuthDestination.ProfileSetup(isEditMode = true) },
            onNavigateToPhoneEntry = { destination = AuthDestination.PhoneEntry },
        )
    }
}

/** The minimal loading/splash state while the silent app-start check is in flight -- not a design task. */
@Composable
private fun AppStartRoute(onResolved: (AppStartDestination) -> Unit) {
    val viewModel: AppStartViewModel = koinViewModel()
    val resolvedDestination by viewModel.destination.collectAsStateWithLifecycle()

    LaunchedEffect(resolvedDestination) {
        resolvedDestination?.let(onResolved)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PhoneEntryRoute(onNavigate: (PhoneEntryNavigationEvent) -> Unit) {
    val viewModel: PhoneEntryViewModel = koinViewModel()
    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event -> onNavigate(event) }
    }
    PhoneEntryScreen(viewModel)
}

@Composable
private fun OtpVerifyRoute(
    phoneNumber: String,
    verificationId: String,
    resendToken: Any?,
    onNavigate: (AuthNavigationEvent) -> Unit,
) {
    val viewModel: OtpVerifyViewModel = koinViewModel(
        key = "otp-verify:$phoneNumber:$verificationId",
        parameters = { parametersOf(phoneNumber, verificationId, resendToken) },
    )
    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event -> onNavigate(event) }
    }
    OtpVerifyScreen(viewModel)
}

@Composable
private fun ProfileSetupRoute(isEditMode: Boolean, onNavigateToOwnProfile: () -> Unit) {
    val viewModel: ProfileSetupViewModel = koinViewModel(
        key = "profile-setup:$isEditMode",
        parameters = { parametersOf(isEditMode) },
    )
    ProfileSetupScreen(viewModel, onNavigateToOwnProfile)
}

/**
 * The 2-tab bottom-nav shell (Dashboard, My Profile) that hosts everything reachable after a
 * complete profile. League Detail/Creation are pushed as sibling states *above* the tab
 * container -- same plain `sealed interface` + `remember { mutableStateOf(...) }` pattern
 * [AuthNavHost] itself uses, not a new nav-library dependency -- so opening a league or creating
 * one temporarily replaces the tab bar rather than nesting inside it, matching how
 * [AuthDestination.OtpVerify]/[AuthDestination.ProfileSetup] already replace the whole screen
 * rather than living inside some enclosing chrome.
 *
 * The My Profile tab reuses [OwnProfileRoute] completely unchanged from before Phase 2 -- only
 * what hosts it changed, not the screen or its `ViewModel`.
 */
private sealed interface MainDestination {
    data class Tabs(val tab: MainTab) : MainDestination
    data class LeagueDetail(val leagueId: String) : MainDestination
    data class LeagueCreation(val editingLeagueId: String?) : MainDestination
}

private enum class MainTab { DASHBOARD, MY_PROFILE }

@Composable
private fun MainRoute(onNavigateToEditProfile: () -> Unit, onNavigateToPhoneEntry: () -> Unit) {
    var destination by remember { mutableStateOf<MainDestination>(MainDestination.Tabs(MainTab.DASHBOARD)) }

    when (val current = destination) {
        is MainDestination.Tabs -> Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = current.tab == MainTab.DASHBOARD,
                        onClick = { destination = MainDestination.Tabs(MainTab.DASHBOARD) },
                        icon = {},
                        label = { Text("Dashboard") },
                    )
                    NavigationBarItem(
                        selected = current.tab == MainTab.MY_PROFILE,
                        onClick = { destination = MainDestination.Tabs(MainTab.MY_PROFILE) },
                        icon = {},
                        label = { Text("My Profile") },
                    )
                }
            },
        ) { contentPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                when (current.tab) {
                    MainTab.DASHBOARD -> LeagueDashboardRoute(
                        onOpenLeague = { leagueId -> destination = MainDestination.LeagueDetail(leagueId) },
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
        )

        is MainDestination.LeagueCreation -> LeagueCreationRoute(
            editingLeagueId = current.editingLeagueId,
            onDone = { savedLeagueId -> destination = MainDestination.LeagueDetail(savedLeagueId) },
            onCancel = { destination = MainDestination.Tabs(MainTab.DASHBOARD) },
        )
    }
}

@Composable
private fun OwnProfileRoute(onNavigateToEditProfile: () -> Unit, onNavigateToPhoneEntry: () -> Unit) {
    val viewModel: OwnProfileViewModel = koinViewModel()
    OwnProfileScreen(viewModel, onNavigateToEditProfile, onNavigateToPhoneEntry)
}

package com.crichere.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
 * The full navigation contract, real end to end as of Task 7:
 *  - [Starting] -> [PhoneEntry] / [ProfileSetup] / [OwnProfile]: the app-start routing check
 *    (`AppStartViewModel`) this task adds -- a silent `/auth/refresh`, routed by its
 *    `profileComplete`, replacing Task 5/6's hardcoded `PhoneEntry` initial value.
 *  - [PhoneEntry] -> [OtpVerify]: real (Task 6).
 *  - [OtpVerify] -> [ProfileSetup] / [OwnProfile]: real, driven by [AuthNavigationEvent] (Task 7
 *    wires up the two destinations Task 6 left as placeholders).
 *  - [OtpVerify] -> [PhoneEntry]: real (Task 6, the 5th-wrong-attempt forced bounce-back).
 *  - [ProfileSetup] -> [OwnProfile]: real (Task 7, on a save that completes the profile).
 *  - [OwnProfile] -> [ProfileSetup] (`isEditMode = true`) / [PhoneEntry] (logout): real (Task 7).
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
    data object OwnProfile : AuthDestination
}

@Composable
fun AuthNavHost() {
    var destination by remember { mutableStateOf<AuthDestination>(AuthDestination.Starting) }

    when (val current = destination) {
        AuthDestination.Starting -> AppStartRoute { resolved ->
            destination = when (resolved) {
                AppStartDestination.PhoneEntry -> AuthDestination.PhoneEntry
                AppStartDestination.ProfileSetup -> AuthDestination.ProfileSetup(isEditMode = false)
                AppStartDestination.OwnProfile -> AuthDestination.OwnProfile
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
                AuthNavigationEvent.NavigateToOwnProfile -> AuthDestination.OwnProfile
                AuthNavigationEvent.NavigateToPhoneEntry -> AuthDestination.PhoneEntry
            }
        }

        is AuthDestination.ProfileSetup -> ProfileSetupRoute(isEditMode = current.isEditMode) {
            destination = AuthDestination.OwnProfile
        }

        AuthDestination.OwnProfile -> OwnProfileRoute(
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

@Composable
private fun OwnProfileRoute(onNavigateToEditProfile: () -> Unit, onNavigateToPhoneEntry: () -> Unit) {
    val viewModel: OwnProfileViewModel = koinViewModel()
    OwnProfileScreen(viewModel, onNavigateToEditProfile, onNavigateToPhoneEntry)
}

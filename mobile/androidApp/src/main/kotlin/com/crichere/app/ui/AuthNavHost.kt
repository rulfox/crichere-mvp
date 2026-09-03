package com.crichere.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.crichere.app.auth.AuthNavigationEvent
import com.crichere.app.auth.OtpVerifyViewModel
import com.crichere.app.auth.PhoneEntryNavigationEvent
import com.crichere.app.auth.PhoneEntryViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The navigation contract this task wires up for real, and the one it stubs for Task 7:
 *  - [PhoneEntry] -> [OtpVerify]: real, both screens exist here.
 *  - [OtpVerify] -> [ProfileSetup] / [OwnProfile]: destinations exist as placeholders
 *    (`TODO(Task 7)` in `ProfileSetupScreen.kt`/`OwnProfileScreen.kt`) -- routing itself is real,
 *    driven by the real `AuthNavigationEvent`/`profileComplete` signal from
 *    [OtpVerifyViewModel].
 *  - [OtpVerify] -> [PhoneEntry]: real, both ends exist (the 5th-wrong-attempt forced bounce-back).
 *
 * A plain `remember { mutableStateOf(...) }` state switcher rather than Jetpack Navigation
 * Compose: this task's UI surface is small (four destinations, one linear flow with one branch),
 * so a new nav-library dependency/version to research wasn't worth it here. A real navigation
 * library remains a reasonable thing for a later task to introduce once the app's back-stack
 * needs actually justify one.
 */
private sealed interface AuthDestination {
    data object PhoneEntry : AuthDestination
    data class OtpVerify(val phoneNumber: String, val verificationId: String, val resendToken: Any?) : AuthDestination
    data object ProfileSetup : AuthDestination
    data object OwnProfile : AuthDestination
}

@Composable
fun AuthNavHost() {
    var destination by remember { mutableStateOf<AuthDestination>(AuthDestination.PhoneEntry) }

    when (val current = destination) {
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
                AuthNavigationEvent.NavigateToProfileSetup -> AuthDestination.ProfileSetup
                AuthNavigationEvent.NavigateToOwnProfile -> AuthDestination.OwnProfile
                AuthNavigationEvent.NavigateToPhoneEntry -> AuthDestination.PhoneEntry
            }
        }

        AuthDestination.ProfileSetup -> ProfileSetupScreen()
        AuthDestination.OwnProfile -> OwnProfileScreen()
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

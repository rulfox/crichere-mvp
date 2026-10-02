package com.crichere.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Every Android destination, as a Navigation 3 back-stack key (docs/PHASE10.md Part B). One sealed
 * hierarchy for the whole app -- auth flow and the post-login area share a single back stack, the
 * same single-stack shape iOS uses (docs/PHASE9.md).
 *
 * `@Serializable` because `rememberNavBackStack` saves the stack across configuration changes and
 * process death. Keys hold only ids and display values, never tokens: the saved stack lives in
 * the Activity's saved-instance state.
 */
@Serializable
sealed interface AppRoute : NavKey {

    /** The silent app-start routing check (`AppStartViewModel`); always replaced once it resolves. */
    @Serializable
    data object Starting : AppRoute

    /** [lockedOut]: arrived via the 5-wrong-attempts bounce-back, so show the lockout notice (design B6). */
    @Serializable
    data class PhoneEntry(val lockedOut: Boolean = false) : AppRoute

    /**
     * [resendToken] is Firebase's `ForceResendingToken`, which isn't serializable: after a
     * process-death restore it comes back `null`, and "Resend" starts a fresh verification instead
     * of a forced resend. `verificationId` alone is useless without the SMS code.
     */
    @Serializable
    data class OtpVerify(
        val phoneNumber: String,
        val verificationId: String,
        @Transient val resendToken: Any? = null,
    ) : AppRoute

    @Serializable
    data class ProfileSetup(val isEditMode: Boolean) : AppRoute

    /**
     * The 3-tab bottom-nav shell. The selected tab is saveable state inside this entry, not part of
     * the key, so popping back here (e.g. from Edit Profile) lands on whichever tab was showing.
     */
    @Serializable
    data object Main : AppRoute

    @Serializable
    data class LeagueDetail(val leagueId: String) : AppRoute

    /** [editingLeagueId] `null` = create a new league. */
    @Serializable
    data class LeagueCreation(val editingLeagueId: String?) : AppRoute

    @Serializable
    data class JoinLeague(val leagueId: String) : AppRoute

    @Serializable
    data class ClaimFranchise(val leagueId: String) : AppRoute

    @Serializable
    data class ScreenshotViewer(val imageUrl: String, val title: String = "Payment screenshot") : AppRoute

    @Serializable
    data class AuctionSettings(val leagueId: String) : AppRoute

    @Serializable
    data class AuctionLive(val leagueId: String) : AppRoute

    @Serializable
    data class ManageRoles(val leagueId: String) : AppRoute
}

internal enum class MainTab { DASHBOARD, MY_LEAGUES, MY_PROFILE }

/**
 * Where hardware back goes from this tab (owner decision, docs/PHASE10.md): the other tabs return
 * to Dashboard; `null` on Dashboard itself means "don't handle", so back falls through and exits.
 */
internal fun MainTab.backTarget(): MainTab? = if (this == MainTab.DASHBOARD) null else MainTab.DASHBOARD

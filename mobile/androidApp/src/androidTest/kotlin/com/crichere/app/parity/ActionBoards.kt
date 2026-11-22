package com.crichere.app.parity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import com.crichere.app.auth.FakeAuthRepository
import com.crichere.app.auth.OtpVerifyState
import com.crichere.app.auth.OtpVerifyViewModel
import com.crichere.app.auth.PhoneVerificationHandle
import com.crichere.app.league.AuctionSettingsViewModel
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.ui.AuctionSettingsScreen
import com.crichere.app.ui.OtpVerifyScreen
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.crichere.app.league.AwardDraft
import com.crichere.app.ui.AppStartContent

// Boards reached the way a person reaches them: another board's screen, then a tap or a scroll (the screen
// keeps that state to itself). Scrolls put the target at the top, like the iOS gallery's `startAt`.

private fun ComposeTestRule.scrollToTop(text: String) {
    val target = onAllNodesWithText(text, useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot.top
    val scroller = onNode(hasScrollAction())
    val top = scroller.fetchSemanticsNode().boundsInRoot.top
    scroller.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, target - top) }
}

private fun ComposeTestRule.scrollToEnd() {
    onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
}

private fun ComposeTestRule.payWithoutUpiApp() {
    // The emulator has no UPI app, so this lands on the "no UPI app" note; then its Copy chip shows "Copied".
    onNodeWithText("Pay via UPI").performClick()
    waitForIdle()
    onNodeWithText("Copy").performClick()
}

private class ActionBoard(val content: @Composable () -> Unit, val action: ComposeTestRule.() -> Unit)

private val actionBoards: Map<String, ActionBoard> by lazy {
    val shell = shellBoards()
    val forced = forcedBoards()
    val flow = joinBoards()
    val claims = claimBoards()
    mapOf(
        "A2" to ActionBoard(shell.getValue("A1")) { onNode(hasSetTextAction()).performClick() },
        "B1" to ActionBoard(forced.getValue("B1")) { onNode(hasSetTextAction()).performClick() },
        // The avatar has no label of its own: tap 60 dp above the name, the middle of the 104 dp photo.
        "N2" to ActionBoard(shell.getValue("N1")) {
            val name = onNodeWithText("Aarav Pawar").fetchSemanticsNode().boundsInRoot
            onRoot().performTouchInput { click(Offset(name.center.x, name.top - 60.dp.toPx())) }
        },
        "N-logout" to ActionBoard(shell.getValue("N1")) { onNodeWithText("Log out").performClick() },
        "F2" to ActionBoard(flow.getValue("F1")) { payWithoutUpiApp() },
        "G3-noupi" to ActionBoard(claims.getValue("G1-initials")) { payWithoutUpiApp() },
        // iOS scrolls the UPI field to the top, which runs past the end of the form: the end of the form.
        "I7-fees" to ActionBoard(shell.getValue("I7")) { scrollToEnd() },
        "I8" to ActionBoard(creation(creationState(dirty = true, name = "Kolhapur Premier League", awards = listOf(
            AwardDraft(name = "Winners", cashAmount = "25000", hasTrophy = true),
            AwardDraft(name = "Runners-up"),
        )))) { scrollToTop("Awards") },
        // J9: the screen reads a live ViewModel (set to the scheduled board) so clearing the time empties the field.
        "J9-cleared" to ActionBoard({
            val viewModel = forced(auctionState()) { AuctionSettingsViewModel("league-kpl", FakeLeagueRepository()) }
            val state by viewModel.state.collectAsState()
            AuctionSettingsScreen(state = state, viewModel = viewModel, onBack = {})
        }) { onNodeWithContentDescription("Clear auction time").performClick() },
        // B5b: a resend that goes through -- one of three used, the cooldown restarted, "New code sent".
        "B5b" to ActionBoard({
            val viewModel = forced(OtpVerifyState(cooldownSecondsRemaining = 0, canResend = true, maxResends = 3)) {
                OtpVerifyViewModel("+919876543210", "verification-id", null,
                    FakeAuthRepository().apply { nextSendOtpResult = Result.success(PhoneVerificationHandle("verification-id-2")) })
            }
            OtpVerifyScreen(viewModel, phoneNumber = "+919876543210")
        }) { onNodeWithText("Resend code", substring = true).performClick() },
        // H2: pinched to about 2.4x, then panned 120 right and 60 up, like the iOS board's initial scale and offset.
        "H2" to ActionBoard(shell.getValue("H1")) {
            onRoot().performTouchInput {
                pinch(center - Offset(100f, 0f), center - Offset(240f, 0f), center + Offset(100f, 0f), center + Offset(240f, 0f), durationMillis = 600)
            }
            waitForIdle()
            onRoot().performTouchInput { swipe(center, center + Offset(120.dp.toPx(), -60.dp.toPx()), durationMillis = 600) }
        },
        "I4-selected" to ActionBoard(creation(filledCreation())) { scrollToTop("Ground") },
        "I9" to ActionBoard(creation(creationState(dirty = true, validation = true, name = "Kolhapur Premier League"))) { scrollToTop("State") },
    )
}

private fun filledCreation() = creationState(dirty = true, name = "Kolhapur Premier League", located = true, ground = true,
    startsOn = "2026-10-12", format = "T20", fees = true, upi = "sachin@okaxis", images = true)

/** Board → its screen, for [ParityBoards.all]. */
fun actionBoardContents(): Map<String, @Composable () -> Unit> =
    actionBoards.mapValues { it.value.content } + mapOf(
        "I1-filled" to creation(filledCreation()),
        "I3" to creation(creationState(dirty = true, name = "Kolhapur Premier League", located = true, ground = true, startsOn = "2026-10-12",
            bannerProgress = 0.42f, saving = true, images = true)),
        "S1" to { AppStartContent(isOffline = false, onRetry = {}) },
        "S2" to { AppStartContent(isOffline = true, onRetry = {}) },
    )

fun hasBoardAction(board: String): Boolean = board in actionBoards

/** What to do on [board]'s screen before the capture, if anything. */
fun ComposeTestRule.runBoardAction(board: String) {
    actionBoards[board]?.action?.invoke(this)
}

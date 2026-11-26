package com.crichere.app.parity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import com.crichere.app.league.ClaimFranchiseState
import com.crichere.app.league.JoinLeagueState
import kotlinx.coroutines.flow.MutableStateFlow

// Join and Claim draw the payment-proof thumbnail only from the photo the user just picked, so these boards
// pick one: the photo picker answers with the sample image, the check sheet is ticked and accepted, and the
// screen's state then moves on to the board's (uploading, failed, attached, submitting).

/** Answers every launch (the photo picker) with the sample image, as if the user had picked it. */
private val pickerAnswersSample = object : ActivityResultRegistryOwner {
    override val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.parse(SamplePhoto.url)))
        }
    }
}

private fun <S> pickedBoard(start: S, end: S, chooseLabel: String, screen: (S) -> @Composable () -> Unit): Pair<@Composable () -> Unit, ComposeTestRule.() -> Unit> {
    val holder = MutableStateFlow(start)
    val content: @Composable () -> Unit = {
        val state by holder.collectAsState()
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerAnswersSample) { screen(state)() }
    }
    val action: ComposeTestRule.() -> Unit = {
        onNodeWithText(chooseLabel).performClick()
        waitForIdle()
        Thread.sleep(500)  // the picked photo decodes off the main thread
        waitForIdle()
        onAllNodes(isToggleable()).fetchSemanticsNodes().indices.forEach { onAllNodes(isToggleable())[it].performClick() }
        onNodeWithText("Use this").performClick()
        waitForIdle()
        holder.value = end
    }
    return content to action
}

private fun joinPicked(end: JoinLeagueState) = pickedBoard(joinState(), end, "Choose screenshot") { join(it) }
private fun claimPicked(end: ClaimFranchiseState, logo: Boolean = true) =
    pickedBoard(claimState(name = "Kolhapur Kings", logo = logo), end, "Attach payment screenshot") { claim(it) }

internal val pickedProofBoards: Map<String, Pair<@Composable () -> Unit, ComposeTestRule.() -> Unit>> by lazy {
    mapOf(
        "F5" to joinPicked(joinState(uploading = 0.42f)),
        "F5-attached" to joinPicked(joinState(attached = true)),
        "F5-failed" to joinPicked(joinState(failed = true)),
        "F7" to joinPicked(joinState(attached = true, errorTitle = "Couldn't join", error = "Registration just filled up. Ask the organizer if a spot opens.")),
        "G3" to claimPicked(claimState(name = "Kolhapur Kings", logo = true, attached = true)),
        "G3-uploading" to claimPicked(claimState(name = "Kolhapur Kings", logo = true, uploadingLogo = true, uploading = 0.42f)),
        "G3-failed" to claimPicked(claimState(name = "Kolhapur Kings", failed = true), logo = false),
        "G5" to claimPicked(claimState(name = "Kolhapur Kings", logo = true, attached = true, submitting = true)),
    )
}

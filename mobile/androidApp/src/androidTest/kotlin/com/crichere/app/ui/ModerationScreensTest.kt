package com.crichere.app.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueStatus
import com.crichere.app.moderation.BlockTarget
import com.crichere.app.moderation.BlockViewModel
import com.crichere.app.moderation.BlockedPeopleViewModel
import com.crichere.app.moderation.BlockedPersonDto
import com.crichere.app.moderation.FakeModerationRepository
import com.crichere.app.moderation.ReportReason
import com.crichere.app.moderation.ReportResult
import com.crichere.app.moderation.ReportSubject
import com.crichere.app.moderation.ReportTargetType
import com.crichere.app.moderation.ReportViewModel
import org.junit.Rule
import org.junit.Test

/** Boards R1 to R13 of docs/PHASE18.md, asserted against the semantics tree. */
class ModerationScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val person = ReportSubject(ReportTargetType.USER, "user-2", "Arjun Patil", "Player in Test League", "league-1")

    @Test
    fun rowMenuOffersReportAndBlock() {
        var reported = false
        composeRule.setContent {
            ModerationRowMenu("Arjun Patil", blocked = false, onReport = { reported = true }, onBlock = {}, onUnblock = {})
        }
        composeRule.onNodeWithContentDescription("More options for Arjun Patil").performClick()
        composeRule.onNodeWithText("Block").assertExists()
        composeRule.onNodeWithText("Report").performClick()
        composeRule.waitForIdle()
        assert(reported)
    }

    @Test
    fun blockedRowMenuOffersUnblock() {
        composeRule.setContent {
            ModerationRowMenu("Arjun Patil", blocked = true, onReport = {}, onBlock = {}, onUnblock = {})
        }
        composeRule.onNodeWithContentDescription("More options for Arjun Patil").performClick()
        composeRule.onNodeWithText("Unblock").assertExists()
    }

    @Test
    fun reportNeedsAReasonThenSends() {
        val repository = FakeModerationRepository()
        val viewModel = ReportViewModel(repository).apply { open(person) }
        composeRule.setContent { ReportSheet(viewModel) }

        composeRule.onNodeWithText("Next").assertIsNotEnabled()
        composeRule.onNodeWithText("Hate or harassment").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Send report").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Thanks. Our team will review this.").assertExists()
        assert(repository.reports.single().reason == ReportReason.HATE_OR_HARASSMENT)
        assert(repository.reports.single().targetUserId == "user-2")
    }

    @Test
    fun aDuplicateReportSaysSo() {
        val repository = FakeModerationRepository(nextReport = { ReportResult.AlreadyReported })
        val viewModel = ReportViewModel(repository).apply {
            open(person)
            selectReason(ReportReason.HATE_OR_HARASSMENT)
            next()
        }
        composeRule.setContent { ReportSheet(viewModel) }

        composeRule.onNodeWithText("Send report").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("You've already reported this. Our team will review it.").assertExists()
    }

    @Test
    fun blockAsksFirstThenBlocks() {
        val repository = FakeModerationRepository()
        val viewModel = BlockViewModel(repository).apply { askToBlock(BlockTarget("user-2", "Arjun Patil")) }
        composeRule.setContent { BlockDialog(viewModel, coOrganizerOfThisLeague = { false }) }

        composeRule.onNodeWithText("They aren't told.").assertExists()
        composeRule.onNodeWithText("Block").performClick()
        composeRule.waitForIdle()

        assert("user-2" in repository.blockedIds.value)
    }

    @Test
    fun blockedPeopleListsAndUnblocks() {
        val repository = FakeModerationRepository(
            nextBlocks = { Result.success(listOf(BlockedPersonDto(userId = "user-2", name = "Arjun Patil", blockedAt = "2026-11-06T00:00:00Z"))) },
        )
        val viewModel = BlockedPeopleViewModel(repository)
        composeRule.setContent { BlockedPeopleScreen(onBack = {}, viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Arjun Patil").assertExists()
        composeRule.onNodeWithText("Unblock").performClick()
        composeRule.waitForIdle()

        assert("user-2" !in repository.blockedIds.value)
    }

    @Test
    fun suspendedScreenShowsTheMaskedNumberAndLeadsBackToSignIn() {
        var back = false
        composeRule.setContent { SuspendedScreen("+91 ••••• •8484", onBackToSignIn = { back = true }) }

        composeRule.onNodeWithText("Your account is suspended").assertExists()
        composeRule.onNodeWithText("+91 ••••• •8484").assertExists()
        composeRule.onNodeWithText("Back to sign in").performClick()
        composeRule.waitForIdle()
        assert(back)
    }

    @Test
    fun blockedOrganizerNoticeWithoutANameStillReads() {
        composeRule.setContent { BlockedOrganizerNotice("this organizer", onUnblock = {}, onHome = {}) }

        composeRule.onNodeWithText("This person organizes this league", substring = true).assertExists()
    }

    @Test
    fun blockOrganizerLinkSitsBesideReportLeagueAndTaps() {
        var blocked = false
        composeRule.setContent { LeagueEndLinks(onReportLeague = {}, onBlockOrganizer = { blocked = true }) }

        composeRule.onNodeWithText("Report league").assertExists()
        composeRule.onNodeWithText("Block organizer").performClick()
        composeRule.waitForIdle()
        assert(blocked)
    }

    @Test
    fun coOrganizerSeesOnlyBlockOrganizer() {
        composeRule.setContent { LeagueEndLinks(onReportLeague = null, onBlockOrganizer = {}) }

        composeRule.onNodeWithText("Report league").assertDoesNotExist()
        composeRule.onNodeWithText("Block organizer").assertExists()
    }

    @Test
    fun blockOrganizerIsOfferedOnlyWhenItCanWork() {
        val league = LeagueDto(
            id = "league-1", organizerUserId = "organizer-1", name = "Riverside", state = "Karnataka",
            district = "Bengaluru Urban", groundId = "g1", groundName = "Ground", startsOn = "2026-10-04",
            status = LeagueStatus.ANNOUNCED,
        )

        assert(canBlockOrganizer(league, "someone", emptySet()))
        assert(!canBlockOrganizer(league, "organizer-1", emptySet()))
        assert(!canBlockOrganizer(league, "someone", setOf("organizer-1")))
        assert(!canBlockOrganizer(league.copy(organizerDeleted = true), "someone", emptySet()))
    }
}

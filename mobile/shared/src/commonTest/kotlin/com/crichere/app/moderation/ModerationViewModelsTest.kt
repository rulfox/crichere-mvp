@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.moderation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModerationViewModelsTest {

    private val person = ReportSubject(ReportTargetType.USER, "u1", "Vishnu Menon", "Player in Kochi Premier League", "l1")
    private val league = ReportSubject(ReportTargetType.LEAGUE, "l1", "Kochi Premier League", "Name, logo, banner and description")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun reportVm(repository: FakeModerationRepository = FakeModerationRepository()) = ReportViewModel(repository)

    @Test
    fun `a person and a league offer their own reasons`() {
        val vm = reportVm()
        vm.open(person)
        assertTrue(ReportReason.UNDER_18 in vm.state.value.reasons)
        assertFalse(ReportReason.BETTING_OR_GAMBLING in vm.state.value.reasons)
        vm.open(league)
        assertTrue(ReportReason.BETTING_OR_GAMBLING in vm.state.value.reasons)
    }

    @Test
    fun `next needs a reason, and back keeps the pick`() {
        val vm = reportVm()
        vm.open(person)
        vm.next()
        assertEquals(ReportStep.REASON, vm.state.value.step)
        vm.selectReason(ReportReason.IMPERSONATION)
        vm.next()
        assertEquals(ReportStep.DETAILS, vm.state.value.step)
        vm.back()
        assertEquals(ReportReason.IMPERSONATION, vm.state.value.reason)
    }

    @Test
    fun `something else needs details, and input stops at 500`() {
        val vm = reportVm()
        vm.open(person)
        vm.selectReason(ReportReason.SOMETHING_ELSE)
        assertFalse(vm.state.value.canSend)
        vm.onDetailsChanged("   ")
        assertFalse(vm.state.value.canSend)
        vm.onDetailsChanged("x".repeat(600))
        assertEquals(500, vm.state.value.details.length)
        assertTrue(vm.state.value.canSend)
    }

    @Test
    fun `sending a person report with also block blocks them`() = runTest {
        val repository = FakeModerationRepository()
        val vm = ReportViewModel(repository)
        vm.open(person)
        vm.selectReason(ReportReason.HATE_OR_HARASSMENT)
        vm.onDetailsChanged("  text  ")
        vm.setAlsoBlock(true)
        vm.send()
        advanceUntilIdle()

        assertEquals(ReportStep.SENT, vm.state.value.step)
        assertEquals("text", repository.reports.single().details)
        assertEquals("u1", repository.reports.single().targetUserId)
        assertEquals("l1", repository.reports.single().contextLeagueId)
        assertTrue("u1" in repository.blockedIds.value)
    }

    @Test
    fun `a league report sets the league target`() = runTest {
        val repository = FakeModerationRepository()
        val vm = ReportViewModel(repository)
        vm.open(league)
        vm.selectReason(ReportReason.FAKE_OR_SCAM_LEAGUE)
        vm.send()
        advanceUntilIdle()

        assertEquals("l1", repository.reports.single().targetLeagueId)
        assertNull(repository.reports.single().targetUserId)
    }

    @Test
    fun `a duplicate shows already reported, and doesn't block`() = runTest {
        val repository = FakeModerationRepository(nextReport = { ReportResult.AlreadyReported })
        val vm = ReportViewModel(repository)
        vm.open(person)
        vm.selectReason(ReportReason.HATE_OR_HARASSMENT)
        vm.setAlsoBlock(true)
        vm.send()
        advanceUntilIdle()

        assertEquals(ReportStep.ALREADY_REPORTED, vm.state.value.step)
        assertTrue(repository.blockedIds.value.isEmpty())
    }

    @Test
    fun `a failed send keeps the text`() = runTest {
        val vm = reportVm(FakeModerationRepository(nextReport = { ReportResult.Failed }))
        vm.open(person)
        vm.selectReason(ReportReason.HATE_OR_HARASSMENT)
        vm.onDetailsChanged("keep me")
        vm.send()
        advanceUntilIdle()

        assertTrue(vm.state.value.failed)
        assertEquals("keep me", vm.state.value.details)
        assertEquals(ReportStep.REASON, vm.state.value.step)
    }

    @Test
    fun `block confirm, failure keeps the dialog, undo unblocks`() = runTest {
        val repository = FakeModerationRepository(nextBlock = { Result.failure(RuntimeException("down")) })
        val vm = BlockViewModel(repository)
        val target = BlockTarget("u1", "Vishnu Menon")

        vm.askToBlock(target)
        vm.confirm()
        advanceUntilIdle()
        assertTrue(vm.state.value.failed)
        assertEquals(target, vm.state.value.confirming)

        repository.nextBlock = { Result.success(Unit) }
        vm.confirm()
        advanceUntilIdle()
        assertNull(vm.state.value.confirming)
        assertEquals(target, vm.state.value.justBlocked)
        assertTrue("u1" in vm.blockedIds.value)

        vm.undoBlock()
        advanceUntilIdle()
        assertFalse("u1" in vm.blockedIds.value)
        assertNull(vm.state.value.justBlocked)
    }

    @Test
    fun `blocked people load, a row fails on its own, and undo restores the row`() = runTest {
        val first = BlockedPersonDto("u1", "A", "2026-11-02T00:00:00Z")
        val second = BlockedPersonDto("u2", "B", "2026-10-28T00:00:00Z")
        val repository = FakeModerationRepository(nextBlocks = { Result.success(listOf(first, second)) })
        val vm = BlockedPeopleViewModel(repository)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.rows.size)

        repository.nextUnblock = { Result.failure(RuntimeException("down")) }
        vm.unblock("u1")
        advanceUntilIdle()
        assertTrue(vm.state.value.rows.first().failed)
        assertFalse(vm.state.value.rows.last().failed)

        repository.nextUnblock = { Result.success(Unit) }
        vm.unblock("u1")
        advanceUntilIdle()
        assertEquals(listOf("u2"), vm.state.value.rows.map { it.person.userId })

        vm.undo()
        advanceUntilIdle()
        assertEquals(listOf("u1", "u2"), vm.state.value.rows.map { it.person.userId })
    }

    @Test
    fun `a failed load offers retry`() = runTest {
        val repository = FakeModerationRepository(nextBlocks = { Result.failure(RuntimeException("down")) })
        val vm = BlockedPeopleViewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.state.value.loadFailed)

        repository.nextBlocks = { Result.success(emptyList()) }
        vm.load()
        advanceUntilIdle()
        assertFalse(vm.state.value.loadFailed)
    }

    @Test
    fun `notices queue, and each is dismissed once`() = runTest {
        val notices = listOf(
            ModerationNoticeDto("n1", ModerationNoticeKind.PHOTO),
            ModerationNoticeDto("n2", ModerationNoticeKind.LEAGUE_LOGO, "Edappally Open"),
        )
        val repository = FakeModerationRepository(nextNotices = { Result.success(notices) })
        val vm = ModerationNoticesViewModel(repository)
        advanceUntilIdle()
        assertEquals(2, vm.notices.value.size)

        vm.dismissFirst()
        advanceUntilIdle()
        assertEquals(listOf("n2"), vm.notices.value.map { it.id })
        assertEquals(listOf("n1"), repository.dismissed)
    }
}

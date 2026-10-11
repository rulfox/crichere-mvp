@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.account

import com.crichere.app.auth.FakeAuthRepository
import com.crichere.app.auth.viewModelTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [DeleteAccountViewModel]: design D2–D7 states (docs/PHASE16.md). */
class DeleteAccountViewModelTest {

    private val erasesOn = "2026-10-25T05:00:00Z"
    private val handover = DeletionHandoverDto("l1", "Kottayam Premier", null, "u2", "Anu")
    private val blocker = DeletionBlockerDto("l2", "E2E Bid League", DeletionBlockReason.AUCTION_LIVE)

    private fun okStatus() = AccountDeletionStatusDto(erasesOn = erasesOn, handovers = listOf(handover))

    private class Fixture(val repo: FakeAccountDeletionRepository) {
        val auth = FakeAuthRepository(currentUserId = "u1")
        val notices = AccountNotices()
        val viewModel = DeleteAccountViewModel(repo, auth, notices)
    }

    @Test
    fun `the check shows CanDelete with handovers, or Blocked, or Error`() = viewModelTest {
        val repo = FakeAccountDeletionRepository(nextStatus = { okStatus() })
        val f = Fixture(repo)
        f.viewModel.check()
        assertEquals(DeleteAccountScreenState.Loading, f.viewModel.state.value.screen)
        advanceUntilIdle()
        assertEquals(DeleteAccountScreenState.CanDelete(erasesOn, listOf(handover)), f.viewModel.state.value.screen)

        repo.nextStatus = { AccountDeletionStatusDto(erasesOn = erasesOn, blockers = listOf(blocker)) }
        f.viewModel.check()
        advanceUntilIdle()
        assertEquals(DeleteAccountScreenState.Blocked(listOf(blocker)), f.viewModel.state.value.screen)

        repo.nextStatus = { throw AccountDeletionFailedException("offline") }
        f.viewModel.check()
        advanceUntilIdle()
        assertEquals(DeleteAccountScreenState.Error, f.viewModel.state.value.screen)
        assertEquals(3, repo.statusCallCount)
    }

    @Test
    fun `Confirm needs exactly DELETE, and other input shows the mismatch helper`() {
        assertFalse(DeleteAccountDialogState(input = "").canConfirm)
        assertFalse(DeleteAccountDialogState(input = "").showMismatch)
        assertFalse(DeleteAccountDialogState(input = "delete").canConfirm)
        assertTrue(DeleteAccountDialogState(input = "delete").showMismatch)
        assertFalse(DeleteAccountDialogState(input = "DELET").canConfirm)
        assertTrue(DeleteAccountDialogState(input = " DELETE ").canConfirm)
        assertFalse(DeleteAccountDialogState(input = " DELETE ").showMismatch)
        assertFalse(DeleteAccountDialogState(input = "DELETE", isSubmitting = true).canConfirm)
    }

    @Test
    fun `a successful request clears the session, leaves the notice and signs out`() = viewModelTest {
        val f = Fixture(FakeAccountDeletionRepository(nextStatus = { okStatus() }, nextRequest = { AccountDeletionResult.Scheduled(erasesOn) }))
        f.viewModel.check()
        advanceUntilIdle()
        f.viewModel.openConfirm()
        f.viewModel.onInputChange("DELETE")
        f.viewModel.confirm()
        assertEquals(true, f.viewModel.state.value.dialog?.isSubmitting)
        f.viewModel.dismissConfirm()
        assertTrue(f.viewModel.state.value.dialog != null, "can't dismiss while submitting")

        assertEquals(DeleteAccountEvent.SignedOut, f.viewModel.events.first())
        assertEquals(1, f.auth.clearLocalSessionCallCount)
        assertEquals(0, f.auth.logoutCallCount)
        assertEquals(erasesOn, f.notices.deletionScheduledOn.value)
    }

    @Test
    fun `a failed request keeps the dialog open with the error and Confirm enabled`() = viewModelTest {
        val repo = FakeAccountDeletionRepository(nextStatus = { okStatus() }, nextRequest = { throw AccountDeletionFailedException("offline") })
        val f = Fixture(repo)
        f.viewModel.check()
        advanceUntilIdle()
        f.viewModel.openConfirm()
        f.viewModel.onInputChange("DELETE")
        f.viewModel.confirm()
        advanceUntilIdle()

        val dialog = f.viewModel.state.value.dialog
        assertTrue(dialog!!.failed)
        assertTrue(dialog.canConfirm)
        assertEquals(0, f.auth.clearLocalSessionCallCount)
        assertNull(f.notices.deletionScheduledOn.value)
    }

    @Test
    fun `a request the server now blocks closes the dialog and shows the blockers`() = viewModelTest {
        val f = Fixture(FakeAccountDeletionRepository(nextStatus = { okStatus() }, nextRequest = { AccountDeletionResult.Blocked(listOf(blocker)) }))
        f.viewModel.check()
        advanceUntilIdle()
        f.viewModel.openConfirm()
        f.viewModel.onInputChange("DELETE")
        f.viewModel.confirm()
        advanceUntilIdle()

        assertNull(f.viewModel.state.value.dialog)
        assertIs<DeleteAccountScreenState.Blocked>(f.viewModel.state.value.screen)
        assertEquals(0, f.auth.clearLocalSessionCallCount)
    }

    @Test
    fun `a sign-in that cancelled the deletion replaces the scheduled notice`() {
        val notices = AccountNotices()
        notices.deletionScheduled(erasesOn)
        notices.deletionWasCancelled()
        assertNull(notices.deletionScheduledOn.value)
        assertTrue(notices.deletionCancelled.value)
        notices.dismissDeletionCancelled()
        assertFalse(notices.deletionCancelled.value)
    }
}

package com.crichere.app.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot account notices that outlive the screen that caused them (docs/PHASE16.md, design D7/D8):
 * - [deletionScheduledOn]: set after a deletion request succeeds; Sign in shows the amber notice once.
 * - [deletionCancelled]: set when a sign-in answers `accountRestored`; the first screen shows the
 *   "Welcome back" snackbar once.
 * In memory only: both belong to this app session, and showing them again after a restart would be wrong.
 */
class AccountNotices {
    private val _deletionScheduledOn = MutableStateFlow<String?>(null)
    val deletionScheduledOn: StateFlow<String?> = _deletionScheduledOn.asStateFlow()

    private val _deletionCancelled = MutableStateFlow(false)
    val deletionCancelled: StateFlow<Boolean> = _deletionCancelled.asStateFlow()

    fun deletionScheduled(erasesOn: String) {
        _deletionCancelled.value = false
        _deletionScheduledOn.value = erasesOn
    }

    fun dismissDeletionScheduled() {
        _deletionScheduledOn.value = null
    }

    fun deletionWasCancelled() {
        _deletionScheduledOn.value = null
        _deletionCancelled.value = true
    }

    fun dismissDeletionCancelled() {
        _deletionCancelled.value = false
    }
}

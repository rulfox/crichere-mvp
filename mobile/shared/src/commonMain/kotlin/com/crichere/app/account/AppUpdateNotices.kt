package com.crichere.app.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The backend turned this build away (docs/PHASE23.md, design U1/U2). [updateUrl] is where the Update
 * button goes (Play Store, TestFlight, App Store; `null` if the backend has none set); [minVersion] and
 * [currentVersion] fill the "You have 1.0.0 · Needed 1.1.0" line.
 */
data class AppUpdateRequired(val updateUrl: String?, val minVersion: String?, val currentVersion: String?)

/**
 * Set by any backend response saying `APP_UPDATE_REQUIRED`; both apps then cover everything with the
 * Update required screen. Never cleared: the only way out is installing the update.
 */
class AppUpdateNotices {
    private val _required = MutableStateFlow<AppUpdateRequired?>(null)
    val required: StateFlow<AppUpdateRequired?> = _required.asStateFlow()

    fun updateRequired(notice: AppUpdateRequired) {
        _required.value = notice
    }
}

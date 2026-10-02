package com.crichere.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation3.runtime.NavEntryDecorator

/**
 * Back with the keyboard open closes the keyboard, never the screen (docs/PHASE10.md Part B).
 *
 * Normally the IME consumes back itself. On the CPH2487 (ColorOS) the secure keyboard used for
 * phone/OTP fields doesn't, so back reached the app and closed it. This decorator registers a
 * keyboard-only `BackHandler` *after* the entry's own content, so it's the most recently
 * registered handler and wins over both the entry's own handlers (e.g. League Creation's discard
 * dialog) and `NavDisplay`'s pop -- but only while the keyboard is actually visible.
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun <T : Any> imeBackGuardDecorator(): NavEntryDecorator<T> = NavEntryDecorator { entry ->
    entry.Content()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = WindowInsets.isImeVisible) {
        focusManager.clearFocus()
        keyboard?.hide()
    }
}

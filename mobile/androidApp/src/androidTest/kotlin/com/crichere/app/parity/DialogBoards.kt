package com.crichere.app.parity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crichere.app.ui.ConfirmDialog
import com.crichere.app.ui.CrichereSnackbar
import com.crichere.app.ui.DestructiveConfirmDialog
import com.crichere.app.ui.DiscardChangesDialog
import com.crichere.app.ui.LeaveDialog
import com.crichere.app.ui.PhotoCropSheet
import com.crichere.app.ui.PickedPhoto
import com.crichere.app.ui.ScheduleDatePicker
import com.crichere.app.ui.ScheduleTimePicker
import com.crichere.app.ui.ScreenshotCheckSheet
import com.crichere.app.ui.StartDatePicker
import java.time.LocalDate
import java.time.LocalTime

// Dialogs and sheets a screen holds in its own state, drawn over that screen's board with the copy the screen
// passes them -- the same pairs the iOS gallery draws with ParityDialogPreview / ParitySheetPreview.

/** The iOS gallery's sample photo: a green-to-gold gradient, portrait like a payment screenshot. */
private fun sampleBitmap(width: Int = 900, height: Int = 1600): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), 0xFF1B5E20.toInt(), 0xFFF2B544.toInt(), Shader.TileMode.CLAMP)
        })
    }

private fun over(screen: @Composable () -> Unit, overlay: @Composable () -> Unit): @Composable () -> Unit = {
    screen()
    overlay()
}

fun dialogBoards(): Map<String, @Composable () -> Unit> = mapOf(
    "E2-leave" to over(detail(ParityFixtures.league(banner = true), me = "u1")) { LeaveDialog(onDismiss = {}, onConfirm = {}) },
    "E10" to over(detail(ParityFixtures.league(), me = "u-organizer", organizer = true)) {
        DestructiveConfirmDialog(
            title = "Mark Kolhapur Premier League as completed?",
            body = "This can't be undone. Rosters, auction results and awards stay visible to everyone. Nobody can join, edit the league or run the auction again.",
            confirmLabel = "Mark completed",
            submittingLabel = "Completing…",
            submitting = false,
            onDismiss = {},
            onConfirm = {},
        )
    },
    "R7d" to over(detail(ParityFixtures.league())) {
        Box(Modifier.fillMaxSize()) {
            CrichereSnackbar("Sahil Kulkarni blocked", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                actionLabel = "Undo", onAction = {})
        }
    },
    "F3" to over(join(joinState())) {
        ScreenshotCheckSheet(sampleBitmap(1200, 900), listOf("Amount ₹500", "UPI ID sachin@okaxis", "Reference / UTR no."), onDismiss = {},
            onChooseAnother = {}, onUse = {})
    },
    "G2" to over(claim(claimState(name = "Kolhapur Kings"))) {
        PhotoCropSheet(PickedPhoto(sampleBitmap(1200, 900), "logo.jpg"), title = "Preview", subtitle = "Franchise logo · drag to reposition",
            onDismiss = {}, onChooseAnother = {}, onUsePhoto = {}, square = true, useLabel = "Use logo")
    },
    "I6" to over(creation(creationState(dirty = true, name = "Kolhapur Premier League", located = true))) {
        StartDatePicker(startsOn = "2026-10-12", onDismiss = {}, onPicked = {})
    },
    "I10" to over(creation(creationState(edit = true, dirty = true, name = "Kolhapur Premier League", located = true, ground = true,
        startsOn = "2026-10-12", format = "T20"))) {
        DiscardChangesDialog(body = "Your edits won't be saved. The league stays as it was.", onKeepEditing = {}, onDiscard = {})
    },
    "J10" to over(auction(auctionState(scheduledAt = null))) { ScheduleDatePicker(initial = null, onDismiss = {}, onPicked = {}) },
    "J11" to over(auction(auctionState(scheduledAt = null))) {
        ScheduleTimePicker(date = LocalDate.of(2026, 12, 12), initial = LocalTime.of(19, 0), onBack = {}, onDismiss = {}, onPicked = {})
    },
    "K3" to over(roles(rolesState(phone = "9876543210", found = true))) {
        ConfirmDialog(
            title = "Grant co-organizer access?",
            body = "Omkar Shinde will be able to do everything you can do for this league, including editing it and running the auction.",
            confirmLabel = "Grant",
            confirmColor = MaterialTheme.colorScheme.primary,
            onDismiss = {},
            onConfirm = {},
        )
    },
    "K9" to over(roles(rolesState())) {
        DestructiveConfirmDialog(
            title = "Remove your own access?",
            body = "You'll stop being a co-organizer of Kolhapur Premier League right away. You won't be able to edit the league or run the auction. Only an organizer can add you back.",
            confirmLabel = "Remove me",
            submittingLabel = "Removing…",
            submitting = false,
            onDismiss = {},
            onConfirm = {},
        )
    },
)

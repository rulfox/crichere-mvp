package com.crichere.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScreenshotViewerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val image = Rect(100f, 200f, 500f, 900f)
    private val screen = Size(600f, 1200f)

    @Test
    fun backLeavesTheViewerFromTheTopBarAndThePill() {
        var backs = 0
        composeRule.setContent {
            ScreenshotViewerScreen(painter = ColorPainter(Color.White), loaded = true, failed = false, onRetry = {}, onBack = { backs++ })
        }

        composeRule.onNodeWithText("Payment screenshot").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Back").performClick()
        assertEquals(2, backs)
    }

    @Test
    fun aFailedLoadOffersRetry() {
        var retries = 0
        composeRule.setContent {
            ScreenshotViewerScreen(painter = ColorPainter(Color.White), loaded = false, failed = true, onRetry = { retries++ }, onBack = {})
        }

        composeRule.onNodeWithText("Couldn't load this screenshot.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun anUnzoomedImageCannotBePanned() {
        assertEquals(Offset.Zero, clampPan(Offset(80f, -40f), 1f, image, screen))
    }

    @Test
    fun aZoomedImagePansOnlyUntilItsEdgeMeetsTheScreenEdge() {
        // 3x: 1200 x 2100, centred at (300, 550). The centre may move between 0..600 on x and
        // 150..1050 on y, so the offset is clamped to -300..300 and -400..500.
        assertEquals(Offset(300f, -400f), clampPan(Offset(1000f, -1000f), 3f, image, screen))
        assertEquals(Offset(-120f, 400f), clampPan(Offset(-120f, 400f), 3f, image, screen))
    }

    @Test
    fun theMinimapOutlinesTheVisiblePartOfTheImage() {
        assertEquals(Rect(0f, 0f, 1f, 1f), visibleFraction(1f, Offset.Zero, image, screen))

        // 3x, centred: shown 1200 x 2100 at (-300, -500); the 600 x 1200 screen is its middle.
        val visible = visibleFraction(3f, Offset.Zero, image, screen)
        assertEquals(0.25f, visible.left, 0.001f)
        assertEquals(0.75f, visible.right, 0.001f)
        assertEquals(500f / 2100f, visible.top, 0.001f)
        assertTrue(visible.bottom < 1f)
    }
}

package com.crichere.app.parity

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.crichere.app.ui.theme.CrichereTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Android side of the iOS parity gallery (docs/IOS-REDESIGN-PLAN.md §5): renders one design-board state per
 * test from [ParityFixtures] and saves a full-screen capture (status bar included, like `simctl io screenshot`)
 * to the app's external files dir `parity/<board>.png`. Run with `scripts/android-parity.sh`, not the
 * orchestrated suite (the orchestrator clears app data between tests).
 */
@RunWith(Parameterized::class)
class ParityCaptureTest(private val board: String) {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun capture() {
        val content = ParityBoards.all.getValue(board)
        composeRule.activity.runOnUiThread {
            composeRule.activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            )
        }
        composeRule.setContent { CrichereTheme { content() } }
        composeRule.waitForIdle()
        Thread.sleep(1_500)
        composeRule.waitForIdle()
        val shot: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(composeRule.activity.getExternalFilesDir(null), "parity").apply { mkdirs() }
        File(dir, "$board.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun boards(): List<String> {
            val only = InstrumentationRegistry.getArguments().getString("boards")?.split(',')?.map(String::trim)
            return only ?: ParityBoards.all.keys.toList()
        }
    }
}

/** Board id → screen state. Same ids as `iosApp/Design/Debug/ParityGallery.swift`. */
object ParityBoards {
    val all: Map<String, @Composable () -> Unit> by lazy {
        joinBoards() + claimBoards() + auctionSettingsBoards() + rolesBoards() + deletionBoards() + auctionBoards() + shellBoards()
    }
}

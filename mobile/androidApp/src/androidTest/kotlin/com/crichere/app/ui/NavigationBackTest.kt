package com.crichere.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.crichere.app.ui.navigation.AppNavigator
import com.crichere.app.ui.navigation.AppRoute
import com.crichere.app.ui.navigation.imeBackGuardDecorator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Hardware-back behavior of the Navigation 3 setup `AuthNavHost` uses (docs/PHASE10.md Part B):
 * the same [AppRoute] keys, [AppNavigator] and decorator list, with stub entry content instead of
 * the real Koin-backed screens. Back is sent straight to the Activity's dispatcher -- i.e. *past*
 * the keyboard, exactly what the ColorOS secure keyboard does on the CPH2487.
 */
@OptIn(ExperimentalLayoutApi::class)
class NavigationBackTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var imeVisible = false

    @Before
    fun setUp() {
        // Edge-to-edge like MainActivity, so the IME inset (and isImeVisible) is reported to Compose.
        composeRule.runOnUiThread { composeRule.activity.enableEdgeToEdge() }
        composeRule.setContent {
            val backStack = rememberNavBackStack(AppRoute.Main)
            val navigator = remember(backStack) { AppNavigator(backStack) }
            imeVisible = WindowInsets.isImeVisible
            NavDisplay(
                backStack = backStack,
                onBack = { navigator.back() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                    imeBackGuardDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<AppRoute.Main> {
                        Button(onClick = { navigator.navigate(AppRoute.LeagueDetail("l1")) }) { Text("Open league") }
                    }
                    entry<AppRoute.LeagueDetail> { route ->
                        Column {
                            Text("Detail ${route.leagueId}")
                            var text by remember { mutableStateOf("") }
                            BasicTextField(text, { text = it }, Modifier.testTag("field"))
                        }
                    }
                },
            )
        }
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun backFromPushedScreenReturnsToPreviousScreen() {
        composeRule.onNodeWithText("Open league").performClick()
        composeRule.onNodeWithText("Detail l1").assertExists()

        pressBack()

        composeRule.onNodeWithText("Open league").assertExists()
        composeRule.onNodeWithText("Detail l1").assertDoesNotExist()
    }

    @Test
    fun backAtRootIsLeftToTheSystem() {
        composeRule.onNodeWithText("Open league").assertExists()
        val handled = composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks() }
        assertFalse("nothing in the app should claim back at the stack root", handled)
    }

    @Test
    fun backWithKeyboardOpenClosesKeyboardAndStaysOnScreen() {
        composeRule.onNodeWithText("Open league").performClick()
        composeRule.onNodeWithTag("field").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { imeVisible }

        pressBack()

        composeRule.waitUntil(timeoutMillis = 5_000) { !imeVisible }
        composeRule.onNodeWithText("Detail l1").assertExists()

        // With the keyboard gone, the next back navigates as usual.
        pressBack()
        composeRule.onNodeWithText("Open league").assertExists()
        assertTrue(!imeVisible)
    }
}

package com.crichere.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {

    private fun navigatorWith(vararg routes: AppRoute): Pair<MutableList<NavKey>, AppNavigator> {
        val stack = mutableListOf<NavKey>(*routes)
        return stack to AppNavigator(stack)
    }

    @Test
    fun `navigate pushes and back pops`() {
        val (stack, navigator) = navigatorWith(AppRoute.Main)

        navigator.navigate(AppRoute.LeagueDetail("l1"))
        navigator.navigate(AppRoute.AuctionLive("l1"))
        assertEquals(listOf(AppRoute.Main, AppRoute.LeagueDetail("l1"), AppRoute.AuctionLive("l1")), stack)

        assertTrue(navigator.back())
        assertEquals(listOf(AppRoute.Main, AppRoute.LeagueDetail("l1")), stack)
    }

    @Test
    fun `back at the root is not handled, so the system can exit`() {
        val (stack, navigator) = navigatorWith(AppRoute.Main)

        assertFalse(navigator.back())
        assertEquals(listOf<NavKey>(AppRoute.Main), stack)
    }

    @Test
    fun `navigate to the route already on top is a no-op`() {
        val (stack, navigator) = navigatorWith(AppRoute.Main, AppRoute.LeagueDetail("l1"))

        navigator.navigate(AppRoute.LeagueDetail("l1"))

        assertEquals(listOf(AppRoute.Main, AppRoute.LeagueDetail("l1")), stack)
    }

    @Test
    fun `login replaces the whole auth stack with Main`() {
        val (stack, navigator) = navigatorWith(AppRoute.PhoneEntry(), AppRoute.OtpVerify("+911234567890", "vid"))

        navigator.replaceAll(AppRoute.Main)

        assertEquals(listOf<NavKey>(AppRoute.Main), stack)
        assertFalse(navigator.back())
    }

    @Test
    fun `logout from deep in the main area leaves only Phone Entry`() {
        val (stack, navigator) = navigatorWith(AppRoute.Main, AppRoute.LeagueDetail("l1"), AppRoute.ScreenshotViewer("u"))

        navigator.replaceAll(AppRoute.PhoneEntry())

        assertEquals(listOf<NavKey>(AppRoute.PhoneEntry()), stack)
    }

    @Test
    fun `replaceAll onto an equal route still leaves exactly one entry`() {
        val (stack, navigator) = navigatorWith(AppRoute.PhoneEntry(), AppRoute.OtpVerify("+911234567890", "vid"))

        navigator.replaceAll(AppRoute.PhoneEntry())

        assertEquals(listOf<NavKey>(AppRoute.PhoneEntry()), stack)
    }

    @Test
    fun `a created league replaces its creation form`() {
        val (stack, navigator) = navigatorWith(AppRoute.Main, AppRoute.LeagueCreation(editingLeagueId = null))

        navigator.replaceTop(AppRoute.LeagueDetail("new"))

        assertEquals(listOf(AppRoute.Main, AppRoute.LeagueDetail("new")), stack)
    }

    @Test
    fun `an edited league pops back to its detail`() {
        val (stack, navigator) = navigatorWith(AppRoute.Main, AppRoute.LeagueDetail("l1"), AppRoute.LeagueCreation(editingLeagueId = "l1"))

        navigator.back()

        assertEquals(listOf(AppRoute.Main, AppRoute.LeagueDetail("l1")), stack)
    }

    @Test
    fun `other tabs go back to Dashboard, Dashboard falls through`() {
        assertNull(MainTab.DASHBOARD.backTarget())
        assertEquals(MainTab.DASHBOARD, MainTab.MY_LEAGUES.backTarget())
        assertEquals(MainTab.DASHBOARD, MainTab.MY_PROFILE.backTarget())
    }
}

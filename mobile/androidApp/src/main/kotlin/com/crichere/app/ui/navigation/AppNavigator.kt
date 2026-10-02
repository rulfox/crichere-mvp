package com.crichere.app.ui.navigation

import androidx.navigation3.runtime.NavKey

/**
 * The back-stack operations the app uses, over Navigation 3's plain `MutableList` back stack.
 * Kept free of Compose so the rules are unit-testable (`AppNavigatorTest`).
 */
internal class AppNavigator(private val backStack: MutableList<NavKey>) {

    /** Push [route]; a no-op when it's already on top (e.g. a repeated deep link to the same league). */
    fun navigate(route: AppRoute) {
        if (backStack.lastOrNull() != route) backStack.add(route)
    }

    /** Pop one entry. Returns `false` at the root, where back belongs to the system (exit). */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /**
     * Clear the stack down to [route]. Used for auth transitions (login, logout, lockout) so back
     * can never return across them. Adds before removing: `NavDisplay` requires a non-empty stack.
     */
    fun replaceAll(route: AppRoute) {
        backStack.add(route)
        while (backStack.size > 1) backStack.removeAt(0)
    }

    /** Swap the top entry for [route] (a newly created league replaces its creation form). */
    fun replaceTop(route: AppRoute) {
        backStack.add(route)
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex - 1)
    }
}

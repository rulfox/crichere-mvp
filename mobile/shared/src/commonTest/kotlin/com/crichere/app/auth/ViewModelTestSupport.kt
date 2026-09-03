package com.crichere.app.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Runs a ViewModel test with `Dispatchers.Main` set to a [StandardTestDispatcher] that shares
 * `runTest`'s own [TestScope.testScheduler] -- required for `viewModelScope`'s launched
 * coroutines (which dispatch on `Dispatchers.Main.immediate`) to be driven by the same virtual
 * clock as the test body, so `advanceTimeBy`/`advanceUntilIdle` inside [testBody] actually affect
 * them. Setting `Dispatchers.setMain(StandardTestDispatcher())` with no shared scheduler (e.g. in
 * a bare `@BeforeTest`) would give `viewModelScope` an *independent* virtual clock the test body
 * has no way to advance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun viewModelTest(testBody: suspend TestScope.() -> Unit): TestResult = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        testBody()
    } finally {
        Dispatchers.resetMain()
    }
}

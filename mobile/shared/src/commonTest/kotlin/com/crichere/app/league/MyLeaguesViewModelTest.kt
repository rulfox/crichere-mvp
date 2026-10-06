@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crichere.app.league

import com.crichere.app.auth.viewModelTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [MyLeaguesViewModel] coverage: loads the four lists via [MyLeaguesRepository], explicit-retry-on-entry convention. */
class MyLeaguesViewModelTest {

    private fun summary(id: String) = LeagueSummaryDto(id = id, name = "League $id", district = "Bengaluru", groundName = "Test Ground", state = "Karnataka", startsOn = "2026-10-12", status = LeagueStatus.ANNOUNCED)

    @Test
    fun `retry loads all four lists`() = viewModelTest {
        val repository = FakeMyLeaguesRepository(
            nextResult = MyLeaguesDto(
                organizing = listOf(summary("l1")),
                playing = listOf(summary("l2")),
                franchiseOwner = listOf(summary("l3")),
                following = listOf(summary("l4")),
            ),
        )
        val viewModel = MyLeaguesViewModel(repository)

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(1, state.data?.organizing?.size)
        assertEquals(1, state.data?.playing?.size)
        assertEquals(1, state.data?.franchiseOwner?.size)
        assertEquals(1, state.data?.following?.size)
    }

    @Test
    fun `a load failure surfaces an error message instead of crashing`() = viewModelTest {
        val repository = FakeMyLeaguesRepository().apply { error = RuntimeException("network down") }
        val viewModel = MyLeaguesViewModel(repository)

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.errorMessage != null)
    }

    @Test
    fun `retry re-fetches every time it's called -- the explicit-retry-on-entry convention`() = viewModelTest {
        val repository = FakeMyLeaguesRepository()
        val viewModel = MyLeaguesViewModel(repository)

        viewModel.retry()
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, repository.callCount)
    }
}

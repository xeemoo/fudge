package dev.danielc.core.mvi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun accept_updatesState() = runTest {
    val vm = TestCounterViewModel()

    vm.accept(CounterIntent.Increment)
    advanceUntilIdle()

    assertEquals(1, vm.state.value.count)
  }

  private class TestCounterViewModel : MviViewModel<CounterIntent, CounterState, Nothing>(
    initialState = CounterState(0)
  ) {
    override suspend fun reduce(intent: CounterIntent) {
      when (intent) {
        CounterIntent.Increment -> setState { copy(count = count + 1) }
      }
    }
  }

  private enum class CounterIntent {
    Increment
  }

  private data class CounterState(val count: Int)
}

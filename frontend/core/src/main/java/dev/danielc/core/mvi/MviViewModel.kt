package dev.danielc.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class MviViewModel<I : Any, S : Any, E : Any>(
  initialState: S
) : ViewModel() {

  private val _state = MutableStateFlow(initialState)
  private val _effect = MutableSharedFlow<E>(extraBufferCapacity = 1)

  val state: StateFlow<S> = _state.asStateFlow()
  val effect: Flow<E> = _effect.asSharedFlow()

  fun accept(intent: I) {
    viewModelScope.launch {
      reduce(intent)
    }
  }

  protected abstract suspend fun reduce(intent: I)

  protected fun setState(reducer: S.() -> S) {
    _state.update(reducer)
  }

  protected suspend fun postEffect(builder: () -> E) {
    _effect.emit(builder())
  }
}

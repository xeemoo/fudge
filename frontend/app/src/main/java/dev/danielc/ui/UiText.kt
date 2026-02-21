package dev.danielc.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

sealed interface UiText {
  data class Res(
    @StringRes val value: Int,
    val args: List<Any> = emptyList()
  ) : UiText {
    constructor(@StringRes value: Int, vararg args: Any) : this(value, args.toList())
  }

  data class Dynamic(val value: String) : UiText
}

fun UiText.resolve(context: Context): String {
  return when (this) {
    is UiText.Res -> context.getString(value, *args.toTypedArray())
    is UiText.Dynamic -> value
  }
}

@Composable
fun UiText.asString(): String {
  return resolve(LocalContext.current)
}

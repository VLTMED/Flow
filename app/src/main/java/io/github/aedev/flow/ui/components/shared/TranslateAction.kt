package io.github.aedev.flow.ui.components.shared

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.translation.GoogleTranslateClient
import io.github.aedev.flow.data.translation.Translation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal val LocalGoogleTranslateClient =
    staticCompositionLocalOf<GoogleTranslateClient?> { null }

/** Starts one direct Google Translate web request for an explicit user action. */
@Stable
class TranslateAction internal constructor(
    private val context: Context,
    private val translator: GoogleTranslateClient?,
    private val scope: CoroutineScope,
    private val onResult: (Translation?) -> Unit,
) {
    fun translate(text: String) {
        if (text.isBlank()) {
            onResult(null)
            return
        }
        val activeTranslator = translator
        if (activeTranslator == null) {
            Toast.makeText(context, R.string.translation_failed, Toast.LENGTH_LONG).show()
            onResult(null)
            return
        }
        scope.launch {
            try {
                val translatedText = activeTranslator.translate(text)
                onResult(Translation(translatedText, context.getString(R.string.google_translate)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(context, R.string.translation_failed, Toast.LENGTH_LONG).show()
                onResult(null)
            }
        }
    }
}

@Composable
fun rememberTranslateAction(onResult: (Translation?) -> Unit): TranslateAction {
    val context = LocalContext.current
    val translator = LocalGoogleTranslateClient.current
    val scope = rememberCoroutineScope()
    val latestOnResult = rememberUpdatedState(onResult)
    return remember(context, translator, scope, latestOnResult) {
        TranslateAction(
            context = context,
            translator = translator,
            scope = scope,
            onResult = { latestOnResult.value(it) },
        )
    }
}

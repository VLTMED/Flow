package io.github.aedev.flow.data.translation

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct, no-key requests to Google's undocumented web translation endpoint. This is not the
 * supported Cloud Translation API and may be rate-limited, blocked, or changed without notice.
 * Callers must invoke it only after an explicit user action; request text is not logged or stored.
 * The dedicated OkHttp client has no cookie jar or account credentials.
 */
@Singleton
class GoogleTranslateClient
    @Inject
    constructor(
        private val preferences: PlayerPreferences,
    ) {
        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build()

        suspend fun translate(text: String): String =
            withContext(PerformanceDispatcher.networkIO) {
                require(text.isNotBlank()) { "Text to translate must not be blank" }
                val target =
                    preferences.translationTarget
                        .first()
                        .takeIf { it.isNotBlank() }
                        ?: YouTube.locale.hl.takeIf { it.isNotBlank() }
                        ?: "en"
                val result = StringBuilder()
                splitTranslationText(text, MAX_CHARS_PER_REQUEST).forEach { chunk ->
                    if (chunk.text.isNotEmpty()) result.append(translateChunk(chunk.text, target))
                    result.append(chunk.separatorAfter)
                }
                result.toString().takeIf { it.isNotBlank() }
                    ?: throw IOException("Google Translate returned no translated text")
            }

        private fun translateChunk(
            text: String,
            targetLanguage: String,
        ): String {
            val url =
                HttpUrl
                    .Builder()
                    .scheme("https")
                    .host(GOOGLE_TRANSLATE_HOST)
                    .addPathSegments("translate_a/single")
                    .addQueryParameter("client", "gtx")
                    .addQueryParameter("sl", "auto")
                    .addQueryParameter("tl", targetLanguage)
                    .addQueryParameter("dt", "t")
                    .addQueryParameter("ie", "UTF-8")
                    .addQueryParameter("oe", "UTF-8")
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Flow Android")
                    .post(FormBody.Builder().add("q", text).build())
                    .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Google Translate returned HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw IOException("Google Translate returned an empty response")
                return GoogleTranslateResponseParser.parse(body)
                    ?: throw IOException("Google Translate returned an unrecognized response")
            }
        }

        private companion object {
            const val GOOGLE_TRANSLATE_HOST = "translate.googleapis.com"
            const val MAX_CHARS_PER_REQUEST = 10_000
        }
    }

internal object GoogleTranslateResponseParser {
    /** Google web API response shape: [ [ [ translatedSegment, originalSegment, ... ], ... ], ... ]. */
    fun parse(responseBody: String): String? {
        val root = runCatching { Json.parseToJsonElement(responseBody) }.getOrNull() as? JsonArray ?: return null
        val sentenceSegments = root.getOrNull(0) as? JsonArray ?: return null
        val translated =
            buildString {
                sentenceSegments.forEach { segment ->
                    val parts = segment as? JsonArray ?: return@forEach
                    val text = (parts.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return@forEach
                    append(text)
                }
            }
        return translated.takeIf { it.isNotBlank() }
    }
}

internal data class TranslationTextChunk(
    val text: String,
    val separatorAfter: String,
)

/** Splits long descriptions at whitespace while preserving the original separators between chunks. */
internal fun splitTranslationText(
    text: String,
    maxChars: Int,
): List<TranslationTextChunk> {
    require(maxChars > 0) { "maxChars must be positive" }
    if (text.isEmpty()) return emptyList()

    val chunks = mutableListOf<TranslationTextChunk>()
    var start = 0
    while (start < text.length) {
        val limit = (start + maxChars).coerceAtMost(text.length)
        if (limit == text.length) {
            chunks += TranslationTextChunk(text.substring(start), "")
            break
        }

        val breakAt = text.lastIndexOfAny(charArrayOf(' ', '\n', '\r', '\t'), limit - 1)
        if (breakAt < start + maxChars / 2) {
            chunks += TranslationTextChunk(text.substring(start, limit), "")
            start = limit
            continue
        }

        var separatorEnd = breakAt
        while (separatorEnd < text.length && text[separatorEnd].isWhitespace()) separatorEnd++
        chunks += TranslationTextChunk(text.substring(start, breakAt), text.substring(breakAt, separatorEnd))
        start = separatorEnd
    }
    return chunks
}

package io.github.aedev.flow.data.translation

/** Text returned by the manually requested Google Translate service. */
data class Translation(
    val text: String,
    val host: String,
)

/** A video's title and description translated together in one request sequence. */
data class TitledTranslation(
    val title: String,
    val body: String,
    val host: String,
)

/** Keeps the title on the first line, so [splitTitle] can separate it after translation. */
fun titledText(
    title: String,
    body: String,
): String = if (body.isBlank()) title.trim() else title.trim() + "\n\n" + body

fun Translation.splitTitle(): TitledTranslation =
    TitledTranslation(
        title = text.substringBefore('\n').trim(),
        body = text.substringAfter('\n', "").trimStart('\n'),
        host = host,
    )

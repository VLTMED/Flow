package io.github.aedev.flow.data.local

/** Codec preference for extracted audio streams. AUTO always remains a safe fallback. */
enum class AudioCodecPreference(
    val key: String,
) {
    AUTO("auto"),
    AAC("aac"),
    OPUS("opus"),
    ;

    companion object {
        fun fromKey(key: String?): AudioCodecPreference = entries.firstOrNull { it.key == key } ?: AUTO

        fun codecKey(
            mimeType: String?,
            codec: String? = null,
        ): String? {
            val value = "$mimeType $codec".lowercase()
            return when {
                "opus" in value -> OPUS.key
                "mp4a" in value || "aac" in value -> AAC.key
                else -> null
            }
        }

        fun matches(
            preference: AudioCodecPreference,
            mimeType: String?,
            codec: String? = null,
        ): Boolean = preference == AUTO || codecKey(mimeType, codec) == preference.key
    }
}

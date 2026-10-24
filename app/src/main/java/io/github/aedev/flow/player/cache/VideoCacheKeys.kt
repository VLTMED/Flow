package io.github.aedev.flow.player.cache

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory

/** Gives every cached stream a stable owner that survives preload and playback transitions. */
@UnstableApi
internal class VideoCacheKeys(
    videoId: String,
) : CacheKeyFactory {
    private val prefix = prefixFor(videoId)

    override fun buildCacheKey(dataSpec: DataSpec): String = prefix + CacheKeyFactory.DEFAULT.buildCacheKey(dataSpec)

    companion object {
        private const val SESSION_KEY_PREFIX = "flow-video-session-v1|"

        fun prefixFor(videoId: String): String = "$SESSION_KEY_PREFIX$videoId|"

        fun isSessionKey(key: String): Boolean = key.startsWith(SESSION_KEY_PREFIX)

        fun belongsToVideo(
            key: String,
            videoId: String,
        ): Boolean = key.startsWith(prefixFor(videoId))
    }
}

/** Tracks only the actively watched video's cache; pausing does not end this session. */
internal class VideoCacheSession(
    private val clearVideo: (String) -> Unit,
) {
    private var activeVideoId: String? = null

    @Synchronized
    fun activate(videoId: String) {
        if (videoId.isBlank() || activeVideoId == videoId) return
        activeVideoId?.let(clearVideo)
        activeVideoId = videoId
    }

    @Synchronized
    fun close() {
        activeVideoId?.let(clearVideo)
        activeVideoId = null
    }

    @Synchronized
    fun currentVideoId(): String? = activeVideoId
}

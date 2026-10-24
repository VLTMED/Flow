package io.github.aedev.flow.player.cache

import android.net.Uri
import androidx.media3.datasource.DataSpec
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class VideoCacheKeysTest {
    private fun open(
        videoId: String,
        url: String,
    ): String {
        val uri = mockk<Uri> { every { this@mockk.toString() } returns url }
        return VideoCacheKeys(videoId).buildCacheKey(DataSpec.Builder().setUri(uri).build())
    }

    @Test
    fun `cache keys are stable for one video and isolated across videos`() {
        val first = open("first", "https://example.com/shared")
        val second = open("second", "https://example.com/shared")

        assertThat(first).isEqualTo(open("first", "https://example.com/shared"))
        assertThat(first).startsWith(VideoCacheKeys.prefixFor("first"))
        assertThat(second).startsWith(VideoCacheKeys.prefixFor("second"))
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `video cleanup ownership does not match another id or legacy shorts key`() {
        val owned = open("video", "https://example.com/a")
        val otherVideo = open("video-next", "https://example.com/a")
        val legacyShortsKey = "https://example.com/a"

        assertThat(VideoCacheKeys.belongsToVideo(owned, "video")).isTrue()
        assertThat(VideoCacheKeys.belongsToVideo(otherVideo, "video")).isFalse()
        assertThat(VideoCacheKeys.belongsToVideo(legacyShortsKey, "video")).isFalse()
        assertThat(VideoCacheKeys.isSessionKey(owned)).isTrue()
        assertThat(VideoCacheKeys.isSessionKey(legacyShortsKey)).isFalse()
    }

    @Test
    fun `session remains active through pause and repeated loading of the same video`() {
        val cleared = mutableListOf<String>()
        val session = VideoCacheSession(cleared::add)

        session.activate("first")
        // Pause/resume does not close or rotate the cache session.
        session.activate("first")

        assertThat(session.currentVideoId()).isEqualTo("first")
        assertThat(cleared).isEmpty()
    }

    @Test
    fun `transition clears previous video and close clears the active video`() {
        val cleared = mutableListOf<String>()
        val session = VideoCacheSession(cleared::add)

        session.activate("first")
        session.activate("second")
        assertThat(cleared).containsExactly("first").inOrder()
        assertThat(session.currentVideoId()).isEqualTo("second")

        session.close()
        assertThat(cleared).containsExactly("first", "second").inOrder()
        assertThat(session.currentVideoId()).isNull()
    }
}

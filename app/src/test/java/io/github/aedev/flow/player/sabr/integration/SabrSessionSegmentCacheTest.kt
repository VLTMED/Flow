package io.github.aedev.flow.player.sabr.integration

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.cache.VideoCacheKeys
import io.github.aedev.flow.player.sabr.core.SabrSegment
import io.github.aedev.flow.player.sabr.proto.FormatId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class SabrSessionSegmentCacheTest {
    private val networkBytes = AtomicInteger()
    private val database = SQLiteDatabase.create(null)
    private val databaseProvider =
        object : DatabaseProvider {
            override fun getWritableDatabase(): SQLiteDatabase = database

            override fun getReadableDatabase(): SQLiteDatabase = database
        }
    private val cacheDirectory: File = Files.createTempDirectory("flow-sabr-cache-test-").toFile()
    private lateinit var mediaCache: SimpleCache
    private lateinit var segmentCache: SabrSessionSegmentCache

    @Before
    fun setUp() {
        mediaCache = SimpleCache(cacheDirectory, NoOpCacheEvictor(), databaseProvider)
        segmentCache =
            SabrSessionSegmentCache(
                cacheProvider = { mediaCache },
                cacheReadUpstreamFactory = DataSource.Factory { CountingPayloadDataSource(FALLBACK_BYTES, networkBytes) },
            )
    }

    @After
    fun tearDown() {
        mediaCache.release()
        database.close()
        cacheDirectory.deleteRecursively()
    }

    @Test
    fun `rewind replays same SABR payload from cache without a second upstream transfer`() {
        val audioInit = networkPayload(byteArrayOf(0x11, 0x12))
        val videoInit = networkPayload(byteArrayOf(0x21, 0x22, 0x23))
        val audioFirst = networkPayload(byteArrayOf(0x31, 0x32, 0x33, 0x34))
        val audioSecond = networkPayload(byteArrayOf(0x35, 0x36, 0x37))
        val videoFirst = networkPayload(byteArrayOf(0x41, 0x42, 0x43, 0x44, 0x45))
        val videoSecond = networkPayload(byteArrayOf(0x46, 0x47, 0x48, 0x49))
        val initialNetworkBytes = networkBytes.get()

        assertThat(segmentCache.storeInitialization(VIDEO_ID, AUDIO_FORMAT, initSegment(AUDIO_FORMAT, true, audioInit))).isTrue()
        assertThat(segmentCache.storeInitialization(VIDEO_ID, VIDEO_FORMAT, initSegment(VIDEO_FORMAT, false, videoInit))).isTrue()
        assertThat(segmentCache.storeSegment(VIDEO_ID, AUDIO_FORMAT, mediaSegment(AUDIO_FORMAT, true, 1, 0, audioFirst))).isTrue()
        assertThat(segmentCache.storeSegment(VIDEO_ID, AUDIO_FORMAT, mediaSegment(AUDIO_FORMAT, true, 2, 2_000, audioSecond))).isTrue()
        assertThat(
            segmentCache.storeSegment(VIDEO_ID, VIDEO_FORMAT, mediaSegment(VIDEO_FORMAT, false, 1, 0, videoFirst, durationMs = 2_500)),
        ).isTrue()
        assertThat(
            segmentCache.storeSegment(VIDEO_ID, VIDEO_FORMAT, mediaSegment(VIDEO_FORMAT, false, 2, 2_500, videoSecond, durationMs = 2_500)),
        ).isTrue()

        val firstRewind = segmentCache.replayForSeek(VIDEO_ID, AUDIO_FORMAT, VIDEO_FORMAT, targetPositionMs = 1_000)
        assertThat(firstRewind).isNotNull()
        assertThat(firstRewind!!.requestPositionMs).isEqualTo(4_000)
        assertThat(firstRewind.audioInitSegment.data).isEqualTo(audioInit)
        assertThat(firstRewind.videoInitSegment.data).isEqualTo(videoInit)
        val replayedAudio = firstRewind.audioSegments.mapNotNull(segmentCache::readCachedSegment).map { it.data.toList() }
        val replayedVideo = firstRewind.videoSegments.mapNotNull(segmentCache::readCachedSegment).map { it.data.toList() }
        assertThat(replayedAudio).containsExactly(audioFirst.toList(), audioSecond.toList()).inOrder()
        assertThat(replayedVideo).containsExactly(videoFirst.toList(), videoSecond.toList()).inOrder()
        assertThat(networkBytes.get()).isEqualTo(initialNetworkBytes)

        // A second rewind to the same already-downloaded media range remains entirely local.
        val secondRewind = segmentCache.replayForSeek(VIDEO_ID, AUDIO_FORMAT, VIDEO_FORMAT, targetPositionMs = 1_000)
        assertThat(secondRewind).isNotNull()
        assertThat(segmentCache.readCachedSegment(secondRewind!!.audioSegments.first())!!.data).isEqualTo(audioFirst)
        assertThat(segmentCache.readCachedSegment(secondRewind.videoSegments.first())!!.data).isEqualTo(videoFirst)
        assertThat(networkBytes.get()).isEqualTo(initialNetworkBytes)

        // Exact format identity and video ID are part of the key; neither a different quality nor
        // another video can reuse this payload.
        assertThat(segmentCache.replayForSeek(VIDEO_ID, AUDIO_FORMAT.copy(lmt = 999), VIDEO_FORMAT, 1_000)).isNull()
        assertThat(segmentCache.replayForSeek("another-video", AUDIO_FORMAT, VIDEO_FORMAT, 1_000)).isNull()

        segmentCache.clearVideo(VIDEO_ID)
        assertThat(mediaCache.keys.none { VideoCacheKeys.belongsToVideo(it, VIDEO_ID) }).isTrue()
        assertThat(segmentCache.replayForSeek(VIDEO_ID, AUDIO_FORMAT, VIDEO_FORMAT, 1_000)).isNull()
    }

    private fun initSegment(
        formatId: FormatId,
        isAudio: Boolean,
        data: ByteArray,
    ) = SabrSegment(
        headerId = 0,
        itag = formatId.itag,
        videoId = VIDEO_ID,
        isAudio = isAudio,
        timeRangeStartMs = 0,
        durationMs = 0,
        sequenceNumber = 0,
        data = data,
        isInitSegment = true,
    )

    private fun mediaSegment(
        formatId: FormatId,
        isAudio: Boolean,
        sequence: Int,
        startMs: Long,
        data: ByteArray,
        durationMs: Long = 2_000,
    ) = SabrSegment(
        headerId = sequence,
        itag = formatId.itag,
        videoId = VIDEO_ID,
        isAudio = isAudio,
        timeRangeStartMs = startMs,
        durationMs = durationMs,
        sequenceNumber = sequence,
        data = data,
    )

    private fun networkPayload(payload: ByteArray): ByteArray {
        val source = CountingPayloadDataSource(payload, networkBytes)
        val dataSpec =
            DataSpec
                .Builder()
                .setUri(NETWORK_URI)
                .setLength(payload.size.toLong())
                .build()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32)
        try {
            source.open(dataSpec)
            while (true) {
                val count = source.read(buffer, 0, buffer.size)
                if (count == C.RESULT_END_OF_INPUT) break
                output.write(buffer, 0, count)
            }
        } finally {
            source.close()
        }
        return output.toByteArray()
    }

    private class CountingPayloadDataSource(
        private val payload: ByteArray,
        private val networkBytes: AtomicInteger,
    ) : BaseDataSource(true) {
        private var position = 0
        private var endPosition = 0
        private var uri: Uri? = null

        override fun open(dataSpec: DataSpec): Long {
            uri = dataSpec.uri
            position = dataSpec.position.toInt()
            val requestedLength = dataSpec.length.takeUnless { it == C.LENGTH_UNSET.toLong() }?.toInt()
            endPosition = (requestedLength?.let { position + it } ?: payload.size).coerceAtMost(payload.size)
            transferInitializing(dataSpec)
            transferStarted(dataSpec)
            return (endPosition - position).toLong()
        }

        override fun read(
            target: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (position >= endPosition) return C.RESULT_END_OF_INPUT
            val count = minOf(length, endPosition - position)
            payload.copyInto(target, offset, position, position + count)
            position += count
            networkBytes.addAndGet(count)
            bytesTransferred(count)
            return count
        }

        override fun getUri(): Uri? = uri

        override fun close() {
            transferEnded()
            uri = null
        }
    }

    private companion object {
        const val VIDEO_ID = "sabr-cache-video"
        val AUDIO_FORMAT = FormatId(itag = 140, lmt = 101, xtags = "audio-opus")
        val VIDEO_FORMAT = FormatId(itag = 137, lmt = 202, xtags = "video-h264-1080p")
        val NETWORK_URI: Uri = Uri.parse("https://example.test/sabr-payload")
        val FALLBACK_BYTES = ByteArray(64) { it.toByte() }
    }
}

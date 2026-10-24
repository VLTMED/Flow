package io.github.aedev.flow.player.sabr.integration

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import io.github.aedev.flow.player.cache.VideoCacheKeys
import io.github.aedev.flow.player.sabr.core.SabrSegment
import io.github.aedev.flow.player.sabr.proto.FormatId
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Reuses complete SABR payloads across source rebuilds inside the active video session.
 * Payload bytes live in Media3's shared video cache; only the segment index is held in memory.
 */
@UnstableApi
internal class SabrSessionSegmentCache(
    private val cacheProvider: () -> Cache?,
    private val cacheReadUpstreamFactory: DataSource.Factory? = null,
) {
    private data class FormatKey(
        val videoId: String,
        val isAudio: Boolean,
        val formatId: FormatId,
    )

    private val initializationSegments = mutableMapOf<FormatKey, SabrCachedSegmentRef>()
    private val segmentsByFormat = mutableMapOf<FormatKey, MutableList<SabrCachedSegmentRef>>()

    @Synchronized
    fun storeInitialization(
        videoId: String,
        formatId: FormatId,
        segment: SabrSegment,
    ): Boolean {
        if (!segment.isInitSegment || videoId.isBlank() || segment.videoId != videoId) return false
        if (formatId.itag <= 0 || segment.itag != formatId.itag || segment.data.isEmpty()) return false
        val formatKey = FormatKey(videoId, segment.isAudio, formatId)
        val cacheKey = initializationCacheKey(formatKey, segment.sequenceNumber)
        if (!storePayload(cacheKey, segment.data)) return false
        initializationSegments[formatKey] = segment.toRef(cacheKey)
        return true
    }

    @Synchronized
    fun storeSegment(
        videoId: String,
        formatId: FormatId,
        segment: SabrSegment,
    ): Boolean {
        if (segment.isInitSegment || videoId.isBlank() || segment.videoId != videoId) return false
        if (formatId.itag <= 0 || segment.itag != formatId.itag || segment.durationMs <= 0 || segment.data.isEmpty()) return false

        val formatKey = FormatKey(videoId, segment.isAudio, formatId)
        val cacheKey = segmentCacheKey(formatKey, segment.sequenceNumber, segment.timeRangeStartMs, segment.durationMs)
        if (!storePayload(cacheKey, segment.data)) return false

        val refs = segmentsByFormat.getOrPut(formatKey) { mutableListOf() }
        if (refs.none { it.key == cacheKey }) refs += segment.toRef(cacheKey)
        return true
    }

    /** Returns a replay only when both selected tracks cover the seek point with contiguous cached ranges. */
    @Synchronized
    fun replayForSeek(
        videoId: String,
        audioFormatId: FormatId,
        videoFormatId: FormatId,
        targetPositionMs: Long,
    ): SabrCachedReplay? {
        if (videoId.isBlank() || targetPositionMs < 0) return null
        val cache = cacheProvider() ?: return null
        val audioKey = FormatKey(videoId, isAudio = true, audioFormatId)
        val videoKey = FormatKey(videoId, isAudio = false, videoFormatId)
        val audioInitRef = initializationSegments[audioKey] ?: return null
        val videoInitRef = initializationSegments[videoKey] ?: return null

        val audioSegments = cachedRefs(cache, segmentsByFormat[audioKey].orEmpty())
        val videoSegments = cachedRefs(cache, segmentsByFormat[videoKey].orEmpty())
        val audioChain = continuousSegmentsFrom(targetPositionMs, audioSegments)
        val videoChain = continuousSegmentsFrom(targetPositionMs, videoSegments)
        if (audioChain.isEmpty() || videoChain.isEmpty()) return null

        // Audio/video segment durations can differ. Include each complete contiguous track range;
        // the minimum end is a safe playerTime cursor, and both per-format ranges are sent to SABR.
        val requestPositionMs = minOf(audioChain.last().endMs, videoChain.last().endMs)
        if (requestPositionMs <= targetPositionMs) return null

        val audioInit = readSegment(cache, audioInitRef) ?: return null
        val videoInit = readSegment(cache, videoInitRef) ?: return null
        return SabrCachedReplay(
            audioInitSegment = audioInit,
            videoInitSegment = videoInit,
            audioSegments = audioChain,
            videoSegments = videoChain,
            requestPositionMs = requestPositionMs,
        )
    }

    @Synchronized
    fun readCachedSegment(segment: SabrCachedSegmentRef): SabrSegment? {
        val cache = cacheProvider() ?: return null
        return readSegment(cache, segment)
    }

    @Synchronized
    fun clearVideo(videoId: String) {
        if (videoId.isBlank()) return
        initializationSegments.keys.removeAll { it.videoId == videoId }
        segmentsByFormat.keys.removeAll { it.videoId == videoId }
        val prefix = VideoCacheKeys.prefixFor(videoId) + SABR_CACHE_NAMESPACE
        val cache = cacheProvider() ?: return
        cache.keys.filter { it.startsWith(prefix) }.forEach { key ->
            runCatching { cache.removeResource(key) }
        }
    }

    @Synchronized
    fun clearAll() {
        initializationSegments.clear()
        segmentsByFormat.clear()
        val cache = cacheProvider() ?: return
        cache.keys
            .filter { it.startsWith(VIDEO_SESSION_CACHE_NAMESPACE) && it.contains("|$SABR_CACHE_NAMESPACE") }
            .forEach { key -> runCatching { cache.removeResource(key) } }
    }

    private fun cachedRefs(
        cache: Cache,
        refs: List<SabrCachedSegmentRef>,
    ): List<SabrCachedSegmentRef> =
        refs
            .asSequence()
            .filter { it.payloadLength in 1..MAX_REPLAY_PAYLOAD_BYTES && cache.isCached(it.key, 0L, it.payloadLength) }
            .sortedWith(compareBy<SabrCachedSegmentRef> { it.timeRangeStartMs }.thenBy { it.sequenceNumber })
            .toList()

    private fun readSegment(
        cache: Cache,
        ref: SabrCachedSegmentRef,
    ): SabrSegment? =
        readPayload(cache, ref.key, ref.payloadLength)?.let { data ->
            SabrSegment(
                headerId = 0,
                itag = ref.itag,
                videoId = ref.videoId,
                isAudio = ref.isAudio,
                timeRangeStartMs = ref.timeRangeStartMs,
                durationMs = ref.durationMs,
                sequenceNumber = ref.sequenceNumber,
                data = data,
                isInitSegment = ref.isInitSegment,
            )
        }

    private fun continuousSegmentsFrom(
        targetPositionMs: Long,
        segments: List<SabrCachedSegmentRef>,
    ): List<SabrCachedSegmentRef> {
        val firstIndex = segments.indexOfFirst { targetPositionMs >= it.timeRangeStartMs && targetPositionMs < it.endMs }
        if (firstIndex < 0) return emptyList()

        val result = mutableListOf(segments[firstIndex])
        var endMs = result.last().endMs
        var sequenceNumber = result.last().sequenceNumber
        for (candidate in segments.drop(firstIndex + 1)) {
            if (candidate.sequenceNumber <= sequenceNumber || candidate.endMs <= endMs) continue
            if (candidate.timeRangeStartMs > endMs) break
            result += candidate
            endMs = candidate.endMs
            sequenceNumber = candidate.sequenceNumber
        }
        return result
    }

    private fun storePayload(
        key: String,
        data: ByteArray,
    ): Boolean {
        val cache = cacheProvider() ?: return false
        if (data.isEmpty() || data.size.toLong() > MAX_REPLAY_PAYLOAD_BYTES) return false
        val length = data.size.toLong()
        val existing = cache.getCachedSpans(key).firstOrNull { it.position == 0L }
        if (existing?.length == length) return true
        if (existing != null) cache.removeResource(key)

        val dataSpec =
            DataSpec
                .Builder()
                .setUri(PAYLOAD_URI)
                .setKey(key)
                .setLength(length)
                .build()
        val source =
            CacheDataSource
                .Factory()
                .setCache(cache)
                .setCacheKeyFactory(CacheKeyFactory { spec -> spec.key ?: key })
                .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache).setFragmentSize(length))
                .setUpstreamDataSourceFactory(DataSource.Factory { ByteArrayDataSource(data) })
                .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
                .createDataSource()
        val completed =
            try {
                source.open(dataSpec)
                val buffer = ByteArray(16 * 1024)
                while (source.read(buffer, 0, buffer.size) != C.RESULT_END_OF_INPUT) {
                    // Drain the in-memory upstream so CacheDataSink commits the complete payload.
                }
                true
            } catch (_: Exception) {
                false
            } finally {
                runCatching { source.close() }
            }
        return completed && cache.isCached(key, 0L, length)
    }

    private fun readPayload(
        cache: Cache,
        key: String,
        expectedLength: Long,
    ): ByteArray? {
        val span = cache.getCachedSpans(key).firstOrNull { it.position == 0L && it.isCached } ?: return null
        val length = span.length
        if (length != expectedLength || length <= 0 || length > MAX_REPLAY_PAYLOAD_BYTES || !cache.isCached(key, 0L, length)) return null
        val factory =
            CacheDataSource
                .Factory()
                .setCache(cache)
                .setCacheKeyFactory(CacheKeyFactory { dataSpec -> dataSpec.key ?: key })
                .apply { cacheReadUpstreamFactory?.let(::setUpstreamDataSourceFactory) }
        val source = factory.createDataSource()
        val dataSpec =
            DataSpec
                .Builder()
                .setUri(PAYLOAD_URI)
                .setKey(key)
                .setLength(length)
                .build()
        val output = ByteArrayOutputStream(length.toInt())
        val buffer = ByteArray(16 * 1024)
        return try {
            source.open(dataSpec)
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                output.write(buffer, 0, read)
            }
            output.toByteArray().takeIf { it.size.toLong() == length }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { source.close() }
        }
    }

    private fun initializationCacheKey(
        formatKey: FormatKey,
        sequenceNumber: Int,
    ): String = cachePrefix(formatKey) + "init|$sequenceNumber"

    private fun segmentCacheKey(
        formatKey: FormatKey,
        sequenceNumber: Int,
        startMs: Long,
        durationMs: Long,
    ): String = cachePrefix(formatKey) + "segment|$sequenceNumber|$startMs|$durationMs"

    private fun cachePrefix(formatKey: FormatKey): String =
        VideoCacheKeys.prefixFor(formatKey.videoId) +
            SABR_CACHE_NAMESPACE +
            "${if (formatKey.isAudio) "audio" else "video"}|${formatKey.formatId.itag}|${formatKey.formatId.lmt}|" +
            "${sha256(formatKey.formatId.xtags)}|"

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun SabrSegment.toRef(key: String) =
        SabrCachedSegmentRef(
            key = key,
            videoId = videoId,
            isAudio = isAudio,
            itag = itag,
            sequenceNumber = sequenceNumber,
            timeRangeStartMs = timeRangeStartMs,
            durationMs = durationMs,
            isInitSegment = isInitSegment,
            payloadLength = data.size.toLong(),
        )

    private companion object {
        const val SABR_CACHE_NAMESPACE = "sabr-v1|"
        const val VIDEO_SESSION_CACHE_NAMESPACE = "flow-video-session-v1|"
        const val MAX_REPLAY_PAYLOAD_BYTES = 32L * 1024L * 1024L
        val PAYLOAD_URI: Uri = Uri.parse("sabr-cache://payload")
    }
}

internal data class SabrCachedSegmentRef(
    val key: String,
    val videoId: String,
    val isAudio: Boolean,
    val itag: Int,
    val sequenceNumber: Int,
    val timeRangeStartMs: Long,
    val durationMs: Long,
    val isInitSegment: Boolean,
    val payloadLength: Long,
) {
    val endMs: Long get() = timeRangeStartMs + durationMs
}

internal data class SabrCachedReplay(
    val audioInitSegment: SabrSegment,
    val videoInitSegment: SabrSegment,
    val audioSegments: List<SabrCachedSegmentRef>,
    val videoSegments: List<SabrCachedSegmentRef>,
    val requestPositionMs: Long,
)

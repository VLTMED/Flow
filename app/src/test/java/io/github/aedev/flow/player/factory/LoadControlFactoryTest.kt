package io.github.aedev.flow.player.factory

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.config.VideoBufferDurationBudget
import io.github.aedev.flow.player.config.VideoBufferMemoryBudget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Shorts and music profiles are fixed constants rather than user preferences, so nothing else
 * re-checks them: [io.github.aedev.flow.data.local.BufferDurationsTest] only covers the video path,
 * whose durations come from DataStore. These tests are what stops an edit to the constants from
 * reaching a device as the #788 class of failure — `setBufferDurationsMs` throws while the player is
 * being built, which surfaces as a crash on every launch rather than as degraded buffering.
 */
@UnstableApi
class LoadControlFactoryTest {
    @Test
    fun `shorts profile is accepted by the load control`() {
        LoadControlFactory.forShorts()
    }

    @Test
    fun `music profile is accepted by the load control`() {
        LoadControlFactory.forMusic()
    }

    @Test
    fun `selected duration controls Media3 loading after allocator byte target is reached`() {
        val memoryBudget =
            VideoBufferMemoryBudget
                .resolve(
                    memoryClassMb = 512,
                    isLowRamDevice = false,
                    requestedMemoryMb = 0,
                    runtimeMaxMemoryBytes = 512L * VideoBufferMemoryBudget.BYTES_PER_MIB,
                ).copy(targetBytes = PlayerConfig.ALLOCATOR_BUFFER_SIZE)
        val shortControl = videoControlForTarget(30_000, memoryBudget)
        val longControl = videoControlForTarget(120_000, memoryBudget)
        val shortPlayerId = PlayerId("short-buffer-test")
        val longPlayerId = PlayerId("long-buffer-test")

        shortControl.onPrepared(shortPlayerId)
        longControl.onPrepared(longPlayerId)
        val shortAllocator = shortControl.getAllocator(shortPlayerId)
        val longAllocator = longControl.getAllocator(longPlayerId)
        val shortAllocation = shortAllocator.allocate()
        val longAllocation = longAllocator.allocate()

        try {
            assertFalse(
                "A 30s target should stop at 30s after its byte target is reached",
                shortControl.shouldContinueLoading(loadParameters(shortPlayerId, 30_000)),
            )
            assertTrue(
                "A 120s target should continue past 30s despite the same reached byte target",
                longControl.shouldContinueLoading(loadParameters(longPlayerId, 30_000)),
            )
            assertTrue(longControl.shouldContinueLoading(loadParameters(longPlayerId, 119_000)))
            assertFalse(longControl.shouldContinueLoading(loadParameters(longPlayerId, 120_000)))
        } finally {
            shortAllocator.release(shortAllocation)
            longAllocator.release(longAllocation)
            shortControl.onReleased(shortPlayerId)
            longControl.onReleased(longPlayerId)
        }
    }

    @Test
    fun `shorts constants satisfy the load control contract before any coercion`() {
        assertContractHolds(
            profile = "shorts",
            minMs = PlayerConfig.SHORTS_MIN_BUFFER_MS,
            maxMs = PlayerConfig.SHORTS_MAX_BUFFER_MS,
            playbackMs = PlayerConfig.SHORTS_BUFFER_FOR_PLAYBACK_MS,
            rebufferMs = PlayerConfig.SHORTS_BUFFER_FOR_REBUFFER_MS,
        )
    }

    @Test
    fun `music constants satisfy the load control contract before any coercion`() {
        assertContractHolds(
            profile = "music",
            minMs = PlayerConfig.MUSIC_MIN_BUFFER_MS,
            maxMs = PlayerConfig.MUSIC_MAX_BUFFER_MS,
            playbackMs = PlayerConfig.MUSIC_BUFFER_FOR_PLAYBACK_MS,
            rebufferMs = PlayerConfig.MUSIC_BUFFER_FOR_REBUFFER_MS,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the load control still rejects a profile that inverts min and rebuffer`() {
        // Keeps the checks above from passing vacuously.
        DefaultLoadControl.Builder().setBufferDurationsMs(1_500, 8_000, 250, 5_000)
    }

    /**
     * Asserts the raw constants, not the coerced output of [LoadControlFactory.build] — the coercion
     * is a backstop, and a profile that only survives because of it has drifted from its intent.
     */
    private fun assertContractHolds(
        profile: String,
        minMs: Int,
        maxMs: Int,
        playbackMs: Int,
        rebufferMs: Int,
    ) {
        assertTrue("$profile: max ($maxMs) must be >= min ($minMs)", maxMs >= minMs)
        assertTrue("$profile: min ($minMs) must be >= playback ($playbackMs)", minMs >= playbackMs)
        assertTrue("$profile: min ($minMs) must be >= rebuffer ($rebufferMs)", minMs >= rebufferMs)

        DefaultLoadControl.Builder().setBufferDurationsMs(minMs, maxMs, playbackMs, rebufferMs)
    }

    private fun videoControlForTarget(
        targetMs: Int,
        memoryBudget: VideoBufferMemoryBudget.Resolved,
    ): DefaultLoadControl {
        val durations =
            VideoBufferDurationBudget
                .resolve(
                    memoryBudget = memoryBudget,
                    minBufferMs = 30_000,
                    maxForwardBufferMs = targetMs,
                    bufferForPlaybackMs = 2_500,
                    bufferForRebufferMs = 5_000,
                ).durations
        return LoadControlFactory.buildVideoLoadControl(durations, memoryBudget, backBufferMs = 0)
    }

    private fun loadParameters(
        playerId: PlayerId,
        bufferedMs: Long,
    ): LoadControl.Parameters {
        val timeline =
            SinglePeriodTimeline(
                600_000_000L,
                true,
                false,
                false,
                null,
                MediaItem.EMPTY,
            )
        return LoadControl.Parameters(
            playerId,
            timeline,
            MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0)),
            0L,
            bufferedMs * 1_000L,
            1f,
            true,
            false,
            C.TIME_UNSET,
            0L,
        )
    }
}

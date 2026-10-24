package io.github.aedev.flow.player.config

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import io.github.aedev.flow.data.local.BufferDurations
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class VideoBufferDurationBudgetTest {
    private val oneMiB = VideoBufferMemoryBudget.BYTES_PER_MIB.toLong()

    @Test
    fun `normal heap uses the selectable 500 second forward duration as the Media3 target`() {
        assertEquals(30_000, PlayerConfig.MIN_SELECTABLE_MAIN_BUFFER_MS)
        assertEquals(500_000, PlayerConfig.MAX_SAFE_MAIN_BUFFER_MS)
        assertEquals(300_000, PlayerConfig.MAX_SAFE_BACK_BUFFER_DURATION_MS)

        val budget = resolveMemoryBudget(memoryClassMb = 512, isLowRamDevice = false)
        val resolved = resolveBufferBudget(memoryBudget = budget, minMs = 30_000, maxMs = 500_000)

        assertEquals(500_000, resolved.maxSafeMinBufferMs)
        assertEquals(500_000, resolved.maxSafeForwardBufferMs)
        assertEquals(500_000, resolved.durations.minMs)
        assertEquals(500_000 + BufferDurations.MAX_HEADROOM_MS, resolved.durations.maxMs)
        assertMedia3Accepts(resolved.durations)
    }

    @Test
    fun `manual 500 second target is retained on constrained and low RAM devices`() {
        listOf(
            resolveMemoryBudget(memoryClassMb = 256, isLowRamDevice = false),
            resolveMemoryBudget(memoryClassMb = 1_024, isLowRamDevice = true),
        ).forEach { budget ->
            val resolved = resolveBufferBudget(memoryBudget = budget, minMs = 30_000, maxMs = 500_000)

            assertEquals(500_000, resolved.maxSafeMinBufferMs)
            assertEquals(500_000, resolved.maxSafeForwardBufferMs)
            assertEquals(500_000, resolved.durations.minMs)
            assertEquals(500_000 + BufferDurations.MAX_HEADROOM_MS, resolved.durations.maxMs)
            assertMedia3Accepts(resolved.durations)
        }
    }

    @Test
    fun `changing forward ceiling does not change RAM or retained back buffer policy`() {
        assertEquals(300_000, PlayerConfig.MAX_SAFE_BACK_BUFFER_DURATION_MS)
        assertEquals(32 * 1024 * 1024, PlayerConfig.MAIN_TARGET_BUFFER_BYTES)

        val constrainedBudget = resolveMemoryBudget(memoryClassMb = 384, isLowRamDevice = false)
        assertEquals(0, VideoBufferMemoryBudget.effectiveBackBufferMs(constrainedBudget, 90_000))
        assertEquals(90_000, VideoBufferMemoryBudget.effectiveBackBufferMs(resolveMemoryBudget(512, false), 90_000))
    }

    private fun resolveMemoryBudget(
        memoryClassMb: Int,
        isLowRamDevice: Boolean,
    ): VideoBufferMemoryBudget.Resolved =
        VideoBufferMemoryBudget.resolve(
            memoryClassMb = memoryClassMb,
            isLowRamDevice = isLowRamDevice,
            requestedMemoryMb = 0,
            runtimeMaxMemoryBytes = memoryClassMb.toLong() * oneMiB,
        )

    private fun resolveBufferBudget(
        memoryBudget: VideoBufferMemoryBudget.Resolved,
        minMs: Int,
        maxMs: Int,
    ): VideoBufferDurationBudget.Resolved =
        VideoBufferDurationBudget.resolve(
            memoryBudget = memoryBudget,
            minBufferMs = minMs,
            maxForwardBufferMs = maxMs,
            bufferForPlaybackMs = 2_500,
            bufferForRebufferMs = 5_000,
        )

    private fun assertMedia3Accepts(durations: io.github.aedev.flow.data.local.BufferDurations) {
        DefaultLoadControl
            .Builder()
            .setBufferDurationsMs(
                durations.minMs,
                durations.maxMs,
                durations.playbackMs,
                durations.rebufferMs,
            ).setTargetBufferBytes(PlayerConfig.MAIN_TARGET_BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
}

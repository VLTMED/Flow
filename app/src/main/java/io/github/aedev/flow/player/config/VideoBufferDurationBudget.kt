package io.github.aedev.flow.player.config

import io.github.aedev.flow.data.local.BufferDurations

/**
 * Resolves the requested forward-buffer window against device heap policy.
 *
 * The selected forward duration becomes Media3's minimum loading target. Allocator byte targets,
 * the disk cache, and retained back-buffer duration remain separate policies; stream availability
 * and Media3's heap-headroom guard can still stop loading before the requested target.
 */
object VideoBufferDurationBudget {
    data class Resolved(
        val durations: BufferDurations,
        val maxSafeMinBufferMs: Int,
        val maxSafeForwardBufferMs: Int,
    )

    fun resolve(
        memoryBudget: VideoBufferMemoryBudget.Resolved,
        minBufferMs: Int,
        maxForwardBufferMs: Int,
        bufferForPlaybackMs: Int,
        bufferForRebufferMs: Int,
    ): Resolved {
        val maxSafeMinBufferMs = PlayerConfig.MAX_SAFE_MAIN_MIN_BUFFER_MS
        val maxSafeForwardBufferMs = PlayerConfig.MAX_SAFE_MAIN_BUFFER_MS
        val requestedTargetMs = maxOf(minBufferMs, maxForwardBufferMs)
        val durations =
            BufferDurations.sanitize(
                minMs = requestedTargetMs,
                maxMs = requestedTargetMs,
                playbackMs = bufferForPlaybackMs,
                rebufferMs = bufferForRebufferMs,
                maxSafeMinMs = maxSafeMinBufferMs,
                maxSafeMaxMs = maxSafeForwardBufferMs,
            )

        return Resolved(
            durations = durations,
            maxSafeMinBufferMs = maxSafeMinBufferMs,
            maxSafeForwardBufferMs = maxSafeForwardBufferMs,
        )
    }
}

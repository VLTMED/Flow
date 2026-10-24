package io.github.aedev.flow.player.config

/**
 * Resolves the main video player's Media3 target against the app process heap, not device RAM.
 *
 * This is an allocator target, not a reservation or a hard cap on total process RAM: renderers,
 * decoders, surfaces and other app allocations remain outside it. Forward and retained back-buffer
 * samples share this target. The public setting may be much larger than the safe per-process target;
 * the applied value is always clamped before it reaches Media3's signed-Int API.
 */
object VideoBufferMemoryBudget {
    const val BYTES_PER_MIB = 1024 * 1024

    data class Resolved(
        val targetBytes: Int,
        val deviceDefaultBytes: Int,
        val deviceCapBytes: Int,
        val processHeapMaxBytes: Long,
        val isLowMemoryDevice: Boolean,
        val isConstrainedHeap: Boolean,
    ) {
        val targetMegabytes: Int get() = targetBytes / BYTES_PER_MIB
        val defaultMegabytes: Int get() = minOf(deviceDefaultBytes, deviceCapBytes) / BYTES_PER_MIB
        val capMegabytes: Int get() = deviceCapBytes / BYTES_PER_MIB
        val processHeapMaxMegabytes: Int get() = (processHeapMaxBytes / BYTES_PER_MIB).toInt()
    }

    /**
     * [requestedMemoryMb] is stored in MiB for compatibility with existing preferences. The
     * Android heap limit used here is the lower of ActivityManager's heap class and Runtime's
     * current process maximum; neither value represents the device's total physical RAM.
     */
    fun resolve(
        memoryClassMb: Int,
        isLowRamDevice: Boolean,
        requestedMemoryMb: Int,
        runtimeMaxMemoryBytes: Long = memoryClassMb.toLong() * BYTES_PER_MIB,
    ): Resolved {
        val safeMemoryClassMb = memoryClassMb.coerceAtLeast(1)
        val memoryClassBytes = safeMemoryClassMb.toLong() * BYTES_PER_MIB
        val runtimeHeapBytes = runtimeMaxMemoryBytes.takeIf { it > 0L } ?: memoryClassBytes
        val processHeapMaxBytes = minOf(memoryClassBytes, runtimeHeapBytes)

        // Keep the old constrained-heap boundary for back-buffer behavior, but reserve the
        // "low-memory device" label for Android's explicit isLowRamDevice signal.
        val isLowMemoryDevice = isLowRamDevice
        val isConstrainedHeap = isLowRamDevice || safeMemoryClassMb <= 384
        // Low-memory classification limits the allocator budget and back buffer, not the user's
        // forward time target. DefaultLoadControl retains its real-time heap-headroom stop guard.

        val deviceDefaultBytes =
            when {
                isLowRamDevice -> PlayerConfig.LOW_MEMORY_MAIN_TARGET_BUFFER_BYTES
                isConstrainedHeap -> PlayerConfig.MID_MEMORY_MAIN_TARGET_BUFFER_BYTES
                else -> PlayerConfig.MAIN_TARGET_BUFFER_BYTES
            }
        val policyCapBytes =
            when {
                isLowRamDevice -> PlayerConfig.LOW_MEMORY_MAIN_TARGET_BUFFER_CAP_BYTES
                isConstrainedHeap -> PlayerConfig.MID_MEMORY_MAIN_TARGET_BUFFER_CAP_BYTES
                else -> PlayerConfig.MAIN_TARGET_BUFFER_CAP_BYTES
            }
        val heapShareDivisor = if (isLowRamDevice) 16L else 8L
        val heapBasedCapBytes = processHeapMaxBytes / heapShareDivisor
        val deviceCapBytes = minOf(policyCapBytes.toLong(), heapBasedCapBytes).coerceAtLeast(1L).toInt()
        val targetBytes =
            if (requestedMemoryMb <= 0) {
                minOf(deviceDefaultBytes, deviceCapBytes)
            } else {
                (requestedMemoryMb.toLong() * BYTES_PER_MIB)
                    .coerceAtMost(deviceCapBytes.toLong())
                    .toInt()
            }

        return Resolved(
            targetBytes = targetBytes,
            deviceDefaultBytes = deviceDefaultBytes,
            deviceCapBytes = deviceCapBytes,
            processHeapMaxBytes = processHeapMaxBytes,
            isLowMemoryDevice = isLowMemoryDevice,
            isConstrainedHeap = isConstrainedHeap,
        )
    }

    fun effectiveBackBufferMs(
        budget: Resolved,
        requestedBackBufferMs: Int,
    ): Int =
        if (budget.isConstrainedHeap) {
            PlayerConfig.LOW_MEMORY_BACK_BUFFER_DURATION_MS
        } else {
            requestedBackBufferMs.coerceIn(0, PlayerConfig.MAX_SAFE_BACK_BUFFER_DURATION_MS)
        }
}

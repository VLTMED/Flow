package io.github.aedev.flow.player.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBufferMemoryBudgetTest {
    private val oneMiB = VideoBufferMemoryBudget.BYTES_PER_MIB.toLong()

    @Test
    fun nonLowRamDeviceWith256MiBHeapIsConstrainedButNotMisclassifiedAsLowRam() {
        val budget =
            VideoBufferMemoryBudget.resolve(
                memoryClassMb = 256,
                isLowRamDevice = false,
                requestedMemoryMb = 64,
                runtimeMaxMemoryBytes = 256L * oneMiB,
            )

        assertEquals(12, budget.defaultMegabytes)
        assertEquals(32, budget.capMegabytes)
        assertEquals(32, budget.targetMegabytes)
        assertFalse(budget.isLowMemoryDevice)
        assertTrue(budget.isConstrainedHeap)
    }

    @Test
    fun nonLowRamDeviceWith512MiBHeapUsesRuntimeHeapBasedBudget() {
        val budget =
            VideoBufferMemoryBudget.resolve(
                memoryClassMb = 512,
                isLowRamDevice = false,
                requestedMemoryMb = 1_500,
                runtimeMaxMemoryBytes = 512L * oneMiB,
            )

        assertEquals(32, budget.defaultMegabytes)
        assertEquals(64, budget.capMegabytes)
        assertEquals(64, budget.targetMegabytes)
        assertFalse(budget.isLowMemoryDevice)
        assertFalse(budget.isConstrainedHeap)
    }

    @Test
    fun runtimeHeapLowerThanMemoryClassFurtherReducesActualTarget() {
        val budget =
            VideoBufferMemoryBudget.resolve(
                memoryClassMb = 512,
                isLowRamDevice = false,
                requestedMemoryMb = 256,
                runtimeMaxMemoryBytes = 128L * oneMiB,
            )

        assertEquals(128, budget.processHeapMaxMegabytes)
        assertEquals(16, budget.capMegabytes)
        assertEquals(16, budget.targetMegabytes)
        assertFalse(budget.isLowMemoryDevice)
    }

    @Test
    fun lowRamFlagUsesSmallDefaultAndConservativeBudgetEvenWithLargeHeap() {
        val budget =
            VideoBufferMemoryBudget.resolve(
                memoryClassMb = 1_024,
                isLowRamDevice = true,
                requestedMemoryMb = 64,
                runtimeMaxMemoryBytes = 1_024L * oneMiB,
            )

        assertEquals(4, budget.defaultMegabytes)
        assertEquals(8, budget.capMegabytes)
        assertEquals(8, budget.targetMegabytes)
        assertTrue(budget.isLowMemoryDevice)
        assertTrue(budget.isConstrainedHeap)
    }

    @Test
    fun selectableMaximumSupportsTwoGbRequestedBudgetAndFitsSignedInt() {
        assertEquals(
            PlayerConfig.MAX_SELECTABLE_MAIN_TARGET_BUFFER_MIB * oneMiB.toInt(),
            PlayerConfig.MAX_SELECTABLE_MAIN_TARGET_BUFFER_BYTES,
        )
        assertTrue(PlayerConfig.MAX_SELECTABLE_MAIN_TARGET_BUFFER_BYTES < Int.MAX_VALUE)
        assertEquals(1_907, PlayerConfig.MAX_SELECTABLE_MAIN_TARGET_BUFFER_MIB)
    }

    @Test
    fun lowHeapStillRestrictsBackBufferIndependentlyOfForwardDuration() {
        val smallHeap =
            VideoBufferMemoryBudget.resolve(
                memoryClassMb = 256,
                isLowRamDevice = false,
                requestedMemoryMb = 0,
                runtimeMaxMemoryBytes = 256L * oneMiB,
            )
        val midHeap =
            VideoBufferMemoryBudget.resolve(
                memoryClassMb = 384,
                isLowRamDevice = false,
                requestedMemoryMb = 0,
                runtimeMaxMemoryBytes = 384L * oneMiB,
            )
        val standard =
            VideoBufferMemoryBudget.resolve(
                memoryClassMb = 512,
                isLowRamDevice = false,
                requestedMemoryMb = 0,
                runtimeMaxMemoryBytes = 512L * oneMiB,
            )

        assertEquals(0, VideoBufferMemoryBudget.effectiveBackBufferMs(smallHeap, 90_000))
        assertEquals(0, VideoBufferMemoryBudget.effectiveBackBufferMs(midHeap, 90_000))
        assertEquals(90_000, VideoBufferMemoryBudget.effectiveBackBufferMs(standard, 90_000))
        assertEquals(
            PlayerConfig.MAX_SAFE_BACK_BUFFER_DURATION_MS,
            VideoBufferMemoryBudget.effectiveBackBufferMs(
                standard,
                PlayerConfig.MAX_SAFE_BACK_BUFFER_DURATION_MS + 10_000,
            ),
        )
    }
}

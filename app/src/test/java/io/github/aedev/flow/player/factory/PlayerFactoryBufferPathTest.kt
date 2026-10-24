package io.github.aedev.flow.player.factory

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.config.VideoBufferMemoryBudget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@UnstableApi
class PlayerFactoryBufferPathTest {
    @Test
    fun `DataStore 500s target reaches production LoadControl on normal and low RAM devices`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            assertNotNull(activityManager)
            val manager = activityManager!!
            val shadow = Shadows.shadowOf(manager)
            val previousMemoryClassMb = manager.memoryClass
            val previousLowRam = manager.isLowRamDevice
            val preferences = PlayerPreferences(context)
            val previousMinMs = preferences.minBufferMs.first()
            val previousMaxMs = preferences.maxBufferMs.first()
            val previousPlaybackMs = preferences.bufferForPlaybackMs.first()
            val previousRebufferMs = preferences.bufferForPlaybackAfterRebufferMs.first()
            val previousMemoryMb = preferences.videoBufferMemoryMb.first()
            val previousBackBufferMs = preferences.videoBackBufferMs.first()
            val factory = PlayerFactory()
            val normalPlayerId = PlayerId("factory-buffer-normal-memory-test")
            val lowMemoryPlayerId = PlayerId("factory-buffer-low-memory-test")
            var normalControl: DefaultLoadControl? = null
            var lowMemoryControl: DefaultLoadControl? = null

            try {
                preferences.setMinBufferMs(30_000)
                preferences.setMaxBufferMs(500_000)
                preferences.setBufferForPlaybackMs(2_500)
                preferences.setBufferForPlaybackAfterRebufferMs(5_000)
                preferences.setVideoBufferMemoryMb(1_500)
                preferences.setVideoBackBufferMs(0)
                val requestedRamMb = preferences.videoBufferMemoryMb.first()
                assertEquals(1_500, requestedRamMb)

                shadow.setMemoryClass(512)
                shadow.setIsLowRamDevice(false)
                val normalEffectiveRamBytes =
                    VideoBufferMemoryBudget
                        .resolve(
                            memoryClassMb = manager.memoryClass,
                            isLowRamDevice = manager.isLowRamDevice,
                            requestedMemoryMb = requestedRamMb,
                            runtimeMaxMemoryBytes = Runtime.getRuntime().maxMemory(),
                        ).targetBytes
                normalControl = factory.createLoadControl(context)
                normalControl.onPrepared(normalPlayerId)
                assertEquals(normalEffectiveRamBytes, intField(normalControl, "targetBufferBytesOverwrite"))
                assertTrue(
                    "PlayerFactory should pass the selected 500s target through DataStore and the active LoadControl",
                    normalControl.shouldContinueLoading(loadParameters(normalPlayerId, 30_000)),
                )
                assertTrue(
                    "Media3 should continue until the selected time target plus its five-second max headroom",
                    normalControl.shouldContinueLoading(loadParameters(normalPlayerId, 504_999)),
                )
                assertFalse(
                    "The normal-memory LoadControl should stop at its resolved 505s maximum",
                    normalControl.shouldContinueLoading(loadParameters(normalPlayerId, 505_000)),
                )
                normalControl.onReleased(normalPlayerId)
                normalControl = null

                factory.invalidateCachedPreferences()
                shadow.setMemoryClass(256)
                shadow.setIsLowRamDevice(false)
                val lowMemoryEffectiveRamBytes =
                    VideoBufferMemoryBudget
                        .resolve(
                            memoryClassMb = manager.memoryClass,
                            isLowRamDevice = manager.isLowRamDevice,
                            requestedMemoryMb = requestedRamMb,
                            runtimeMaxMemoryBytes = Runtime.getRuntime().maxMemory(),
                        ).targetBytes
                lowMemoryControl = factory.createLoadControl(context)
                lowMemoryControl.onPrepared(lowMemoryPlayerId)
                assertEquals(500_000_000L, longField(lowMemoryControl, "minBufferUs"))
                assertEquals(505_000_000L, longField(lowMemoryControl, "maxBufferUs"))
                assertEquals(lowMemoryEffectiveRamBytes, intField(lowMemoryControl, "targetBufferBytesOverwrite"))
                assertTrue(booleanField(lowMemoryControl, "prioritizeTimeOverSizeThresholds"))
            } finally {
                normalControl?.onReleased(normalPlayerId)
                lowMemoryControl?.onReleased(lowMemoryPlayerId)
                factory.invalidateCachedPreferences()
                preferences.setMinBufferMs(previousMinMs)
                preferences.setMaxBufferMs(previousMaxMs)
                preferences.setBufferForPlaybackMs(previousPlaybackMs)
                preferences.setBufferForPlaybackAfterRebufferMs(previousRebufferMs)
                preferences.setVideoBufferMemoryMb(previousMemoryMb)
                preferences.setVideoBackBufferMs(previousBackBufferMs)
                shadow.setMemoryClass(previousMemoryClassMb)
                shadow.setIsLowRamDevice(previousLowRam)
            }
        }

    private fun longField(
        control: DefaultLoadControl,
        name: String,
    ): Long =
        control.javaClass
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .getLong(control)

    private fun intField(
        control: DefaultLoadControl,
        name: String,
    ): Int =
        control.javaClass
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .getInt(control)

    private fun booleanField(
        control: DefaultLoadControl,
        name: String,
    ): Boolean =
        control.javaClass
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .getBoolean(control)

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

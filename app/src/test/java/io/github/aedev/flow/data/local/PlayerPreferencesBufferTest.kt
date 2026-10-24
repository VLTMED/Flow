package io.github.aedev.flow.data.local

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PlayerPreferencesBufferTest {
    @Test
    fun `500 second duration and 1500 MiB RAM request survive a preference reload`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val writer = PlayerPreferences(context)
            val reader = PlayerPreferences(context)
            val previousMemoryMb = reader.videoBufferMemoryMb.first()

            try {
                writer.setMaxBufferMs(500_000)
                writer.setVideoBufferMemoryMb(1_500)
                writer.setOverlayEdgeScrimDisabled(true)

                assertEquals(500_000, reader.maxBufferMs.first())
                assertEquals(1_500, reader.videoBufferMemoryMb.first())
                assertTrue(reader.overlayPreferences.first().edgeScrimDisabled)
            } finally {
                writer.setMaxBufferMs(BufferProfile.STABLE.maxBuffer)
                writer.setVideoBufferMemoryMb(previousMemoryMb)
                writer.setOverlayEdgeScrimDisabled(false)
            }
        }
}

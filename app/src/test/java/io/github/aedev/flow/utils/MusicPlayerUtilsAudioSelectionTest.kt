package io.github.aedev.flow.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicPlayerUtilsAudioSelectionTest {
    private data class Option(
        val itag: Int,
        val label: String,
    )

    private val options = listOf(Option(itag = 140, label = "AAC"), Option(itag = 251, label = "Opus"))

    @Test
    fun exactAvailableItagSelectsThatFormat() {
        val selected = MusicPlayerUtils.exactAudioFormatByItag(options, requestedItag = 251) { it.itag }

        assertEquals(Option(itag = 251, label = "Opus"), selected)
    }

    @Test
    fun unavailableItagFallsBackToTheExistingAutomaticSelection() {
        val selected = MusicPlayerUtils.exactAudioFormatByItag(options, requestedItag = 999) { it.itag }

        assertNull(selected)
    }

    @Test
    fun zeroItagMeansUseTheExistingAutomaticSelection() {
        val selected = MusicPlayerUtils.exactAudioFormatByItag(options, requestedItag = 0) { it.itag }

        assertNull(selected)
    }
}

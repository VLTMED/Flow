package io.github.aedev.flow.player.quality

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import io.github.aedev.flow.player.stream.StreamProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.util.Locale

class Media3AudioQualityTest {
    @Test fun mapsCurrentLanguageAndAllSupportedCodecGroups() {
        val aac = group("aac", format("en", 128_000, "mp4a.40.2"))
        val opus = group("opus", format("en", 160_000, "opus"), selected = false)
        val spanish = group("spanish", format("es", 256_000, "mp4a.40.2"), selected = false)
        val state = Media3AudioQuality.state(Tracks(listOf(aac, opus, spanish)), parameters())
        assertEquals(listOf(160_000, 128_000), state.options.map { it.bitrate })
        assertTrue(state.options.all { it.language == "en" })
        assertEquals("AAC · 128 kbps", state.options.single { it.key == state.currentKey }.label)
        assertTrue(state.isAuto)
        assertNull(state.selectedKey)
    }

    @Test fun manualAndAutoAudioParametersDoNotChangeVideoTextOrLanguageParameters() {
        val video = TrackGroup("video", format("", 1_000_000, "avc1.640028", video = true))
        val audio = group("audio", format("en", 256_000, "mp4a.40.2"))
        val override = TrackSelectionOverride(video, 0)
        val initial =
            TrackSelectionParameters
                .Builder()
                .setMaxVideoBitrate(2_000_000)
                .setMaxVideoSize(1920, 1080)
                .setPreferredAudioLanguage("en")
                .setPreferredTextLanguage("ar")
                .setOverrideForType(override)
                .build()
        val selected = Media3AudioQuality.select(initial, Media3AudioQuality.optionsFor(audio).single())
        val auto = Media3AudioQuality.selectAuto(selected)
        for (value in listOf(selected, auto)) {
            assertEquals(initial.maxVideoBitrate, value.maxVideoBitrate)
            assertEquals(initial.maxVideoWidth, value.maxVideoWidth)
            assertEquals(initial.maxVideoHeight, value.maxVideoHeight)
            assertEquals(initial.preferredAudioLanguages, value.preferredAudioLanguages)
            assertEquals(initial.preferredTextLanguages, value.preferredTextLanguages)
            assertEquals(override, value.overrides[video])
            assertEquals(initial.forceHighestSupportedBitrate, value.forceHighestSupportedBitrate)
        }
        assertFalse(auto.overrides.containsKey(audio.mediaTrackGroup))
    }

    @Test fun unsupportedAndUnknownBitrateDoNotBecomeFakeQualityOptions() {
        val unknown = group("unknown", format("en", Format.NO_VALUE, "opus"))
        val unsupported =
            Tracks.Group(
                TrackGroup("bad", format("en", 320_000, "unsupported")),
                false,
                intArrayOf(C.FORMAT_UNSUPPORTED_TYPE),
                booleanArrayOf(false),
            )
        val state = Media3AudioQuality.state(Tracks(listOf(unknown, unsupported)), parameters())
        assertTrue(state.isUnavailable)
        assertTrue(state.options.isEmpty())
    }

    @Test fun sourceCatalogListsRealItagsAndBitratesWithoutDroppingCodecVariants() {
        val aac = source(140, 128_000, "en.4", MediaFormat.M4A, "mp4a.40.2")
        val opus = source(251, 160_000, "en.4", MediaFormat.WEBMA_OPUS, "opus")
        val state = Media3AudioQuality.fromSources(listOf(aac, opus), emptyList(), aac)
        assertEquals(listOf(160_000, 128_000), state.options.map { it.bitrate })
        assertEquals(setOf(140, 251), state.options.map { it.itag }.toSet())
        assertEquals(setOf("AAC", "Opus"), state.options.map { it.codecs }.toSet())
        assertEquals(140, state.options.single { it.key == state.currentKey }.itag)
        assertNull(state.selectedKey)
        assertEquals(251, Media3AudioQuality.bestOption(state)?.itag)
    }

    @Test fun manualPreferenceRestoresByItagThenStableCodecAndBitrateFallback() {
        val aac = source(140, 128_000, "en.4", MediaFormat.M4A, "AAC")
        val opus = source(251, 160_000, "en.4", MediaFormat.WEBMA_OPUS, "Opus")
        val state = Media3AudioQuality.fromSources(listOf(aac, opus), emptyList(), aac)
        assertEquals(251, Media3AudioQuality.findPreference(state, AudioRepresentationPreference("Opus", 160_000, 251))?.itag)
        assertEquals(140, Media3AudioQuality.findPreference(state, AudioRepresentationPreference("AAC", 128_000, null))?.itag)
        assertNull(Media3AudioQuality.findPreference(state, AudioRepresentationPreference("Vorbis", 192_000, 999)))
    }

    @Test fun sourceQualityDoesNotSwitchLanguageOrAnotherDubOfTheSameLanguage() {
        val original = source(140, 128_000, "en.4", MediaFormat.M4A, "mp4a.40.2")
        val originalOpus = source(251, 160_000, "en.4", MediaFormat.WEBMA_OPUS, "opus")
        val differentDub = source(251, 256_000, "en.3", MediaFormat.WEBMA_OPUS, "opus")
        val spanish = source(251, 320_000, "es.3", MediaFormat.WEBMA_OPUS, "opus")
        val state = Media3AudioQuality.fromSources(listOf(original, originalOpus, differentDub, spanish), emptyList(), original)
        assertEquals(2, state.options.size)
        assertTrue(state.options.all { it.sourceStream?.audioTrackId == "en.4" })
        assertEquals(160_000, Media3AudioQuality.bestOption(state)?.bitrate)
    }

    @Test fun autoQualityRestoresBestRepresentationWithinTheSelectedAudioTrack() {
        val englishLow = source(140, 128_000, "en.4", MediaFormat.M4A, "mp4a.40.2")
        val englishHigh = source(251, 160_000, "en.4", MediaFormat.WEBMA_OPUS, "opus")
        val englishDub = source(251, 256_000, "en.3", MediaFormat.WEBMA_OPUS, "opus")
        val spanish = source(251, 320_000, "es.3", MediaFormat.WEBMA_OPUS, "opus")

        val restored =
            StreamProcessor.bestAudioStreamForTrack(
                listOf(englishLow, englishHigh, englishDub, spanish),
                englishLow,
            )

        assertSame(englishHigh, restored)
        assertNull(StreamProcessor.bestAudioStreamForTrack(listOf(englishLow, englishHigh), null))
    }

    @Test fun manualSelectionIsDistinctFromTheCurrentlyPlayingAutomaticRepresentation() {
        val source = source(140, 128_000, "en.4", MediaFormat.M4A, "mp4a.40.2")
        val auto = Media3AudioQuality.fromSources(listOf(source), emptyList(), source)
        val manual = Media3AudioQuality.fromSources(listOf(source), emptyList(), source, auto.currentKey, auto = false)
        assertTrue(auto.isAuto)
        assertFalse(manual.isAuto)
        assertEquals(manual.currentKey, manual.selectedKey)
    }

    @Test fun autoSelectionIndexRemainsAutoWhileARealRepresentationIsPlaying() {
        val source = source(140, 128_000, "en.4", MediaFormat.M4A, "mp4a.40.2")
        val automatic = Media3AudioQuality.fromSources(listOf(source), emptyList(), source)
        val manual = automatic.copy(isAuto = false)

        assertEquals(source.itag, automatic.options.single().itag)
        assertEquals(-1, automatic.selectedOptionIndex())
        assertEquals(0, manual.selectedOptionIndex())
    }

    @Test fun refreshedUrlsKeepStableRepresentationKeysAndUnknownIdsAreNotCalledItags() {
        val old = source(140, 128_000, "en.4", MediaFormat.M4A, "mp4a.40.2", "old")
        val fresh = source(140, 129_000, "en.4", MediaFormat.M4A, "mp4a.40.2", "fresh")
        val before = Media3AudioQuality.fromSources(listOf(old), emptyList(), old)
        val after = Media3AudioQuality.fromSources(listOf(fresh), emptyList(), fresh)
        assertEquals(before.options.single().key, after.options.single().key)
        val numericIdButNotItag =
            AudioStream
                .Builder()
                .setId("99999")
                .setContent("https://example.invalid/audio", true)
                .setMediaFormat(MediaFormat.M4A)
                .setAverageBitrate(128_000)
                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                .build()
        assertNull(
            Media3AudioQuality
                .fromSources(listOf(numericIdButNotItag), emptyList(), numericIdButNotItag)
                .options
                .single()
                .itag,
        )
    }

    private fun source(
        id: Int,
        bitrate: Int,
        track: String,
        format: MediaFormat,
        codec: String,
        url: String = "audio",
    ): AudioStream {
        val itag =
            ItagItem.getItag(id).apply {
                this.codec = codec
                this.bitrate = bitrate
            }
        return AudioStream
            .Builder()
            .setId(id.toString())
            .setItagItem(itag)
            .setContent("https://example.invalid/$track/$id/$url", true)
            .setMediaFormat(format)
            .setAverageBitrate(bitrate)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setAudioTrackId(track)
            .setAudioLocale(Locale.forLanguageTag(track.substringBefore('.')))
            .setAudioTrackType(if (track.endsWith(".4")) AudioTrackType.ORIGINAL else AudioTrackType.DUBBED)
            .build()
    }

    private fun parameters() = TrackSelectionParameters.Builder().build()

    private fun group(
        id: String,
        vararg formats: Format,
        selected: Boolean = true,
    ) = Tracks.Group(
        TrackGroup(id, *formats),
        true,
        IntArray(formats.size) { C.FORMAT_HANDLED },
        BooleanArray(formats.size) { selected && it == 0 },
    )

    private fun format(
        language: String,
        bitrate: Int,
        codec: String,
        video: Boolean = false,
    ) = Format
        .Builder()
        .setSampleMimeType(
            if (video) {
                "video/avc"
            } else if (codec == "opus") {
                "audio/opus"
            } else {
                "audio/mp4a-latm"
            },
        ).setLanguage(language.takeIf { it.isNotBlank() })
        .setAverageBitrate(bitrate)
        .setCodecs(codec)
        .build()
}

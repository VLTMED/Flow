package io.github.aedev.flow.player.sabr.integration

import io.github.aedev.flow.innertube.models.ResponseContext
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.quality.Media3AudioQuality
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SabrAudioQualityTest {
    private val json =
        Json {
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    private val info =
        SabrStreamInfo(
            streamingUrl = "https://example.invalid/sabr",
            audioItag = 140,
            audioLmt = 100,
            videoItag = 137,
            videoLmt = 200,
            audioXtags = "original-audio",
            videoXtags = "original-video",
            durationMs = 60_000,
            audioMimeType = "audio/mp4",
            videoMimeType = "video/mp4",
            audioTrackId = "en.4",
            targetHeight = 1080,
            videoHeight = 1080,
        )

    @Test fun defaultAudioLeavesTheEntireOriginalSessionUnchanged() {
        assertSame(info, info.withAudioQuality(null))
    }

    @Test fun selectedRepresentationChangesOnlyAudioIdentityAndContainer() {
        val selected = audioFormat()
        val result = info.withAudioQuality(selected)
        assertEquals(251, result.audioItag)
        assertEquals(789L, result.audioLmt)
        assertEquals("real-tags", result.audioXtags)
        assertEquals("en.4", result.audioTrackId)
        assertEquals("audio/webm; codecs=\"opus\"", result.audioMimeType)
        assertEquals(info.videoItag, result.videoItag)
        assertEquals(info.videoLmt, result.videoLmt)
        assertEquals(info.videoMimeType, result.videoMimeType)
        assertEquals(info.videoXtags, result.videoXtags)
        assertEquals(info.targetHeight, result.targetHeight)
    }

    @Test fun responseReloadKeepsChosenAudioButUsesFreshVideoAndSessionMetadata() {
        val fresh = info.copy(streamingUrl = "https://example.invalid/new-session", videoLmt = 999, poToken = "fixture-token")
        val result = fresh.withAudioQuality(audioFormat())
        assertEquals(251, result.audioItag)
        assertEquals(999L, result.videoLmt)
        assertEquals(fresh.streamingUrl, result.streamingUrl)
        assertEquals(fresh.poToken, result.poToken)
    }

    @Test fun refreshedPlayerResponseKeepsSelectedAudioItagAndExactDubTrack() {
        val resolved =
            SabrUrlResolver.resolveForQuality(
                playerResponse = refreshedPlayerResponse(),
                targetHeight = 1080,
                preferredAudioItag = 251,
                preferredAudioTrackId = "en.4",
                injectedPoToken = "fresh-token",
                injectedVisitorData = "fresh-visitor",
            )

        assertEquals(251, resolved?.audioItag)
        assertEquals(890L, resolved?.audioLmt)
        assertEquals("fresh-english-tags", resolved?.audioXtags)
        assertEquals("en.4", resolved?.audioTrackId)
        assertEquals(999L, resolved?.videoLmt)
        assertEquals("https://example.invalid/fresh-sabr", resolved?.streamingUrl)
        assertEquals("fresh-token", resolved?.poToken)
    }

    @Test fun parsesRealCodecBitrateAndItagWithoutFabricatingUrlLessChoices() {
        val parsed = audioFormat()
        val unavailable = Media3AudioQuality.fromSources(emptyList(), listOf(parsed), null)
        assertTrue(unavailable.isUnavailable)
        val supportedSabr = Media3AudioQuality.fromSources(emptyList(), listOf(parsed), null, includeUrlLessFormats = true)
        val option = supportedSabr.options.single()
        assertEquals(251, option.itag)
        assertEquals(160_000, option.bitrate)
        assertEquals("Opus · 160 kbps", option.label)
        assertEquals(parsed, option.sourceFormat)
    }

    private fun audioFormat(): PlayerResponse.StreamingData.Format =
        json.decodeFromString(
            """{
            "itag":251,"url":null,"mimeType":"audio/webm; codecs=\"opus\"",
            "bitrate":162000,"averageBitrate":160000,"quality":"tiny","lastModified":789,
            "xtags":"real-tags","audioTrack":{"id":"en.4","displayName":"English","audioIsDefault":true}
        }""",
        )

    private fun refreshedPlayerResponse(): PlayerResponse =
        PlayerResponse(
            responseContext = ResponseContext(visitorData = "fresh-visitor", serviceTrackingParams = emptyList()),
            playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK", reason = null),
            playerConfig =
                PlayerResponse.PlayerConfig(
                    audioConfig = null,
                    mediaCommonConfig =
                        PlayerResponse.PlayerConfig.MediaCommonConfig(
                            mediaUstreamerRequestConfig =
                                PlayerResponse.PlayerConfig.MediaCommonConfig.MediaUstreamerRequestConfig(
                                    videoPlaybackUstreamerConfig = "AQ==",
                                ),
                        ),
                ),
            streamingData =
                PlayerResponse.StreamingData(
                    formats = null,
                    adaptiveFormats =
                        listOf(
                            audioFormat(251, "en.4", 890L, "fresh-english-tags"),
                            audioFormat(251, "es.3", 991L, "fresh-spanish-tags"),
                            videoFormat(),
                        ),
                    expiresInSeconds = 0,
                    serverAbrStreamingUrl = "https://example.invalid/fresh-sabr",
                    hlsManifestUrl = null,
                    dashManifestUrl = null,
                ),
            videoDetails =
                PlayerResponse.VideoDetails(
                    videoId = "fixture-video",
                    title = "Fixture",
                    author = "Fixture Author",
                    channelId = "fixture-channel",
                    lengthSeconds = "60",
                ),
            playbackTracking = null,
        )

    private fun audioFormat(
        itag: Int,
        trackId: String,
        lastModified: Long,
        xtags: String,
    ): PlayerResponse.StreamingData.Format =
        PlayerResponse.StreamingData.Format(
            itag = itag,
            url = null,
            mimeType = "audio/webm; codecs=\"opus\"",
            bitrate = 160_000,
            width = null,
            height = null,
            contentLength = null,
            quality = "tiny",
            fps = null,
            qualityLabel = null,
            averageBitrate = 160_000,
            audioQuality = "AUDIO_QUALITY_MEDIUM",
            approxDurationMs = "60000",
            audioSampleRate = 48_000,
            audioChannels = 2,
            loudnessDb = null,
            lastModified = lastModified,
            signatureCipher = null,
            audioTrack =
                PlayerResponse.StreamingData.Format.AudioTrack(
                    displayName = trackId,
                    id = trackId,
                    audioIsDefault = trackId == "en.4",
                ),
            xtags = xtags,
        )

    private fun videoFormat(): PlayerResponse.StreamingData.Format =
        PlayerResponse.StreamingData.Format(
            itag = 137,
            url = null,
            mimeType = "video/mp4; codecs=\"avc1.640028\"",
            bitrate = 2_000_000,
            width = 1_920,
            height = 1_080,
            contentLength = null,
            quality = "hd1080",
            fps = 30,
            qualityLabel = "1080p",
            averageBitrate = 1_500_000,
            audioQuality = null,
            approxDurationMs = "60000",
            audioSampleRate = null,
            audioChannels = null,
            loudnessDb = null,
            lastModified = 999L,
            signatureCipher = null,
            xtags = "fresh-video-tags",
        )
}

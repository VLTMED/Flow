package io.github.aedev.flow.player.quality

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.player.stream.StreamProcessor
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.player.stream.preferNonDrc
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType

/** A real audio representation from extraction or a Media3 manifest. Bitrates are bits/second. */
data class AudioQualityOption(
    val key: String,
    val label: String,
    val language: String,
    val bitrate: Int,
    val codecs: String?,
    val trackGroup: TrackGroup? = null,
    val trackIndex: Int = -1,
    val itag: Int? = null,
    val sourceStream: AudioStream? = null,
    val sourceFormat: PlayerResponse.StreamingData.Format? = null,
)

data class AudioRepresentationPreference(
    val codec: String?,
    val bitrate: Int?,
    val itag: Int?,
)

data class AudioQualityState(
    val options: List<AudioQualityOption> = emptyList(),
    val selectedKey: String? = null,
    val isAuto: Boolean = true,
    val isUnavailable: Boolean = true,
    val currentKey: String? = selectedKey,
) {
    internal fun selectedOptionIndex(): Int =
        if (isAuto) {
            -1
        } else {
            options.indexOfFirst { it.key == currentKey }
        }
}

object Media3AudioQuality {
    fun state(
        tracks: Tracks,
        parameters: TrackSelectionParameters,
    ): AudioQualityState {
        val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && supportedAudio(it) }
        val selectedGroup = groups.firstOrNull { it.isSelected } ?: groups.firstOrNull()
        val anchor =
            selectedGroup?.let { group ->
                (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat)
                    ?: group.getTrackFormat(0)
            }
        val matchingGroups =
            groups.filter { group ->
                anchor != null &&
                    (0 until group.length).any { index ->
                        val format = group.getTrackFormat(index)
                        format.language.orEmpty() == anchor.language.orEmpty() &&
                            format.roleFlags == anchor.roleFlags && format.label == anchor.label
                    }
            }
        val options = matchingGroups.flatMap(::optionsFor).sortedByDescending { it.bitrate }
        val audioOverrides = parameters.overrides.values.filter { it.mediaTrackGroup.type == C.TRACK_TYPE_AUDIO }
        val selected =
            options.firstOrNull { option ->
                audioOverrides.any { it.mediaTrackGroup == option.trackGroup && option.trackIndex in it.trackIndices }
            }
        val playing =
            options.firstOrNull { option ->
                groups.any { group -> group.mediaTrackGroup == option.trackGroup && group.isTrackSelected(option.trackIndex) }
            }
        return AudioQualityState(
            options = options,
            selectedKey = selected?.key,
            isAuto = audioOverrides.isEmpty(),
            isUnavailable = options.isEmpty(),
            currentKey = playing?.key,
        )
    }

    /** Keep the extractor's full representation ladder, but never change language or dubbing. */
    fun fromSources(
        streams: List<AudioStream>,
        innerTubeFormats: List<PlayerResponse.StreamingData.Format>,
        current: AudioStream?,
        selectedKey: String? = null,
        auto: Boolean = true,
        includeUrlLessFormats: Boolean = false,
    ): AudioQualityState {
        val bridged = InnerTubeStreamBridge.convertAudioFormats(innerTubeFormats)
        val candidates = (streams + bridged).mapNotNull(::sourceOption).toMutableList()
        if (includeUrlLessFormats) {
            innerTubeFormats.preferNonDrc().filter { it.url.isNullOrBlank() && it.isAudio }.forEach { format ->
                val bitrate = (format.averageBitrate ?: format.bitrate).takeIf { it > 0 } ?: return@forEach
                val codec =
                    codecLabel(VideoCodecUtils.codecStringFromMimeType(format.mimeType))
                        .ifBlank { format.mimeType.substringBefore(';').substringAfter('/') }
                val group = formatTrackKey(format)
                candidates +=
                    AudioQualityOption(
                        key = sourceKey(group, format.itag, codec, bitrate),
                        label = qualityLabel(codec, bitrate),
                        language = format.audioLanguageTag.orEmpty(),
                        bitrate = bitrate,
                        codecs = codec,
                        itag = format.itag.takeIf { it > 0 },
                        sourceFormat = format,
                    )
            }
        }
        val anchorKey =
            current?.let(::sourceTrackKey)
                ?: (streams + bridged).firstOrNull { it.audioTrackType == AudioTrackType.ORIGINAL }?.let(::sourceTrackKey)
                ?: candidates.firstOrNull()?.let(::optionTrackKey)
        val options =
            candidates
                .filter { optionTrackKey(it) == anchorKey }
                .distinctBy { it.key }
                .sortedByDescending { it.bitrate }
        val playing =
            current?.let { stream ->
                options.firstOrNull { it.sourceStream?.content == stream.content }
                    ?: options.firstOrNull { it.itag != null && it.itag == stream.itag }
            }
        return AudioQualityState(
            options = options,
            selectedKey = if (auto) null else selectedKey?.takeIf { key -> options.any { it.key == key } } ?: playing?.key,
            isAuto = auto,
            isUnavailable = options.isEmpty(),
            currentKey = playing?.key,
        )
    }

    fun bestOption(state: AudioQualityState): AudioQualityOption? = state.options.maxByOrNull { it.bitrate }

    fun preferenceFor(option: AudioQualityOption): AudioRepresentationPreference =
        AudioRepresentationPreference(option.codecs, option.bitrate.takeIf { it > 0 }, option.itag)

    fun findPreference(
        state: AudioQualityState,
        preference: AudioRepresentationPreference,
    ): AudioQualityOption? =
        preference.itag?.takeIf { it > 0 }?.let { itag ->
            state.options.firstOrNull { it.itag == itag }
        } ?: state.options.firstOrNull { option ->
            val codecMatches =
                preference.codec.isNullOrBlank() ||
                    option.codecs.equals(preference.codec, ignoreCase = true)
            val bitrateMatches = preference.bitrate == null || option.bitrate == preference.bitrate
            codecMatches && bitrateMatches
        }

    fun optionsFor(group: Tracks.Group): List<AudioQualityOption> =
        (0 until group.length)
            .filter { group.isTrackSupported(it) }
            .mapNotNull { index ->
                val format = group.getTrackFormat(index)
                if (format.bitrate <= 0) return@mapNotNull null
                val codec = codecLabel(format.codecs.orEmpty()).ifBlank { format.sampleMimeType.orEmpty().substringAfter('/') }
                AudioQualityOption(
                    key = "manifest|${group.mediaTrackGroup.id}|$index|${format.id.orEmpty()}|${format.bitrate}|${format.codecs.orEmpty()}",
                    label = qualityLabel(codec, format.bitrate),
                    language = format.language.orEmpty(),
                    bitrate = format.bitrate,
                    codecs = codec,
                    trackGroup = group.mediaTrackGroup,
                    trackIndex = index,
                )
            }.sortedByDescending { it.bitrate }

    fun select(
        parameters: TrackSelectionParameters,
        option: AudioQualityOption,
    ): TrackSelectionParameters {
        val group = option.trackGroup ?: return parameters
        require(group.type == C.TRACK_TYPE_AUDIO && option.trackIndex in 0 until group.length)
        return parameters.buildUpon().setOverrideForType(TrackSelectionOverride(group, option.trackIndex)).build()
    }

    /** Only audio overrides are cleared; no global bitrate force flag or video setting is changed. */
    fun selectAuto(parameters: TrackSelectionParameters): TrackSelectionParameters =
        parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()

    private fun sourceOption(stream: AudioStream): AudioQualityOption? {
        if (stream.content.isBlank()) return null
        val bitrate = stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate.takeIf { it > 0 } ?: return null
        val codec = codecLabel(stream.codec.orEmpty()).ifBlank { stream.getFormat()?.name.orEmpty() }
        val itag = stream.itag.takeIf { it > 0 }
        return AudioQualityOption(
            key = sourceKey(sourceTrackKey(stream), itag, codec, bitrate),
            label = qualityLabel(codec, bitrate),
            language = stream.audioLocale?.toLanguageTag().orEmpty(),
            bitrate = bitrate,
            codecs = codec.takeIf { it.isNotBlank() },
            itag = itag,
            sourceStream = stream,
        )
    }

    private fun sourceKey(
        trackKey: String,
        itag: Int?,
        codec: String,
        bitrate: Int,
    ): String = "source|$trackKey|${itag ?: "$codec|$bitrate"}"

    private fun sourceTrackKey(stream: AudioStream): String =
        StreamProcessor.audioTrackGroupingKey(
            trackId = stream.audioTrackId,
            trackName = stream.audioTrackName,
            languageTag = stream.audioLocale?.toLanguageTag(),
            trackType = stream.audioTrackType?.name,
        )

    private fun formatTrackKey(format: PlayerResponse.StreamingData.Format): String =
        StreamProcessor.audioTrackGroupingKey(
            trackId = format.audioTrack?.id,
            trackName = format.audioTrack?.displayName,
            languageTag = format.audioLanguageTag,
            trackType = if (format.isOriginal) AudioTrackType.ORIGINAL.name else AudioTrackType.DUBBED.name,
        )

    private fun optionTrackKey(option: AudioQualityOption): String? =
        option.sourceStream?.let(::sourceTrackKey) ?: option.sourceFormat?.let(::formatTrackKey)

    private fun supportedAudio(group: Tracks.Group): Boolean = group.isSupported && (0 until group.length).any(group::isTrackSupported)

    private fun codecLabel(codec: String): String =
        when {
            codec.startsWith("mp4a", ignoreCase = true) -> "AAC"
            codec.equals("opus", ignoreCase = true) -> "Opus"
            codec.equals("vorbis", ignoreCase = true) -> "Vorbis"
            else -> codec
        }

    private fun qualityLabel(
        codec: String,
        bitrate: Int,
    ): String = listOf(codec.takeIf { it.isNotBlank() }, "${bitrate / 1000} kbps").filterNotNull().joinToString(" · ")
}

package io.github.aedev.flow.ui.components.videoplayer.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.AudioTrackOption
import io.github.aedev.flow.player.QualityOption
import io.github.aedev.flow.player.SubtitleOption
import io.github.aedev.flow.player.state.SubtitleOrigin
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.components.shared.FlowRowGroup
import io.github.aedev.flow.ui.components.shared.FlowSectionHeader
import io.github.aedev.flow.ui.components.shared.FlowSelectionRow
import io.github.aedev.flow.ui.components.shared.MediaAudioTrackRow
import io.github.aedev.flow.ui.components.shared.MediaPlaybackSpeedPicker
import io.github.aedev.flow.ui.components.shared.MediaQualitySelectorContent
import io.github.aedev.flow.ui.components.shared.MediaQualitySelectorOption
import io.github.aedev.flow.ui.components.shared.audioTrackFallbackLabel
import io.github.aedev.flow.ui.components.shared.flowRowGroupShape

@Composable
internal fun PlayerSettingsQualityPage(
    availableQualities: List<QualityOption>,
    currentQuality: Int,
    currentQualityKey: String?,
    useGroupedQualitySelector: Boolean,
    onQualitySelected: (QualityOption) -> Unit,
    availableAudioQualities: List<AudioTrackOption> = emptyList(),
    currentAudioQuality: Int = -1,
    onAudioQualitySelected: (Int) -> Unit = {},
    showAudioQualityTabs: Boolean = false,
) {
    val autoLabel = stringResource(R.string.quality_auto)
    val selectorOptions =
        availableQualities.map { quality ->
            MediaQualitySelectorOption(
                item = quality,
                height = quality.height,
                label = if (quality.height == 0) autoLabel else quality.displayLabel(),
                codecKey = quality.codecKey,
                codecLabel =
                    quality.codecKey
                        .takeIf { it.isNotBlank() }
                        ?.let(VideoCodecUtils::codecLabelFromKey)
                        .orEmpty(),
                selected = quality.isSelected(currentQuality, currentQualityKey),
            )
        }

    if (!showAudioQualityTabs) {
        MediaQualitySelectorContent(
            options = selectorOptions,
            groupedByResolution = useGroupedQualitySelector,
            onOptionSelected = onQualitySelected,
        )
        return
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
        Tab(
            selected = selectedTabIndex == 0,
            onClick = { selectedTabIndex = 0 },
            text = { Text(stringResource(R.string.player_quality_tab_picture)) },
        )
        Tab(
            selected = selectedTabIndex == 1,
            onClick = { selectedTabIndex = 1 },
            text = { Text(stringResource(R.string.player_quality_tab_audio)) },
        )
    }

    when (selectedTabIndex) {
        0 -> {
            MediaQualitySelectorContent(
                options = selectorOptions,
                groupedByResolution = false,
                onOptionSelected = onQualitySelected,
            )
        }

        else -> {
            if (availableAudioQualities.isEmpty()) {
                Text(
                    text = stringResource(R.string.player_quality_audio_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                FlowRowGroup {
                    availableAudioQualities.forEachIndexed { index, track ->
                        val codecLabel =
                            when (track.codecKey) {
                                "aac" -> stringResource(R.string.audio_codec_aac)
                                "opus" -> stringResource(R.string.audio_codec_opus)
                                else -> null
                            }
                        val bitrateKbps =
                            (track.bitrate.coerceAtLeast(0L) / 1000L)
                                .coerceAtMost(Int.MAX_VALUE.toLong())
                                .toInt()
                        val qualityLabel =
                            when {
                                codecLabel != null && bitrateKbps > 0 -> {
                                    stringResource(R.string.audio_quality_format_template, codecLabel, bitrateKbps)
                                }

                                codecLabel != null -> {
                                    codecLabel
                                }

                                bitrateKbps > 0 -> {
                                    stringResource(R.string.audio_bitrate_kbps, bitrateKbps)
                                }

                                else -> {
                                    ""
                                }
                            }
                        MediaAudioTrackRow(
                            label = qualityLabel.ifBlank { audioTrackDisplayLabel(track, index) },
                            supportingText =
                                track.language.takeIf { it.isNotBlank() }
                                    ?: track.label.takeIf { it.isNotBlank() },
                            selected = index == currentAudioQuality,
                            shape = flowRowGroupShape(index, availableAudioQualities.size),
                            onClick = { onAudioQualitySelected(index) },
                        )
                    }
                }
            }
        }
    }
}

private fun QualityOption.displayLabel(): String = label.takeIf { it.isNotBlank() } ?: "${height}p"

private fun QualityOption.isSelected(
    currentQuality: Int,
    currentQualityKey: String?,
): Boolean =
    if (height == 0) {
        currentQuality == 0
    } else {
        streamKey != null && streamKey == currentQualityKey
    }

@Composable
internal fun PlayerSettingsSpeedPage(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onSpeedSelectionFinished: () -> Unit,
) {
    val context = LocalContext.current
    val playerPrefs =
        remember {
            io.github.aedev.flow.data.local
                .PlayerPreferences(context)
        }
    val customSpeedsEnabled by playerPrefs.customSpeedsEnabled.collectAsState(initial = false)
    val customSpeedPresetsRaw by playerPrefs.customSpeedPresets.collectAsState(initial = "")
    val speedSliderEnabled by playerPrefs.speedSliderEnabled.collectAsState(initial = false)

    MediaPlaybackSpeedPicker(
        currentSpeed = currentSpeed,
        sliderEnabled = speedSliderEnabled,
        customSpeedsEnabled = customSpeedsEnabled,
        customSpeedPresetsRaw = customSpeedPresetsRaw,
        onSpeedSelected = onSpeedSelected,
        onSpeedRowSelected = { onSpeedSelectionFinished() },
    )
}

@Composable
internal fun PlayerSettingsAudioPage(
    availableAudioTracks: List<AudioTrackOption>,
    currentAudioTrack: Int,
    onTrackSelected: (Int) -> Unit,
) {
    if (availableAudioTracks.isEmpty()) {
        Text(
            text = stringResource(R.string.player_audio_tracks_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    } else {
        FlowRowGroup {
            availableAudioTracks.forEachIndexed { index, track ->
                MediaAudioTrackRow(
                    label = audioTrackDisplayLabel(track, index),
                    supportingText = track.language.takeIf { it.isNotBlank() },
                    selected = index == currentAudioTrack,
                    shape = flowRowGroupShape(index, availableAudioTracks.size),
                    onClick = { onTrackSelected(index) },
                )
            }
        }
    }
}

@Composable
internal fun PlayerSettingsSubtitlesPage(
    availableSubtitles: List<SubtitleOption>,
    selectedSubtitleUrl: String?,
    subtitlesEnabled: Boolean,
    onSubtitleSelected: (Int) -> Unit,
    onDisableSubtitles: () -> Unit,
    onShowStyleCustomizer: () -> Unit,
    onAddSubtitleFile: (() -> Unit)? = null,
    subtitleOffsetMs: Long = 0L,
    onSubtitleOffsetChange: ((Long) -> Unit)? = null,
) {
    val rowCount = availableSubtitles.size + 1
    FlowRowGroup {
        FlowSelectionRow(
            title = stringResource(R.string.off),
            selected = !subtitlesEnabled,
            shape = flowRowGroupShape(0, rowCount),
            onClick = onDisableSubtitles,
        )
        availableSubtitles.forEachIndexed { index, subtitle ->
            FlowSelectionRow(
                title = subtitleRowTitle(subtitle, index),
                supportingText =
                    when (subtitle.origin) {
                        SubtitleOrigin.FILE -> {
                            subtitle.detail?.takeIf { it != subtitle.label }
                                ?: stringResource(R.string.subtitle_origin_file)
                        }

                        SubtitleOrigin.EMBEDDED -> {
                            stringResource(R.string.subtitle_origin_embedded)
                        }

                        SubtitleOrigin.ONLINE -> {
                            subtitle.language.takeIf { it.isNotBlank() }
                        }
                    },
                selected = subtitle.url == selectedSubtitleUrl && subtitlesEnabled,
                shape = flowRowGroupShape(index + 1, rowCount),
                onClick = { onSubtitleSelected(index) },
            )
        }
    }
    if (onSubtitleOffsetChange != null && subtitlesEnabled) {
        Spacer(modifier = Modifier.height(SubtitleStyleRowSpacing))
        SubtitleTimingCard(offsetMs = subtitleOffsetMs, onOffsetChange = onSubtitleOffsetChange)
    }
    Spacer(modifier = Modifier.height(SubtitleStyleRowSpacing))
    val navRows = if (onAddSubtitleFile != null) 2 else 1
    FlowRowGroup {
        if (onAddSubtitleFile != null) {
            FlowNavRow(
                title = stringResource(R.string.subtitle_add_file),
                leadingIcon = Icons.Filled.FileOpen,
                shape = flowRowGroupShape(0, navRows),
                onClick = onAddSubtitleFile,
            )
        }
        FlowNavRow(
            title = stringResource(R.string.subtitle_style),
            leadingIcon = Icons.Filled.Tune,
            shape = flowRowGroupShape(navRows - 1, navRows),
            onClick = onShowStyleCustomizer,
        )
    }
}

/** The track's name, with what kind of track it is when that is not a plain authored one. */
@Composable
private fun subtitleRowTitle(
    subtitle: SubtitleOption,
    index: Int,
): String {
    val name = subtitle.label.ifBlank { stringResource(R.string.subtitle_track_number, index + 1) }
    val kind =
        when {
            subtitle.isTranslated -> stringResource(R.string.subtitle_translated)
            subtitle.isAutoGenerated -> stringResource(R.string.quality_auto)
            subtitle.isForced -> stringResource(R.string.subtitle_forced)
            else -> return name
        }
    return stringResource(R.string.subtitle_auto_generated_template, name, kind)
}

private val SubtitleStyleRowSpacing = 12.dp

@Composable
internal fun audioTrackDisplayLabel(
    track: AudioTrackOption?,
    fallbackIndex: Int,
): String = track?.label?.takeIf { it.isNotBlank() } ?: audioTrackFallbackLabel(fallbackIndex)

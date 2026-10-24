package io.github.aedev.flow.ui.components.musicplayer.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.AudioCodecPreference
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.localmedia.LocalMediaIds
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.components.equalizer.EqQuickPanel
import io.github.aedev.flow.ui.components.equalizer.PanelRow
import io.github.aedev.flow.ui.components.layout.navigation.LocalMediaNavigator
import io.github.aedev.flow.ui.components.shared.FlowModalSheetDefaults
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.components.shared.FlowRowGroup
import io.github.aedev.flow.ui.components.shared.FlowSectionHeader
import io.github.aedev.flow.ui.components.shared.FlowSelectionRow
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.FlowSwitchRow
import io.github.aedev.flow.ui.components.shared.flowRowGroupShape
import io.github.aedev.flow.ui.components.shared.rememberFlowSheetState
import io.github.aedev.flow.utils.MusicPlayerUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TEMPO_ROWS = 3
private const val SPEED_STEPS = 34
private const val PITCH_STEPS = 23
private val SliderRowPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

/** The music player's audio settings: the equalizer panel, tempo and pitch, and loudness normalisation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsSheet(onDismiss: () -> Unit) {
    val speed by EnhancedMusicPlayerManager.playbackSpeed.collectAsState()
    val pitch by EnhancedMusicPlayerManager.playbackPitch.collectAsState()
    val currentTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()
    val currentPlayer by EnhancedMusicPlayerManager.playerInstance.collectAsState()
    val navigator = LocalMediaNavigator.current
    val context = LocalContext.current
    val preferences = remember { PlayerPreferences(context) }
    val normalizationEnabled by preferences.musicLoudnessNormalizationEnabled.collectAsState(initial = true)
    val preferredFormatItag by preferences.musicAudioFormatItag.collectAsState(initial = 0)
    val audioQualityByVideoId by MusicPlayerUtils.audioQualityByVideoId.collectAsState()
    val scope = rememberCoroutineScope()
    val currentVideoId = currentTrack?.videoId
    val currentAudioQuality = currentVideoId?.let(audioQualityByVideoId::get)
    val currentOption = currentAudioQuality?.let { state -> state.options.firstOrNull { it.itag == state.selectedItag } }
    val isStreamedTrack =
        currentVideoId != null &&
            !LocalMediaIds.isLocal(currentVideoId) &&
            currentPlayer
                ?.currentMediaItem
                ?.localConfiguration
                ?.uri
                ?.scheme == "music"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        modifier = FlowModalSheetDefaults.modifier,
        contentWindowInsets = FlowModalSheetDefaults.contentWindowInsets,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
        ) {
            FlowSheetHeader(title = stringResource(R.string.audio_settings_title), onClose = onDismiss, showDragHandle = false)

            FlowSectionHeader(stringResource(R.string.equalizer))
            EqQuickPanel(
                onEdit = {
                    onDismiss()
                    navigator.openEqualizer()
                },
            )

            FlowSectionHeader(stringResource(R.string.label_tempo_pitch))
            FlowRowGroup {
                PanelRow(shape = flowRowGroupShape(0, TEMPO_ROWS)) {
                    SliderRow(
                        label = stringResource(R.string.template_speed, (speed * 100).roundToInt()),
                        value = speed,
                        onValueChange = EnhancedMusicPlayerManager::setPlaybackSpeed,
                        range = 0.25f..2.0f,
                        steps = SPEED_STEPS,
                    )
                }
                PanelRow(shape = flowRowGroupShape(1, TEMPO_ROWS)) {
                    SliderRow(
                        label = stringResource(R.string.template_pitch, pitch.roundToInt()),
                        value = pitch,
                        onValueChange = EnhancedMusicPlayerManager::setPlaybackPitch,
                        range = -12f..12f,
                        steps = PITCH_STEPS,
                    )
                }
                FlowNavRow(
                    title = stringResource(R.string.action_reset),
                    onClick = {
                        EnhancedMusicPlayerManager.setPlaybackSpeed(1f)
                        EnhancedMusicPlayerManager.setPlaybackPitch(0f)
                    },
                    leadingIcon = Icons.Filled.Refresh,
                    showChevron = false,
                    shape = flowRowGroupShape(2, TEMPO_ROWS),
                )
            }

            if (isStreamedTrack) {
                FlowSectionHeader(stringResource(R.string.player_audio_quality))
                if (currentAudioQuality == null || currentAudioQuality.options.isEmpty()) {
                    Text(
                        text = stringResource(R.string.music_audio_quality_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                } else {
                    val currentLabel =
                        if (currentOption != null) {
                            audioQualityLabel(currentOption)
                        } else {
                            stringResource(R.string.audio_codec_other)
                        }
                    Text(
                        text = stringResource(R.string.music_audio_quality_current, currentLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                    if (preferredFormatItag > 0 && currentAudioQuality.selectedItag != preferredFormatItag) {
                        Text(
                            text = stringResource(R.string.music_audio_quality_format_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
                    val options = currentAudioQuality.options.distinctBy { it.codecKey to it.bitrateKbps }.take(10)
                    val rowCount = options.size + 1
                    FlowRowGroup(modifier = Modifier.padding(top = 8.dp)) {
                        FlowSelectionRow(
                            title = stringResource(R.string.audio_codec_auto),
                            supportingText = stringResource(R.string.music_audio_quality_auto_desc),
                            selected = preferredFormatItag == 0,
                            onClick = { scope.launch { preferences.setMusicAudioFormatItag(0) } },
                            shape = flowRowGroupShape(0, rowCount),
                        )
                        options.forEachIndexed { index, option ->
                            FlowSelectionRow(
                                title = audioQualityLabel(option),
                                selected = preferredFormatItag == option.itag,
                                onClick = { scope.launch { preferences.setMusicAudioFormatItag(option.itag) } },
                                shape = flowRowGroupShape(index + 1, rowCount),
                            )
                        }
                    }
                }
            }

            FlowRowGroup(modifier = Modifier.padding(top = 16.dp)) {
                FlowSwitchRow(
                    title = stringResource(R.string.music_normalize_volume_title),
                    supportingText = stringResource(R.string.music_normalize_volume_desc),
                    checked = normalizationEnabled,
                    onCheckedChange = { enabled -> scope.launch { preferences.setMusicLoudnessNormalizationEnabled(enabled) } },
                    leadingIcon = Icons.Filled.GraphicEq,
                    shape = flowRowGroupShape(0, 1),
                )
            }
        }
    }
}

@Composable
private fun audioQualityLabel(option: MusicPlayerUtils.MusicAudioQualityOption): String {
    val codecLabel =
        when (option.codecKey) {
            AudioCodecPreference.AAC.key -> stringResource(R.string.audio_codec_aac)
            AudioCodecPreference.OPUS.key -> stringResource(R.string.audio_codec_opus)
            else -> stringResource(R.string.audio_codec_other)
        }
    return if (option.bitrateKbps > 0) {
        stringResource(R.string.audio_quality_format_template, codecLabel, option.bitrateKbps)
    } else {
        codecLabel
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(SliderRowPadding)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}

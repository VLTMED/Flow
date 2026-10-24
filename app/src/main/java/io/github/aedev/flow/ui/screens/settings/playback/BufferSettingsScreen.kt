package io.github.aedev.flow.ui.screens.settings.playback

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.BufferProfile
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.config.VideoBufferDurationBudget
import io.github.aedev.flow.player.config.VideoBufferMemoryBudget
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsGroupScope
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.info
import io.github.aedev.flow.ui.components.settings.option
import io.github.aedev.flow.ui.components.settings.slider
import io.github.aedev.flow.ui.screens.settings.index.BufferIndex
import java.util.Locale
import kotlin.math.roundToInt

private const val MS_PER_SECOND = 1000f
private const val MEMORY_SLIDER_STEP_MB = 8
private val PresetProfiles = listOf(BufferProfile.STABLE, BufferProfile.AGGRESSIVE, BufferProfile.DATASAVER)

/** Forward buffering, retained back-buffer and the RAM target for the main video player. */
@Composable
internal fun BufferSettingsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: BufferSettingsViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val minBuffer by viewModel.minBufferMs.collectAsStateWithLifecycle()
    val maxBuffer by viewModel.maxBufferMs.collectAsStateWithLifecycle()
    val startBuffer by viewModel.startBufferMs.collectAsStateWithLifecycle()
    val rebuffer by viewModel.rebufferMs.collectAsStateWithLifecycle()
    val memoryBudgetMb by viewModel.videoBufferMemoryMb.collectAsStateWithLifecycle()
    val backBufferMs by viewModel.videoBackBufferMs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activityManager = remember(context) { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
    val runtimeMaxMemoryBytes = remember { Runtime.getRuntime().maxMemory() }
    val resolvedMemoryBudget =
        remember(memoryBudgetMb, activityManager?.memoryClass, activityManager?.isLowRamDevice, runtimeMaxMemoryBytes) {
            VideoBufferMemoryBudget.resolve(
                memoryClassMb = activityManager?.memoryClass ?: 256,
                isLowRamDevice = activityManager?.isLowRamDevice == true,
                requestedMemoryMb = memoryBudgetMb,
                runtimeMaxMemoryBytes = runtimeMaxMemoryBytes,
            )
        }
    val resolvedBufferDurationBudget =
        remember(resolvedMemoryBudget, minBuffer, maxBuffer, startBuffer, rebuffer) {
            VideoBufferDurationBudget.resolve(
                memoryBudget = resolvedMemoryBudget,
                minBufferMs = minBuffer,
                maxForwardBufferMs = maxBuffer,
                bufferForPlaybackMs = startBuffer,
                bufferForRebufferMs = rebuffer,
            )
        }
    val effectiveBackBufferMs = VideoBufferMemoryBudget.effectiveBackBufferMs(resolvedMemoryBudget, backBufferMs)
    val forwardBufferAppliedTitle = stringResource(R.string.buffer_forward_buffer_applied_title)
    val forwardBufferAppliedValue =
        stringResource(
            R.string.buffer_forward_buffer_applied_value,
            secondsText(maxBuffer),
            secondsText(resolvedBufferDurationBudget.durations.minMs),
        )
    val backBufferAppliedTitle = stringResource(R.string.buffer_back_buffer_applied_title)
    val backBufferAppliedValue =
        stringResource(
            R.string.buffer_back_buffer_applied_value,
            secondsText(backBufferMs),
            secondsText(effectiveBackBufferMs),
        )
    val memoryAppliedTitle = stringResource(R.string.buffer_memory_applied_title)
    val requestedMemoryValue =
        if (memoryBudgetMb <= 0) {
            stringResource(R.string.buffer_memory_device_default, resolvedMemoryBudget.defaultMegabytes)
        } else {
            stringResource(
                R.string.buffer_memory_megabytes,
                memoryBudgetMb.coerceIn(0, PlayerConfig.MAX_SELECTABLE_MAIN_TARGET_BUFFER_MIB),
            )
        }
    val memoryAppliedValue =
        stringResource(
            R.string.buffer_memory_applied_value,
            requestedMemoryValue,
            resolvedMemoryBudget.targetMegabytes,
            resolvedMemoryBudget.capMegabytes,
            resolvedMemoryBudget.processHeapMaxMegabytes,
        )
    val profileLabels =
        mapOf(
            BufferProfile.STABLE to profileLabel(BufferProfile.STABLE),
            BufferProfile.AGGRESSIVE to profileLabel(BufferProfile.AGGRESSIVE),
            BufferProfile.DATASAVER to profileLabel(BufferProfile.DATASAVER),
            BufferProfile.CUSTOM to profileLabel(BufferProfile.CUSTOM),
        )

    SettingsPage(
        title = stringResource(R.string.buffer_settings_title),
        onBack = onBack,
        highlight = highlight,
    ) {
        group(key = BufferIndex.profile.key, header = R.string.settings_buffer_profiles, footer = R.string.buffer_settings_desc) {
            PresetProfiles.forEach { preset ->
                option(
                    key = "buffer.profile.${preset.name}",
                    label = profileLabels.getValue(preset).first,
                    supportingText = profileLabels.getValue(preset).second,
                    selected = profile == preset,
                    onClick = { viewModel.setProfile(preset) },
                )
            }
        }
        group(key = "buffer.custom", header = R.string.buffer_settings_header_custom, footer = R.string.buffer_custom_mode_desc) {
            option(
                key = "buffer.profile.CUSTOM",
                label = profileLabels.getValue(BufferProfile.CUSTOM).first,
                supportingText = profileLabels.getValue(BufferProfile.CUSTOM).second,
                selected = profile == BufferProfile.CUSTOM,
                onClick = { viewModel.setProfile(BufferProfile.CUSTOM) },
            )
            if (profile == BufferProfile.CUSTOM) {
                millisSlider(
                    BufferIndex.maxBuffer,
                    maxBuffer,
                    PlayerConfig.MIN_SELECTABLE_MAIN_BUFFER_MS..PlayerConfig.MAX_SAFE_MAIN_BUFFER_MS,
                    5_000,
                    viewModel::setMaxBuffer,
                )
                millisSlider(BufferIndex.startBuffer, startBuffer, 500..5_000, 500, viewModel::setStartBuffer)
                millisSlider(BufferIndex.rebuffer, rebuffer, 1_000..10_000, 500, viewModel::setRebuffer)
            }
        }
        group(
            key = "buffer.forward.group",
            header = R.string.buffer_forward_buffer_header,
            footer = R.string.buffer_forward_buffer_desc,
        ) {
            info(
                key = "buffer.forward.effective",
                title = forwardBufferAppliedTitle,
                value = forwardBufferAppliedValue,
            )
        }
        group(
            key = "buffer.back.group",
            header = R.string.buffer_back_buffer_header,
        ) {
            millisSlider(
                entry = BufferIndex.backBuffer,
                valueMs = backBufferMs,
                range = 0..PlayerConfig.MAX_SAFE_BACK_BUFFER_DURATION_MS,
                stepMs = 5_000,
                onCommit = viewModel::setVideoBackBufferMs,
            )
            info(
                key = "buffer.back.effective",
                title = backBufferAppliedTitle,
                value = backBufferAppliedValue,
            )
        }
        group(
            key = "buffer.memory.group",
            header = R.string.buffer_memory_header,
            footer = R.string.buffer_memory_desc,
        ) {
            memorySlider(
                entry = BufferIndex.memory,
                requestedMb = memoryBudgetMb,
                effectiveDefaultMb = resolvedMemoryBudget.defaultMegabytes,
                maximumSelectableMb = PlayerConfig.MAX_SELECTABLE_MAIN_TARGET_BUFFER_MIB,
                onCommit = viewModel::setVideoBufferMemoryMb,
            )
            info(
                key = "buffer.memory.effective",
                title = memoryAppliedTitle,
                value = memoryAppliedValue,
            )
        }
    }
}

private fun SettingsGroupScope.millisSlider(
    entry: SettingEntry,
    valueMs: Int,
    range: IntRange,
    stepMs: Int,
    onCommit: (Int) -> Unit,
) = slider(
    entry = entry,
    value = valueMs.coerceIn(range).toFloat(),
    onValueCommitted = { committed -> onCommit(((committed / stepMs).roundToInt() * stepMs).coerceIn(range)) },
    valueRange = range.first.toFloat()..range.last.toFloat(),
    steps = ((range.last - range.first) / stepMs - 1).coerceAtLeast(0),
    valueLabel = { stringResource(R.string.settings_seconds_value, secondsText(it.roundToInt())) },
)

private fun SettingsGroupScope.memorySlider(
    entry: SettingEntry,
    requestedMb: Int,
    effectiveDefaultMb: Int,
    maximumSelectableMb: Int,
    onCommit: (Int) -> Unit,
) = slider(
    entry = entry,
    value = requestedMb.coerceIn(0, maximumSelectableMb).toFloat(),
    onValueCommitted = { rawValue ->
        val selectedMb = rawValue.roundToInt()
        val steppedMb =
            if (selectedMb == 0) {
                0
            } else if (selectedMb >= maximumSelectableMb - MEMORY_SLIDER_STEP_MB / 2) {
                maximumSelectableMb
            } else {
                ((selectedMb.toFloat() / MEMORY_SLIDER_STEP_MB).roundToInt() * MEMORY_SLIDER_STEP_MB)
            }
        onCommit(steppedMb.coerceIn(0, maximumSelectableMb))
    },
    valueRange = 0f..maximumSelectableMb.toFloat(),
    steps = (maximumSelectableMb / MEMORY_SLIDER_STEP_MB - 1).coerceAtLeast(0),
    valueLabel = { rawValue ->
        val selectedMb = rawValue.roundToInt()
        if (selectedMb == 0) {
            stringResource(R.string.buffer_memory_default_value, effectiveDefaultMb)
        } else {
            stringResource(R.string.buffer_memory_megabytes, selectedMb)
        }
    },
)

private fun secondsText(ms: Int): String =
    String.format(Locale.getDefault(), "%.1f", ms / MS_PER_SECOND).removeSuffix(".0").removeSuffix(",0")

@Composable
private fun profileLabel(profile: BufferProfile): Pair<String, String?> =
    when (profile) {
        BufferProfile.STABLE -> {
            stringResource(R.string.buffer_profile_stable) to profileSummary(R.string.buffer_desc_stable, profile)
        }

        BufferProfile.AGGRESSIVE -> {
            stringResource(R.string.buffer_profile_aggressive) to
                profileSummary(R.string.buffer_desc_aggressive, profile)
        }

        BufferProfile.DATASAVER -> {
            stringResource(R.string.buffer_profile_datasaver) to
                profileSummary(R.string.buffer_desc_datasaver, profile)
        }

        BufferProfile.CUSTOM -> {
            stringResource(R.string.buffer_profile_custom) to stringResource(R.string.buffer_profile_custom_desc)
        }
    }

@Composable
private fun profileSummary(
    description: Int,
    profile: BufferProfile,
): String =
    stringResource(
        R.string.settings_value_pair,
        stringResource(description),
        stringResource(R.string.settings_buffer_range, secondsText(profile.minBuffer), secondsText(profile.maxBuffer)),
    )

package io.github.aedev.flow.ui.screens.settings.playback

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.BufferProfile
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import javax.inject.Inject

/** The video player's buffer: a preset profile, or four custom durations. */
@HiltViewModel
class BufferSettingsViewModel
    @Inject
    constructor(
        private val preferences: PlayerPreferences,
    ) : SettingsViewModel() {
        val profile = preferences.bufferProfile.asState(BufferProfile.STABLE)
        val minBufferMs = preferences.minBufferMs.asState(BufferProfile.STABLE.minBuffer)
        val maxBufferMs = preferences.maxBufferMs.asState(BufferProfile.STABLE.maxBuffer)
        val startBufferMs = preferences.bufferForPlaybackMs.asState(BufferProfile.STABLE.playbackBuffer)
        val rebufferMs = preferences.bufferForPlaybackAfterRebufferMs.asState(BufferProfile.STABLE.rebufferBuffer)
        val videoBufferMemoryMb = preferences.videoBufferMemoryMb.asState(0)
        val videoBackBufferMs = preferences.videoBackBufferMs.asState(PlayerConfig.BACK_BUFFER_DURATION_MS)

        fun setProfile(value: BufferProfile) =
            write {
                preferences.setBufferProfile(value)
                if (value != BufferProfile.CUSTOM) {
                    preferences.setMinBufferMs(value.minBuffer)
                    preferences.setMaxBufferMs(value.maxBuffer)
                    preferences.setBufferForPlaybackMs(value.playbackBuffer)
                    preferences.setBufferForPlaybackAfterRebufferMs(value.rebufferBuffer)
                }
            }

        fun setMaxBuffer(ms: Int) =
            write {
                preferences.setMaxBufferMs(ms)
                if (minBufferMs.value > ms) preferences.setMinBufferMs(ms)
            }

        fun setStartBuffer(ms: Int) = write { preferences.setBufferForPlaybackMs(ms) }

        fun setRebuffer(ms: Int) = write { preferences.setBufferForPlaybackAfterRebufferMs(ms) }

        fun setVideoBufferMemoryMb(megabytes: Int) = write { preferences.setVideoBufferMemoryMb(megabytes) }

        fun setVideoBackBufferMs(ms: Int) = write { preferences.setVideoBackBufferMs(ms) }
    }

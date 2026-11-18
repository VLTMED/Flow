package io.github.aedev.flow.ui.components.videoplayer.motion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MiniPlayerAlignmentTest {
    @Test
    fun enabledPreferenceUsesLeftOnlyInRtlPortrait() {
        assertThat(
            shouldUseLeftAlignedMiniPlayer(enabled = true, isRtl = false, isPortrait = true),
        ).isFalse()
        assertThat(
            shouldUseLeftAlignedMiniPlayer(enabled = true, isRtl = false, isPortrait = false),
        ).isFalse()
        assertThat(
            shouldUseLeftAlignedMiniPlayer(enabled = true, isRtl = true, isPortrait = true),
        ).isTrue()
        assertThat(
            shouldUseLeftAlignedMiniPlayer(enabled = true, isRtl = true, isPortrait = false),
        ).isFalse()
    }

    @Test
    fun disabledPreferenceDoesNotForceLeftAlignment() {
        assertThat(
            shouldUseLeftAlignedMiniPlayer(enabled = false, isRtl = false, isPortrait = true),
        ).isFalse()
        assertThat(
            shouldUseLeftAlignedMiniPlayer(enabled = false, isRtl = true, isPortrait = true),
        ).isFalse()
    }
}

package io.github.aedev.flow.ui.tv.player

import android.view.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.tv.input.TvPlayerAction
import io.github.aedev.flow.ui.tv.player.state.TvOverlayMode
import org.junit.Test

class TvPlayerInputTest {
    @Test
    fun hiddenOverlayKeepsPlaybackSeekAndDpadRevealActions() {
        val actions = mutableListOf<TvPlayerAction>()
        val input =
            TvPlayerInput(
                overlayMode = { TvOverlayMode.HIDDEN },
                seekBarFocused = { false },
                isScrubbing = { true },
                perform = { action, _ -> actions += action },
                onInteraction = {},
                onDownFromControls = { true },
            )

        assertThat(input.onKey(KeyEventType.KeyDown, KeyEvent.KEYCODE_DPAD_CENTER, 0)).isTrue()
        assertThat(input.onKey(KeyEventType.KeyDown, KeyEvent.KEYCODE_DPAD_RIGHT, 0)).isTrue()
        assertThat(input.onKey(KeyEventType.KeyUp, KeyEvent.KEYCODE_DPAD_RIGHT, 0)).isTrue()
        assertThat(input.onKey(KeyEventType.KeyDown, KeyEvent.KEYCODE_DPAD_DOWN, 0)).isTrue()

        assertThat(actions)
            .containsExactly(
                TvPlayerAction.TOGGLE_PLAYBACK,
                TvPlayerAction.SCRUB_FORWARD,
                TvPlayerAction.COMMIT_SCRUB,
                TvPlayerAction.SHOW_TRANSPORT,
            ).inOrder()
    }
}

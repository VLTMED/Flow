package io.github.aedev.flow.ui.components.videoplayer.gesture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerSpeedBoostTest {
    @Test
    fun `long press starts at the configured speed within supported bounds`() {
        assertThat(PlayerSpeedBoost.longPressSpeed(2.0f)).isEqualTo(2.0f)
        assertThat(PlayerSpeedBoost.longPressSpeed(0.05f)).isEqualTo(0.1f)
        assertThat(PlayerSpeedBoost.longPressSpeed(10.0f)).isEqualTo(4.0f)
    }

    @Test
    fun `horizontal drag requires a threshold and steps by half speed`() {
        val changes = mutableListOf<Float>()
        val drag = PlayerSpeedBoostDragController(baseSpeed = 2.0f, stepDistancePx = 48f, onSpeedChanged = changes::add)

        drag.dragBy(47f)
        assertThat(drag.speed).isEqualTo(2.0f)
        assertThat(changes).isEmpty()

        drag.dragBy(1f)
        assertThat(drag.speed).isEqualTo(2.5f)
        drag.dragBy(144f)

        assertThat(drag.speed).isEqualTo(4.0f)
        assertThat(changes).containsExactly(2.5f, 3.0f, 3.5f, 4.0f).inOrder()

        drag.dragBy(48f)
        assertThat(drag.speed).isEqualTo(4.0f)
        drag.dragBy(-48f)
        assertThat(drag.speed).isEqualTo(3.5f)
    }

    @Test
    fun `drag down is bounded by the minimum speed after four levels`() {
        val changes = mutableListOf<Float>()
        val drag = PlayerSpeedBoostDragController(baseSpeed = 2.0f, stepDistancePx = 48f, onSpeedChanged = changes::add)

        drag.dragBy(-192f)

        assertThat(drag.speed).isEqualTo(0.1f)
        assertThat(changes).containsExactly(1.5f, 1.0f, 0.5f, 0.1f).inOrder()
        drag.dragBy(-48f)
        assertThat(drag.speed).isEqualTo(0.1f)
        drag.dragBy(48f)
        assertThat(drag.speed).isEqualTo(0.5f)
    }

    @Test
    fun `drag does not exceed a base at the maximum or fall below a base at the minimum`() {
        val maximum = PlayerSpeedBoostDragController(baseSpeed = 4.0f, stepDistancePx = 48f, onSpeedChanged = {})
        maximum.dragBy(192f)
        assertThat(maximum.speed).isEqualTo(4.0f)
        maximum.dragBy(-48f)
        assertThat(maximum.speed).isEqualTo(3.5f)

        val minimum = PlayerSpeedBoostDragController(baseSpeed = 0.1f, stepDistancePx = 48f, onSpeedChanged = {})
        minimum.dragBy(-192f)
        assertThat(minimum.speed).isEqualTo(0.1f)
        minimum.dragBy(48f)
        assertThat(minimum.speed).isEqualTo(0.6f)
    }

    @Test
    fun `ordinary horizontal seek is retained only when no boost is active`() {
        assertThat(shouldAcceptHorizontalSeekDrag(isSpeedBoostActive = false, seekSwipeGesturesEnabled = true, durationMs = 60_000L))
            .isTrue()
        assertThat(shouldAcceptHorizontalSeekDrag(isSpeedBoostActive = true, seekSwipeGesturesEnabled = true, durationMs = 60_000L))
            .isFalse()
        assertThat(shouldAcceptHorizontalSeekDrag(isSpeedBoostActive = false, seekSwipeGesturesEnabled = false, durationMs = 60_000L))
            .isFalse()
        assertThat(shouldAcceptHorizontalSeekDrag(isSpeedBoostActive = false, seekSwipeGesturesEnabled = true, durationMs = 0L))
            .isFalse()
    }
}

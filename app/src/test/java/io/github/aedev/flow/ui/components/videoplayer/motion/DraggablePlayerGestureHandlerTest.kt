package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.animation.core.Animatable
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.components.videoplayer.MiniPlayerCorner
import io.github.aedev.flow.ui.components.videoplayer.PlayerDraggableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DraggablePlayerGestureHandlerTest {
    @Test
    fun `release commits left target and fires collapse callback exactly once`() =
        runTest {
            val state = newState(this)
            state.expandFraction.snapTo(0.21f)
            state.preferLeftAlignedMiniPlayer = true
            state.leftAlignedTargetX = 24f
            state.cachedTargetX = 570f
            state.cachedTargetY = 2200f
            var callbackCount = 0
            val metrics = DraggablePlayerGestureMetrics().apply { onCollapseGesture = { callbackCount++ } }

            commitCollapseRelease(state, metrics, velocityY = 0f, collapseTravel = 2200f)

            assertThat(callbackCount).isEqualTo(1)
            assertThat(state.corner).isEqualTo(MiniPlayerCorner.BottomLeft)
            assertThat(state.cachedTargetX).isEqualTo(24f)
            state.scope.cancel()
        }

    @Test
    fun `release preserves an explicit right preference`() =
        runTest {
            val state = newState(this)
            state.expandFraction.snapTo(0.21f)
            state.preferLeftAlignedMiniPlayer = false
            state.leftAlignedTargetX = 24f
            state.cachedTargetX = 570f
            state.cachedTargetY = 2200f
            var callbackCount = 0
            val metrics = DraggablePlayerGestureMetrics().apply { onCollapseGesture = { callbackCount++ } }

            commitCollapseRelease(state, metrics, velocityY = 0f, collapseTravel = 2200f)

            assertThat(callbackCount).isEqualTo(1)
            assertThat(state.corner).isEqualTo(MiniPlayerCorner.BottomRight)
            assertThat(state.cachedTargetX).isEqualTo(570f)
            state.scope.cancel()
        }

    @Test
    fun `a release below the collapse threshold does not call collapse callback`() =
        runTest {
            val state = newState(this)
            state.expandFraction.snapTo(0.12f)
            var callbackCount = 0
            val metrics = DraggablePlayerGestureMetrics().apply { onCollapseGesture = { callbackCount++ } }

            commitCollapseRelease(state, metrics, velocityY = 600f, collapseTravel = 1000f)

            assertThat(callbackCount).isEqualTo(0)
            state.scope.cancel()
        }

    private fun newState(scope: CoroutineScope) =
        PlayerDraggableState(
            offsetX = Animatable(0f),
            offsetY = Animatable(0f),
            expandFraction = Animatable(0f),
            scope = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job])),
        )
}

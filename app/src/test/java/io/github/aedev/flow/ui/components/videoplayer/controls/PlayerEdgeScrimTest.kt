package io.github.aedev.flow.ui.components.videoplayer.controls

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class PlayerEdgeScrimTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `disable shading removes global control dim but keeps initial loading backdrop`() {
        assertThat(
            playerControlsBackdropColor(
                isTouchLocked = false,
                isInitialLoading = false,
                shadingDisabled = true,
                fadeAlpha = 1f,
            ),
        ).isEqualTo(Color.Transparent)
        assertThat(
            playerControlsBackdropColor(
                isTouchLocked = false,
                isInitialLoading = true,
                shadingDisabled = true,
                fadeAlpha = 1f,
            ),
        ).isNotEqualTo(Color.Transparent)
    }

    @Test
    fun `top and bottom edge gradients are absent when disabled and present when enabled`() {
        var edge by mutableStateOf(ScrimEdge.Top)
        var disabled by mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Box(
                        modifier =
                            Modifier
                                .align(if (edge == ScrimEdge.Top) Alignment.TopCenter else Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(120.dp)
                                .playerEdgeScrim(edge = edge, color = Color.Black, disabled = disabled),
                    )
                }
            }
        }

        ScrimEdge.values().forEach { candidate ->
            rule.runOnIdle {
                edge = candidate
                disabled = true
            }
            val disabledImage = rule.onRoot().captureToImage()
            val disabledPixels = disabledImage.toPixelMap()
            val disabledEdgeY = if (candidate == ScrimEdge.Top) 0 else disabledImage.height - 1
            assertThat(disabledPixels[disabledImage.width / 2, disabledEdgeY].red).isAtLeast(0.99f)

            rule.runOnIdle { disabled = false }
            val enabledImage = rule.onRoot().captureToImage()
            val enabledPixels = enabledImage.toPixelMap()
            val enabledEdgeY = if (candidate == ScrimEdge.Top) 0 else enabledImage.height - 1
            assertThat(enabledPixels[enabledImage.width / 2, enabledEdgeY].red).isLessThan(0.99f)
        }
    }

    @Test
    fun `portrait fullscreen top and bottom gradients both follow the disable switch`() {
        var disabled by mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    PortraitFullscreenEdgeScrims(disabled = disabled, modifier = Modifier.fillMaxSize())
                }
            }
        }

        val enabledImage = rule.onRoot().captureToImage()
        val enabledPixels = enabledImage.toPixelMap()
        assertThat(enabledPixels[enabledImage.width / 2, 0].red).isLessThan(0.99f)
        assertThat(enabledPixels[enabledImage.width / 2, enabledImage.height - 1].red).isLessThan(0.99f)
        assertThat(enabledPixels[enabledImage.width / 2, enabledImage.height / 2].red).isAtLeast(0.99f)

        rule.runOnIdle { disabled = true }
        val disabledImage = rule.onRoot().captureToImage()
        val disabledPixels = disabledImage.toPixelMap()
        assertThat(disabledPixels[disabledImage.width / 2, 0].red).isAtLeast(0.99f)
        assertThat(disabledPixels[disabledImage.width / 2, disabledImage.height - 1].red).isAtLeast(0.99f)
    }
}

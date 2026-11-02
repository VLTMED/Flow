package io.github.aedev.flow.ui.components.shared

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class CommentThreadConnectorDirectionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rtlConnectorMirrorsNestedAndLeafReplyLinesFromLtr() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val continuesBelow = mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                ThreadConnector(
                    color = Color.Red,
                    continuesBelow = continuesBelow.value,
                    modifier = Modifier.size(width = 20.dp, height = 72.dp).testTag("thread-connector"),
                )
            }
        }
        rule.waitForIdle()
        val ltr = rule.onNodeWithTag("thread-connector").captureToImage().toPixelMap()

        rule.runOnIdle { direction.value = LayoutDirection.Rtl }
        rule.waitForIdle()
        val rtl = rule.onNodeWithTag("thread-connector").captureToImage().toPixelMap()

        assertHorizontalMirror(ltr, rtl)

        rule.runOnIdle {
            direction.value = LayoutDirection.Ltr
            continuesBelow.value = false
        }
        rule.waitForIdle()
        val ltrLeaf = rule.onNodeWithTag("thread-connector").captureToImage().toPixelMap()
        rule.runOnIdle { direction.value = LayoutDirection.Rtl }
        rule.waitForIdle()
        val rtlLeaf = rule.onNodeWithTag("thread-connector").captureToImage().toPixelMap()

        assertHorizontalMirror(ltrLeaf, rtlLeaf)
    }

    private fun assertHorizontalMirror(
        ltr: PixelMap,
        rtl: PixelMap,
    ) {
        assertThat(rtl.width).isEqualTo(ltr.width)
        assertThat(rtl.height).isEqualTo(ltr.height)
        for (y in 0 until ltr.height) {
            for (x in 0 until ltr.width) {
                val expected = ltr[ltr.width - x - 1, y]
                val actual = rtl[x, y]
                // Canvas antialiasing can differ slightly on mirrored fractional pixel edges.
                assertThat(abs(actual.red - expected.red)).isAtMost(0.08f)
                assertThat(abs(actual.green - expected.green)).isAtMost(0.08f)
                assertThat(abs(actual.blue - expected.blue)).isAtMost(0.08f)
                assertThat(abs(actual.alpha - expected.alpha)).isAtMost(0.08f)
            }
        }
    }
}

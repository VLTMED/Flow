package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OverlayPreferenceMigrationTest {
    @Test
    fun `current values take precedence over legacy values`() {
        assertThat(resolveMigratedOverlayPreference(currentValue = false, legacyValue = true, defaultValue = false)).isFalse()
        assertThat(resolveMigratedOverlayPreference(currentValue = true, legacyValue = false, defaultValue = false)).isTrue()
    }

    @Test
    fun `legacy values survive when the new key is absent`() {
        assertThat(resolveMigratedOverlayPreference(currentValue = null, legacyValue = true, defaultValue = false)).isTrue()
        assertThat(resolveMigratedOverlayPreference(currentValue = null, legacyValue = false, defaultValue = true)).isFalse()
    }

    @Test
    fun `defaults are used only when both keys are absent`() {
        assertThat(resolveMigratedOverlayPreference(currentValue = null, legacyValue = null, defaultValue = false)).isFalse()
        assertThat(resolveMigratedOverlayPreference(currentValue = null, legacyValue = null, defaultValue = true)).isTrue()
    }
}

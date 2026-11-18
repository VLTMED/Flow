package io.github.aedev.flow.data.translation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TranslationSecretStoreFileTest {
    @Test
    fun `translation key file is under no backup directory`() {
        val noBackupDirectory = File("no_backup")
        val file = translationSecretDataStoreFile(noBackupDirectory)

        assertEquals(noBackupDirectory, file.parentFile)
        assertEquals("translation_secrets.preferences_pb", file.name)
    }
}

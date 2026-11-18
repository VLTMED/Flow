package io.github.aedev.flow.data.translation

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val TranslationApiKey = stringPreferencesKey("api_key")

/** Keep server credentials on this device; the normal settings DataStore is included in Android backup. */
@Singleton
class TranslationSecretStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: PlayerPreferences,
    ) {
        private val dataStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { translationSecretDataStoreFile(context.noBackupFilesDir) },
            )
        }

        val apiKey: Flow<String?> =
            dataStore.data
                .map { it[TranslationApiKey] }
                .onStart { migrateLegacyApiKey() }
                .distinctUntilChanged()

        suspend fun setApiKey(value: String?) {
            dataStore.edit { stored ->
                val key = value?.trim()?.takeIf { it.isNotEmpty() }
                if (key == null) {
                    stored.remove(TranslationApiKey)
                } else {
                    stored[TranslationApiKey] = key
                }
            }
        }

        private suspend fun migrateLegacyApiKey() {
            if (!dataStore.data.first()[TranslationApiKey].isNullOrBlank()) return
            val legacy = preferences.getLegacyTranslationApiKey()?.takeIf { it.isNotBlank() } ?: return
            val normalized = legacy.trim()
            dataStore.edit { stored ->
                if (stored[TranslationApiKey].isNullOrBlank()) stored[TranslationApiKey] = normalized
            }
            if (dataStore.data.first()[TranslationApiKey] == normalized) {
                preferences.removeLegacyTranslationApiKeyIfUnchanged(legacy)
            }
        }
    }

internal fun translationSecretDataStoreFile(noBackupDirectory: File): File = File(noBackupDirectory, "translation_secrets.preferences_pb")
